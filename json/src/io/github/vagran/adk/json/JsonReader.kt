package io.github.vagran.adk.json

interface JsonReader {

    fun Peek(): JsonToken

    fun Read(): JsonToken

    fun ReadName() = Read().Name()
    fun BeginObject() = Read().AssertBeginObject()
    fun EndObject() = Read().AssertEndObject()
    fun BeginArray() = Read().AssertBeginArray()
    fun EndArray() = Read().AssertEndArray()
    fun ReadString() = Read().StringValue()
    fun ReadInt() = Read().IntValue()
    fun ReadLong() = Read().LongValue()
    fun ReadDouble() = Read().DoubleValue()
    fun ReadBoolean() = Read().BooleanValue()

    fun SkipValue()
    {
        val token = Read()
        when {
            token === JsonToken.BEGIN_OBJECT -> SkipObject()
            token === JsonToken.BEGIN_ARRAY -> SkipArray()
            else -> token.AssertValue()
        }
    }

    /** @return true if current array or object has next value. */
    fun HasNext(): Boolean
    {
        val token = Peek()
        return token !== JsonToken.END_ARRAY && token !== JsonToken.END_OBJECT
    }

    fun AssertFullConsumption()
    {
        val token = Peek()
        if (token !== JsonToken.EOF) {
            throw JsonReadError("Input was not fully consumed, next token $token")
        }
    }

    private fun SkipObject()
    {
        while (true) {
            val token = Peek()
            if (token == JsonToken.END_OBJECT) {
                EndObject()
                return
            }
            ReadName()
            SkipValue()
        }
    }

    private fun SkipArray()
    {
        while (true) {
            val token = Peek()
            if (token == JsonToken.END_ARRAY) {
                EndArray()
                return
            }
            SkipValue()
        }
    }
}
