package com.ast.adk.async.db.mongo.codecs

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import java.util.*

class BitSetCodec: Codec<BitSet> {

    override fun getEncoderClass(): Class<BitSet>
    {
        return BitSet::class.java
    }

    override fun encode(writer: BsonWriter, obj: BitSet, encoderContext: EncoderContext)
    {
        val words = obj.toLongArray()
        writer.writeStartArray()
        for (word in words) {
            writer.writeInt64(word)
        }
        writer.writeEndArray()
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): BitSet
    {
        reader.readStartArray()
        val words = ArrayList<Long>()
        while (true) {
            if (reader.readBsonType() == BsonType.END_OF_DOCUMENT) {
                break
            }
            words.add(reader.readInt64())
        }
        reader.readEndArray()
        val wordsArray = LongArray(words.size)
        for (i in wordsArray.indices) {
            wordsArray[i] = words[i]
        }
        return BitSet.valueOf(wordsArray)
    }
}
