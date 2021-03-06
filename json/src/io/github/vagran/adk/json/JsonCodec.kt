/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json

interface JsonCodec<T> {

    /** Perform initialization which potentially can request other codecs. Other codecs should be
     * requested in this method instead of constructor to solve potential circular dependency.
     */
    fun Initialize(json: Json) {}

    fun WriteNonNull(obj: T, writer: JsonWriter, json: Json)

    fun ReadNonNull(reader: JsonReader, json: Json): T

    fun Write(obj: T?, writer: JsonWriter, json: Json)
    {
        if (obj == null) {
            writer.WriteNull()
            return
        }
        WriteNonNull(obj, writer, json)
    }

    fun Read(reader: JsonReader, json: Json): T?
    {
        if (reader.Peek() === JsonToken.NULL) {
            reader.SkipValue()
            return null
        }
        return ReadNonNull(reader, json)
    }
}
