/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.db.mongo.codecs

import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import java.nio.file.Path
import java.nio.file.Paths

class PathCodec: Codec<Path> {

    override fun getEncoderClass(): Class<Path>
    {
        return Path::class.java
    }

    override fun encode(writer: BsonWriter, obj: Path, encoderContext: EncoderContext)
    {
        writer.writeString(obj.toString())
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Path
    {
        return Paths.get(reader.readString())
    }
}
