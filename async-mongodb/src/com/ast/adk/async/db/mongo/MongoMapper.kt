package com.ast.adk.async.db.mongo

import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.*
import org.bson.codecs.configuration.CodecConfigurationException
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.jvmErasure

/**
 * @param outerInstance Outer class instance for inner class, pass null for non-inner class.
 * @return Created instance.
 */
private typealias ConstructorFunc = (outerInstance: Any?) -> Any
private typealias GetterFunc = (obj: Any) -> Any?
private typealias SetterFunc = (obj: Any, value: Any?) -> Unit

class MongoMapper {

    companion object {
        fun ForClasses(vararg classes: KClass<*>): CodecRegistry
        {
            return ForClasses(classes.asIterable())
        }

        /** Create codec registry for the specified mapped classes. Nested classes (ones used as
         * types of mapped fields) are recognized automatically and are not needed to be specified
         * in this method arguments).
         * Example of the result usage:
         * {@code
         * database.getCollection("Items", Item::class.java).withCodecRegistry(registry)
         * }
         */
        fun ForClasses(classes: Iterable<KClass<*>>): CodecRegistry
        {
            return Builder(classes).Build()
        }

        @Suppress("UNCHECKED_CAST")
        fun <T: Any> EncodeObject(codecRegistry: CodecRegistry, obj: T): BsonDocument
        {
            val codec = codecRegistry.get(obj::class.java) as Codec<T>
            val writer = BsonDocumentWriter(BsonDocument())
            codec.encode(writer, obj, EncoderContext.builder().build())
            return writer.document
        }
    }

    private class Builder(private val mappedClasses: Iterable<KClass<*>>) {

        companion object {
            private val builtinCodecs = listOf(BsonValueCodecProvider(),
                                               ValueCodecProvider(),
                                               DocumentCodecProvider())
            private val builtinRegistry = CodecRegistries.fromProviders(builtinCodecs)

            /** @return  built-in codec instance for the specified class if exists. Null if does not
             * exist.
             */
            fun GetBuiltinCodec(cls: Class<*>): Codec<*>?
            {
                return try {
                    builtinRegistry.get(cls)
                } catch (_: CodecConfigurationException) {
                    null
                }
            }
        }

        private val mappedCodecs: MutableMap<KClass<*>, MappedCodec<*>> = HashMap()
        private val pendingClasses: Deque<KClass<*>> = ArrayDeque()

        private class MappedCodec<T: Any>(private val cls: KClass<T>):
            Codec<T> {

            override fun getEncoderClass(): Class<T>
            {
                return cls.java
            }

            override fun encode(writer: BsonWriter, value: T, encoderContext: EncoderContext)
            {

            }

            override fun decode(reader: BsonReader, decoderContext: DecoderContext): T
            {
                TODO()
            }
        }

        private class ClassDesc(private val cls: KClass<*>) {
            var isInner = false
            var constructor: ConstructorFunc? = null
            /** True if the object has read-only mapped properties and thus can only be encoded. */
            var encodeOnly = false
            /** Indexed by DB name. */
            val fields: MutableMap<String, FieldDesc> = TreeMap()

            fun Finalize()
            {
                if (fields.isEmpty()) {
                    throw Error("Mapped class does not have mapped fields: ${cls.qualifiedName}")
                }
                for (fd in fields.values) {
                    if (fd.setter == null) {
                        encodeOnly = true
                        break
                    }
                }
            }
        }

        private class FieldDesc(val name: String, val property: KProperty1<*, *>) {
            val getter: GetterFunc
            val setter: SetterFunc?

            init {
                getter = property::get as GetterFunc
                setter = if (property is KMutableProperty1) {
                    property::set as SetterFunc
                } else {
                    null
                }
            }
        }

        fun Build(): CodecRegistry
        {
            pendingClasses.addAll(mappedClasses)
            while (!pendingClasses.isEmpty()) {
                ProcessClass(pendingClasses.removeFirst())
            }

            val providers = ArrayList<CodecProvider>()
            providers.addAll(builtinCodecs)
            providers.add(MappedCodecsProvider(mappedCodecs.values))
            return CodecRegistries.fromProviders(providers)
        }

