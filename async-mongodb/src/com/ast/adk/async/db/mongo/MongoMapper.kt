package com.ast.adk.async.db.mongo

import com.ast.adk.async.db.mongo.MongoMapper.Builder.Companion.GetBuiltinCodec
import com.ast.adk.async.db.mongo.MongoMapper.Builder.Companion.GetClassConstructor
import com.ast.adk.async.db.mongo.MongoMapper.Builder.Companion.GetClassHierarchy
import com.ast.adk.async.db.mongo.MongoMapper.Builder.Companion.GetCollectionElementType
import com.mongodb.async.client.MongoCollection
import com.mongodb.async.client.MongoDatabase
import org.bson.*
import org.bson.codecs.*
import org.bson.codecs.configuration.CodecConfigurationException
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import java.lang.reflect.ParameterizedType
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.internal.impl.util.Check
import kotlin.reflect.jvm.javaField
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

        fun <T: Any> GetCollection(db: MongoDatabase, name: String, cls: KClass<T>): MongoCollection<T>
        {
            return db.getCollection<T>(name, cls.java).withCodecRegistry(ForClasses(cls))
        }
    }

    private class Builder(private val mappedClasses: Iterable<KClass<*>>) {

        private val mappedCodecs: MutableMap<KClass<*>, MappedCodec<*>> = HashMap()
        private val pendingClasses: Deque<KClass<*>> = ArrayDeque()

        companion object {
            private val builtinCodecs = listOf(BsonValueCodecProvider(),
                                               ValueCodecProvider(),
                                               DocumentCodecProvider(),
                                               PrimitiveValueCodecProvider())
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

            /** Get type of element of the specified property collection class. Returns null if the
             * specified property class is not a collection.
             */
            fun GetCollectionElementType(prop: KProperty1<*, *>): KClass<*>?
            {
                val cls = prop.returnType.jvmErasure
                if (cls.isSubclassOf(Collection::class)) {
                    val type = prop.javaField?.genericType as? ParameterizedType ?: return null
                    val elCls = type.actualTypeArguments[0] as? Class<*> ?: return null
                    return elCls.kotlin
                }
                return null
            }

            /* Get all non-interface classes in inheritance hierarchy. Order is from base to derived
             * (to have more clear duplicates handling). "Any" base class is not included.
             */
            fun GetClassHierarchy(cls: KClass<*>): Collection<KClass<*>>
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
            fun GetClassConstructor(cls: KClass<*>): ConstructorFunc
            {
                var defCtr: ConstructorFunc? = null
                for (ctr in cls.constructors) {
                    defCtr = CheckConstructor(ctr, cls.isInner)
                    if (defCtr != null) {
                        if (ctr.visibility != KVisibility.PUBLIC) {
                            throw Error("Default constructor for mapped class " +
                                        "${cls.qualifiedName} must be public")
                        }
                        break
                    }
                }
                if (defCtr == null) {
                    throw Error("No default constructor for mapped class ${cls.qualifiedName}")
                }
                return defCtr
            }

            /** Check if constructor is suitable for instance creation. It should not have mandatory
             * arguments except outer class instance for inner class.
             */
            private fun CheckConstructor(ctr: KFunction<*>, isInner: Boolean): ConstructorFunc?
            {
                if (isInner && ctr.parameters.isEmpty()) {
                    return null
                }
                var outerParam: KParameter? = null
                for (paramIdx in 0 until ctr.parameters.size) {
                    val param = ctr.parameters[paramIdx]
                    if (isInner && paramIdx == 0) {
                        if (param.isOptional) {
                            return null
                        }
                        outerParam = param
                        continue
                    }
                    if (!param.isOptional) {
                        return null
                    }
                }
                if (isInner) {
                    val _outerParam = outerParam!!
                    return {outerInstance ->  ctr.callBy(mapOf(_outerParam to outerInstance)) as Any}
                } else {
                    return { _ ->  ctr.callBy(emptyMap()) as Any}
                }
            }
        }

        private class MappedCodec<T: Any>(val classDesc: ClassDesc<T>):
            Codec<T> {

            override fun getEncoderClass(): Class<T>
            {
                return classDesc.cls.java
            }

            @Suppress("UNCHECKED_CAST")
            override fun encode(writer: BsonWriter, value: T, encoderContext: EncoderContext)
            {
                writer.writeStartDocument()
                for (field in classDesc.fields.values) {
                    val fieldValue = field.getter(value)
                    if (fieldValue != null) {
                        writer.writeName(field.name)
                        when {
                            field.isArray -> {
                                writer.writeStartArray()
                                WriteArray(writer, encoderContext, fieldValue,
                                    field.elementType!!, field.codec!!)
                                writer.writeEndArray()
                            }

                            field.elementType != null -> {
                                writer.writeStartArray()
                                WriteCollection(writer, encoderContext, fieldValue, field.codec!!)
                                writer.writeEndArray()
                            }

                            else ->
                                encoderContext.encodeWithChildContext(field.codec as Codec<Any>,
                                                                      writer,
                                                                      fieldValue)
                        }
                    }
                }
                writer.writeEndDocument()
            }

            override fun decode(reader: BsonReader, decoderContext: DecoderContext): T
            {
                return decode(reader, decoderContext, null)
            }

            @Suppress("UNCHECKED_CAST")
            private fun decode(reader: BsonReader, decoderContext: DecoderContext,
                               parentContext: CodecContext?): T
            {
                val item: T
                item =
                    if (classDesc.outerClass != null) {
                        if (parentContext == null) {
                            throw Error("No codecContext for inner class decoding")
                        }
                        val instance = parentContext.FindInstance(classDesc.outerClass)
                        classDesc.constructor.invoke(instance) as T
                    } else {
                        classDesc.constructor.invoke(null) as T
                    }
                val context = CodecContext(item, parentContext)

                reader.readStartDocument()
                while (true) {
                    val type = reader.readBsonType()
                    if (type == BsonType.END_OF_DOCUMENT) {
                        break
                    }
                    if (type == BsonType.NULL) {
                        reader.skipName()
                        reader.readNull()
                        continue
                    }
                    val fieldName = reader.readName()
                    val field = classDesc.fields[fieldName]
                    if (field != null) {
                        if (field.elementType != null && !field.isArray) {
                            ReadCollection(reader, decoderContext, item, field, context)
                        } else {
                            if (field.setter == null) {
                                throw Error("Field is not writable: $field")
                            }
                            val value: Any =
                                when {
                                    field.isArray -> {
                                        ReadArray(reader, decoderContext, field, context)
                                    }
                                    field.codec is MappedCodec ->
                                        (field.codec as MappedCodec<Any>).decode(reader,
                                                                                 decoderContext,
                                                                                 context)
                                    else -> field.codec!!.decode(reader, decoderContext)
                                }
                            field.setter.invoke(item, value)
                        }
                    } else {
                        reader.skipValue()
                    }
                }
                reader.readEndDocument()

                return item
            }

            @Suppress("UNCHECKED_CAST")
            private fun WriteArray(writer: BsonWriter, encoderContext: EncoderContext, array: Any,
                                   elementClass: KClass<*>, codec: Codec<*>)
            {
                if (elementClass.java.isPrimitive) {
                    when (elementClass) {
                        Byte::class ->
                            for (element in array as ByteArray) {
                                writer.writeInt32(element.toInt())
                            }
                        Short::class ->
                            for (element in array as ShortArray) {
                                writer.writeInt32(element.toInt())
                            }
                        Int::class ->
                            for (element in array as IntArray) {
                                writer.writeInt32(element)
                            }
                        Long::class ->
                            for (element in array as LongArray) {
                                writer.writeInt64(element)
                            }
                        Char::class ->
                            for (element in array as CharArray) {
                                (codec as Codec<Char>).encode(writer, element, encoderContext)
                            }
                        Float::class ->
                            for (element in array as FloatArray) {
                                writer.writeDouble(element.toDouble())
                            }
                        Double::class ->
                            for (element in array as DoubleArray) {
                                writer.writeDouble(element)
                            }
                        else -> throw Error("Unhandled type: ${elementClass.simpleName}")
                    }
                } else {
                    for (element in array as Array<Any?>) {
                        if (element != null) {
                            encoderContext.encodeWithChildContext(codec as Codec<Any>,
                                                                  writer, element)
                        } else {
                            writer.writeNull()
                        }
                    }
                }
            }

            @Suppress("UNCHECKED_CAST")
            private fun WriteCollection(writer: BsonWriter,
                                        encoderContext: EncoderContext,
                                        collection: Any,
                                        codec: Codec<*>)
            {
                (collection as Collection<*>).forEach {
                    element ->
                    if (element != null) {
                        encoderContext.encodeWithChildContext(codec as Codec<Any>, writer, element)
                    } else {
                        writer.writeNull()
                    }
                }
            }

            @Suppress("UNCHECKED_CAST")
            private fun ReadArray(reader: BsonReader, decoderContext: DecoderContext,
                                  field: FieldDesc, context: CodecContext): Any
            {
                reader.readStartArray()
                val elementClass = field.elementType
                val values = ArrayList<Any?>()
                while (true) {
                    val type = reader.readBsonType()
                    if (type == BsonType.END_OF_DOCUMENT) {
                        break
                    }
                    if (field.isPrimitive) {
                        when (elementClass) {
                            Byte::class, Short::class, Int::class ->
                                values.add(reader.readInt32())
                            Long::class ->
                                values.add(reader.readInt64())
                            Char::class ->
                                values.add((field.codec as Codec<Char>)
                                    .decode(reader, decoderContext))
                            Float::class, Double::class ->
                                values.add(reader.readDouble())
                            else ->
                                throw Error("Unhandled type: ${elementClass!!.simpleName}")
                        }
                    } else {
                        values.add(ReadValue(type, field, reader, decoderContext, context))
                    }
                }
                reader.readEndArray()
                val n = values.size
                if (field.isPrimitive) {
                    when (elementClass) {
                        Byte::class -> {
                            val result = ByteArray(n)
                            for (i in 0 until n) {
                                result[i] = (values[i] as Int).toByte()
                            }
                            return result
                        }
                        Short::class -> {
                            val result = ShortArray(n)
                            for (i in 0 until n) {
                                result[i] = (values[i] as Int).toShort()
                            }
                            return result
                        }
                        Int::class -> {
                            val result = IntArray(n)
                            for (i in 0 until n) {
                                result[i] = values[i] as Int
                            }
                            return result
                        }
                        Long::class -> {
                            val result = LongArray(n)
                            for (i in 0 until n) {
                                result[i] = values[i] as Long
                            }
                            return result
                        }
                        Char::class -> {
                            val result = CharArray(n)
                            for (i in 0 until n) {
                                result[i] = values[i] as Char
                            }
                            return result
                        }
                        Float::class -> {
                            val result = FloatArray(n)
                            for (i in 0 until n) {
                                result[i] = (values[i] as Double).toFloat()
                            }
                            return result
                        }
                        Double::class -> {
                            val result = DoubleArray(n)
                            for (i in 0 until n) {
                                result[i] = values[i] as Double
                            }
                            return result
                        }
                        else -> throw Error("Unhandled type: ${elementClass!!.simpleName}")
                    }
                } else {
                    val result = java.lang.reflect.Array.newInstance(elementClass!!.java, n)
                    System.arraycopy(values.toTypedArray(), 0, result, 0, n)
                    return result
                }
            }

            @Suppress("UNCHECKED_CAST")
            private fun ReadCollection(reader: BsonReader,
                                       decoderContext: DecoderContext,
                                       item: T,
                                       field: FieldDesc,
                                       context: CodecContext)
            {
                var collection = field.getter(item) as MutableCollection<Any?>?
                val isNew: Boolean
                if (collection == null) {
                    if (field.setter == null) {
                        throw Error("Field is not writable: $field")
                    }
                    collection = field.type.createInstance() as MutableCollection<Any?>
                    isNew = true
                } else {
                    collection.clear()
                    isNew = false
                }
                reader.readStartArray()
                while (true) {
                    val type = reader.readBsonType()
                    if (type == BsonType.END_OF_DOCUMENT) {
                        break
                    }
                    if (type == BsonType.NULL) {
                        collection.add(null)
                        reader.readNull()
                    } else {
                        collection.add(ReadValue(type, field, reader, decoderContext, context))
                    }
                }
                reader.readEndArray()
                if (isNew) {
                    val _collection = collection
                    field.setter!!.invoke(item, _collection)
                }
            }

            @Suppress("UNCHECKED_CAST")
            private fun ReadValue(type: BsonType,
                                  field: FieldDesc,
                                  reader: BsonReader,
                                  decoderContext: DecoderContext,
                                  context: CodecContext): Any?
            {
                return when {
                    type == BsonType.NULL -> {
                        reader.readNull()
                        null
                    }
                    field.codec is MappedCodec ->
                        (field.codec as MappedCodec<Any>).decode(reader,
                                                                 decoderContext,
                                                                 context)
                    else ->
                        (field.codec as Codec<Any>).decode(reader, decoderContext)
                }
            }
        }

        private class ClassDesc<T: Any>(val cls: KClass<T>) {
            val outerClass: KClass<*>? = if (cls.isInner) cls.java.enclosingClass?.kotlin else null
            val constructor: ConstructorFunc = GetClassConstructor(cls)
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

            fun ResolveCodecs(registry: CodecRegistry)
            {
                for (field in fields.values) {
                    field.codec = registry.get(
                        if (field.elementType != null) field.elementType.java else field.type.java)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private class FieldDesc(val name: String,
                                val property: KProperty1<*, *>,
                                val enclosingClass: KClass<*>) {
            val type = property.returnType.jvmErasure
            /** Null if not collection or array field. */
            val elementType: KClass<*>?
            val getter: GetterFunc
            val setter: SetterFunc?
            var codec: Codec<*>? = null
            val isArray: Boolean
            val isPrimitive: Boolean

            init {
                getter = { obj -> (property as KProperty1<Any, Any?>).get(obj) }
                setter =
                    if (property is KMutableProperty1) {
                        { obj, value -> (property as KMutableProperty1<Any, Any?>).set(obj, value) }
                    } else {
                        null
                    }
                isArray = type.java.isArray
                elementType =
                    if (isArray) {
                        isPrimitive = type.java.componentType.isPrimitive
                        type.java.componentType.kotlin
                    } else {
                        isPrimitive = false
                        GetCollectionElementType(property)
                    }
            }

            override fun toString(): String
            {
                val sb = StringBuilder()
                sb.append(enclosingClass.qualifiedName)
                sb.append("::")
                sb.append(property.name)
                if (name != property.name) {
                    sb.append(" as '")
                    sb.append(name)
                    sb.append("'")
                }
                return sb.toString()
            }
        }

        fun Build(): CodecRegistry
        {
            pendingClasses.addAll(mappedClasses)
            while (!pendingClasses.isEmpty()) {
                val classDesc = ProcessClass(pendingClasses.removeFirst())
                if (classDesc != null) {
                    mappedCodecs[classDesc.cls] = MappedCodec(classDesc)
                }
            }

            val providers = ArrayList<CodecProvider>()
            providers.addAll(builtinCodecs)
            providers.add(MappedCodecsProvider(mappedCodecs.values))
            val registry = CodecRegistries.fromProviders(providers)
            for (mappedCodec in mappedCodecs.values) {
                mappedCodec.classDesc.ResolveCodecs(registry)
            }
            return registry
        }

        private fun ProcessClass(cls: KClass<*>): ClassDesc<*>?
        {
            if (mappedCodecs.containsKey(cls)) {
                return null
            }
            if (GetBuiltinCodec(cls.java) != null) {
                return null
            }

            val classDesc = ClassDesc(cls)

            var idSeen = false
            for (curCls in GetClassHierarchy(cls)) {
                val properties = curCls.declaredMemberProperties
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

                    val type = prop.returnType.jvmErasure
                    val name:String
                    if (fieldAnn != null) {
                        name =
                            if (fieldAnn.name != "") {
                                fieldAnn.name
                            } else {
                                prop.name
                            }
                    } else {
                        if (idSeen) {
                            throw Error(
                                "@MongoId annotation should be used only once. " +
                                "Found duplicate for property ${curCls.qualifiedName}::${prop.name}")
                        }
                        idSeen = true
                        if (type != ObjectId::class) {
                            throw Error(
                                "@MongoId annotation should be used for field with type ObjectId. " +
                                "Found for property ${curCls.qualifiedName}::${prop.name}")
                        }
                        name = "_id"
                    }

                    if (prop.visibility != KVisibility.PUBLIC) {
                        throw Error("Mapped field should be public: " +
                                    "${curCls.qualifiedName}::${prop.name}")
                    }

                    val fd = FieldDesc(name, prop, curCls)
                    val prevField = classDesc.fields.put(fd.name, fd)
                    if (prevField != null) {
                        throw Error("Duplicated field DB name: $fd clashes with $prevField")
                    }

                    if (fd.elementType != null) {
                        if (fd.elementType != cls) {
                            QueueFieldType(fd.elementType)
                        }
                    } else if (type != cls) {
                        QueueFieldType(type)
                    }
                }
            }

            classDesc.Finalize()
            return classDesc
        }

        /** Queue type of a mapped class field as dependency for mapping if applicable. */
        private fun QueueFieldType(cls: KClass<*>)
        {
            if (cls.java.isArray) {
                QueueFieldType(cls.java.componentType.kotlin)
                return
            }
            if (cls.java.isPrimitive) {
                return
            }
            if (pendingClasses.contains(cls)) {
                return
            }
            if (mappedCodecs.containsKey(cls)) {
                return
            }
            if (GetBuiltinCodec(cls.java) != null) {
                return
            }
            pendingClasses.add(cls)
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

    /** Context for resolving outer class instance when creating inner class instance. */
    private class CodecContext(val instance: Any,
                               val parent: CodecContext?) {

        fun FindInstance(cls: KClass<*>): Any
        {
            return when {
                this.instance::class == cls -> instance
                parent != null -> parent.FindInstance(cls)
                else -> throw Error("Required outer class instance not found: ${cls.simpleName}")
            }
        }
    }

    private class PrimitiveValueCodecProvider: CodecProvider {

        private val codecs: MutableMap<Class<*>, Codec<*>> = HashMap()

        init
        {
            codecs[Boolean::class.java] = BooleanCodec()
            codecs[Char::class.java] = CharacterCodec()
            codecs[Byte::class.java] = ByteCodec()
            codecs[Short::class.java] = ShortCodec()
            codecs[Int::class.java] = IntegerCodec()
            codecs[Long::class.java] = LongCodec()
            codecs[Float::class.java] = FloatCodec()
            codecs[Double::class.java] = DoubleCodec()
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(clazz: Class<T>, registry: CodecRegistry): Codec<T>?
        {
            return codecs[clazz] as? Codec<T>
        }
    }
}
