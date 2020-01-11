package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.Json
import io.github.vagran.adk.json.JsonCodec
import io.github.vagran.adk.json.JsonReader
import io.github.vagran.adk.json.JsonWriter
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

class ArrayCodec(type: KType): JsonCodec<Array<*>> {
    override fun WriteNonNull(obj: Array<*>, writer: JsonWriter, json: Json)
    {
        writer.BeginArray()
        for (element in obj) {
            if (element == null) {
                writer.WriteNull()
                continue
            }
            @Suppress("UNCHECKED_CAST")
            val codec = if (elementClass != null) {
                if (element::class != elementClass) {
                    throw IllegalArgumentException(
                        "Element type mismatch, expected $elementClass, have ${element::class}")
                }
                elementCodec
            } else {
                json.GetCodec(element::class) as JsonCodec<Any>
            }
            codec.WriteNonNull(element, writer, json)
        }
        writer.EndArray()
    }

    @Suppress("UNCHECKED_CAST")
    override fun ReadNonNull(reader: JsonReader, json: Json): Array<*>
    {
        val result = ArrayList<Any?>()
        reader.BeginArray()
        while (reader.HasNext()) {
            result.add(readElementCodec.Read(reader, json))
        }
        reader.EndArray()
        if (elementClass != null) {
            val array = java.lang.reflect.Array.newInstance(elementClass.java, result.size)
            return result.toArray(array as Array<Any?>)
        }
        return result.toArray()
    }

    override fun Initialize(json: Json)
    {
        if (elementClass != null) {
            @Suppress("UNCHECKED_CAST")
            elementCodec = json.GetCodec(elementClass)as JsonCodec<Any>
            readElementCodec = elementCodec
        } else {
            readElementCodec = json.GetCodec()
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val elementClass: KClass<*>? = type.arguments[0].type?.jvmErasure
    private lateinit var elementCodec: JsonCodec<Any>
    private lateinit var readElementCodec: JsonCodec<Any>
}
