/*
 * This file is part of BetBot project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json

/** Adapter class which reads from arbitrary object (any JSON allowed primitives, List and Map of
 * them).
 */
class JsonObjectReader(private val obj: Any?): JsonReader {
    override fun Peek(): JsonToken
    {
        CheckEof()
        nextToken?.also { return it }
        val token = ReadNext()
        nextToken = token
        return token
    }

    override fun Read(): JsonToken
    {
        CheckEof()
        val token = run {
            nextToken?.also {
                nextToken = null
                return@run it
            }
            ReadNext()
        }
        if (token == JsonToken.EOF) {
            isEof = true
        }
        return token
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var isEof = false
    private var firstRead = false
    private var nextToken: JsonToken? = null
    private val stack = ArrayDeque<StackItem>()

    @Suppress("UNCHECKED_CAST")
    private inner class StackItem(collection: Any, isList: Boolean) {
        val listIterator: Iterator<Any?>?
        val mapIterator: Iterator<Map.Entry<String, Any?>>?
        var curEntry: Map.Entry<String, Any?>? = null

        init {
            if (isList) {
                listIterator = (collection as List<*>).iterator()
                mapIterator = null
            } else {
                mapIterator = (collection as Map<String, *>).iterator()
                listIterator = null
            }
        }

        fun ReadNext(): JsonToken
        {
            if (listIterator != null) {
                if (listIterator.hasNext()) {
                    return ReadValue(listIterator.next())
                }
                stack.removeLast()
                return JsonToken.END_ARRAY
            }
            curEntry?.also {
                curEntry = null
                return ReadValue(it.value)
            }
            if (!mapIterator!!.hasNext()) {
                stack.removeLast()
                return JsonToken.END_OBJECT
            }
            val e = mapIterator.next()
            curEntry = e
            return JsonToken(JsonToken.Type.NAME, e.key)
        }
    }

    private fun ReadNext(): JsonToken
    {
        stack.lastOrNull()?.ReadNext()?.also { return it }
        return if (firstRead) {
            JsonToken.EOF
        } else {
            firstRead = true
            ReadValue(obj)
        }
    }

    private fun ReadValue(value: Any?): JsonToken
    {
        return when (value) {
            null -> JsonToken.NULL
            is Number -> JsonToken(JsonToken.Type.NUMBER, value.toString())
            is String -> JsonToken(JsonToken.Type.STRING, value)
            is Boolean -> if (value) JsonToken.TRUE else JsonToken.FALSE
            is List<*> -> Push(value, true)
            is Map<*, *> -> Push(value, false)
            else -> throw Error("Unexpected type: value $value of class ${value::class}")
        }
    }

    private fun Push(collection: Any, isList: Boolean): JsonToken
    {
        stack.addLast(StackItem(collection, isList))
        return if (isList) JsonToken.BEGIN_ARRAY else JsonToken.BEGIN_OBJECT
    }

    private fun CheckEof()
    {
        if (isEof) {
            throw Error("Read past end of file")
        }
    }
}