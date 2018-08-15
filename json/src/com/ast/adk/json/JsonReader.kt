package com.ast.adk.json

interface JsonReader {

    fun Peek(): JsonToken

    fun Read(): JsonToken

    fun ReadName(): String = Read().Name()
    fun BeginObject() = Read().AssertBeginObject()
    fun EndObject() = Read().AssertEndObject()
    fun BeginArray() = Read().AssertBeginArray()
    fun EndArray() = Read().AssertEndArray()
    fun ReadString(): String? = Read().StringValue()
    fun ReadInt(): Int? = Read().IntValue()
    fun ReadLong(): Long? = Read().LongValue()
    fun ReadDouble(): Double? = Read().DoubleValue()
    fun ReadBoolean(): Boolean? = Read().BooleanValue()

    fun SkipValue() = Read().AssertValue()

    /** @return true if current array or object has next value. */
    fun HasNext(): Boolean
    {
        val token = Peek()
        return token.type != JsonToken.Type.END_ARRAY && token.type != JsonToken.Type.END_OBJECT
    }
}
