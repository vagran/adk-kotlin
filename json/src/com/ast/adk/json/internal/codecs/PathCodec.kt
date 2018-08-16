package com.ast.adk.json.internal.codecs

import com.ast.adk.json.Json
import com.ast.adk.json.JsonCodec
import com.ast.adk.json.JsonReader
import com.ast.adk.json.JsonWriter
import java.nio.file.Path
import java.nio.file.Paths

class PathCodec: JsonCodec<Path> {

    override fun WriteNonNull(obj: Path, writer: JsonWriter, json: Json)
    {
        writer.Write(obj.toString())
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): Path
    {
        return Paths.get(reader.ReadString())
    }
}
