package com.ast.adk.json

import com.ast.adk.json.internal.AppendableWriter
import com.ast.adk.json.internal.TextJsonReader
import com.ast.adk.json.internal.TextJsonWriter
import java.io.*
import java.lang.Appendable
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass
import kotlin.reflect.KType

/** Encapsulates encoding/decoding parameters and codecs registry. */
class Json(val prettyPrint: Boolean = false,
           val escapeHtml: Boolean = false,
           val serializeNulls: Boolean = false) {

    fun <T> GetCodec(type: KType): JsonCodec<T>
    {
        TODO()
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

    inline fun <reified T> GetSerializer(): JsonSerializer<T> = GetSerializer(TypeToken.Create())


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
        return TextJsonWriter(this, OutputStreamWriter(
            BufferedOutputStream(output)))
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

    fun ToJson(obj: Any?, output: JsonWriter)
    {
        if (obj == null) {
            output.WriteNull()
            return
        }
        @Suppress("UNCHECKED_CAST")
        (GetCodec(obj::class) as JsonCodec<Any>).Write(obj, output, this)
    }


    fun <T> FromJson(input: JsonReader, type: KType): T?
    {
        return GetCodec<T>(type).Read(input, this)
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
}
