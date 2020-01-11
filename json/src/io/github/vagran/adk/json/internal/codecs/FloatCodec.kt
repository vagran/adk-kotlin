package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.Json
import io.github.vagran.adk.json.JsonCodec
import io.github.vagran.adk.json.JsonReader
import io.github.vagran.adk.json.JsonWriter

class FloatCodec: JsonCodec<Float> {
    override fun WriteNonNull(obj: Float, writer: JsonWriter, json: Json)
    {
        writer.Write(obj.toDouble())
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): Float
    {
        return reader.ReadDouble().toFloat()
    }
}
