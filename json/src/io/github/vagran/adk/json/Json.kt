/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json


import io.github.vagran.adk.TypeToken
import io.github.vagran.adk.json.internal.AppendableWriter
import io.github.vagran.adk.json.internal.TextJsonReader
import io.github.vagran.adk.json.internal.TextJsonWriter
import io.github.vagran.adk.json.internal.codecs.*
import io.github.vagran.adk.omm.OmmParams
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
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

typealias JsonCodecProvider = (type: KType) -> JsonCodec<*>

open class JsonCodecRegistry {
    val typeCodecs: MutableMap<KType, JsonCodec<*>> = HashMap()
    val classCodecs: MutableMap<KClass<*>, JsonCodecProvider> = HashMap()
    val subclassCodecs: MutableMap<KClass<*>, JsonCodecProvider> = HashMap()

    fun Merge(other: JsonCodecRegistry)
    {
        typeCodecs.putAll(other.typeCodecs)
        classCodecs.putAll(other.classCodecs)
        subclassCodecs.putAll(other.subclassCodecs)
    }
}

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
    serializeNulls: Boolean = true,
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
    setAccessible: Boolean = false,
    typeCodecs: Map<KType, JsonCodec<*>> = emptyMap(),
    classCodecs: Map<KClass<*>, JsonCodecProvider> = emptyMap(),
    subclassCodecs: Map<KClass<*>, JsonCodecProvider> = emptyMap(),
    additionalRegistries: List<JsonCodecRegistry> = emptyList()) {

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

    inline fun <reified T> FromJson(input: InputStream): T? =
        FromJson(input, TypeToken.Create<T>().type)


    fun RegisterCodec(type: KType, codec: JsonCodec<*>)
    {
        codecs[type] = codec
    }

    fun <T> RegisterCodec(type: TypeToken<T>, codec: JsonCodec<T>)
    {
        return RegisterCodec(type.type, codec)
    }

    fun <T: Any> RegisterCodec(cls: KClass<T>, codec: JsonCodec<T>)
    {
        return RegisterCodec(TypeToken.Create(cls), codec)
    }

    inline fun <reified T> RegisterCodec(codec: JsonCodec<T>) =
        RegisterCodec(TypeToken.Create(), codec)


    /** Register codec provider for a specific class. */
    fun RegisterClassCodec(cls: KClass<*>, codecProvider: JsonCodecProvider)
    {
        registry.classCodecs[cls] = codecProvider
    }

    inline fun <reified T> RegisterClassCodec(noinline codecProvider: JsonCodecProvider) =
        RegisterClassCodec(T::class, codecProvider)


    /** Register codec provider for a class and all its derived classes. */
    fun RegisterSubclassCodec(cls: KClass<*>, codecProvider: JsonCodecProvider)
    {
        registry.subclassCodecs[cls] = codecProvider
    }

    inline fun <reified T> RegisterSubclassCodec(noinline codecProvider: JsonCodecProvider) =
        RegisterSubclassCodec(T::class, codecProvider)

    // /////////////////////////////////////////////////////////////////////////////////////////////
    internal val ommParams = OmmParams(requireAllFields = requireAllFields,
                                       annotatedOnlyFields = annotatedOnlyFields,
                                       serializeNulls = serializeNulls,
                                       acceptedVisibility = acceptedVisibility,
                                       allowInnerClasses = allowInnerClasses,
                                       requireLateinitVars = requireLateinitVars,
                                       qualifier = qualifier,
                                       qualifiedOnly = qualifiedOnly,
                                       setAccessible = setAccessible)
    private val codecs = ConcurrentHashMap<KType, JsonCodec<*>>()
    private val registry = JsonCodecRegistry()

    init {
        registry.classCodecs[LocalDateTime::class] = { LocalDateTimeCodec() }
        registry.classCodecs[BitSet::class] = { BitSetCodec() }

        registry.subclassCodecs[Path::class] = { PathCodec() }

        codecs.putAll(typeCodecs)
        registry.classCodecs.putAll(classCodecs)
        registry.subclassCodecs.putAll(subclassCodecs)

        additionalRegistries.forEach {
            registry.Merge(it)
        }
    }

    private fun CreateCodec(type: KType): JsonCodec<*>
    {
        val jvmErasure = type.jvmErasure

        for ((cls, provider) in registry.classCodecs) {
            if (jvmErasure == cls) {
                return provider(type)
            }
        }

        for ((cls, provider) in registry.subclassCodecs) {
            if (jvmErasure.isSubclassOf(cls)) {
                return provider(type)
            }
        }

        ommParams.FindAnnotation<JsonClass>(jvmErasure)?.also {
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
        if (jvmErasure.isSubclassOf(Float::class)) {
            return FloatCodec()
        }
        if (jvmErasure.isSubclassOf(Double::class)) {
            return DoubleCodec()
        }
        if (jvmErasure.isSubclassOf(Boolean::class)) {
            return BooleanCodec()
        }

        if (jvmErasure.isSubclassOf(Enum::class)) {
            return EnumCodec(type, this)
        }

        if (jvmErasure == Any::class) {
            return AnyCodec()
        }

        return MappedClassCodec<Any>(type)
    }
}
