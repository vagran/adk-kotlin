package com.ast.adk.json.internal.codecs

import com.ast.adk.json.Json
import com.ast.adk.json.JsonCodec
import com.ast.adk.json.JsonReader
import com.ast.adk.json.JsonWriter

class IntCodec: JsonCodec<Int> {
    override fun WriteNonNull(obj: Int, writer: JsonWriter, json: Json)
    {
        writer.Write(obj)
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): Int
    {
        return reader.ReadInt()
    }
}
