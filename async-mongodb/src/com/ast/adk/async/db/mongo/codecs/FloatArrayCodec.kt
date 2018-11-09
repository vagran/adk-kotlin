package com.ast.adk.async.db.mongo.codecs

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

class FloatArrayCodec: Codec<FloatArray> {
    override fun getEncoderClass(): Class<FloatArray>
    {
        return FloatArray::class.java
    }

    override fun encode(writer: BsonWriter, obj: FloatArray, encoderContext: EncoderContext)
    {
        writer.writeStartArray()
        for (element in obj) {
            writer.writeDouble(element.toDouble())
        }
        writer.writeEndArray()
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): FloatArray
    {
        val result = ArrayList<Float>()
        reader.readStartArray()
        while (true) {
            if (reader.readBsonType() == BsonType.END_OF_DOCUMENT) {
                break
            }
            result.add(reader.readDouble().toFloat())
        }
        reader.readEndArray()
        return result.toFloatArray()
    }
}
