package com.ast.adk.json.internal.codecs

import com.ast.adk.json.Json
import com.ast.adk.json.JsonCodec
import com.ast.adk.json.JsonReader
import com.ast.adk.json.JsonWriter

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
