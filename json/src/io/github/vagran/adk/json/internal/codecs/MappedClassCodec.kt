/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.*
import io.github.vagran.adk.omm.OmmClassNode
import io.github.vagran.adk.omm.OmmError
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.jvmErasure

class MappedClassCodec<T>(private val type: KType): JsonCodec<T> {

    override fun WriteNonNull(obj: T, writer: JsonWriter, json: Json)
    {
        clsNode.delegatedRepresentationField?.also {
            desc ->
            val value = desc.getter(obj as Any)
            if (value == null) {
                writer.WriteNull()
            } else {
                desc.Write(value, writer, json)
            }
            return
        }

        writer.BeginObject()
        for ((name, desc) in clsNode.fields) {
            val value = desc.getter(obj as Any)
            if (value == null) {
                if (desc.serializeNull) {
                    writer.WriteName(name)
                    writer.WriteNull()
                }
                continue
            }
            writer.WriteName(name)
            desc.Write(value, writer, json)
        }
        writer.EndObject()
    }

    @Suppress("UNCHECKED_CAST")
    override fun ReadNonNull(reader: JsonReader, json: Json): T
    {
        try {
            clsNode.delegatedRepresentationField?.also { desc ->
                val value = desc.Read(reader, json)
                val setter = clsNode.SpawnObject(null)
                setter.Set(desc, value)
                return setter.Finalize() as T
            }

            val setter = clsNode.SpawnObject(null)
            reader.BeginObject()
            while (true) {
                if (!reader.HasNext()) {
                    break
                }
                val name = reader.ReadName()
                val desc = clsNode.fields[name]
                if (desc == null) {
                    if (!allowUnmatchedFields) {
                        throw JsonReadError("Unmatched field $name for $type")
                    }
                    reader.SkipValue()
                    continue
                }
                val value = desc.Read(reader, json)
                setter.Set(desc, value)
            }
            reader.EndObject()

            return setter.Finalize() as T
        } catch (e: OmmError) {
            throw JsonReadError(e.message ?: "", e)
        }
    }

    override fun Initialize(json: Json)
    {
        allowUnmatchedFields = clsAnn?.allowUnmatchedFields?.booleanValue ?: json.allowUnmatchedFields
        clsNode = OmmClassNode(type.jvmErasure, json.ommParams)
        clsNode.Initialize(json.ommParams, { fp -> FieldDesc(fp, json) })
        if (clsNode.fields.isEmpty() && clsNode.delegatedRepresentationField == null) {
            throw IllegalArgumentException("No mapped fields in $type")
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val clsAnn: JsonClass? = type.jvmErasure.findAnnotation()
    private var allowUnmatchedFields = false
    private lateinit var clsNode: OmmClassNode<FieldDesc>

    @Suppress("UNCHECKED_CAST")
    private class FieldDesc(params: OmmClassNode.FieldParams,
                            json: Json): OmmClassNode.OmmFieldNode(params) {

        val codec: JsonCodec<Any> = json.GetCodec(property.returnType)

        fun Read(reader: JsonReader, json: Json): Any?
        {
            return if (enumMode != OmmClassNode.EnumMode.NONE) {
                (codec as EnumCodec).ReadEnum(reader, enumMode == OmmClassNode.EnumMode.NAME)
            } else {
                codec.Read(reader, json)
            }
        }

        fun Write(value: Any, writer: JsonWriter, json: Json)
        {
            if (enumMode != OmmClassNode.EnumMode.NONE) {
                (codec as EnumCodec).WriteEnumNonNull(value as Enum<*>, writer,
                                                      enumMode == OmmClassNode.EnumMode.NAME)
            } else {
                codec.WriteNonNull(value, writer, json)
            }
        }
    }
}
