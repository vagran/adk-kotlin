package com.ast.adk.json.internal.codecs

import com.ast.adk.json.*
import java.util.*

class BitSetCodec: JsonCodec<BitSet> {

    override fun WriteNonNull(obj: BitSet, writer: JsonWriter, json: Json)
    {
        val words = obj.toLongArray()
        writer.BeginArray()
        for (word in words) {
            writer.Write(word)
        }
        writer.EndArray()
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): BitSet
    {
        reader.BeginArray()
        val words = ArrayList<Long>()
        while (reader.Peek() != JsonToken.END_ARRAY) {
            words.add(reader.ReadLong())
        }
        reader.EndArray()
        val wordsArray = LongArray(words.size)
        for (i in wordsArray.indices) {
            wordsArray[i] = words[i]
        }
        return BitSet.valueOf(wordsArray)
    }
}
