/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.html

import io.github.vagran.adk.SplitByWhitespace
import io.github.vagran.adk.omm.OmmIgnore


class HtmlDocument(val root: ElementNode = RootNode()) {

    abstract class Node(val id: Int, @OmmIgnore var parent: ElementNode?) {
        /** Deep clone of the node. Start node parent is not set. */
        abstract fun Clone(): Node
    }

    class TextNode(id: Int, parent: ElementNode?, val text: String): Node(id, parent) {

        override fun Clone(): TextNode
        {
            return TextNode(id, null, text)
        }

        override fun toString(): String
        {
            return text
        }
    }

    /**
     * @param indexOfType Index among sibling elements with the same name.
     */
    open class ElementNode(id: Int, parent: ElementNode?, val name: String,
                           val index: Int, val indexOfType: Int):
        Node(id, parent) {

        val children: List<Node>
            get() = _children ?: emptyList()

        val attrs: List<AttrNode>
            get() = _attrs ?: emptyList()

        private var _children: ArrayList<Node>? = null
        private var _attrs: ArrayList<AttrNode>? = null

        /** Zero if not set. */
        @OmmIgnore
        var numSiblings = 0
        /** Zero if not set. */
        @OmmIgnore
        var numSiblingsOfType = 0

        /** Index in parent element starting from last sibling. */
        @OmmIgnore
        val lastIndex: Int get() {
            if (numSiblings == 0) {
                throw Error("Siblings count not set")
            }
            return numSiblings - index - 1
        }

        /** Index in parent element starting from last sibling of the same type. */
        @OmmIgnore
        val lastIndexOfType: Int get() {
            if (numSiblingsOfType == 0) {
                throw Error("Siblings count not set")
            }
            return numSiblingsOfType - indexOfType - 1
        }

        override fun Clone(): ElementNode
        {
            return ElementNode(id, null, name, index, indexOfType).also {
                node ->
                _children?.also {
                    children ->
                    node._children = ArrayList<Node>().also {
                        list ->
                        children.forEach {
                            list.add(it.Clone())
                            it.parent = node
                        }
                    }
                }
                _attrs?.also {
                    attrs ->
                    node._attrs = ArrayList<AttrNode>().also {
                        list ->
                        attrs.forEach {
                            list.add(it.Clone())
                            it.parent = node
                        }
                    }
                }
            }
        }

        internal fun Add(attr: AttrNode)
        {
            val nodes = _attrs ?: ArrayList<AttrNode>().also { _attrs = it }
            nodes.add(attr)
        }

        internal fun Add(child: Node)
        {
            val nodes = _children ?: ArrayList<Node>().also { _children = it }
            nodes.add(child)
        }

        fun GetAttribute(attrName: String): AttrNode?
        {
            for (attr in attrs) {
                if (attr.name == attrName) {
                    return attr
                }
            }
            return null
        }

        fun HasAttribute(attrName: String): Boolean
        {
            return GetAttribute(attrName) != null
        }

        fun InnerText(): String
        {
            val result = StringBuilder()
            InnerText(result)
            return result.toString()
        }

        fun GetClasses(): List<String>
        {
            for (attr in attrs) {
                if (attr.name == "class") {
                    if (attr.value == null) {
                        break
                    }
                    return attr.value.SplitByWhitespace()
                }
            }
            return emptyList()
        }

        fun MatchClasses(classes: List<String>?): Boolean
        {
            if (classes == null) {
                /* Any node matches empty set of classes. */
                return true
            }
            return GetClasses().containsAll(classes)
        }

        fun GetHtmlId(): String?
        {
            for (attr in attrs) {
                if (attr.name == "id") {
                    return attr.value
                }
            }
            return null
        }

        private fun InnerText(sb: StringBuilder)
        {
            for (child in children) {
                if (child is TextNode) {
                    sb.append(child.text)
                } else if (child is ElementNode) {
                    child.InnerText(sb)
                }
            }
        }

        fun GetElement(name: String, index: Int = 0): ElementNode?
        {
            var curIdx = 0
            for (child in children) {
                if (child is ElementNode && child.name == name) {
                    if (curIdx == index) {
                        return child
                    }
                    curIdx++
                }
            }
            return null
        }

        override fun toString(): String
        {
            return "<$name>"
        }
    }

    class RootNode: ElementNode(0, null, "", 0, 0) {

        override fun toString(): String
        {
            return "ROOT"
        }
    }

    class AttrNode(id: Int, parent: ElementNode?, val name: String, val value: String?):
        Node(id, parent) {

        override fun Clone(): AttrNode
        {
            return AttrNode(id, null, name, value)
        }

        override fun toString(): String
        {
            if (value != null) {
                return "[$name=$value]"
            }
            return "[$name]"
        }
    }
}
