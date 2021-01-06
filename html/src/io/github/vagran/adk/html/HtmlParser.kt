/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.html

import io.github.vagran.adk.CodePointsFilter
import io.github.vagran.adk.CodeUnitsParser
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.*
import kotlin.math.min


/** HTML streaming parser, synchronous, push workflow.
 * @param encoding Characters encoding for built-in decoder raw-bytes if specified non-null. Null
 *      value puts the parser in characters feeding mode so that only FeedChar() can be called.
 * @param errorCbk Optional callback for errors reporting. It called before exception is thrown when
 *      failOnError option is set.
 */
class HtmlParser(private val tokenCbk: (Token) -> Unit,
                 private val errorCbk: ((ParsingError) -> Unit)? = null,
                 encoding: Charset? = Charsets.UTF_8,
                 val options: Options = Options()) {

    data class Options(
        /** Trim leading and trailing whitespaces, replace sequence of interim whitespaces with a
         * single space. This is not applied to some tags content.
         */
        val normalizeWhitespaces: Boolean = true,
        /** Do not emit text token for script content. */
        val skipScriptText: Boolean = true,
        /** Do not emit text token for style tag content. */
        val skipStyleText: Boolean = true,
        /** Throw ParserError on failure without recovery attempt. */
        val failOnError: Boolean = false,
        /** Treat the document as XML. */
        val isXml: Boolean = false
    )

    data class Token(
        val type: Type,
        val value: String) {

        companion object {
            val EOF: Token = Token(Type.EOF, "")
        }

        enum class Type {
            /** End of file reached. The last token. */
            EOF,
            COMMENT,
            TEXT,
            /* Value is tag name in lower case. */
            TAG_OPEN,
            TAG_CLOSE,
            TAG_SELF_CLOSING,
            /* Value is attribute name in lower case. */
            ATTR_NAME,
            ATTR_VALUE
        }
    }

    class ParsingError(message: String,
                       val lineNumber: Int,
                       val colNumber: Int,
                       cause: Exception? = null):
        Exception(message, cause)

    fun FeedChar(c: Char)
    {
        if (charsetDecoder != null) {
            throw IllegalStateException("Cannot accept characters in raw bytes mode")
        }
        FeedCodeUnit(c.toInt())
    }

    fun FeedChars(chars: CharSequence)
    {
        for (c in chars) {
            FeedChar(c)
        }
    }

    fun FeedBytes(buf: ByteArray, offset: Int = 0, size: Int = buf.size - offset)
    {
        val bbuf = ByteBuffer.wrap(buf, offset, size)
        FeedBytes(bbuf)
        if (bbuf.hasRemaining()) {
            AppendInputBuf(bbuf)
        }
    }

    fun FeedBytes(buf: ByteBuffer)
    {
        FeedBytesExternal(buf, false)
    }

    /** Should be called after last byte/character fed. */
    fun Finish(buf: ByteBuffer? = null)
    {
        if (buf != null) {
            FeedBytesExternal(buf, true)
        } else if (inputBuf.position() > 0) {
            inputBuf.flip()
            FeedBytesInternal(inputBuf, true)
            inputBuf.compact()
        }
        /* Input buffer still can have some data left, e.g. not terminated multi-byte character,
         * ignore it for now.
         */
        FeedCodeUnit(-1)
    }


    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var state: State<*> = if (options.isXml) XmlDeclarationState() else DataState()
    private var nextState: State<*>? = null
    private val tagStack = ArrayDeque<TagStackItem>()
    private val tableStack = ArrayDeque<TableCtx>()
    private var rootText: HtmlTextBuilder? = null
    private var rootTextSeen = false
    private val inputBuf = ByteBuffer.allocate(4096)
    private val charsetDecoder = encoding?.newDecoder()
    private val charBuffer = CharBuffer.allocate(4096)
    private val codePointsFilter = CodePointsFilter(this::FeedFilteredCodePoint,
                                                    { ParsingError(it) },
                                                    this::IsValidCharacter)
    private val codeUnitsParser = CodeUnitsParser(codePointsFilter::Feed) { ParsingError(it) }
    private val curLine get() = codePointsFilter.curLine
    private val curCol get() = codePointsFilter.curCol


    init {
        if (charsetDecoder != null) {
            if (options.failOnError) {
                charsetDecoder.onMalformedInput(CodingErrorAction.REPORT)
                charsetDecoder.onUnmappableCharacter(CodingErrorAction.REPORT)
            } else {
                charsetDecoder.onMalformedInput(CodingErrorAction.IGNORE)
                charsetDecoder.onUnmappableCharacter(CodingErrorAction.IGNORE)
            }
        }
    }

    companion object {
        private val formattingTags = TreeSet<String>().apply {
            add("a")
            add("b")
            add("big")
            add("code")
            add("em")
            add("font")
            add("i")
            add("nobr")
            add("s")
            add("small")
            add("strike")
            add("strong")
            add("tt")
            add("u")
        }

        /** Implicit closing allowed without error for these tags. */
        private val impliedClosingTags = TreeSet<String>().apply {
            add("caption")
            add("colgroup")
            add("dd")
            add("dt")
            add("li")
            add("optgroup")
            add("option")
            add("p")
            add("rb")
            add("rp")
            add("rt")
            add("rtc")
            add("tbody")
            add("td")
            add("tfoot")
            add("th")
            add("thead")
            add("tr")
        }

        /** Tags which are always empty. They should be assumed self-closed even if self-closing is not
         * specified.
         */
        private val emptyTags = TreeSet<String>().apply {
            add("area")
            add("base")
            add("basefont")
            add("bgsound")
            add("br")
            add("col")
            add("embed")
            add("hr")
            add("img")
            add("input")
            add("link")
            add("meta")
            add("param")
            add("source")
            add("track")
            add("wbr")
        }

        /** Tags which are used for table formatting. Special logic applied to them since they
         * are not required to have closing tag which should be implied based on valid nesting
         * graph.
         */
        private val tableTags = TreeSet<String>().apply {
            add("caption")
            add("colgroup")
            add("col")
            add("thead")
            add("tbody")
            add("tfoot")
            add("tr")
            add("th")
            add("td")
        }

        /** Validity graph for table formatting tags. */
        private class TableGraphNode(val name: String) {
            val parents = ArrayList<TableGraphNode>()
        }
        private val tableGraph = TreeMap<String, TableGraphNode>()
        init {
            fun Add(name: String, parents: List<String>): TableGraphNode
            {
                return TableGraphNode(name).also {
                    tableGraph[name] = it
                    it.parents.addAll(parents.map { parentName -> tableGraph[parentName]!! })
                }
            }
            /* Parent tags are in preference order for implicit insertion. */
            Add("", emptyList()) // Root
            Add("caption", listOf(""))
            Add("colgroup", listOf(""))
            Add("col", listOf("", "colgroup"))
            Add("thead", listOf(""))
            Add("tbody", listOf(""))
            Add("tfoot", listOf(""))
            Add("tr", listOf("tbody", "thead", "tfoot"))
            Add("th", listOf("tr"))
            Add("td", listOf("tr"))
        }

        /** These character codes are remapped if specified via numeric character references. This
         * is fallback action, parsing error still is raised.
         */
        private val numericCharRefRemap = TreeMap<Int, Int>().apply {
            put(0x00, 0xFFFD)
            put(0x80, 0x20AC)
            put(0x82, 0x201A)
            put(0x83, 0x0192)
            put(0x84, 0x201E)
            put(0x85, 0x2026)
            put(0x86, 0x2020)
            put(0x87, 0x2021)
            put(0x88, 0x02C6)
            put(0x89, 0x2030)
            put(0x8A, 0x0160)
            put(0x8B, 0x2039)
            put(0x8C, 0x0152)
            put(0x8E, 0x017D)
            put(0x91, 0x2018)
            put(0x92, 0x2019)
            put(0x93, 0x201C)
            put(0x94, 0x201D)
            put(0x95, 0x2022)
            put(0x96, 0x2013)
            put(0x97, 0x2014)
            put(0x98, 0x02DC)
            put(0x99, 0x2122)
            put(0x9A, 0x0161)
            put(0x9B, 0x203A)
            put(0x9C, 0x0153)
            put(0x9E, 0x017E)
            put(0x9F, 0x0178)
        }
    }


    private class TagStackItem(val tagName: String) {
        var text: HtmlTextBuilder? = null
        var textSeen = false
        val isRaw = tagName == "script" || tagName == "style" || tagName == "textarea"
    }


    private class TableCtx {
        class Tag(val name: String)

        val stack = ArrayDeque<Tag>().apply {
            add(ROOT)
        }

        companion object {
            val ROOT = Tag("")
        }
    }


    private abstract inner class State<TReturn> {
        /** Parent state if any. */
        var parent: State<*>? = null
        var returnHandler: ((TReturn) -> Unit)? = null

        /** Try to consume next character.
         * @return True if character consumed, false if it should be re-consumed.
         */
        abstract fun Consume(c: Int): Boolean

        /** Called when entering state. */
        open fun OnEnter()
        {}

        /** Called when exiting state. */
        open fun OnExit()
        {}

        /** Called when entering substate of this state. */
        open fun OnEnterSubstate()
        {}

        /** Called when returning from a substate. Return value is provided as argument. */
        open fun OnReturnSubstate(value: String)
        {}

        /** Return from substate. */
        protected fun Return(value: TReturn)
        {
            parent!!.also { parent ->
                Switch(parent)
                if (returnHandler == null) {
                    parent.OnReturnSubstate(value as String)
                } else {
                    returnHandler!!(value)
                }
            }
        }

        protected fun Switch(newState: State<*>)
        {
            SwitchState(newState)
        }

        protected fun <TSubstateReturn>
            EnterSubstate(newState: State<TSubstateReturn>,
                          returnHandler: ((TSubstateReturn) -> Unit)? = null)
        {
            newState.parent = this
            newState.returnHandler = returnHandler
            Switch(newState)
        }
    }


    private inner class DataState: State<Unit>() {

        override fun Consume(c: Int): Boolean
        {
            when (c) {
                '<'.toInt() -> {
                    SwitchState(TagState())
                }

                '&'.toInt() -> {
                    EnterSubstate(CharRefState())
                }

                -1 -> {
                    SwitchState(EofState())
                    return false
                }

                else -> {
                    EmitText(c)
                }
            }
            return true
        }

        override fun OnReturnSubstate(value: String)
        {
            EmitText(value)
        }
    }


    private inner class XmlDeclarationState: State<Unit>() {
        override fun Consume(c: Int): Boolean
        {
            when (c) {
                '<'.toInt() -> {
                    EnterSubstate(MatchStringState("?xml", false), this::OnXmlMatched)
                }

                -1 -> {
                    SwitchState(EofState())
                    return false
                }

                else -> {
                    ParsingError("XML declaration expected")
                    SwitchState(DataState())
                    return false
                }
            }
            return true
        }

        fun OnXmlMatched(result: MatchResult)
        {
            if (result.isMatched) {
                SwitchState(ProcessingInstructionState())
            } else {
                ParsingError("Bad XML declaration")
                SwitchState(if (result.text.isEmpty()) TagState() else BogusCommentState())
            }
        }
    }


    /** Switched when initial "<?" already consumed. */
    private inner class ProcessingInstructionState: State<Unit>() {
        var qmSeen = false

        override fun Consume(c: Int): Boolean
        {
            when (c) {
                '?'.toInt() -> {
                    qmSeen = true
                }

                '>'.toInt() -> {
                    if (qmSeen) {
                        SwitchState(DataState())
                    }
                    qmSeen = false
                }

                -1 -> {
                    ParsingError("Unterminated processing instruction")
                    SwitchState(EofState())
                    return false
                }

                else -> {
                    qmSeen = false
                }
            }
            return true
        }
    }


    private inner class RawTextDataState(val tagName: String, val allowCharRef: Boolean):
        State<Unit>() {

        override fun Consume(c: Int): Boolean
        {
            when {
                c == '<'.toInt() -> {
                    EnterSubstate(RawTextClosingTagState(tagName), this::OnClosingTagFallback)
                }

                c == -1 -> {
                    ParsingError("Unterminated tag $tagName")
                    SwitchState(EofState())
                    return false
                }

                allowCharRef && c == '&'.toInt() -> {
                    EnterSubstate(CharRefState())
                }

                else -> {
                    if (emitText) {
                        EmitText(c)
                    }
                }
            }
            return true
        }

        private val emitText = !((tagName == "script" && options.skipScriptText) ||
                                 (tagName == "style" && options.skipStyleText))

        override fun OnReturnSubstate(value: String)
        {
            if (emitText) {
                EmitText(value)
            }
        }

        fun OnClosingTagFallback(text: String) {
            if (emitText) {
                EmitText('<'.toInt())
                EmitText(text)
            }
        }
    }


    /** Switches state to DataState if closing tag found, returns consumed text otherwise.
     * Attributes in closing tag will fail matching.
     */
    private inner class RawTextClosingTagState(val tagName: String): State<String>() {

        override fun Consume(c: Int): Boolean
        {
            if (!nameMatched) {
                EnterSubstate(MatchStringState("/$tagName", false), this::OnName)
                return false
            }
            when {
                Character.isWhitespace(c) -> {
                    buf.appendCodePoint(c)
                }

                c == '>'.toInt() -> {
                    EmitTagClose(tagName, false)
                    SwitchState(DataState())
                }

                else -> {
                    Return(buf.toString())
                    return false
                }
            }
            return true
        }

        private var nameMatched = false
        private val buf = StringBuilder()

        private fun OnName(result: MatchResult)
        {
            if (!result.isMatched) {
                Return(result.text)
            }
            nameMatched = true
            buf.append(result.text)
        }
    }


    data class MatchResult(val text: String,
                           val isMatched: Boolean)

    /** Tries to match the specified string. Pattern should be in lower case if case-insensitive
     * match is requested. Assuming pattern consists of BMP code points only.
     */
    private inner class MatchStringState(val pattern: String, caseSensitive: Boolean):
        State<MatchResult>() {

        override fun Consume(c: Int): Boolean
        {
            if (buf == null) {
                if (c == pattern[matchIdx].toInt()) {
                    matchIdx++
                } else {
                    Return(MatchResult(pattern.substring(0, matchIdx), false))
                    return false
                }
            } else {
                if (Character.toLowerCase(c) == pattern[matchIdx].toInt()) {
                    matchIdx++
                    buf.appendCodePoint(c)
                } else {
                    Return(MatchResult(buf.toString(), false))
                    return false
                }
            }
            if (matchIdx == pattern.length) {
                Return(MatchResult(buf.toString(), true))
            }
            return true
        }

        private var matchIdx = 0
        private val buf: StringBuilder? = if (caseSensitive) null else StringBuilder()
    }


    private inner class TagState: State<Unit>() {

        override fun Consume(c: Int): Boolean
        {
            if (selfClosing) {
                when (c) {
                    '>'.toInt() -> {
                        EmitTagClose(name!!, true)
                        SwitchState(DataState())
                    }

                    -1 -> {
                        ParsingError("Incomplete tag: $name")
                        EmitTagClose(name!!, true)
                        SwitchState(EofState())
                        return false
                    }

                    else -> {
                        ParsingError("Unexpected character in tag: %c".format(c))
                        selfClosing = false
                        return false
                    }
                }

            } else if (name == null) {
                when (c) {
                    '/'.toInt() -> {
                        isClosingTag = true
                        EnterSubstate(TagNameState(), this::OnName)
                    }

                    '!'.toInt() -> {
                        SwitchState(MarkupDeclarationState())
                    }

                    '?'.toInt() -> {
                        if (options.isXml) {
                            SwitchState(ProcessingInstructionState())
                        } else {
                            ParsingError("Unexpected processing instruction")
                            SwitchState(BogusCommentState())
                        }
                    }

                    in 'a'.toInt()..'z'.toInt(),
                    in 'A'.toInt()..'Z'.toInt() -> {
                        EnterSubstate(TagNameState(), this::OnName)
                        return false
                    }

                    else -> {
                        ParsingError("Unexpected character: %c".format(c))
                        EmitText('<'.toInt())
                        SwitchState(DataState())
                        return false
                    }
                }

            } else if (!attrValuePending) {
                when {
                    Character.isWhitespace(c) -> {/* Ignore */}

                    c == '/'.toInt() -> {
                        selfClosing = true
                    }

                    c == '>'.toInt() -> {
                        if (name == "script" || name == "style") {
                            SwitchState(RawTextDataState(name!!, false))
                        } else if (name == "title" || name == "textarea") {
                            SwitchState(RawTextDataState(name!!, true))
                        } else {
                            SwitchState(DataState())
                        }
                    }

                    c == -1 -> {
                        ParsingError("Incomplete tag: $name")
                        EmitTagClose(name!!, true)
                        SwitchState(EofState())
                        return false
                    }

                    c == '='.toInt() -> {
                        if (attrNameSeen) {
                            attrValuePending = true
                        } else {
                            ParsingError("Unexpected =")
                        }
                    }

                    else -> {
                        if (isClosingTag) {
                            ParsingError("Unexpected attribute in closing tag")
                        }
                        EnterSubstate(AttrNameState(), this::OnAttrName)
                        return false
                    }
                }

            } else {
                when {
                    Character.isWhitespace(c) -> {/* Ignore */}

                    c == '>'.toInt() -> {
                        ParsingError("Missing attribute value")
                        SwitchState(DataState())
                    }

                    c == -1 -> {
                        ParsingError("Unexpected EOF")
                        EmitTagClose(name!!, true)
                        SwitchState(EofState())
                        return false
                    }

                    else -> {
                        EnterSubstate(AttrValueState(), this::OnAttrValue)
                        return false
                    }
                }
            }
            return true
        }

        private var name: String? = null
        private var attrValuePending = false
        private var attrNameSeen = false
        private var selfClosing = false
        private var isClosingTag = false

        private fun OnName(name: String)
        {
            this.name = name
            if (isClosingTag) {
                EmitTagClose(name, false)
            } else {
                EmitTagOpen(name)
            }
        }

        private fun OnAttrName(name: String)
        {
            attrNameSeen = true
            if (!isClosingTag) {
                EmitToken(Token(Token.Type.ATTR_NAME, name))
            }
        }

        private fun OnAttrValue(value: String)
        {
            attrNameSeen = false
            attrValuePending = false
            if (!isClosingTag) {
                EmitToken(Token(Token.Type.ATTR_VALUE, value))
            }
        }

        override fun OnExit()
        {
            if (name != null && !isClosingTag && !selfClosing && name!! in emptyTags &&
                !options.isXml) {

                EmitTagClose(name!!, true)
            }
        }
    }

    private inner class TagNameState: State<String>() {

        override fun Consume(c: Int): Boolean
        {
            when {
                c in 'A'.toInt()..'Z'.toInt() -> {
                    buf.appendCodePoint(Character.toLowerCase(c))
                }

                Character.isWhitespace(c) ||
                c == '>'.toInt() ||
                c == '/'.toInt() ||
                c == -1 -> {
                    Return(buf.toString())
                    return false
                }

                else -> {
                    buf.appendCodePoint(c)
                }
            }
            return true
        }

        private val buf = StringBuilder()
    }


    private inner class AttrNameState: State<String>() {
        override fun Consume(c: Int): Boolean
        {
            when {
                Character.isWhitespace(c) -> {
                    Return(buf.toString())
                }

                c == '/'.toInt() ||
                c == '>'.toInt() ||
                c == '='.toInt() ||
                c == -1 -> {
                    Return(buf.toString())
                    return false
                }

                c == '"'.toInt() ||
                c == '\''.toInt() ||
                c == '<'.toInt() -> {
                    ParsingError("Unexpected character in attribute name: %c".format(c))
                    buf.appendCodePoint(c)
                }

                c in 'A'.toInt()..'Z'.toInt() -> {
                    buf.appendCodePoint(Character.toLowerCase(c))
                }

                else -> {
                    buf.appendCodePoint(c)
                }
            }
            return true
        }

        private val buf = StringBuilder()
    }


    private inner class AttrValueState: State<String>() {
        override fun Consume(c: Int): Boolean
        {
            if (quote == 0) {
                if (c == '"'.toInt() || c == '\''.toInt()) {
                    quote = c
                    return true
                } else {
                    quote = -1
                }
            }
            when {
                (quote == -1 && Character.isWhitespace(c)) ||
                (quote != -1 && c == quote) -> {
                    Return(buf.toString())
                }

                c == -1 -> {
                    Return(buf.toString())
                    return false
                }

                c == '&'.toInt() -> {
                    EnterSubstate(CharRefState())
                }

                quote == -1 &&
                    (c == '>'.toInt() ||
                     c == '/'.toInt()) -> {
                    Return(buf.toString())
                    return false
                }

                quote == -1 &&
                    (c == '"'.toInt() ||
                     c == '\''.toInt() ||
                     c == '<'.toInt() ||
                     c == '='.toInt() ||
                     c == '`'.toInt()) -> {
                    ParsingError("Unexpected character in attribute value: %c".format(c))
                    buf.appendCodePoint(c)
                }

                else -> {
                    buf.appendCodePoint(c)
                }
            }
            return true
        }

        private var quote: Int = 0
        private val buf = StringBuilder()

        override fun OnReturnSubstate(value: String)
        {
            buf.append(value)
        }
    }


    private inner class MarkupDeclarationState: State<Unit>() {

        override fun Consume(c: Int): Boolean
        {
            when (c) {
                '['.toInt() -> {
                    EnterSubstate(MatchStringState("[CDATA[", true), this::OnCdataMatch)
                    return false
                }

                'D'.toInt(), 'd'.toInt() -> {
                    EnterSubstate(MatchStringState("doctype", false), this::OnDoctypeMatch)
                    return false
                }

                '-'.toInt() -> {
                    SwitchState(CommentState())
                    return false
                }

                else -> {
                    ParsingError("Unrecognized markup declaration")
                    SwitchState(BogusCommentState())
                }
            }
            return true
        }

        private fun OnCdataMatch(result: MatchResult)
        {
            if (result.isMatched) {
                SwitchState(CdataState())
            } else {
                ParsingError("Unrecognized markup declaration")
                SwitchState(BogusCommentState())
            }
        }

        private fun OnDoctypeMatch(result: MatchResult) {
            if (result.isMatched) {
                SwitchState(DoctypeState())
            } else {
                ParsingError("Unrecognized markup declaration")
                SwitchState(BogusCommentState())
            }
        }
    }

    private inner class DoctypeState: State<Unit>() {

        override fun Consume(c: Int): Boolean
        {
            /* For now DOCTYPE is not fully parsed and token is not generated. */
            if (c == '>'.toInt()) {
                SwitchState(DataState())
            }
            if (c == -1) {
                ParsingError("Incomplete document declaration")
                SwitchState(EofState())
                return false
            }
            return true
        }
    }

    private inner class CdataState: State<Unit>() {
        override fun Consume(c: Int): Boolean
        {
            when (c) {
                -1 -> {
                    SwitchState(EofState())
                    return false
                }

                ']'.toInt() -> {
                    EnterSubstate(MatchStringState("]]>", false), this::OnEndMarker)
                    return false
                }

                else -> {
                    EmitText(c)
                }
            }
            return true
        }

        private fun OnEndMarker(result: MatchResult)
        {
            if (result.isMatched) {
                SwitchState(DataState())
            } else {
                EmitText(result.text)
            }
        }
    }


    private inner class CommentState: State<Unit>() {

        override fun Consume(c: Int): Boolean
        {
            when (matchIdx) {
                0, 1 -> {
                    if (c != '-'.toInt()) {
                        ParsingError("Bogus comment")
                        SwitchState(BogusCommentState())
                        return false
                    }
                    matchIdx++
                }

                2 -> {
                    if (c == '>'.toInt()) {
                        ParsingError("Bogus comment")
                        SwitchState(DataState())
                    } else {
                        matchIdx++
                    }
                }

                3, 4 -> {
                    if (c == '-'.toInt()) {
                        matchIdx++
                    } else {
                        matchIdx = 3
                    }
                }

                5 -> {
                    if (c == '>'.toInt()) {
                        EmitToken(Token(Token.Type.COMMENT, ""))
                        SwitchState(DataState())
                    } else {
                        matchIdx = 3
                    }
                }
            }
            return true
        }

        private var matchIdx = 0
    }


    private inner class BogusCommentState: State<Unit>() {

        override fun Consume(c: Int): Boolean
        {
            when (c) {
                '>'.toInt() -> {
                    SwitchState(DataState())
                }

                -1 -> {
                    SwitchState(EofState())
                    return false
                }
            }
            return true
        }
    }


    private inner class CharRefState: State<String>() {

        override fun Consume(c: Int): Boolean
        {
            when {
                Character.isWhitespace(c) ||
                c == '<'.toInt() ||
                c == '&'.toInt() ||
                c == -1 -> {
                    Return(buf.toString())
                    return false
                }

                buf.length == 1 && c == '#'.toInt() -> {
                    buf.append('#')
                    isNumeric = true
                }

                isNumeric && buf.length == 2 && (c == 'x'.toInt() || c == 'X'.toInt()) -> {
                    buf.appendCodePoint(c)
                    isHex = true
                }

                c == ';'.toInt() -> {
                    if (isNumeric) {
                        if (charCode != 0 && IsValidCharacter(charCode)) {
                            Return(String(Character.toChars(charCode)))
                        } else {
                            val replacement = numericCharRefRemap[charCode]
                            if (replacement != null) {
                                ParsingError("Illegal code specified via numeric character reference: %Xh"
                                                 .format(charCode))
                                Return(String(Character.toChars(replacement)))
                            } else {
                                ParsingError(
                                    "Invalid code specified via numeric character reference: %Xh"
                                        .format(charCode))
                                Return("\uFFFD")
                            }
                        }
                    } else {
                        val name = buf.substring(1)
                        val code = HtmlEntities.entities[name]
                        if (code == null) {
                            ParsingError("Invalid character reference: $name")
                            buf.append(';')
                            Return(buf.toString())
                        } else {
                            Return(String(Character.toChars(code)))
                        }
                    }
                    return true
                }

                isHex -> {
                    when (c) {
                        in '0'.toInt()..'9'.toInt() -> {
                            charCode *= 16
                            charCode += c - '0'.toInt()
                        }

                        in 'a'.toInt()..'f'.toInt() -> {
                            charCode *= 16
                            charCode += c - 'a'.toInt() + 10
                        }

                        in 'A'.toInt()..'F'.toInt() -> {
                            charCode *= 16
                            charCode += c - 'A'.toInt() + 10
                        }

                        else -> {
                            ParsingError("Invalid character for hexadecimal code: %c".format(c))
                            Return(buf.toString())
                            return false
                        }
                    }
                    buf.appendCodePoint(c)
                    if (charCode > 0x10ffff) {
                        ParsingError("Numeric character reference code overflow")
                        Return(buf.toString())
                    }
                }

                isNumeric -> {
                    if (c in '0'.toInt()..'9'.toInt()) {
                        charCode *= 10
                        charCode += c - '0'.toInt()
                        buf.appendCodePoint(c)
                        if (charCode > 0x10ffff) {
                            ParsingError("Numeric character reference code overflow")
                            Return(buf.toString())
                        }
                    } else {
                        ParsingError("Invalid character for decimal code: %c".format(c))
                        Return(buf.toString())
                        return false
                    }
                }

                c in 'a'.toInt()..'z'.toInt() -> {
                    buf.appendCodePoint(c)
                    if (buf.length > 128) {
                        Return(buf.toString())
                    }
                }

                c in 'A'.toInt()..'Z'.toInt() -> {
                    buf.appendCodePoint(Character.toLowerCase(c))
                    if (buf.length > 128) {
                        Return(buf.toString())
                    }
                }

                else -> {
                    Return(buf.toString())
                    return false
                }
            }
            return true
        }

        private val buf = StringBuilder(8)
        init {
            buf.append('&')
        }
        private var isNumeric = false
        private var isHex = false
        private var charCode: Int = 0
    }


    private inner class EofState: State<Unit>() {

        override fun OnEnter()
        {
            CommitText(options.normalizeWhitespaces)
            FinalizeTags()
        }

        override fun Consume(c: Int): Boolean
        {
            if (consumed) {
                throw Error("Character fed after EOF: $c")
            }
            if (c != -1) {
                throw Error("Unexpected character in EOF state: $c")
            }
            consumed = true
            EmitToken(Token.EOF)
            return true
        }

        private var consumed = false
    }


    private fun ParsingError(message: String, cause: Exception? = null)
    {
        if (!options.failOnError && errorCbk == null) {
            return
        }
        val e = ParsingError(message, curLine, curCol, cause)
        errorCbk?.invoke(e)
        if (options.failOnError) {
            throw e
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
            (c and 0xffff) == 0xfffe ||
            (c and 0xffff) == 0xffff ||
            c > 0x10ffff)
    }

    private fun EmitToken(token: Token)
    {
        tokenCbk(token)
    }

    private fun GetCurrentTextBuilder(): HtmlTextBuilder
    {
        val tag = GetCurrentTag()

        if (tag == null) {
            return rootText ?: HtmlTextBuilder(options.normalizeWhitespaces && !rootTextSeen,
                                               options.normalizeWhitespaces)
                .also { rootText = it }
        }

        return tag.text ?: HtmlTextBuilder(!tag.isRaw && options.normalizeWhitespaces &&
                                               !IsInTextContext(),
                                           !tag.isRaw && options.normalizeWhitespaces)
            .also { tag.text = it }
    }

    private fun EmitText(c: Int)
    {
        GetCurrentTextBuilder().FeedChar(c)
    }

    private fun EmitText(text: String)
    {
        GetCurrentTextBuilder().FeedString(text)
    }

    private fun EmitTagOpen(name: String)
    {
        if (!options.isXml) {
            if (name == "table") {
                tableStack.addLast(TableCtx())
            } else if (name in tableTags) {
                val ctx = tableStack.peekLast()
                if (ctx == null) {
                    ParsingError("Table tag not in table scope: $name")
                } else {
                    EmitTableTagOpen(name, ctx)
                }
            }
        }
        EmitTagOpenImpl(name)
    }

    private fun EmitTagOpenImpl(name: String)
    {
        CommitText(false)
        EmitToken(Token(Token.Type.TAG_OPEN, name))
        tagStack.addLast(TagStackItem(name))
    }

    private fun EmitTagClose(name: String, selfClosing: Boolean)
    {
        if (!options.isXml) {
            if (name == "table") {
                tableStack.pollLast()
            } else if (name in tableTags) {
                val ctx = tableStack.peekLast()
                if (ctx != null) {
                    EmitTableTagClose(name, ctx)
                }
            }
        }
        EmitTagCloseImpl(name, selfClosing)
    }

    private fun EmitTagCloseImpl(name: String, selfClosing: Boolean)
    {
        CommitText(true)
        val closedTag = FindOpenTag(name)
        if (closedTag == null) {
            ParsingError("Unmatched closing tag: $name")
            return
        }
        while (true) {
            val tag = tagStack.removeLast()
            if (tag.textSeen && tag.tagName in formattingTags) {
                tagStack.last.textSeen = true
            }
            if (tag == closedTag) {
                if (selfClosing) {
                    EmitToken(Token(Token.Type.TAG_SELF_CLOSING, name))
                } else {
                    EmitToken(Token(Token.Type.TAG_CLOSE, name))
                }
                break
            } else {
                if (options.isXml || tag.tagName !in impliedClosingTags) {
                    ParsingError("Misnested or unclosed tag: ${tag.tagName}")
                }
                EmitToken(Token(Token.Type.TAG_CLOSE, tag.tagName))
            }
        }
    }

    /** Check if the specified child parent node is reachable for the specified child node via
     * table formatting validity graph.
     * @param missing Missing nodes between the parent and child are stored there, descending order.
     * @return true if reachable.
     */
    private fun CheckTableTagReachable(child: TableGraphNode, parent: TableGraphNode,
                                       missing: Deque<TableGraphNode>): Boolean
    {
        if (child === parent) {
            /* Graph is acyclic so node is never parent of itself. */
            return false
        }
        /* First check all direct parents which is the most common case. */
        for (_parent in child.parents) {
            if (_parent === parent) {
                return true
            }
        }
        for (_parent in child.parents) {
            missing.addFirst(_parent)
            if (CheckTableTagReachable(_parent, parent, missing)) {
                return true
            }
            missing.removeFirst()
        }
        return false
    }

    private fun EmitTableTagOpen(name: String, ctx: TableCtx)
    {
        /* Check if current tag from table context stack top is reachable from the new tag via the
         * table validity graph. Insert missing tags if yes, pop the tag if no and repeat.
         */
        var curParent: TableCtx.Tag
        val newNode = tableGraph[name]!!
        val missingNodes = ArrayDeque<TableGraphNode>()
        while (true) {
            curParent = ctx.stack.last
            val curParentNode = tableGraph[curParent.name]!!
            if (!CheckTableTagReachable(newNode, curParentNode, missingNodes)) {
                EmitTagCloseImpl(curParentNode.name, false)
                ctx.stack.removeLast()
                continue
            }
            for (missingNode in missingNodes) {
                EmitTagOpenImpl(missingNode.name)
                ctx.stack.addLast(TableCtx.Tag(missingNode.name))
            }
            break
        }
        ctx.stack.addLast(TableCtx.Tag(name))
    }

    private fun EmitTableTagClose(name: String, ctx: TableCtx)
    {
        /* Unclosed tags will be closed automatically later, just pop table stack here. */
        val openTag = run {
            for (tag in ctx.stack.descendingIterator()) {
                if (tag.name == name) {
                    return@run tag
                }
            }
            return
        }

        while (true) {
            val tag = ctx.stack.removeLast()
            if (tag === openTag) {
                break
            }
        }
    }

    private fun FinalizeTags()
    {
        while (true) {
            val tag = tagStack.pollLast() ?: break
            if (options.isXml || tag.tagName !in impliedClosingTags) {
                ParsingError("Misnested or unclosed tag: ${tag.tagName}")
            }
            EmitToken(Token(Token.Type.TAG_CLOSE, tag.tagName))
        }
    }

    /** Emit text token for all the text accumulated so far if any. */
    private fun CommitText(trimTrailingWs: Boolean)
    {
        val tag = GetCurrentTag()

        if (tag == null) {
            rootText?.also { rootText ->
                if (!options.normalizeWhitespaces) {
                    EmitToken(Token(Token.Type.TEXT, rootText.GetResult(false)))
                } else {
                    val result = rootText.GetResult(trimTrailingWs)
                    if (result.isNotEmpty()) {
                        EmitToken(Token(Token.Type.TEXT, result))
                        rootTextSeen = true
                    }
                }
                this.rootText = null
            }
            return
        }

        tag.text?.also { text ->
            if (!options.normalizeWhitespaces) {
                EmitToken(Token(Token.Type.TEXT, text.GetResult(false)))
            } else {
                val result = text.GetResult(trimTrailingWs && !tag.isRaw)
                if (result.isNotEmpty()) {
                    EmitToken(Token(Token.Type.TEXT, result))
                    tag.textSeen = true
                }
            }
            tag.text = null
        }
    }

    private fun SwitchState(state: State<*>)
    {
        nextState = state
    }

    private fun IsInTextContext(): Boolean
    {
        if (rootTextSeen) {
            return true
        }
        for (item in tagStack) {
            if (item.textSeen) {
                return true
            }
        }
        return false
    }

    private fun GetCurrentTag(): TagStackItem?
    {
        if (tagStack.size == 0) {
            return null
        }
        return tagStack.last
    }

    private fun FindOpenTag(name: String): TagStackItem?
    {
        for (item in tagStack.descendingIterator()) {
            if (item.tagName == name) {
                return item
            }
        }
        return null
    }

    /** Feed bytes from an external buffer. */
    private fun FeedBytesExternal(buf: ByteBuffer, isLastChunk: Boolean)
    {
        while (true) {
            val feedBuf = if (inputBuf.position() != 0) {
                AppendInputBuf(buf)
                inputBuf.flip()
                inputBuf
            } else {
                buf
            }
            FeedBytesInternal(feedBuf, isLastChunk && !(feedBuf === inputBuf && buf.hasRemaining()))
            if (feedBuf === inputBuf) {
                inputBuf.compact()
            }
            if (!isLastChunk || !buf.hasRemaining()) {
                break
            }
        }
    }

    /** Feed bytes from the specified buffer. */
    private fun FeedBytesInternal(buf: ByteBuffer, isLastChunk: Boolean)
    {
        if (charsetDecoder == null) {
            throw IllegalStateException("Cannot accept raw bytes in character mode")
        }
        var flush = false
        while (true) {
            val result = try {
                val result = if (flush) {
                    charsetDecoder.flush(charBuffer)
                } else {
                    charsetDecoder.decode(buf, charBuffer, isLastChunk)
                }
                if (result.isError) {
                    result.throwException()
                }
                result
            } catch (e: CharacterCodingException) {
                ParsingError("Character decoding failed", e)
                break
            }
            charBuffer.flip()
            while (charBuffer.hasRemaining()) {
                FeedCodeUnit(charBuffer.get().toInt())
            }
            charBuffer.clear()
            if (result.isOverflow) {
                continue
            }
            if (!flush && isLastChunk) {
                flush = true
                continue
            }
            break
        }
    }

    private fun AppendInputBuf(buf: ByteBuffer)
    {
        val n = min(inputBuf.remaining(), buf.remaining())
        if (buf.hasArray()) {
            val inPos = buf.position()
            val outPos = inputBuf.position()
            System.arraycopy(buf.array(), buf.arrayOffset() + inPos, inputBuf.array(), outPos, n)
            buf.position(inPos + n)
            inputBuf.position(outPos + n)
        } else {
            for (i in 0 until n) {
                inputBuf.put(buf.get())
            }
        }
    }

    /** Feed next code unit, -1 for EOF. */
    private fun FeedCodeUnit(c: Int)
    {
        codeUnitsParser.Feed(c)
    }

    /** Feed next filtered code point after some pr-processing performed, -1 for EOF. */
    private fun FeedFilteredCodePoint(c: Int)
    {
        var consumed: Boolean
        do {
            consumed = state.Consume(c)
            nextState?.also { nextState ->
                if (nextState.parent === state) {
                    state.OnEnterSubstate()
                } else {
                    state.OnExit()
                }
                if (state.parent !== nextState) {
                    nextState.OnEnter()
                }
                state = nextState
                this.nextState = null
            }
        } while (!consumed)
    }
}
