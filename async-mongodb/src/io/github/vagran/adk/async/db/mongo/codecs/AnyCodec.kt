package io.github.vagran.adk.async.db.mongo.codecs

import io.github.vagran.adk.async.db.mongo.MongoCodec
import io.github.vagran.adk.async.db.mongo.MongoMapper
import io.github.vagran.adk.omm.OmmError
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

class AnyCodec(private val mapper: MongoMapper): MongoCodec<Any> {

    @Suppress("UNCHECKED_CAST")
    override fun getEncoderClass(): Class<Any>
    {
        return Any::class.java
    }

    override fun encode(writer: BsonWriter, obj: Any?, encoderContext: EncoderContext)
    {
        if (obj == null) {
            writer.writeNull()
            return
        }
        val cls = obj::class
        if (cls == Any::class) {
            writer.writeStartDocument()
            writer.writeEndDocument()
            return
        }
        @Suppress("UNCHECKED_CAST")
        val codec = mapper.GetCodec(cls) as Codec<Any>
        codec.encode(writer, obj, encoderContext)
    }

    @Suppress("UNCHECKED_CAST")
    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Any?
    {
        val type = reader.currentBsonType
        return when (type) {
            BsonType.NULL -> {
                reader.readNull()
                return null
            }
            BsonType.DOCUMENT -> mapCodec.decode(reader, decoderContext)
            BsonType.ARRAY -> listCodec.decode(reader, decoderContext)
            BsonType.STRING -> reader.readString()
            BsonType.BOOLEAN -> reader.readBoolean()
            BsonType.DOUBLE -> reader.readDouble()
            BsonType.INT32 -> reader.readInt32()
            BsonType.INT64 -> reader.readInt64()
            BsonType.OBJECT_ID -> reader.readObjectId()
            BsonType.BINARY -> reader.readBinaryData()
            else -> throw OmmError("Unexpected token type: $type")
        }
    }

    override fun Initialize(mapper: MongoMapper)
    {
        listCodec = mapper.GetCodec()
        mapCodec = mapper.GetCodec()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private lateinit var listCodec: Codec<List<Any?>>
    private lateinit var mapCodec: Codec<Map<String, Any?>>
}
