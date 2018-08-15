package com.ast.adk.json.internal.codecs

import com.ast.adk.json.Json
import com.ast.adk.json.JsonCodec
import com.ast.adk.json.JsonReader
import com.ast.adk.json.JsonWriter
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

    override fun ReadNonNull(reader: JsonReader, json: Json): Array<*>
    {
        TODO("not implemented") //XXX
    }

    override fun Initialize(json: Json)
    {
        if (elementClass != null) {
            @Suppress("UNCHECKED_CAST")
            elementCodec = json.GetCodec(elementClass)as JsonCodec<Any>
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val elementClass: KClass<*>? = GetElementClass(type)
    private lateinit var elementCodec: JsonCodec<Any>

    private companion object {
        fun GetElementClass(type: KType): KClass<*>?
        {
            return type.arguments[0].type?.jvmErasure
        }
    }
}
