/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json.adk_codecs.codecs

import io.github.vagran.adk.LocalId
import io.github.vagran.adk.json.Json
import io.github.vagran.adk.json.JsonCodec
import io.github.vagran.adk.json.JsonReader
import io.github.vagran.adk.json.JsonWriter


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
