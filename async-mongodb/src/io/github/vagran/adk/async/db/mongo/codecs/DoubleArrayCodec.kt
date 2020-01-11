/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.db.mongo.codecs

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

class DoubleArrayCodec: Codec<DoubleArray> {
    override fun getEncoderClass(): Class<DoubleArray>
    {
        return DoubleArray::class.java
    }

    override fun encode(writer: BsonWriter, obj: DoubleArray, encoderContext: EncoderContext)
    {
        writer.writeStartArray()
        for (element in obj) {
            writer.writeDouble(element)
        }
        writer.writeEndArray()
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): DoubleArray
    {
        val result = ArrayList<Double>()
        reader.readStartArray()
        while (true) {
            if (reader.readBsonType() == BsonType.END_OF_DOCUMENT) {
                break
            }
            result.add(reader.readDouble())
        }
        reader.readEndArray()
        return result.toDoubleArray()
    }
}
