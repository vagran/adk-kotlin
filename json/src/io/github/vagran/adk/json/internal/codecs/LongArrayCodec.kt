/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.Json
import io.github.vagran.adk.json.JsonCodec
import io.github.vagran.adk.json.JsonReader
import io.github.vagran.adk.json.JsonWriter

class LongArrayCodec: JsonCodec<LongArray> {
    override fun WriteNonNull(obj: LongArray, writer: JsonWriter, json: Json)
    {
        writer.BeginArray()
        for (element in obj) {
            writer.Write(element)
        }
        writer.EndArray()
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): LongArray
    {
        val result = ArrayList<Long>()
        reader.BeginArray()
        while (reader.HasNext()) {
            result.add(reader.ReadLong())
        }
        reader.EndArray()
        return result.toLongArray()
    }
}
