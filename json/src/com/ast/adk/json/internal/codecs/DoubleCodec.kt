package com.ast.adk.json.internal.codecs

import com.ast.adk.json.Json
import com.ast.adk.json.JsonCodec
import com.ast.adk.json.JsonReader
import com.ast.adk.json.JsonWriter

class DoubleCodec: JsonCodec<Double> {
    override fun WriteNonNull(obj: Double, writer: JsonWriter, json: Json)
    {
        writer.Write(obj)
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): Double
    {
        return reader.ReadDouble()
    }
}
