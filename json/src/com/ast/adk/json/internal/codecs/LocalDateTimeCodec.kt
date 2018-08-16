package com.ast.adk.json.internal.codecs

import com.ast.adk.json.Json
import com.ast.adk.json.JsonCodec
import com.ast.adk.json.JsonReader
import com.ast.adk.json.JsonWriter
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
