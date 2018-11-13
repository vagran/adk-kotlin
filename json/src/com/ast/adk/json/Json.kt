package com.ast.adk.json


import com.ast.adk.json.internal.AppendableWriter
import com.ast.adk.json.internal.TextJsonReader
import com.ast.adk.json.internal.TextJsonWriter
import com.ast.adk.json.internal.codecs.*
import com.ast.adk.omm.OmmParams
import com.ast.adk.omm.TypeToken
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

typealias JsonCodecProvider = (type: KType) -> JsonCodec<*>

/** Encapsulates encoding/decoding parameters and codecs registry.
 * @param allowUnmatchedFields Default value for unmatched fields handling behaviour. Can be
 * overridden for a class by JsonClass.allowUnmatchedFields annotation.
 * @param typeCodecs Predefined codes for specific types (with compile-time information e.g. about
 * type parameters).
 * @param classCodecs Predefined codecs for specific classes.
 * @param subclassCodecs Predefined codecs for a class and all its derived classes.
 */
class Json(
    val prettyPrint: Boolean = false,
    val serializeNulls: Boolean = true,
    val prettyPrintIndent: Int = 2,
    val enableComments: Boolean = true,
    val allowUnmatchedFields: Boolean = false,
    requireAllFields: Boolean = false,
    annotatedOnlyFields: Boolean = false,
    acceptedVisibility: KVisibility = KVisibility.PUBLIC,
    requireLateinitVars: Boolean = true,
    allowInnerClasses: Boolean = true,
    qualifier: String? = "json",
    qualifiedOnly: Boolean = false,
    typeCodecs: Map<KType, JsonCodec<*>> = emptyMap(),
    classCodecs: Map<KClass<*>, JsonCodecProvider> = emptyMap(),
    subclassCodecs: Map<KClass<*>, JsonCodecProvider> = emptyMap()) {

    @Suppress("UNCHECKED_CAST")
    fun <T> GetCodec(type: KType): JsonCodec<T>
    {
        var codec = codecs[type]
        if (codec != null) {
            return codec as JsonCodec<T>
        }
        codec = CreateCodec(type)
        codecs[type] = codec
        codec.Initialize(this)
        return codec as JsonCodec<T>
    }

    fun <T> GetCodec(type: TypeToken<T>): JsonCodec<T>
    {
        return GetCodec(type.type)
    }

    fun <T: Any> GetCodec(cls: KClass<T>): JsonCodec<T>
    {
        return GetCodec(TypeToken.Create(cls))
    }

    inline fun <reified T> GetCodec(): JsonCodec<T> = GetCodec(TypeToken.Create())


    fun <T> GetSerializer(type: KType): JsonSerializer<T>
    {
        return JsonSerializer(this, GetCodec(type))
    }

    fun <T> GetSerializer(type: TypeToken<T>): JsonSerializer<T>
    {
        return JsonSerializer(this, GetCodec(type))
    }

    fun <T: Any> GetSerializer(cls: KClass<T>): JsonSerializer<T>
    {
        return JsonSerializer(this, GetCodec(cls))
    }

    inline fun <reified T> GetSerializer(): JsonSerializer<T> = GetSerializer(
        TypeToken.Create())

    fun <T> GetSerializer(codec: JsonCodec<T>): JsonSerializer<T>
    {
        return JsonSerializer(this, codec)
    }


    fun GetReader(input: InputStream): JsonReader
    {
        return TextJsonReader(
            this,
            InputStreamReader(BufferedInputStream(input), StandardCharsets.UTF_8))
    }

    fun GetReader(input: String): JsonReader
    {
        return TextJsonReader(this, StringReader(input))
    }

    fun GetReader(input: Reader): JsonReader
    {
        return TextJsonReader(this, input)
    }


    fun GetWriter(output: Appendable): JsonWriter
    {
        return TextJsonWriter(this, AppendableWriter(output))
    }

    fun GetWriter(output: Writer): JsonWriter
    {
        return TextJsonWriter(this, output)
    }

    fun GetWriter(output: OutputStream): JsonWriter
    {
        return TextJsonWriter(this, OutputStreamWriter(BufferedOutputStream(output),
                                                       StandardCharsets.UTF_8))
    }


    fun ToJson(obj: Any?): String
    {
        val sb = StringWriter()
        ToJson(obj, sb)
        return sb.toString()
    }

    fun ToJson(obj: Any?, output: Appendable)
    {
        ToJson(obj, GetWriter(output))
    }

    fun ToJson(obj: Any?, output: Writer)
    {
        ToJson(obj, GetWriter(output))
    }

    fun ToJson(obj: Any?, output: OutputStream)
    {
        ToJson(obj, GetWriter(output))
    }

    fun ToJson(obj: Any?, output: JsonWriter)
    {
        if (obj == null) {
            output.WriteNull()
        } else {
            @Suppress("UNCHECKED_CAST")
            (GetCodec(obj::class) as JsonCodec<Any>).Write(obj, output, this)
        }
        output.Finish()
    }


    fun <T> FromJson(input: JsonReader, type: KType): T?
    {
        return GetCodec<T>(type).Read(input, this).also { input.AssertFullConsumption() }
    }

    fun <T> FromJson(input: String, type: KType): T?
    {
        return FromJson(GetReader(input), type)
    }

    fun <T> FromJson(input: Reader, type: KType): T?
    {
        return FromJson(GetReader(input), type)
    }

    fun <T> FromJson(input: InputStream, type: KType): T?
    {
        return FromJson(GetReader(input), type)
    }

    inline fun <reified T> FromJson(input: String): T? = FromJson(input, TypeToken.Create<T>().type)

    inline fun <reified T> FromJson(input: Reader): T? = FromJson(input, TypeToken.Create<T>().type)

    inline fun <reified T> FromJson(input: InputStream): T? = FromJson(input, TypeToken.Create<T>().type)

    // /////////////////////////////////////////////////////////////////////////////////////////////
    internal val ommParams = OmmParams(requireAllFields = requireAllFields,
                                       annotatedOnlyFields = annotatedOnlyFields,
                                       acceptedVisibility = acceptedVisibility,
                                       allowInnerClasses = allowInnerClasses,
                                       requireLateinitVars = requireLateinitVars,
                                       qualifier = qualifier,
                                       qualifiedOnly = qualifiedOnly)
    private val codecs = ConcurrentHashMap<KType, JsonCodec<*>>()
    private val classCodecs = HashMap<KClass<*>, JsonCodecProvider>()
    private val subclassCodecs = HashMap<KClass<*>, JsonCodecProvider>()

    init {
        this.classCodecs[LocalDateTime::class] = { LocalDateTimeCodec() }
        this.classCodecs[BitSet::class] = { BitSetCodec() }

        this.subclassCodecs[Path::class] = { PathCodec() }

        codecs.putAll(typeCodecs)
        this.classCodecs.putAll(classCodecs)
        this.subclassCodecs.putAll(subclassCodecs)
    }

    private fun CreateCodec(type: KType): JsonCodec<*>
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

        jvmErasure.findAnnotation<JsonClass>()?.also {
            ann ->
            if (ann.codec != Unit::class) {
                if (!ann.codec.isSubclassOf(JsonCodec::class)) {
                    throw Error("Invalid codec class provided in annotation for $jvmErasure")
                }
                return ann.codec.createInstance() as JsonCodec<*>
            }
        }

        if (jvmErasure.isSubclassOf(List::class) || jvmErasure.isSubclassOf(Collection::class)) {
            return ListCodec(type)
        }
        if (jvmErasure.isSubclassOf(Map::class)) {
            return MapCodec(type)
        }

        if (jvmErasure.isSubclassOf(IntArray::class)) {
            return IntArrayCodec()
        }
        if (jvmErasure.isSubclassOf(LongArray::class)) {
            return LongArrayCodec()
        }
        if (jvmErasure.isSubclassOf(DoubleArray::class)) {
            return DoubleArrayCodec()
        }
        if (jvmErasure.java.isArray) {
            return ArrayCodec(type)
        }

        if (jvmErasure.isSubclassOf(String::class)) {
            return StringCodec()
        }
        if (jvmErasure.isSubclassOf(Int::class)) {
            return IntCodec()
        }
        if (jvmErasure.isSubclassOf(Long::class)) {
            return LongCodec()
        }
        if (jvmErasure.isSubclassOf(Double::class)) {
            return DoubleCodec()
        }
        if (jvmErasure.isSubclassOf(Boolean::class)) {
            return BooleanCodec()
        }

        if (jvmErasure.isSubclassOf(Enum::class)) {
            return EnumCodec(type)
        }

        if (jvmErasure == Any::class) {
            return AnyCodec()
        }

        return MappedClassCodec<Any>(type)
    }
}
