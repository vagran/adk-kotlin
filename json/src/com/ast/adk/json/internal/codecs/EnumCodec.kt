package com.ast.adk.json.internal.codecs

import com.ast.adk.json.*
import java.util.*
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

class EnumCodec(private val type: KType): JsonCodec<Enum<*>> {
    override fun WriteNonNull(obj: Enum<*>, writer: JsonWriter, json: Json)
    {
        WriteEnumNonNull(obj, writer, json.ommParams.enumByName)
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): Enum<*>
    {
        return ReadEnumNonNull(reader, json.ommParams.enumByName)
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

    init {
        val _values = type.jvmErasure.java.enumConstants
        names = TreeMap()
        values = Array(_values.size) {
            idx ->
            val value = _values[idx] as Enum<*>
            names[value.name] = value
            value
        }
    }
}
