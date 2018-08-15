package com.ast.adk.json.internal

import com.ast.adk.json.Json
import com.ast.adk.json.JsonReader
import com.ast.adk.json.JsonToken
import java.io.Reader

class TextJsonReader(private val json: Json,
                     private val reader: Reader): JsonReader {

    override fun Peek(): JsonToken
    {
        TODO("not implemented") //XXX
    }

    override fun Read(): JsonToken
    {
        TODO("not implemented") //XXX
    }
}
