/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.db.mongo.codecs

import io.github.vagran.adk.LocalId
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

class LocalIdCodec: Codec<LocalId> {

    override fun getEncoderClass(): Class<LocalId>
    {
        return LocalId::class.java
    }

    override fun encode(writer: BsonWriter, obj: LocalId, encoderContext: EncoderContext)
    {
        writer.writeInt64(obj.value)
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): LocalId
    {
        return LocalId(reader.readInt64())
    }
}
