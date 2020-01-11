package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.Json
import io.github.vagran.adk.json.JsonCodec
import io.github.vagran.adk.json.JsonReader
import io.github.vagran.adk.json.JsonWriter
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
