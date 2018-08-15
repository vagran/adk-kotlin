package com.ast.adk.json.internal

import com.ast.adk.json.Json
import com.ast.adk.json.JsonWriter
import java.io.Writer

internal class TextJsonWriter(private val json: Json,
                              private val output: Writer): JsonWriter {

    override fun WriteName() {
        TODO("not implemented") //XXX
    }

    override fun BeginObject() {
        TODO("not implemented") //XXX
    }

    override fun EndObject() {
        TODO("not implemented") //XXX
    }

    override fun BeginArray() {
        TODO("not implemented") //XXX
    }

    override fun EndArray() {
        TODO("not implemented") //XXX
    }

    override fun WriteNull() {
        TODO("not implemented") //XXX
    }

    override fun Write(value: Int) {
        TODO("not implemented") //XXX
    }

    override fun Write(value: Long) {
        TODO("not implemented") //XXX
    }

    override fun Write(value: Double) {
        TODO("not implemented") //XXX
    }

    override fun Write(value: String) {
        TODO("not implemented") //XXX
    }

}
