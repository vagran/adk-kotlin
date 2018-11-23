package com.ast.adk.json.internal.codecs

import com.ast.adk.json.Json
import com.ast.adk.json.JsonCodec
import com.ast.adk.json.JsonReader
import com.ast.adk.json.JsonWriter

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
