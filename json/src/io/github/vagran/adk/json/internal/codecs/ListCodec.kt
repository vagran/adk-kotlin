/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.*
import io.github.vagran.adk.json.internal.ConstructorFunc
import io.github.vagran.adk.json.internal.GetDefaultConstructor
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

class ListCodec(private val type: KType): JsonCodec<Collection<*>> {

    override fun WriteNonNull(obj: Collection<*>, writer: JsonWriter, json: Json)
    {
        writer.BeginArray()
        for (element in obj) {
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
        writer.EndArray()
    }

    @Suppress("UNCHECKED_CAST")
    override fun ReadNonNull(reader: JsonReader, json: Json): Collection<*>
    {
        if (constructor == null) {
            throw JsonReadError("No constructor found for $type")
        }
        val result = constructor.invoke() as MutableCollection<Any?>
        reader.BeginArray()
        while (reader.HasNext()) {
            result.add(readElementCodec.Read(reader, json))
        }
        reader.EndArray()
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
        setAccessible = json.ommParams.setAccessible
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val elementClass: KClass<*>? = GetElementClass(type)
    private lateinit var elementCodec: JsonCodec<Any>
    private lateinit var readElementCodec: JsonCodec<Any>
    private val constructor: ConstructorFunc?
    private var setAccessible = false

    init {
        val defCtr = GetDefaultConstructor(type.jvmErasure, setAccessible)
        constructor = when {
            defCtr != null -> defCtr
            type.jvmErasure == List::class || type.jvmErasure == MutableList::class ||
            type.jvmErasure == Collection::class || type.jvmErasure == MutableCollection::class -> {
                { ArrayList<Any?>() }
            }
            else -> null
        }
    }

    private companion object {
        fun GetElementClass(type: KType): KClass<*>?
        {
            return type.arguments.firstOrNull()?.type?.jvmErasure
        }
    }
}
