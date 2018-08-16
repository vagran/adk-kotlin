package com.ast.adk.json.internal

import com.ast.adk.json.Json
import com.ast.adk.json.JsonReadError
import com.ast.adk.json.JsonReader
import com.ast.adk.json.JsonToken
import java.io.Reader
import java.util.*

class TextJsonReader(json: Json,
                     private val reader: Reader): JsonReader {

    override fun Peek(): JsonToken
    {
        return NextToken(true)
    }

    override fun Read(): JsonToken
    {
        return NextToken(false)
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val enableComments = json.enableComments
    private val buf = StringBuilder()
    private var lastToken: JsonToken? = null
    private var pendingChar: Int = 0
    private var state = State.BEFORE_VALUE
    private val stack = ArrayDeque<StackItem>()
    private var commentState = CommentState.NONE
    private var curCol = 1
    private var curLine = 1
    private var lastCharCr = false
    private var stringEscape = false
    private var stringEscapeDigitsNum = -1
    private var stringEscapeCode = 0

    private enum class State(val skipWs: Boolean) {
        BEFORE_VALUE(true),
        STRING(false),
        NUMBER(false),
        SYMBOL(false),
        BEFORE_NAME(true),
        AFTER_NAME(true),
        AFTER_VALUE(true),
        NAME(false)
    }

    private enum class StackItem {
        ARRAY,
        OBJECT
    }

    private enum class CommentState {
        NONE,
        START,
        COMMENT,
        END
    }

    private fun NextToken(peek: Boolean): JsonToken
    {
        while (true) {
            val token = lastToken
            if (token != null) {
                if (!peek && token.type != JsonToken.Type.EOF) {
                    lastToken = null
                }
                return token
            }
            if (pendingChar != 0) {
                if (ProcessChar(pendingChar)) {
                    pendingChar = 0
                }
                continue
            }
            val c = NextChar()
            if (!ProcessChar(c)) {
                pendingChar = c
            }
        }
    }

    /** @param c Code point, -1 if EOF.
     * @return true if character consumed.
     */
    private fun ProcessChar(c: Int): Boolean
    {
        if (commentState != CommentState.NONE) {
            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (commentState) {
                CommentState.START -> {
                    if (c == '*'.toInt()) {
                        commentState = CommentState.COMMENT
                    } else {
                        UnexpectedChar(c)
                    }
                }
                CommentState.COMMENT -> {
                    if (c == '*'.toInt()) {
                        commentState = CommentState.END
                    }
                }
                CommentState.END -> {
                    commentState = if (c == '/'.toInt()) {
                        CommentState.NONE
                    } else {
                        CommentState.COMMENT
                    }
                }
            }
            return true
        }

        if (state.skipWs) {
            if (Character.isWhitespace(c)) {
                return true
            }
            if (enableComments && c == '/'.toInt()) {
                commentState = CommentState.START
                return true
            }
        }

        return when (state) {
            State.BEFORE_VALUE -> HandleValueState(c)
            State.SYMBOL -> HandleSymbolState(c)
            State.NUMBER -> HandleNumberState(c)
            State.NAME, State.STRING -> HandleNameStringState(c)
            State.BEFORE_NAME -> HandleBeforeNameState(c)
            State.AFTER_NAME -> HandleAfterNameState(c)
            State.AFTER_VALUE -> HandleAfterValueState(c)
        }
    }

    private fun HandleValueState(c: Int): Boolean
    {
        return when {
            c == '"'.toInt() -> {
                state = State.STRING
                true
            }
            (c >= '0'.toInt() && c <= '9'.toInt()) || c == '-'.toInt() -> {
                state = State.NUMBER
                false
            }
            IsSymbolCharacter(c) -> {
                state = State.SYMBOL
                false
            }
            c == '['.toInt() -> {
                stack.push(StackItem.ARRAY)
                EmitToken(JsonToken.BEGIN_ARRAY)
                true
            }
            c == '{'.toInt() -> {
                stack.push(StackItem.OBJECT)
                EmitToken(JsonToken.BEGIN_OBJECT)
                state = State.BEFORE_NAME
                true
            }
            c == -1 -> Error("Unexpected end of file")
            else -> UnexpectedChar(c)
        }
    }

    private fun HandleSymbolState(c: Int): Boolean
    {
        return if (IsSymbolCharacter(c)) {
            buf.appendCodePoint(c)
            true
        } else {
            EmitSymbol(buf.toString())
            buf.clear()
            state = State.AFTER_VALUE
            false
        }
    }

    private fun HandleNumberState(c: Int): Boolean
    {
        return if ((c >= '0'.toInt() && c <= '9'.toInt()) ||
            c == '-'.toInt() || c == '.'.toInt() || c == 'e'.toInt() || c == 'E'.toInt()) {

            buf.appendCodePoint(c)
            true
        } else {
            EmitToken(JsonToken(JsonToken.Type.NUMBER, buf.toString()))
            buf.clear()
            state = State.AFTER_VALUE
            false
        }
    }

    private fun HandleNameStringState(c: Int): Boolean
    {
        if (c == -1) {
            Error("Unexpected end of file")
        }

        if (stringEscapeDigitsNum != -1) {
            val digit = when {
                c >= '0'.toInt() && c <= '9'.toInt() -> c - '0'.toInt()
                c >= 'a'.toInt() && c <= 'f'.toInt() -> c - 'a'.toInt() + 10
                c >= 'A'.toInt() && c <= 'F'.toInt() -> c - 'A'.toInt() + 10
                else -> Error("Invalid escape code digit: %c".format(c))
            }
            stringEscapeCode *= 16
            stringEscapeCode += digit
            stringEscapeDigitsNum++
            if (stringEscapeDigitsNum == 4) {
                buf.appendCodePoint(stringEscapeCode)
                stringEscapeDigitsNum = -1
                stringEscapeCode = 0
            }
            return true
        }

        if (stringEscape) {
            when (c) {
                '"'.toInt(), '/'.toInt(), '\\'.toInt() -> buf.appendCodePoint(c)
                'b'.toInt() -> buf.appendCodePoint('\b'.toInt())
                'f'.toInt() -> buf.appendCodePoint(0xc)
                'n'.toInt() -> buf.appendCodePoint('\n'.toInt())
                'r'.toInt() -> buf.appendCodePoint('\r'.toInt())
                't'.toInt() -> buf.appendCodePoint('\t'.toInt())
                'u'.toInt() -> {
                    stringEscapeDigitsNum = 0
                }
                else -> Error("Invalid escape character: %c".format(c))
            }
            stringEscape = false
            return true
        }

        if (c == '\\'.toInt()) {
            stringEscape = true
            return true
        }

        if (c == '"'.toInt()) {
            EmitToken(JsonToken(
                if (state == State.NAME) JsonToken.Type.NAME else JsonToken.Type.STRING,
                buf.toString()))
            buf.clear()
            state = if (state == State.NAME) State.AFTER_NAME else State.AFTER_VALUE
            return true
        }

        buf.appendCodePoint(c)
        return true
    }

    private fun HandleBeforeNameState(c: Int): Boolean
    {
        if (c == '"'.toInt()) {
            state = State.NAME
            return true
        }
        if (c == -1) {
            Error("Unexpected end of file")
        }
        UnexpectedChar(c)
    }

    private fun HandleAfterNameState(c: Int): Boolean
    {
        if (c == ':'.toInt()) {
            state = State.BEFORE_VALUE
            return true
        }
        if (c == -1) {
            Error("Unexpected end of file")
        }
        UnexpectedChar(c)
    }

    private fun HandleAfterValueState(c: Int):Boolean
    {
        val stackTop = stack.peek()
        if (stackTop == null) {
            if (c == -1) {
                EmitToken(JsonToken.EOF)
            } else {
                UnexpectedChar(c)
            }
            /* EOF character never consumed. */
            return false
        }

        if (stackTop == StackItem.ARRAY) {
            if (c == ','.toInt()) {
                state = State.BEFORE_VALUE
                return true
            }
            if (c == ']'.toInt()) {
                EmitToken(JsonToken.END_ARRAY)
                state = State.AFTER_VALUE
                stack.pop()
                return true
            }
            UnexpectedChar(c)
        }

        if (c == ','.toInt()) {
            state = State.BEFORE_NAME
            return true
        }
        if (c == '}'.toInt()) {
            EmitToken(JsonToken.END_OBJECT)
            state = State.AFTER_VALUE
            stack.pop()
            return true
        }
        UnexpectedChar(c)
    }

    private fun EmitToken(token: JsonToken)
    {
        if (lastToken != null) {
            throw IllegalStateException("Token overwritten")
        }
        lastToken = token
    }

    private fun EmitSymbol(name: String)
    {
        val token = when (name) {
            "null" -> JsonToken.NULL
            "true" -> JsonToken.TRUE
            "false" -> JsonToken.FALSE
            else -> Error("Illegal symbol: $name")
        }
        EmitToken(token)
    }

    private fun Error(msg: String): Nothing
    {
        throw JsonReadError("[$curLine:$curCol] $msg")
    }

    private fun UnexpectedChar(c: Int): Nothing
    {
        Error("Unexpected character: %c".format(c))
    }

    /** Read next character from the input. Some pre-processing is performed. -1 is returned when
     * EOF reached.
     */
    private fun NextChar(): Int
    {
        while (true) {
            val c = NextCodePoint()
            if (!IsValidCharacter(c)) {
                Error("Illegal character encountered")
            }
            if (c == -1) {
                return -1
            }
            if (c == 0) {
                continue
            }
            if (c == 0xd) {
                lastCharCr = true
                curLine++
                curCol = 1
                return 0xa
            }
            if (c == 0xa && lastCharCr) {
                lastCharCr = false
                continue
            }
            if (c == 0xa) {
                curLine++
                curCol = 1
            } else {
                curCol++
            }
            lastCharCr = false
            return c
        }
    }

    /** Read next Unicode code point from the input. -1 is returned when EOF reached. */
    private fun NextCodePoint(): Int
    {
        while (true) {
            val hi = reader.read()
            if (hi == -1) {
                return -1
            }
            val hiC = hi.toChar()
            if (Character.isLowSurrogate(hiC)) {
                Error("Low surrogate unit encountered before high surrogate")
            }
            if (!Character.isHighSurrogate(hiC)) {
                return hi
            }
            val lo = reader.read()
            if (lo == -1) {
                Error("Unterminated high surrogate at EOF")
            }
            val loC = lo.toChar()
            if (!Character.isLowSurrogate(loC)) {
                Error("Low surrogate not found after high surrogate")
            }
            return Character.toCodePoint(hiC, loC)
        }
    }

    private fun IsValidCharacter(c: Int): Boolean
    {
        return !(
            c in 1..8 ||
                c in 0x0e..0x1f ||
                c in 0x7f..0x9f ||
                c in 0xfdd0..0xfdef ||
                c == 0xb ||
                c == 0xfffe ||
                c == 0xffff ||
                c == 0x1fffe ||
                c == 0x1ffff ||
                c == 0x2fffe ||
                c == 0x2ffff ||
                c == 0x3fffe ||
                c == 0x3ffff ||
                c == 0x4fffe ||
                c == 0x4ffff ||
                c == 0x5fffe ||
                c == 0x5ffff ||
                c == 0x6fffe ||
                c == 0x6ffff ||
                c == 0x7fffe ||
                c == 0x7ffff ||
                c == 0x8fffe ||
                c == 0x8ffff ||
                c == 0x9fffe ||
                c == 0x9ffff ||
                c == 0xafffe ||
                c == 0xaffff ||
                c == 0xbfffe ||
                c == 0xbffff ||
                c == 0xcfffe ||
                c == 0xcffff ||
                c == 0xdfffe ||
                c == 0xdffff ||
                c == 0xefffe ||
                c == 0xeffff ||
                c == 0xffffe ||
                c == 0xfffff ||
                c == 0x10fffe ||
                c == 0x10ffff ||
                c > 0x10ffff)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun IsSymbolCharacter(c: Int): Boolean
    {
        return c == '_'.toInt() ||
            (c >= 'a'.toInt() && c <= 'z'.toInt()) ||
            (c >= 'A'.toInt() && c <= 'Z'.toInt())
    }
}
