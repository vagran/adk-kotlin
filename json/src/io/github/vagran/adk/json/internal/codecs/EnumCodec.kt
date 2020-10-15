/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.*
import io.github.vagran.adk.omm.OmmClass
import java.util.*
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

class EnumCodec(private val type: KType, json: Json): JsonCodec<Enum<*>> {
    override fun WriteNonNull(obj: Enum<*>, writer: JsonWriter, json: Json)
    {
        WriteEnumNonNull(obj, writer, enumByName ?: json.ommParams.enumByName)
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): Enum<*>
    {
        return ReadEnumNonNull(reader, enumByName ?: json.ommParams.enumByName)
    }

    fun WriteEnumNonNull(obj: Enum<*>, writer: JsonWriter, enumByName: Boolean)
    {
        if (enumByName) {
            writer.Write(obj.name)
        } else {
            writer.Write(obj.ordinal)
        }
    }

    fun ReadEnum(reader: JsonReader, enumByName: Boolean): Enum<*>?
    {
        if (reader.Peek() === JsonToken.NULL) {
            reader.SkipValue()
            return null
        }
        return ReadEnumNonNull(reader, enumByName)
    }

    fun ReadEnumNonNull(reader: JsonReader, enumByName: Boolean): Enum<*>
    {
        return if (enumByName) {
            val name = reader.ReadString()
            names[name] ?: throw JsonReadError("Unrecognized enum $type value name: $name")
        } else {
            val index = reader.ReadInt()
            if (index >= values.size) {
                throw JsonReadError("Enum $type value index out of range: $index")
            }
            values[index]
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val values: Array<Enum<*>>
    private val names: Map<String, Enum<*>>
    private val enumByName: Boolean?

    init {
        val _values = type.jvmErasure.java.enumConstants
        names = TreeMap()
        values = Array(_values.size) {
            idx ->
            val value = _values[idx] as Enum<*>
            names[value.name] = value
            value
        }
        enumByName = json.ommParams.FindAnnotation<OmmClass>(type.jvmErasure)
            ?.enumByName?.booleanValue
    }
}
