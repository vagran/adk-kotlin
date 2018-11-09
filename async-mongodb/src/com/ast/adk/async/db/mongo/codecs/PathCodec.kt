package com.ast.adk.async.db.mongo.codecs

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
