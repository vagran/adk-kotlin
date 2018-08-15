package com.ast.adk.json

import java.lang.NumberFormatException

class JsonToken(val type: Type, val value: String) {

    companion object {
        val EOF = JsonToken(Type.EOF, "")
        val NULL = JsonToken(Type.NULL, "")
        val BEGIN_OBJECT = JsonToken(Type.BEGIN_OBJECT, "")
        val END_OBJECT = JsonToken(Type.END_OBJECT, "")
        val BEGIN_ARRAY = JsonToken(Type.BEGIN_ARRAY, "")
        val END_ARRAY = JsonToken(Type.END_ARRAY, "")
    }

    enum class Type {
        EOF,
        NAME,
        NULL,
        STRING,
        SYMBOL,
        NUMBER,
        BEGIN_OBJECT,
        END_OBJECT,
        BEGIN_ARRAY,
        END_ARRAY
    }

    override fun toString(): String
    {
        return "$type[$value]"
    }

    fun Name(): String
    {
        if (type != JsonToken.Type.NAME) {
            throw JsonReadError("Expected name, have $this")
        }
        return value
    }

    fun AssertBeginObject()
    {
        if (type != JsonToken.Type.BEGIN_OBJECT) {
            throw JsonReadError("Expected object begin, have $this")
        }
    }

    fun AssertEndObject()
    {
        if (type != JsonToken.Type.END_OBJECT) {
            throw JsonReadError("Expected object end, have $this")
        }
    }

    fun AssertBeginArray()
    {
        if (type != JsonToken.Type.BEGIN_ARRAY) {
            throw JsonReadError("Expected array begin, have $this")
        }
    }

    fun AssertEndArray()
    {
        if (type != JsonToken.Type.END_ARRAY) {
            throw JsonReadError("Expected array end, have $this")
        }
    }

    fun AssertValue()
    {
        if (type != Type.NULL &&
            type != Type.STRING &&
            type != Type.NUMBER &&
            type != Type.SYMBOL &&
            type != Type.BEGIN_OBJECT &&
            type != Type.BEGIN_ARRAY) {

            throw JsonReadError("Expected value, have $this")
        }
    }

    fun StringValue(): String?
    {
        if (type == Type.NULL) {
            return null
        }
        if (type != JsonToken.Type.STRING) {
            throw JsonReadError("Expected string value, have $this")
        }
        return value
    }

    fun IntValue(): Int?
    {
        if (type == Type.NULL) {
            return null
        }
        if (type != JsonToken.Type.NUMBER) {
            throw JsonReadError("Expected integer value, have $this")
        }
        return try {
            value.toInt()
        } catch (e: NumberFormatException) {
            throw JsonReadError("Bad integer value", e)
        }
    }

    fun LongValue(): Long?
    {
        if (type == Type.NULL) {
            return null
        }
        if (type != JsonToken.Type.NUMBER) {
            throw JsonReadError("Expected long integer value, have $this")
        }
        return try {
            value.toLong()
        } catch (e: NumberFormatException) {
            throw JsonReadError("Bad long integer value", e)
        }
    }

    fun DoubleValue(): Double?
    {
        if (type == Type.NULL) {
            return null
        }
        if (type != JsonToken.Type.NUMBER) {
            throw JsonReadError("Expected double value, have $this")
        }
        return try {
            value.toDouble()
        } catch (e: NumberFormatException) {
            throw JsonReadError("Bad long integer value", e)
        }
    }

    fun BooleanValue(): Boolean?
    {
        if (type == Type.NULL) {
            return null
        }
        if (type != JsonToken.Type.SYMBOL) {
            throw JsonReadError("Expected boolean value, have $this")
        }
        return when (value) {
            "true" -> true
            "false" -> false
            else -> throw JsonReadError("Bad boolean value: $value")
        }
    }
}