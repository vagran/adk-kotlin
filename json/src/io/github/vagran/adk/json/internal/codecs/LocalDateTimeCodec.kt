package io.github.vagran.adk.json.internal.codecs

import io.github.vagran.adk.json.Json
import io.github.vagran.adk.json.JsonCodec
import io.github.vagran.adk.json.JsonReader
import io.github.vagran.adk.json.JsonWriter
import java.time.LocalDateTime

class LocalDateTimeCodec: JsonCodec<LocalDateTime> {

    override fun WriteNonNull(obj: LocalDateTime, writer: JsonWriter, json: Json)
    {
        writer.Write(obj.toString())
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): LocalDateTime
    {
        return LocalDateTime.parse(reader.ReadString())
    }
}
