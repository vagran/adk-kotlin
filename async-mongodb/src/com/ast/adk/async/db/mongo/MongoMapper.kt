package com.ast.adk.async.db.mongo

import com.ast.adk.async.db.mongo.codecs.*
import com.ast.adk.omm.OmmParams
import com.ast.adk.omm.TypeToken
import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.codecs.*
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

typealias MongoCodecProvider = (type: KType) -> Codec<*>

class MongoMapper(
    val serializeNulls: Boolean = false,
    val allowUnmatchedFields: Boolean = false,
    requireAllFields: Boolean = false,
    annotatedOnlyFields: Boolean = false,
    acceptedVisibility: KVisibility = KVisibility.PUBLIC,
    requireLateinitVars: Boolean = true,
    allowInnerClasses: Boolean = true,
    qualifier: String? = "mongo",
    qualifiedOnly: Boolean = false,
    typeCodecs: Map<KType, Codec<*>> = emptyMap(),
    classCodecs: Map<KClass<*>, MongoCodecProvider> = emptyMap(),
    subclassCodecs: Map<KClass<*>, MongoCodecProvider> = emptyMap()
): CodecRegistry {

    override fun <T: Any> get(cls: Class<T>): Codec<T>
    {
        return registry.get(cls)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> GetCodec(type: KType): Codec<T>
    {
        var codec = codecs[type]
        if (codec != null) {
            return codec as Codec<T>
        }
        codec = GetCustomCodec(type)
        val existingCodec = codecs.putIfAbsent(type, codec)
        if (existingCodec != null) {
            return existingCodec as Codec<T>
        }
        if (codec is MongoCodec) {
            codec.Initialize(this)
        }
        return codec as Codec<T>
    }

    fun <T> GetCodec(type: TypeToken<T>): Codec<T>
    {
        return GetCodec(type.type)
    }

    fun <T: Any> GetCodec(cls: KClass<T>): Codec<T>
    {
        return GetCodec(TypeToken.Create(cls))
    }

    inline fun <reified T> GetCodec(): Codec<T> = GetCodec(TypeToken.Create())

    @Suppress("UNCHECKED_CAST")
    fun Encode(obj: Any?): BsonDocument
    {
        val writer = BsonDocumentWriter(BsonDocument())
        if (obj == null) {
            writer.writeNull()
        } else {
            val codec: Codec<Any> = GetCodec(obj::class) as Codec<Any>
            codec.encode(writer, obj, EncoderContext.builder().build())
        }
        return writer.document
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    internal val ommParams = OmmParams(requireAllFields = requireAllFields,
                                       annotatedOnlyFields = annotatedOnlyFields,
                                       acceptedVisibility = acceptedVisibility,
                                       allowInnerClasses = allowInnerClasses,
                                       requireLateinitVars = requireLateinitVars,
                                       qualifier = qualifier,
                                       qualifiedOnly = qualifiedOnly)
    private val codecs = ConcurrentHashMap<KType, Codec<*>>()
    private val builtinCodecProviders = listOf(BsonValueCodecProvider(),
                                               ValueCodecProvider(),
                                               PrimitiveValueCodecProvider(),
                                               DocumentCodecProvider())
    private val registry =
        CodecRegistries.fromProviders(builtinCodecProviders + MappedCodecProvider())
    private val classCodecs = HashMap<KClass<*>, MongoCodecProvider>()
    private val subclassCodecs = HashMap<KClass<*>, MongoCodecProvider>()

    init {
//        this.classCodecs[LocalDateTime::class] = { LocalDateTimeCodec() }
//        this.classCodecs[BitSet::class] = { BitSetCodec() }
//
//        this.subclassCodecs[Path::class] = { PathCodec() }

        codecs.putAll(typeCodecs)
        this.classCodecs.putAll(classCodecs)
        this.subclassCodecs.putAll(subclassCodecs)
    }

    private class PrimitiveValueCodecProvider: CodecProvider {

        private val codecs: Map<KClass<*>, Codec<*>>

        init {
            codecs = HashMap()
            codecs[Boolean::class] = BooleanCodec()
            codecs[Char::class] = CharacterCodec()
            codecs[Byte::class] = ByteCodec()
            codecs[Short::class] = ShortCodec()
            codecs[Int::class] = IntegerCodec()
            codecs[Long::class] = LongCodec()
            codecs[Float::class] = FloatCodec()
            codecs[Double::class] = DoubleCodec()
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T: Any> get(cls: Class<T>, registry: CodecRegistry): Codec<T>?
        {
            return codecs[cls.kotlin] as? Codec<T>
        }
    }

    private inner class MappedCodecProvider: CodecProvider {

        @Suppress("UNCHECKED_CAST")
        override fun <T: Any> get(cls: Class<T>, registry: CodecRegistry): Codec<T>
        {
            val type = TypeToken.Create(cls.kotlin).type
            var codec = codecs[type]
            if (codec != null) {
                return codec as Codec<T>
            }
            codec = MappedClassCodec<T>(type)
            val existingCodec = codecs.putIfAbsent(type, codec)
            if (existingCodec != null) {
                return existingCodec as Codec<T>
            }
            codec.Initialize(this@MongoMapper)
            return codec
        }
    }

    private fun GetCustomCodec(type: KType): Codec<*>
    {
        val jvmErasure = type.jvmErasure

        for ((cls, provider) in classCodecs) {
            if (jvmErasure == cls) {
                return provider(type)
            }
        }

        for ((cls, provider) in subclassCodecs) {
            if (jvmErasure.isSubclassOf(cls)) {
                return provider(type)
            }
        }

        jvmErasure.findAnnotation<MongoClass>()?.also {
            ann ->
            if (ann.codec != Unit::class) {
                if (!ann.codec.isSubclassOf(Codec::class)) {
                    throw Error("Invalid codec class provided in annotation for $jvmErasure")
                }
                return ann.codec.createInstance() as Codec<*>
            }
        }

        if (jvmErasure.isSubclassOf(List::class) || jvmErasure.isSubclassOf(Collection::class)) {
            return ListCodec(type, this)
        }

        //XXX

        if (jvmErasure.isSubclassOf(IntArray::class)) {
            return IntArrayCodec()
        }
        if (jvmErasure.isSubclassOf(LongArray::class)) {
            return LongArrayCodec()
        }
        if (jvmErasure.isSubclassOf(FloatArray::class)) {
            return FloatArrayCodec()
        }
        if (jvmErasure.isSubclassOf(DoubleArray::class)) {
            return DoubleArrayCodec()
        }

        if (jvmErasure.java.isArray && !jvmErasure.java.componentType.isPrimitive) {
            return ArrayCodec(type, this)
        }

        return registry.get(jvmErasure.java)
    }
}
