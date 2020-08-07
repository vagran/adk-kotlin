/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.html

import java.util.*
import kotlin.collections.ArrayList

class HtmlDocumentBuilder(private val filter: HtmlFilter? = null) {

    fun Build(): HtmlDocument = HtmlDocument(root)

    fun Build(parser: HtmlPullParser): HtmlDocument
    {
        for (token in parser) {
            PushToken(token)
        }
        return Build()
    }

    fun PushToken(token: HtmlParser.Token)
    {
        val attr: HtmlDocument.AttrNode? = if (token.type == HtmlParser.Token.Type.ATTR_VALUE) {
            val attrName = curAttrName ?: throw IllegalStateException(
                "Preceding attribute name token not seen before attribute value token")
            if (!curNode.filteredOut) {
                HtmlDocument.AttrNode(curId++, curNode.element, attrName, token.value)
            } else {
                null
            }
        } else if (!curNode.filteredOut) {
            curAttrName?.let { HtmlDocument.AttrNode(curId++, curNode.element, it, null) }
        } else {
            null
        }

        if (attr != null) {
            curNode.element.Add(attr)
            curAttrName = null
        }

        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (token.type) {
            HtmlParser.Token.Type.TEXT -> {
                if (!curNode.filteredOut) {
                    curNode.element.Add(
                        HtmlDocument.TextNode(curId++, curNode.element, token.value))
                }
            }

            HtmlParser.Token.Type.TAG_OPEN -> {
                if (filter != null) {
                    curNode.Filter(filter)
                }
                val parentElement = curNode.element
                val indexOfType = curNode.RegisterChildElement(token.value)
                val element = HtmlDocument.ElementNode(curId++, parentElement, token.value,
                                                       curNode.numChildren - 1,
                                                       indexOfType)

                curNode = ElementCtxNode(curNode, element, filter)
            }

            HtmlParser.Token.Type.TAG_CLOSE,
            HtmlParser.Token.Type.TAG_SELF_CLOSING -> {
                val parent = curNode.parent!!
                if (filter != null) {
                    curNode.Filter(filter)
                }
                if (filter == null || curNode.captureAll || curNode.captured) {
                    parent.element.Add(curNode.element)
                    curNode.UpdateChildSiblingCount()
                }
                parent.captured = parent.captured || curNode.captured
                curNode = parent
            }

            HtmlParser.Token.Type.ATTR_NAME -> curAttrName = token.value

            HtmlParser.Token.Type.EOF -> curNode.UpdateChildSiblingCount()
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val root = HtmlDocument.RootNode()
    private var curId = 1
    private var curAttrName: String? = null

    private class SiblingElementsCtx {
        var count = 0
    }

    private class ElementCtxNode(val parent: ElementCtxNode?,
                                 val element: HtmlDocument.ElementNode,
                                 filter: HtmlFilter?) {

        var childSiblings: MutableMap<String, SiblingElementsCtx>?
        var filterNodes: List<HtmlFilter.Node>? = null
        var captureAll = false
        var captured = false
        var filtered: Boolean
        var filteredOut: Boolean
        var numChildren = 0

        init {
            if (parent != null) {
                captureAll = parent.captureAll
            } else if (filter == null) {
                captureAll = true
            }
            if (parent != null && parent.filteredOut) {
                filtered = true
                filteredOut = true
            } else {
                filtered = false
                filteredOut = false
            }
            childSiblings = if (filter == null) TreeMap() else null
        }

        fun Filter(filter: HtmlFilter)
        {
            if (filtered) {
                return
            }
            filtered = true
            if (parent == null) {
                /* Root node. */
                filterNodes = listOf(filter.root)
                filteredOut = false
                childSiblings = TreeMap()
                return
            }
            val parentNodes = parent.filterNodes
            if (parentNodes != null) {
                var filterNodes: ArrayList<HtmlFilter.Node>? = null
                for (parentFilterNode in parentNodes) {
                    parentFilterNode.ForEachChild {
                        filterNode ->
                        if (filterNode.Match(element)) {
                            (filterNodes ?:
                                ArrayList<HtmlFilter.Node>().also { filterNodes = it })
                                .add(filterNode)
                            if (filterNode.extractInfo != null) {
                                captureAll = true
                                captured = true
                            }
                        }
                    }
                }
                this.filterNodes = filterNodes
            } else {
                filterNodes = null
            }
            filteredOut = filterNodes == null && !captureAll
            childSiblings = if (!filteredOut) TreeMap() else null
        }

        /** Returns index-of-type for the element. */
        fun RegisterChildElement(elementName: String): Int
        {
            numChildren++
            /* Index not calculated for filtered out nodes. */
            val childSiblings = childSiblings ?: run {
                if (!filteredOut) {
                    throw Error("No siblings for non filtered out node")
                }
                return 0
            }
            return childSiblings.computeIfAbsent(elementName) { SiblingElementsCtx() }.let {
                val idx = it.count
                it.count++
                idx
            }
        }

        fun UpdateChildSiblingCount()
        {
            val childSiblings = this.childSiblings ?: return
            var numSiblings = 0
            for (ctx in childSiblings.values) {
                numSiblings += ctx.count
            }
            for (child in element.children) {
                if (child !is HtmlDocument.ElementNode) {
                    continue
                }
                child.numSiblings = numSiblings
                child.numSiblingsOfType = childSiblings[child.name]!!.count
            }
        }
    }

    private var curNode = ElementCtxNode(null, root, filter)
}
