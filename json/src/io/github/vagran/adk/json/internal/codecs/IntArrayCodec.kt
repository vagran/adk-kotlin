package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.Json
import io.github.vagran.adk.json.JsonCodec
import io.github.vagran.adk.json.JsonReader
import io.github.vagran.adk.json.JsonWriter

class IntArrayCodec: JsonCodec<IntArray> {
    override fun WriteNonNull(obj: IntArray, writer: JsonWriter, json: Json)
    {
        writer.BeginArray()
        for (element in obj) {
            writer.Write(element)
        }
        writer.EndArray()
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): IntArray
    {
        val result = ArrayList<Int>()
        reader.BeginArray()
        while (reader.HasNext()) {
            result.add(reader.ReadInt())
        }
        reader.EndArray()
        return result.toIntArray()
    }
}
