package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.*
import io.github.vagran.adk.json.internal.ConstructorFunc
import io.github.vagran.adk.json.internal.GetDefaultConstructor
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

class MapCodec(private val type: KType): JsonCodec<Map<*, *>> {

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
                if (!elementClass.isInstance(element)) {
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

    @Suppress("UNCHECKED_CAST")
    override fun ReadNonNull(reader: JsonReader, json: Json): Map<String, *>
    {
        if (constructor == null) {
            throw JsonReadError("No constructor found for $type")
        }
        val result = constructor.invoke() as MutableMap<String, Any?>
        reader.BeginObject()
        while (reader.HasNext()) {
            result[reader.ReadName()] = readElementCodec.Read(reader, json)
        }
        reader.EndObject()
        return result
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
    private val elementClass: KClass<*>? = GetElementClass(type)
    private lateinit var elementCodec: JsonCodec<Any>
    private lateinit var readElementCodec: JsonCodec<Any>
    private val constructor: ConstructorFunc?

    init {
        val defCtr = GetDefaultConstructor(type.jvmErasure)
        constructor = when {
            defCtr != null -> defCtr
            type.jvmErasure == Map::class || type.jvmErasure == MutableMap::class -> {
                { HashMap<String, Any?>() }
            }
            else -> null
        }
    }

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
