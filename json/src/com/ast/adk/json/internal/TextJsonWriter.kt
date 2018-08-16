package com.ast.adk.json.internal

import com.ast.adk.json.Json
import com.ast.adk.json.JsonWriter
import java.io.Writer
import java.util.*

internal class TextJsonWriter(private val json: Json,
                              private val output: Writer): JsonWriter {

    override fun WriteName(name: String)
    {
        val state = GetCurState()
        if (state.type != State.Type.OBJECT) {
            throw IllegalStateException("Cannot write name in $state state")
        }
        if (state.keyWritten) {
            throw IllegalStateException("Cannot write name while expecting value")
        }
        if (state.valueWritten) {
            output.write(','.toInt())
        }
        if (isPrettyPrint) {
            output.write('\n'.toInt())
            Indent()
        }
        output.write('"'.toInt())
        WriteEscapedString(name)
        output.write('"'.toInt())
        output.write(':'.toInt())
        if (isPrettyPrint) {
            output.write(' '.toInt())
        }
        state.keyWritten = true
    }

    override fun BeginObject()
    {
        val state = GetCurState()
        state.BeginValueWrite(this)
        output.write('{'.toInt())
        PushState(State.Type.OBJECT)
    }

    override fun EndObject()
    {
        val state = GetCurState()
        if (state.type != State.Type.OBJECT) {
            throw IllegalStateException("Expected object state, have $state")
        }
        if (state.keyWritten) {
            throw java.lang.IllegalStateException("Attempting to end object before value written")
        }
        PopState()
        if (isPrettyPrint && state.valueWritten) {
            output.write('\n'.toInt())
            Indent()
        }
        output.write('}'.toInt())
    }

    override fun BeginArray()
    {
        val state = GetCurState()
        state.BeginValueWrite(this)
        output.write('['.toInt())
        PushState(State.Type.ARRAY)
    }

    override fun EndArray()
    {
        val state = GetCurState()
        if (state.type != State.Type.ARRAY) {
            throw IllegalStateException("Expected array state, have $state")
        }
        PopState()
        if (isPrettyPrint && state.valueWritten) {
            output.write('\n'.toInt())
            Indent()
        }
        output.write(']'.toInt())
    }

    override fun WriteNull()
    {
        val state = GetCurState()
        state.BeginValueWrite(this)
        output.write("null")
    }

    override fun Write(value: Int)
    {
        val state = GetCurState()
        state.BeginValueWrite(this)
        output.write(value.toString())
    }

    override fun Write(value: Long)
    {
        val state = GetCurState()
        state.BeginValueWrite(this)
        output.write(value.toString())
    }

    override fun Write(value: Double)
    {
        val state = GetCurState()
        state.BeginValueWrite(this)
        output.write(value.toString())
    }

    override fun Write(value: String)
    {
        val state = GetCurState()
        state.BeginValueWrite(this)
        output.write('"'.toInt())
        WriteEscapedString(value)
        output.write('"'.toInt())
    }

    override fun Write(value: Boolean)
    {
        val state = GetCurState()
        state.BeginValueWrite(this)
        output.write(if (value) "true" else "false")
    }

    override fun Finish()
    {
        val state = GetCurState()
        if (state.type != State.Type.ROOT || !state.valueWritten) {
            throw IllegalStateException("JSON document not complete")
        }
        output.flush()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private class State(val type: Type) {
        enum class Type {
            ROOT,
            OBJECT,
            ARRAY
        }

        var keyWritten = false
        var valueWritten = false

        fun BeginValueWrite(writer: TextJsonWriter)
        {
            if (type == Type.ROOT) {
                if (valueWritten) {
                    throw IllegalStateException("Attempted to write multiple root values")
                }

            } else if (type == Type.OBJECT) {
                if (!keyWritten) {
                    throw IllegalStateException("Attempted to write value before key specified")
                }
                keyWritten = false

            } else if (type == Type.ARRAY) {
                if (valueWritten) {
                    writer.output.write(','.toInt())
                }
                if (writer.isPrettyPrint) {
                    writer.output.write('\n'.toInt())
                    writer.Indent()
                }
            }

            valueWritten = true
        }

        override fun toString(): String
        {
            return type.toString()
        }
    }

    private val isPrettyPrint = json.prettyPrint
    private var curIndent = 0

    private val stateStack = ArrayDeque<State>()

    init {
        stateStack.push(State(State.Type.ROOT))
    }

    private fun GetCurState(): State = stateStack.peek()

    private fun PushState(type: State.Type)
    {
        stateStack.push(State(type))
        if (isPrettyPrint) {
            curIndent += json.prettyPrintIndent
        }
    }

    private fun PopState()
    {
        stateStack.pop()
        if (isPrettyPrint) {
            curIndent -= json.prettyPrintIndent
        }
    }

    private fun Indent()
    {
        for (i in 1..curIndent) {
            output.write(' '.toInt())
        }
    }

    private fun WriteEscapedString(s: String)
    {
        for (i in 0 until s.length) {
            val c = s[i]
            if (c == '\\' || c == '"') {
                output.write('\\'.toInt())
            }
            output.write(c.toInt())
        }
    }
}
