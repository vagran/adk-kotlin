/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.html

import io.github.vagran.adk.CodePointsFilter
import io.github.vagran.adk.CodeUnitsParser

/**
 * Parses filter expression. The expression can be in form:
 * selector[; auxSelector][, selector[; auxSelector]]...
 *
 * Auxiliary selector used for main selector adjusting (typically in alternative form, e.g. XPath).
 * XPath selector should be enclosed in curved braces. Otherwise it is treated as CSS selector.
 * Multiple selectors are merged into one filter instance.
 * Examples:
 * elA > * > elB#someId.someClass.anotherClass@elementTag elC@~textTag elD@^markTag
 * [attrName="attrValue"] .someClass[@tagName:attrName]
 * a:nth-child(1) b:nth-of-type(2) c:nth-last-child(1) d:nth-last-of-type(2)
 */
class HtmlFilterParser {
    class Expression(
        val selector: HtmlFilter,
        val auxSelector: HtmlFilter?
    ) {
        fun GetFilter(): HtmlFilter
        {
            if (auxSelector == null) {
                return selector
            }
            return selector.Clone().also { it.MergeWithAux(auxSelector) }
        }

        override fun toString(): String
        {
            return GetFilter().toString()
        }

        companion object {
            fun GetFilter(expressions: List<Expression>): HtmlFilter
            {
                val result = expressions.first().GetFilter()
                expressions.forEachIndexed {
                    idx, expr ->
                    if (idx != 0) {
                        result.MergeWith(expr.GetFilter())
                    }
                }
                return result
            }
        }
    }

    class ParsingError(message: String,
                       val lineNumber: Int,
                       val colNumber: Int,
                       cause: Exception? = null):
        Exception(message, cause)

