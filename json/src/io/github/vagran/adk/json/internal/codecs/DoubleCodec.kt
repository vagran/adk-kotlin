package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.Json
import io.github.vagran.adk.json.JsonCodec
import io.github.vagran.adk.json.JsonReader
import io.github.vagran.adk.json.JsonWriter

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
