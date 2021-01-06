/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.db.mongo.codecs

import io.github.vagran.adk.async.db.mongo.MongoClass
import io.github.vagran.adk.async.db.mongo.MongoCodec
import io.github.vagran.adk.async.db.mongo.MongoId
import io.github.vagran.adk.async.db.mongo.MongoMapper
import io.github.vagran.adk.omm.OmmClassNode
import io.github.vagran.adk.omm.OmmError
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.jvmErasure

class MappedClassCodec<T>(private val type: KType): MongoCodec<T> {

    @Suppress("UNCHECKED_CAST")
    override fun getEncoderClass(): Class<T>
    {
        return type.jvmErasure.java as Class<T>
    }

    override fun encode(writer: BsonWriter, obj: T, encoderContext: EncoderContext)
    {
        clsNode.delegatedRepresentationField?.also {
            desc ->
            val value = desc.getter(obj as Any)
            if (value == null) {
                writer.writeNull()
            } else {
                desc.Write(writer, value, encoderContext)
            }
            return
        }

        writer.writeStartDocument()
        for ((name, desc) in clsNode.fields) {
            val value = desc.getter(obj as Any)
            if (value == null) {
                if (desc.serializeNull) {
                    writer.writeName(name)
                    writer.writeNull()
                }
                continue
            }
            writer.writeName(name)
            desc.Write(writer, value, encoderContext)
        }
        writer.writeEndDocument()
    }

    @Suppress("UNCHECKED_CAST")
    override fun decode(reader: BsonReader, decoderContext: DecoderContext): T
    {
        clsNode.delegatedRepresentationField?.also { desc ->
            val value = desc.Read(reader, decoderContext)
            val setter = clsNode.SpawnObject(null)
            setter.Set(desc, value)
            return setter.Finalize() as T
        }

        val setter = clsNode.SpawnObject(null)
        reader.readStartDocument()
        while (true) {
            val type = reader.readBsonType()
            if (type == BsonType.END_OF_DOCUMENT) {
                break
            }

            val name = reader.readName()
            val desc = clsNode.fields[name]
            if (desc == null) {
                if (!allowUnmatchedFields) {
                    throw OmmError("Unmatched field $name for $type")
                }
                reader.skipValue()
                continue
            }

            if (type == BsonType.NULL) {
                reader.readNull()
                setter.Set(desc, null)
                continue
            }

            val value = desc.Read(reader, decoderContext)
            setter.Set(desc, value)
        }
        reader.readEndDocument()

        return setter.Finalize() as T
    }

    override fun Initialize(mapper: MongoMapper)
    {
        allowUnmatchedFields = clsAnn?.allowUnmatchedFields?.booleanValue ?: mapper.allowUnmatchedFields
        clsNode = OmmClassNode(type.jvmErasure, mapper.ommParams)
        clsNode.Initialize(
            mapper.ommParams,
            { fp -> FieldDesc(fp, mapper) },
            fieldNameHook = {
                prop ->
                if (prop.findAnnotation<MongoId>() != null) {
                    "_id"
                } else {
                    null
                }
            },
            additionalAnnotations = listOf(MongoId::class))
        if (clsNode.fields.isEmpty() && clsNode.delegatedRepresentationField == null) {
            throw IllegalArgumentException("No mapped fields in $type")
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val clsAnn: MongoClass? = type.jvmErasure.findAnnotation()
    private var allowUnmatchedFields = false
    private lateinit var clsNode: OmmClassNode<FieldDesc>

    @Suppress("UNCHECKED_CAST")
    private class FieldDesc(params: OmmClassNode.FieldParams,
                            mapper: MongoMapper): OmmClassNode.OmmFieldNode(params) {

        val codec: Codec<Any> = mapper.GetCodec(property.returnType)

        fun Read(reader: BsonReader, decoderContext: DecoderContext): Any?
        {
            return if (enumMode != OmmClassNode.EnumMode.NONE) {
                (codec as EnumCodec).DecodeEnum(reader, enumMode == OmmClassNode.EnumMode.NAME)
            } else {
                codec.decode(reader, decoderContext)
            }
        }

        fun Write(writer: BsonWriter, value: Any?, encoderContext: EncoderContext)
        {
            if (enumMode != OmmClassNode.EnumMode.NONE) {
                (codec as EnumCodec).EncodeEnum(writer, value as Enum<*>,
                                                enumMode == OmmClassNode.EnumMode.NAME)
            } else {
                codec.encode(writer, value, encoderContext)
            }
        }
    }
}
