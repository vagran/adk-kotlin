package com.ast.adk.json

import java.io.Reader
import java.lang.Appendable
import java.lang.StringBuilder
import kotlin.reflect.KClass

/** Encapsulates encoding/decoding parameters and codecs registry. */
class Json(val prettyPrint: Boolean = false,
           val escapeHtml: Boolean = false,
           val serializeNulls: Boolean = false) {

    fun <T: Any> GetCodec(cls: KClass<T>): JsonCodec<T>
    {
        TODO()
    }

    inline fun <reified T: Any> GetCodec(): JsonCodec<T> = GetCodec(T::class)

    fun ToJson(obj: Any?): String
    {
        val sb = StringBuilder()
        ToJson(obj, sb)
        return sb.toString()
    }

    fun ToJson(obj: Any?, writer: Appendable)
    {
        ToJson(obj, TextJsonWriter(this, writer))
    }

    fun ToJson(obj: Any?, writer: JsonWriter)
    {
        TODO()
    }

    fun <T: Any> FromJson(data: String, cls: KClass<T>): T?
    {
        TODO()
    }

    inline fun <reified T: Any> FromJson(data: String): T? = FromJson(data, T::class)

    fun <T: Any> FromJson(reader: Reader, cls: KClass<T>): T?
    {
        TODO()
    }

    inline fun <reified T: Any> FromJson(reader: Reader): T? = FromJson(reader, T::class)

    fun <T: Any> FromJson(reader: JsonReader, cls: KClass<T>): T?
    {
        TODO()
    }

    inline fun <reified T: Any> FromJson(reader: JsonReader): T? = FromJson(reader, T::class)
}
