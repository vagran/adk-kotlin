package com.ast.adk.json

interface JsonWriter {

    fun WriteName(name: String)
    fun BeginObject()
    fun EndObject()
    fun BeginArray()
    fun EndArray()
    fun WriteNull()
    fun Write(value: Int)
    fun Write(value: Long)
    fun Write(value: Double)
    fun Write(value: String)
    fun Write(value: Boolean)

    fun Finish()
}
