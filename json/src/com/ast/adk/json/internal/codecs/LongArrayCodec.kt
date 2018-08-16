package com.ast.adk.json.internal.codecs

import com.ast.adk.json.Json
import com.ast.adk.json.JsonCodec
import com.ast.adk.json.JsonReader
import com.ast.adk.json.JsonWriter

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
