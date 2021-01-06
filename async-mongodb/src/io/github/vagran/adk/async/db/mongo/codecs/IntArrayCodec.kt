/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.db.mongo.codecs

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

class IntArrayCodec: Codec<IntArray> {
    override fun getEncoderClass(): Class<IntArray>
    {
        return IntArray::class.java
    }

    override fun encode(writer: BsonWriter, obj: IntArray, encoderContext: EncoderContext)
    {
        writer.writeStartArray()
        for (element in obj) {
            writer.writeInt32(element)
        }
        writer.writeEndArray()
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): IntArray
    {
        val result = ArrayList<Int>()
        reader.readStartArray()
        while (true) {
            if (reader.readBsonType() == BsonType.END_OF_DOCUMENT) {
                break
            }
            result.add(reader.readInt32())
        }
        reader.readEndArray()
        return result.toIntArray()
    }
}
