package com.ast.adk.json.internal.codecs

import com.ast.adk.json.*
import com.ast.adk.omm.OmmClassNode
import com.ast.adk.omm.OmmError
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
                desc.codec.WriteNonNull(value, writer, json)
            }
            return
        }

        writer.BeginObject()
        for ((name, desc) in clsNode.fields) {
            val value = desc.getter(obj as Any)
            if (value == null) {
                if (json.serializeNulls) {
                    writer.WriteName(name)
                    writer.WriteNull()
                }
                continue
            }
            writer.WriteName(name)
            desc.codec.WriteNonNull(value, writer, json)
        }
        writer.EndObject()
    }

    @Suppress("UNCHECKED_CAST")
    override fun ReadNonNull(reader: JsonReader, json: Json): T
    {
        try {
            clsNode.delegatedRepresentationField?.also { desc ->
                val value = desc.codec.Read(reader, json)
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
                val value = desc.codec.Read(reader, json)
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
        clsNode.Initialize(json.ommParams) { fp -> FieldDesc(fp, json) }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    @Suppress("UNCHECKED_CAST")
    private class FieldDesc(params: OmmClassNode.FieldParams,
                            json: Json): OmmClassNode.OmmFieldNode(params) {

        val codec: JsonCodec<Any> = json.GetCodec(property.returnType)
    }

    private val clsAnn: JsonClass? = type.jvmErasure.findAnnotation()
    private var allowUnmatchedFields = false
    private lateinit var clsNode: OmmClassNode<FieldDesc>
}
