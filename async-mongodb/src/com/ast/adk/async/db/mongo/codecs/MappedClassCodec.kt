package com.ast.adk.async.db.mongo.codecs

import com.ast.adk.async.db.mongo.MongoClass
import com.ast.adk.async.db.mongo.MongoCodec
import com.ast.adk.async.db.mongo.MongoMapper_new
import com.ast.adk.omm.OmmClassNode
import com.ast.adk.omm.OmmError
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
                desc.codec.encode(writer, value, encoderContext)
            }
            return
        }

        writer.writeStartDocument()
        for ((name, desc) in clsNode.fields) {
            val value = desc.getter(obj as Any)
            if (value == null) {
                if (serializeNulls) {
                    writer.writeName(name)
                    writer.writeNull()
                }
                continue
            }
            writer.writeName(name)
            encoderContext.encodeWithChildContext(desc.codec, writer, value)
        }
        writer.writeEndDocument()
    }

    @Suppress("UNCHECKED_CAST")
    override fun decode(reader: BsonReader, decoderContext: DecoderContext): T
    {
        clsNode.delegatedRepresentationField?.also { desc ->
            val value = desc.codec.decode(reader, decoderContext)
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
            if (type == BsonType.NULL) {
                reader.skipName()
                reader.readNull()
                continue
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
            val value = decoderContext.decodeWithChildContext(desc.codec, reader)
            setter.Set(desc, value)
        }
        reader.readEndDocument()

        return setter.Finalize() as T
    }

    override fun Initialize(mapper: MongoMapper_new)
    {
        allowUnmatchedFields = clsAnn?.allowUnmatchedFields?.booleanValue ?: mapper.allowUnmatchedFields
        serializeNulls = clsAnn?.serializeNulls?.booleanValue ?: mapper.serializeNulls
        clsNode = OmmClassNode(type.jvmErasure, mapper.ommParams)
        clsNode.Initialize(mapper.ommParams) { fp -> FieldDesc(fp, mapper) }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val clsAnn: MongoClass? = type.jvmErasure.findAnnotation()
    private var allowUnmatchedFields = false
    private var serializeNulls = false
    private lateinit var clsNode: OmmClassNode<FieldDesc>

    @Suppress("UNCHECKED_CAST")
    private class FieldDesc(params: OmmClassNode.FieldParams,
                            mapper: MongoMapper_new): OmmClassNode.OmmFieldNode(params) {

        val codec: Codec<Any> = mapper.GetCodec(property.returnType)
    }
}
