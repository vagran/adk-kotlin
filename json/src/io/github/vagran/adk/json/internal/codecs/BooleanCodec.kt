package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.Json
import io.github.vagran.adk.json.JsonCodec
import io.github.vagran.adk.json.JsonReader
import io.github.vagran.adk.json.JsonWriter

class BooleanCodec: JsonCodec<Boolean> {
    override fun WriteNonNull(obj: Boolean, writer: JsonWriter, json: Json)
    {
        writer.Write(obj)
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): Boolean
    {
        return reader.ReadBoolean()
    }
}
