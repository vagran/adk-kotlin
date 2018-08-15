package com.ast.adk.json

interface JsonCodec<T> {

    fun Write(obj: T, writer: JsonWriter, json: Json)
    fun Read(reader: JsonReader, json: Json): T
}
