package com.ast.adk.json

import java.io.*

/** Serializer bound to a specific class. Provides best performance for mapped classes. */
class JsonSerializer<T>(private val json: Json,
                        private val codec: JsonCodec<T>) {

    fun ToJson(obj: T?): String
    {
        val sb = StringWriter()
        ToJson(obj, sb)
        return sb.toString()
    }

    fun ToJson(obj: T?, output: Writer)
    {
        ToJson(obj, json.GetWriter(output))
    }

    fun ToJson(obj: T?, output: Appendable)
    {
        ToJson(obj, json.GetWriter(output))
    }

    fun ToJson(obj: T?, output: OutputStream)
    {
        ToJson(obj, json.GetWriter(output))
    }

    fun FromJson(input: String): T?
    {
        return FromJson(json.GetReader(input))
    }

    fun FromJson(input: Reader): T?
    {
        return FromJson(json.GetReader(input))
    }

    fun FromJson(input: InputStream): T?
    {
        return FromJson(json.GetReader(input))
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    @Suppress("NOTHING_TO_INLINE")
    private inline fun ToJson(obj: T?, writer: JsonWriter)
    {
        codec.Write(obj, writer, json)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun FromJson(reader: JsonReader): T?
    {
        return codec.Read(reader, json)
    }
}
