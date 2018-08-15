package com.ast.adk.json.internal.codecs

import com.ast.adk.json.Json
import com.ast.adk.json.JsonCodec
import com.ast.adk.json.JsonReader
import com.ast.adk.json.JsonWriter
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

class MapCodec(type: KType): JsonCodec<Map<*, *>> {

    override fun WriteNonNull(obj: Map<*, *>, writer: JsonWriter, json: Json)
    {
        writer.BeginObject()
        for ((key, element) in obj) {
            if (key == null) {
                continue
            }
            writer.WriteName(key.toString())
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
        writer.EndObject()
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): Map<String, *>
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
            if (type.arguments.size < 2) {
                return null
            }
            return type.arguments[1].type?.jvmErasure
        }
    }

}