    fun Parse(chars: CharSequence): List<Expression>
    {
        for (c in chars) {
            codeUnitsParser.Feed(c.toInt())
        }
        codeUnitsParser.Feed(-1)
        return BuildResult()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var state: State<*> = BeforeExpressionState()
    private var nextState: State<*>? = null
    private val codePointsFilter = CodePointsFilter(this::FeedCodePoint,
                                                    { ParsingError(it) },
                                                    this::IsValidCharacter)
    private val codeUnitsParser = CodeUnitsParser(codePointsFilter::Feed) { ParsingError(it) }
    private val curLine get() = codePointsFilter.curLine
    private val curCol get() = codePointsFilter.curCol
    private val nodes = ArrayList<Node>()
    private lateinit var curNode: Node
    private var rootPending = false

    private class Node(val type: Type) {
        enum class Type {
            SELECTOR,
            CHILD_SEP,
            AUX_SEP,
            SEL_SEP
        }
        val filterNode = HtmlFilter.Node()
        var isRoot = false
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

    private inner class BeforeExpressionState: State<Void>() {
        override fun Consume(c: Int): Boolean
        {
            if (Character.isWhitespace(c)) {
                return true
            }
            if (c == '{'.toInt()) {
                Switch(BeforeXpathState())
                return true
            }
            Switch(SelectorStartState())
            return false
        }
    }

    private inner class SelectorStartState: State<Void>() {
        private var skipWs = true
        override fun Consume(c: Int): Boolean
        {
            if (skipWs) {
                if (Character.isWhitespace(c) || c == -1) {
                    return true
                }
                skipWs = false
            }
            StartNode(Node.Type.SELECTOR)
            if (IsIdentStartChar(c) || c == '*'.toInt()) {
                Switch(TypeSelectorState())
                return false
            }
            Switch(SelectorSpecifierStartState())
            return false
        }
    }

    private inner class TypeSelectorState: State<Void>() {
        override fun Consume(c: Int): Boolean
        {
            if (c == '*'.toInt()) {
                Switch(AfterSelectorState())
                return true
            }
            EnterSubstate(IdentifierState()) {
                ident ->
                curNode.filterNode.elementName = ident
                Switch(AfterSelectorState())
            }
            return false
        }
    }

    private inner class IdentifierState(private val allowHyphen: Boolean = true):
        State<String>() {

        private val buf = StringBuilder()

        override fun Consume(c: Int): Boolean
        {
            if (buf.isEmpty()) {
                if (!IsIdentStartChar(c) || (allowHyphen && c == '-'.toInt())) {
                    UnexpectedCharacter(c)
                }
                buf.appendCodePoint(c)
                return true
            }
            if (IsIdentChar(c) || (allowHyphen && c == '-'.toInt())) {
                buf.appendCodePoint(c)
                return true
            }
            Return(buf.toString())
            return false
        }
    }

    private inner class AfterSelectorState: State<Void>() {
        private var wsSeen = false

        override fun Consume(c: Int): Boolean
        {
            if (c == -1) {
                return true
            }
            if (Character.isWhitespace(c)) {
                wsSeen = true
                return true
            }
            if (c == '>'.toInt()) {
                StartNode(Node.Type.CHILD_SEP)
                Switch(SelectorStartState())
                return true
            }
            if (c == ';'.toInt()) {
                StartNode(Node.Type.AUX_SEP)
                Switch(BeforeExpressionState())
                return true
            }
            if (c == ','.toInt()) {
                StartNode(Node.Type.SEL_SEP)
                Switch(BeforeExpressionState())
                return true
            }
            if (wsSeen) {
                Switch(SelectorStartState())
            } else {
                Switch(SelectorSpecifierStartState())
            }
            return false
        }
    }

    private inner class SelectorSpecifierStartState: State<Void>() {
        override fun Consume(c: Int): Boolean
        {
            when (c) {
                '#'.toInt() -> {
                    Switch(SelectorIdState())
                    return true
                }
                '.'.toInt() -> {
                    Switch(SelectorClassState())
                    return true
                }
                ':'.toInt() -> {
                    Switch(SelectorPseudoState())
                    return true
                }
                '['.toInt() -> {
                    Switch(SelectorAttributeState())
                    return true
                }
                '@'.toInt() -> {
                    Switch(SelectorTagState())
                    return true
                }
                else -> UnexpectedCharacter(c)
            }
        }
    }

    private inner class SelectorIdState: State<Void>() {
        override fun Consume(c: Int): Boolean
        {
            if (curNode.filterNode.htmlId != null) {
                ParsingError("ID already specified")
            }
            EnterSubstate(IdentifierState()) {
                ident ->
                curNode.filterNode.htmlId = ident
                Switch(AfterSelectorState())
            }
            return false
        }
    }

    private inner class SelectorClassState: State<Void>() {
        override fun Consume(c: Int): Boolean
        {
            EnterSubstate(IdentifierState()) {
                ident ->
                curNode.filterNode.AddClass(ident)
                Switch(AfterSelectorState())
            }
            return false
        }
    }

    private inner class SelectorPseudoState: State<Void>() {
        override fun Consume(c: Int): Boolean
        {
            EnterSubstate(IdentifierState(), this::OnName)
            return false
        }

        private fun OnName(name: String)
        {
            if (name == "root") {
                curNode.isRoot = true
                Switch(AfterSelectorState())
                return
            }
            EnterSubstate(ParenthesesIntegerState()) {
                num ->
                when(name) {
                    "nth-child" -> {
                        curNode.filterNode.index = num - 1
                    }
                    "nth-of-type" -> {
                        curNode.filterNode.indexOfType = num - 1
                    }
                    "nth-last-child" -> {
                        curNode.filterNode.index = -num
                    }
                    "nth-last-of-type" -> {
                        curNode.filterNode.indexOfType = -num
                    }
                    else -> ParsingError("Unsupported pseudo class")
                }
                SwitchState(AfterSelectorState())
            }
        }
    }

    private inner class SelectorTagState: State<Void>() {
        var extractText = false
        var markNode = false

        override fun Consume(c: Int): Boolean
        {
            if (c == '~'.toInt()) {
                if (extractText) {
                    UnexpectedCharacter(c)
                }
                extractText = true
                return true
            }
            if (c == '^'.toInt()) {
                if (markNode) {
                    UnexpectedCharacter(c)
                }
                markNode = true
                return true
            }
            EnterSubstate(IdentifierState()) {
                ident ->
                curNode.filterNode.AddExtractInfo(HtmlFilter.ExtractInfo().also {
                    it.tagName = ident
                    it.extractText = extractText
                    it.markNode = markNode
                })
                Switch(AfterSelectorState())
            }
            return false
        }
    }

    private inner class ParenthesesIntegerState: State<Int>() {
        private var parenthesesSeen = false
        private var result: Int? = null

        override fun Consume(c: Int): Boolean
        {
            if (!parenthesesSeen) {
                if (c != '('.toInt()) {
                    ParsingError("'(' expected")
                }
                parenthesesSeen = true
                return true
            }
            result?.also {
                if (c != ')'.toInt()) {
                    ParsingError("')' expected")
                }
                Return(it)
                return true
            }
            EnterSubstate(IntegerState()) { result = it }
            return false
        }
    }

    private inner class IntegerState: State<Int>() {
        private val buf = StringBuilder()

        override fun Consume(c: Int): Boolean
        {
            if (c in '0'.toInt() .. '9'.toInt()) {
                buf.appendCodePoint(c)
                return true
            }
            if (buf.isEmpty()) {
                ParsingError("Integer expected")
            }
            Return(try {
                buf.toString().toInt()
            } catch (e: NumberFormatException) {
                throw ParsingError("Bad integer", e)
            })
            return false
        }
    }

    private inner class QuotedStringState(private val allowUnquoted: Boolean = false):
        State<String>() {

        private var firstCharSeen = false
        private var isQuoted = false
        private val buf = StringBuilder()
        private var escape = false

        override fun Consume(c: Int): Boolean
        {
            if (!firstCharSeen) {
                firstCharSeen = true
                if (c == '"'.toInt()) {
                    isQuoted = true
                    return true
                }
                if (!allowUnquoted) {
                    ParsingError("'\"' expected")
                }
            }
            if (isQuoted) {
                if (escape) {
                    escape = false
                    buf.appendCodePoint(c)
                    return true
                }
                if (c == '\\'.toInt()) {
                    escape = true
                    return true
                }
                if (c == '"'.toInt()) {
                    Return(buf.toString())
                    return true
                }
                buf.appendCodePoint(c)
                return true
            } else {
                if (IsUnquotedStringChar(c)) {
                    buf.appendCodePoint(c)
                    return true
                }
                Return(buf.toString())
                return false
            }
        }
    }

    private inner class SelectorAttributeState: State<Void>() {
        private var tagName: String? = null
        private var tagSepSeen = false
        private var attrName: String? = null
        private var valueSepSeen = false
        private var attrValue: String? = null

        override fun Consume(c: Int): Boolean
        {
            if (c == '@'.toInt()) {
                if (tagName != null) {
                    UnexpectedCharacter(c)
                }
                EnterSubstate(IdentifierState(allowHyphen = false)) {
                    ident ->
                    tagName = ident
                }
                return true
            }
            if (tagName != null && !tagSepSeen) {
                if (c != ':'.toInt()) {
                    ParsingError("':' expected")
                }
                tagSepSeen = true
                return true
            }
            if (attrName == null) {
                EnterSubstate(IdentifierState()) { attrName = it }
                return false
            }
            if (c == ']'.toInt()) {
                Switch(AfterSelectorState())
                return true
            }
            if (!valueSepSeen) {
                if (c != '='.toInt()) {
                    ParsingError("'=' expected")
                }
                valueSepSeen = true
                return true
            }
            if (attrValue == null) {
                EnterSubstate(QuotedStringState(true)) { attrValue = it }
                return false
            }
            UnexpectedCharacter(c)
        }

        override fun OnExit()
        {
            if (tagName != null && attrValue != null) {
                ParsingError("Attribute value cannot be specified for tagged attribute")
            }
            if (tagName != null) {
                curNode.filterNode.AddExtractInfo(HtmlFilter.ExtractInfo().also {
                    it.tagName = tagName!!
                    it.attrName = attrName
                })
            } else {
                curNode.filterNode.AddAttribute(attrName!!, attrValue)
            }
        }
    }

    private inner class BeforeXpathState: State<Void>() {

        override fun Consume(c: Int): Boolean
        {
            if (Character.isWhitespace(c)) {
                return true
            }
            if (c == '/'.toInt()) {
                Switch(XpathNodeSepState())
                return true
            }
            Switch(XpathNodeStartState())
            return false
        }
    }

    private inner class XpathNodeStartState: State<Void>() {
        override fun Consume(c: Int): Boolean
        {
            if (IsIdentStartChar(c) || c == '*'.toInt()) {
                Switch(XpathNodeState())
                return false
            }
            if (c == '@'.toInt()) {
                Switch(XpathAttrState())
                return true
            }
            UnexpectedCharacter(c)
        }
    }

    private inner class XpathNodeState: State<Void>() {
        override fun Consume(c: Int): Boolean
        {
            StartNode(Node.Type.SELECTOR)
            if (rootPending) {
                curNode.isRoot = true
                rootPending = false
            }
            if (c == '*'.toInt()) {
                Switch(XpathAfterNodeState())
                return true
            }
            EnterSubstate(IdentifierState(allowHyphen = true)) {
                ident ->
                curNode.filterNode.elementName = ident
                Switch(XpathAfterNodeState())
            }
            return false
        }
    }

    private inner class XpathAttrState: State<Void>() {
        private var attrName: String? = null

        override fun Consume(c: Int): Boolean
        {
            if (attrName == null) {
                EnterSubstate(IdentifierState()) {
                    ident ->
                    attrName = ident
                }
                return false
            }
            if (c == ':'.toInt()) {
                EnterSubstate(IdentifierState(allowHyphen = false)) {
                    ident ->
                    if (nodes.isEmpty() || curNode.type != Node.Type.SELECTOR) {
                        if (rootPending) {
                            throw ParsingError("Attribute node not allowed in root")
                        }
                        StartNode(Node.Type.SELECTOR)
                    }
                    curNode.filterNode.AddExtractInfo(
                        HtmlFilter.ExtractInfo().also {
                            it.tagName = ident
                            it.attrName = attrName
                        })
                    Switch(XpathAfterNodeState())
                }
                return true
            }
            UnexpectedCharacter(c)
        }
    }

    private inner class XpathNodeSepState: State<Void>() {
        override fun Consume(c: Int): Boolean
        {
            if (c == '@'.toInt()) {
                Switch(XpathAttrState())
                return true
            }
            Switch(XpathNodeState())
            if (c == '/'.toInt()) {
                return true
            }
            if (nodes.isEmpty() || curNode.type == Node.Type.AUX_SEP ||
                curNode.type == Node.Type.SEL_SEP) {

                rootPending = true
            } else {
                StartNode(Node.Type.CHILD_SEP)
            }
            return false
        }
    }

    private inner class XpathAfterNodeState: State<Void>() {
        var wsSeen = false

        override fun Consume(c: Int): Boolean
        {
            if (Character.isWhitespace(c)) {
                wsSeen = true
                return true
            }
            if (c == '}'.toInt()) {
                Switch(AfterXpathState())
                return true
            }
            if (wsSeen) {
                UnexpectedCharacter(c)
            }
            if (c == '/'.toInt()) {
                Switch(XpathNodeSepState())
                return true
            }
            if (c == '['.toInt()) {
                Switch(XpathPredicateState())
                return true
            }
            if (c == ':'.toInt()) {
                Switch(XpathTagState())
                return true
            }
            UnexpectedCharacter(c)
        }
    }

    private inner class XpathPredicateState: State<Void>()
    {
        override fun Consume(c: Int): Boolean
        {
            if (c == '@'.toInt()) {
                Switch(XpathPredicateAttrState())
                return true
            }
            Switch(XpathPredicateIndexState())
            return false
        }
    }

    private inner class XpathPredicateAttrState: State<Void>()
    {
        private var attrName: String? = null
        private var attrValue: String? = null

        override fun Consume(c: Int): Boolean
        {
            val attrName = attrName
            if (attrName == null) {
                EnterSubstate(IdentifierState()) {
                    ident ->
                    this.attrName = ident
                }
                return false
            }
            if (attrValue == null) {
                if (c == '='.toInt()) {
                    EnterSubstate(QuotedStringState(true)) {
                        value ->
                        attrValue = value
                    }
                    return true
                }
                if (c == ']'.toInt()) {
                    curNode.filterNode.AddAttribute(attrName)
                    Switch(XpathAfterNodeState())
                    return true
                }
                UnexpectedCharacter(c)
            }
            if (c != ']'.toInt()) {
                ParsingError("']' character expected")
            }
            if (attrName.equals("id", ignoreCase = true)) {
                curNode.filterNode.htmlId = attrValue
            } else {
                curNode.filterNode.AddAttribute(attrName, attrValue)
            }
            Switch(XpathAfterNodeState())
            return true
        }
    }

    private inner class XpathPredicateIndexState: State<Void>()
    {
        private var index: Int? = null

        override fun Consume(c: Int): Boolean
        {
            if (index == null) {
                EnterSubstate(IntegerState()) { index = it }
                return false
            }
            if (c == ']'.toInt()) {
                curNode.filterNode.indexOfType = index!! - 1
                Switch(XpathAfterNodeState())
                return true
            }
            UnexpectedCharacter(c)
        }
    }

    private inner class XpathTagState: State<Void>()
    {
        private var extractText = false

        override fun Consume(c: Int): Boolean
        {
            if (!extractText && c == '~'.toInt()) {
                extractText = true
                return true
            }
            EnterSubstate(IdentifierState()) {
                ident ->
                curNode.filterNode.AddExtractInfo(
                    HtmlFilter.ExtractInfo().also {
                        it.tagName = ident
                        it.extractText = extractText
                    })
                Switch(XpathAfterNodeState())
            }
            return false
        }
    }

    private inner class AfterXpathState: State<Void>() {
        override fun Consume(c: Int): Boolean
        {
            if (c == -1 || Character.isWhitespace(c)) {
                return true
            }
            if (c == ';'.toInt()) {
                StartNode(Node.Type.AUX_SEP)
                Switch(BeforeExpressionState())
                return true
            }
            if (c == ','.toInt()) {
                StartNode(Node.Type.SEL_SEP)
                Switch(BeforeExpressionState())
                return true
            }
            UnexpectedCharacter(c)
        }
    }

    private fun SwitchState(state: State<*>)
    {
        nextState = state
    }

    /** Feed next filtered code point after some pre-processing performed, -1 for EOF. */
    private fun FeedCodePoint(c: Int)
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

    private fun ParsingError(message: String, cause: Exception? = null): Nothing
    {
        throw ParsingError(message, curLine, curCol, cause)
    }

    private fun IsValidCharacter(c: Int): Boolean
    {
        return !(
            (c < 0x20 && !Character.isWhitespace(c)) ||
            c in 0xfdd0..0xfdef ||
            (c and 0xffff) == 0xfffe ||
            (c and 0xffff) == 0xffff ||
            c > 0x10ffff)
    }

    private fun IsIdentStartChar(c: Int): Boolean
    {
        return c in 'a'.toInt() .. 'z'.toInt() ||
            c in 'A'.toInt() .. 'Z'.toInt() ||
            c == '_'.toInt()
    }

    private fun IsIdentChar(c: Int): Boolean
    {
        return c in 'a'.toInt() .. 'z'.toInt() ||
            c in 'A'.toInt() .. 'Z'.toInt() ||
            c in '0'.toInt() .. '9'.toInt() ||
            c == '_'.toInt()
    }

    /** Check if character may be part of unquoted string. */
    private fun IsUnquotedStringChar(c: Int): Boolean
    {
        return IsIdentChar(c) || c == '-'.toInt()
    }

    private fun StartNode(type: Node.Type)
    {
        curNode = Node(type)
        nodes.add(curNode)
    }

    private fun UnexpectedCharacter(c: Int): Nothing
    {
        if (c == -1) {
            ParsingError("Unexpected end of input stream")
        } else {
            ParsingError("Unexpected character: " + String(Character.toChars(c)))
        }
    }

    private fun BuildResult(): List<Expression>
    {
        var selSeen = false
        var isAux = false
        var childSepSeen = false
        val result = ArrayList<Expression>()
        var curMainSelector: HtmlFilter? = null
        var curAuxSelector: HtmlFilter? = null
        var curSelector: HtmlFilter
        lateinit var curFilterNode: HtmlFilter.Node

        fun CommitExpression()
        {
            val mainSel = curMainSelector ?: throw Error("Main selector not specified")
            mainSel.ReassignIds()
            mainSel.SetParents()
            curAuxSelector?.also {
                it.ReassignIds()
                it.SetParents()
            }
            result.add(Expression(mainSel, curAuxSelector))
            curMainSelector = null
            curAuxSelector = null
            selSeen = false
            isAux = false
            childSepSeen = false
        }

        for (node in nodes) {
            if (!selSeen) {
                selSeen = true
                if (node.type != Node.Type.SELECTOR) {
                    throw Error("Should start with selector node")
                }
                if (isAux) {
                    curSelector = HtmlFilter()
                    curAuxSelector = curSelector
                } else {
                    curSelector = HtmlFilter()
                    curMainSelector = curSelector
                }
                curFilterNode = curSelector.root
                if (!node.isRoot) {
                    val wld = HtmlFilter.Node()
                    wld.isWildcard = true
                    curFilterNode.children.add(wld)
                    curFilterNode = wld
                }
                curFilterNode.children.add(node.filterNode)
                curFilterNode = node.filterNode
                continue
            }
            if (node.type == Node.Type.CHILD_SEP) {
                childSepSeen = true
                continue
            }
            if (node.type == Node.Type.AUX_SEP) {
                if (isAux) {
                    throw ParsingError("Only one auxiliary selector can be specified")
                }
                isAux = true
                selSeen = false
                continue
            }
            if (node.type == Node.Type.SEL_SEP) {
                CommitExpression()
                continue
            }
            if (!childSepSeen) {
                val wld = HtmlFilter.Node()
                wld.isWildcard = true
                curFilterNode.children.add(wld)
                curFilterNode = wld
            }
            childSepSeen = false
            curFilterNode.children.add(node.filterNode)
            curFilterNode = node.filterNode
        }
        if (curMainSelector != null) {
            CommitExpression()
        }

        return result
    }
}
