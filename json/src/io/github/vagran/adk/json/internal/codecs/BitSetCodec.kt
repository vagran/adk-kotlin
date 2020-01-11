/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.*
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
