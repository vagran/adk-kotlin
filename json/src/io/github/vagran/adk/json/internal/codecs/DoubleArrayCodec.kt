/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.Json
import io.github.vagran.adk.json.JsonCodec
import io.github.vagran.adk.json.JsonReader
import io.github.vagran.adk.json.JsonWriter

class DoubleArrayCodec: JsonCodec<DoubleArray> {
    override fun WriteNonNull(obj: DoubleArray, writer: JsonWriter, json: Json)
    {
        writer.BeginArray()
        for (element in obj) {
            writer.Write(element)
        }
        writer.EndArray()
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): DoubleArray
    {
        val result = ArrayList<Double>()
        reader.BeginArray()
        while (reader.HasNext()) {
            result.add(reader.ReadDouble())
        }
        reader.EndArray()
        return result.toDoubleArray()
    }
}
