package com.ast.adk

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*

/** Fabric method for Gson instance creation.  */
object DefGson {

    /** Get builder with default parameters applied.  */
    fun Builder(): GsonBuilder
    {
        val b = GsonBuilder()
        b.serializeNulls()
        b.registerTypeAdapter(BitSet::class.java, BitSetAdapter())
        b.registerTypeHierarchyAdapter(Path::class.java, PathAdapter())
        b.registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        b.disableHtmlEscaping()
        return b
    }

    @JvmOverloads
    fun Create(prettyPrint: Boolean = false): Gson
    {
        val b = Builder()
        if (prettyPrint) {
            b.setPrettyPrinting()
        }
        return b.create()
    }

    fun ToJson(value: Any): String
    {
        return Create().toJson(value)
    }

    fun ToJson(value: Any, prettyPrint: Boolean): String
    {
        return Create(prettyPrint).toJson(value)
    }

    fun <T> FromJson(json: String, cls: Class<T>): T
    {
        return cachedGsons.get().fromJson(json, cls)
    }

    inline fun <reified T> FromJson(json: String): T
    {
        return FromJson(json, T::class.java)
    }

    fun <T> FromJson(json: Reader, cls: Class<T>): T
    {
        return cachedGsons.get().fromJson(json, cls)
    }

    inline fun <reified T> FromJson(json: Reader): T
    {
        return FromJson(json, T::class.java)
    }

    fun <T> FromJson(json: JsonElement, cls: Class<T>): T
    {
        return cachedGsons.get().fromJson(json, cls)
    }

    inline fun <reified T> FromJson(json: JsonElement): T
    {
        return FromJson(json, T::class.java)
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////

    private val cachedGsons = ThreadLocal.withInitial<Gson> { Create() }

    private class BitSetAdapter: TypeAdapter<BitSet>() {

        override fun write(out: JsonWriter, value: BitSet?)
        {
            if (value != null) {
                val words = value.toLongArray()
                out.beginArray()
                for (word in words) {
                    out.value(word)
                }
                out.endArray()
            } else {
                out.nullValue()
            }
        }

        override fun read(`in`: JsonReader): BitSet?
        {
            if (`in`.peek() == JsonToken.NULL) {
                `in`.nextNull()
                return null
            }
            `in`.beginArray()
            val words = ArrayList<Long>()
            while (`in`.peek() != JsonToken.END_ARRAY) {
                words.add(`in`.nextLong())
            }
            `in`.endArray()
            val wordsArray = LongArray(words.size)
            for (i in wordsArray.indices) {
                wordsArray[i] = words[i]
            }
            return BitSet.valueOf(wordsArray)
        }
    }

    private class PathAdapter: TypeAdapter<Path>() {

        override fun write(out: JsonWriter, value: Path?)
        {
            if (value != null) {
                out.value(value.toString())
            } else {
                out.nullValue()
            }
        }

        override fun read(`in`: JsonReader): Path?
        {
            if (`in`.peek() == JsonToken.NULL) {
                `in`.nextNull()
                return null
            }
            return Paths.get(`in`.nextString())
        }
    }

    private class LocalDateTimeAdapter: TypeAdapter<LocalDateTime>() {

        override fun write(out: JsonWriter, value: LocalDateTime?)
        {
            if (value != null) {
                out.value(value.toString())
            } else {
                out.nullValue()
            }
        }

        override fun read(`in`: JsonReader): LocalDateTime?
        {
            if (`in`.peek() == JsonToken.NULL) {
                `in`.nextNull()
                return null
            }
            return LocalDateTime.parse(`in`.nextString())
        }
    }

}
