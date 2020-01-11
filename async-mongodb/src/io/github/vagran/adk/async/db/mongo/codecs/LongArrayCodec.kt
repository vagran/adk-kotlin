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

class LongArrayCodec: Codec<LongArray> {
    override fun getEncoderClass(): Class<LongArray>
    {
        return LongArray::class.java
    }

    override fun encode(writer: BsonWriter, obj: LongArray, encoderContext: EncoderContext)
    {
        writer.writeStartArray()
        for (element in obj) {
            writer.writeInt64(element)
        }
        writer.writeEndArray()
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): LongArray
    {
        val result = ArrayList<Long>()
        reader.readStartArray()
        while (true) {
            if (reader.readBsonType() == BsonType.END_OF_DOCUMENT) {
                break
            }
            result.add(reader.readInt64())
        }
        reader.readEndArray()
        return result.toLongArray()
    }
}
