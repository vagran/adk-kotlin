package com.ast.adk.json.adk_codecs

import com.ast.adk.LocalId
import com.ast.adk.json.Json
import com.ast.adk.json.JsonCodec
import com.ast.adk.json.JsonReader
import com.ast.adk.json.JsonWriter


class LocalIdJsonCodec: JsonCodec<LocalId> {

    override fun WriteNonNull(obj: LocalId, writer: JsonWriter, json: Json)
    {
        writer.Write(obj.toString())
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): LocalId
    {
        return LocalId(reader.ReadString())
    }
}