        private fun ProcessClass(cls: KClass<*>): ClassDesc?
        {
            if (mappedCodecs.containsKey(cls)) {
                return null
            }
            if (GetBuiltinCodec(cls.java) != null) {
                return null
            }

            val classDesc = ClassDesc(cls)
            classDesc.isInner = cls.isInner
            classDesc.constructor = GetClassConstructor(cls)

            var idSeen = false
            for (curCls in GetClassHierarchy(cls)) {
                val properties = curCls.memberProperties
                for (prop in properties) {
                    val fieldAnn = prop.findAnnotation<MongoField>()
                    val idAnn = prop.findAnnotation<MongoId>()
                    if (fieldAnn == null && idAnn == null) {
                        continue
                    }
                    if (fieldAnn != null && idAnn != null) {
                        throw Error(
                            "@MongoId cannot be used simultaneously with @MongoField annotation. " +
                            "Found for property ${curCls.qualifiedName}::${prop.name}")
                    }

                    val name:String
                    if (fieldAnn != null) {
                        if (fieldAnn.name != "") {
                            name = fieldAnn.name
                        } else {
                            name = prop.name
                        }
                    } else {
                        if (idSeen) {
                            throw Error(
                                "@MongoId annotation should be used only once. " +
                                "Found duplicate for property ${cls.qualifiedName}::${prop.name}")
                        }
                        idSeen = true
                        if (prop.returnType.jvmErasure != ObjectId::class) {
                            throw Error(
                                "@MongoId annotation should be used for field with type ObjectId. " +
                                "Found for property ${cls.qualifiedName}::${prop.name}")
                        }
                        name = "_id"
                    }

                    val fd = FieldDesc(name, prop)
                    classDesc.fields[fd.name] = fd
                }
            }

            classDesc.Finalize()
            return classDesc
        }

        /* Get all non-interface classes in inheritance hierarchy. Order is from base to derived
         * (to have more clear duplicates handling). "Any" base class is not included.
         */
        private fun GetClassHierarchy(cls: KClass<*>): Collection<KClass<*>>
        {
            val classes = ArrayDeque<KClass<*>>()
            var curCls = cls
            while (true) {
                if (curCls == Any::class) {
                    break
                }
                classes.addFirst(curCls)
                for (_cls in curCls.superclasses) {
                    if (_cls.java.isInterface) {
                        continue
                    }
                    curCls = _cls
                    break
                }
            }
            return classes
        }

        /** Get constructor for the specified class. */
        private fun GetClassConstructor(cls: KClass<*>): ConstructorFunc
        {
            var defCtr: KFunction<*>? = null
            for (ctr in cls.constructors) {
                if (cls.isInner) {
                    if (ctr.parameters.size == 1) {
                        defCtr = ctr
                        break
                    }
                } else {
                    if (ctr.parameters.isEmpty()) {
                        defCtr = ctr
                        break
                    }
                }
            }
            if (defCtr == null) {
                throw Error("No default constructor for mapped class ${cls.qualifiedName}")
            }
            if (defCtr.visibility != KVisibility.PUBLIC) {
                throw Error("Default constructor for mapped class ${cls.qualifiedName} must be public")
            }
            if (cls.isInner) {
                return {outerInstance ->  defCtr.call(outerInstance) as Any}
            } else {
                return { _ ->  defCtr.call() as Any}
            }
        }
    }

    private class MappedCodecsProvider(codecs: Collection<Codec<*>>):
            CodecProvider {

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(clazz: Class<T>, registry: CodecRegistry): Codec<T>?
        {
            return codecs[clazz] as Codec<T>?
        }

        private val codecs: MutableMap<Class<*>, Codec<*>> = HashMap()

        init {
            for (codec in codecs) {
                this.codecs[codec.encoderClass] = codec
            }
        }
    }
}
