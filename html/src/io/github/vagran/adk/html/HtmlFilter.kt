/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.html

import io.github.vagran.adk.omm.OmmClass
import io.github.vagran.adk.omm.OmmFinalizer
import io.github.vagran.adk.omm.OmmIgnore
import io.github.vagran.adk.omm.OmmOption
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList

/** HTML filter capable of extracting data based on CSS-selectors-like expressions. */
class HtmlFilter {

    var root = RootNode()
    var transforms = TreeMap<String, TagTransform>()

    data class TagTransform(
        val matchPattern: String,
        val replacePattern: String
    )

    @OmmClass(serializeNulls = OmmOption.NO)
    class ResultNode {
        var docNode: HtmlDocument.ElementNode? = null
        var text: String? = null
    }

    class Result {
        /** Indexed by tag name. */
        val nodes = TreeMap<String, MutableList<ResultNode>>()

        /** Clear parent links for aggregated HTML fragments so that the documents can be collected
         * by GC.
         */
        fun UnlinkFragments()
        {
            nodes.values.forEach {
                resultNodes ->
                resultNodes.forEach {
                    resultNode ->
                    resultNode.docNode?.also { it.parent = null }
                }
            }
        }
    }

    class ExtractInfo {
        /** Tag name to assign for extracted data (text or HTML fragment). */
        lateinit var tagName: String
        /** Attribute name to extract value from if node is extracted. */
        var attrName: String? = null
        /** Extract element inner text instead of the whole HTML fragment. */
        var extractText: Boolean = false

        fun Clone(): ExtractInfo
        {
            return ExtractInfo().also {
                it.tagName = tagName
                it.attrName = attrName
                it.extractText = extractText
            }
        }

        fun ToString(sb: StringBuilder)
        {
            val attrName = attrName
            if (attrName != null) {
                sb.append("[@")
                sb.append(tagName)
                sb.append(':')
                sb.append(attrName)
                sb.append(']')
            } else {
                sb.append('@')
                if (extractText) {
                    sb.append('~')
                }
                sb.append(tagName)
            }
        }

        override fun toString(): String
        {
            val sb = StringBuilder()
            ToString(sb)
            return sb.toString()
        }
    }

    class Node {
        @OmmIgnore
        var parent: Node? = null
        /** Internal node ID for editing manipulations, zero reserved for root. */
        @OmmIgnore(qualifier = "mongo")
        var id: Int = 0
        /** Wildcard node matches any number of any elements. */
        var isWildcard: Boolean = false
        /** Element name to match, null if any element. */
        var elementName: String? = null
        /** Tag index in parent regardless of type, Int.MIN_VALUE if not bound. Negative values
         * mean index from last sibling.
         */
        var index = Int.MIN_VALUE
        /** Tag index in parent for same type of sibling nodes, Int.MIN_VALUE if not bound. Negative
         * values mean index from last sibling.
         */
        var indexOfType = Int.MIN_VALUE
        /** HTML element "id" attribute to match. */
        var htmlId: String? = null
        /** The node should have all the specified CSS classes set to match the path. */
        var classes: ArrayList<String>? = null
        /* The node should have all the specified attributes present to match. */
        var attrs: ArrayList<AttrNode>? = null
        /** Child nodes. */
        var children = ArrayList<Node>()
        /** Extraction instructions if any attached to this node. */
        var extractInfo: ArrayList<ExtractInfo>? = null

        /** Check if top-level node. */
        @OmmIgnore
        val isTop get() = parent?.parent == null

        class AttrNode {
            lateinit var name: String
            /** Just the attribute presence is checked if the value is null. */
            var value: String? = null

            fun Clone(): AttrNode
            {
                return AttrNode().also {
                    it.name = name
                    it.value = value
                }
            }

            override fun equals(other: Any?): Boolean
            {
                val _other = other as AttrNode
                return name == _other.name && value == _other.value
            }

            override fun hashCode(): Int
            {
                var result = name.hashCode()
                result = 31 * result + (value?.hashCode() ?: 0)
                return result
            }

            override fun toString(): String
            {
                val value = this.value ?: return name
                return "$name=$value"
            }
        }

        fun AddClass(clsName: String)
        {
            (classes ?: ArrayList<String>().also {classes = it}).add(clsName)
        }

        fun AddExtractInfo(info: ExtractInfo)
        {
            (extractInfo ?: ArrayList<ExtractInfo>().also {extractInfo = it}).add(info)
        }

        fun MergeExtractInfo(info: ExtractInfo)
        {
            val extractInfo = (extractInfo ?: ArrayList<ExtractInfo>().also {extractInfo = it})
            for (existingInfo in extractInfo) {
                if (existingInfo.tagName == info.tagName &&
                    existingInfo.extractText == info.extractText &&
                    existingInfo.attrName == info.attrName) {
                    return
                }
            }
            extractInfo.add(info)
        }

        fun AddAttribute(name: String, value: String? = null)
        {
            (attrs ?: ArrayList<AttrNode>().also {attrs = it})
                .add(AttrNode().also {
                    it.name = name
                    it.value = value
                })
        }

        fun Clone(): Node
        {
            val copy = Node()
            copy.id = id
            copy.isWildcard = isWildcard
            copy.elementName = elementName
            copy.index = index
            copy.indexOfType = indexOfType
            copy.htmlId = htmlId
            classes?.also {
                copy.classes = ArrayList(it)
            }
            attrs?.also {
                attrs ->
                copy.attrs = ArrayList<AttrNode>().also {
                    copyAttrs ->
                    attrs.forEach {
                        copyAttrs.add(it.Clone())
                    }
                }
            }
            children.forEach {
                copy.children.add(it.Clone())
            }
            extractInfo?.also {
                extractInfo ->
                copy.extractInfo = ArrayList<ExtractInfo>().also {
                    copyExtractInfo ->
                    extractInfo.forEach {
                        copyExtractInfo.add(it.Clone())
                    }
                }
            }
            return copy
        }

        fun Match(docNode: HtmlDocument.ElementNode): Boolean
        {
            if (isWildcard) {
                return true
            }

            if (elementName != null && elementName != docNode.name) {
                return false
            }

            /* Assume index match if reverse match requested but not available yet. */
            if ((index >= 0 && index != docNode.index) ||
                (index != Int.MIN_VALUE && index < 0 &&
                 docNode.numSiblings != 0 && -index - 1 != docNode.lastIndex)) {

                return false
            }

            if ((indexOfType >= 0 && indexOfType != docNode.indexOfType) ||
                (indexOfType != Int.MIN_VALUE && indexOfType < 0 &&
                 docNode.numSiblingsOfType != 0 && -indexOfType - 1 != docNode.lastIndexOfType)) {

                return false
            }

            htmlId?.also {
                htmlId ->
                if (docNode.GetHtmlId() != htmlId) {
                    return false
                }
            }

            attrs?.also {
                attrs ->
                if (!MatchAttributes(docNode, attrs)) {
                    return false
                }
            }

            classes?.also {
                classes ->
                if (!docNode.MatchClasses(classes)) {
                    return false
                }
            }

            return true
        }

        fun HasChildren(): Boolean
        {
            return isWildcard || children.isNotEmpty()
        }

        fun ForEachChild(handler: (Node) -> Unit)
        {
            children.forEach {
                child ->
                if (child.isWildcard) {
                    child.ForEachChild(handler)
                } else {
                    handler(child)
                }
            }
            if (isWildcard) {
                handler(this)
            }
        }

        fun SameClasses(classes: List<String>?): Boolean
        {
            if (classes == null) {
                return this.classes == null
            }
            val _classes = this.classes ?: return false
            if (classes.size != _classes.size) {
                return false
            }
            for (clsName in classes) {
                if (!_classes.contains(clsName)) {
                    return false
                }
            }
            return true
        }

        fun SameAttributes(attrs: List<AttrNode>?): Boolean
        {
            if (attrs == null) {
                return this.attrs == null
            }
            val _attrs = this.attrs ?: return false
            if (attrs.size != _attrs.size) {
                return false
            }
            for (attr in _attrs) {
                if (!attrs.contains(attr)) {
                    return false
                }
            }
            return true
        }

        fun HasAttribute(name: String): Boolean
        {
            val attrs = this.attrs ?: return false
            for (attr in attrs) {
                if (attr.name == name) {
                    return true
                }
            }
            return false
        }

        fun IsSameSelector(other: Node): Boolean
        {
            if (isWildcard && other.isWildcard) {
                return true
            }
            return isWildcard == other.isWildcard &&
                   elementName == other.elementName &&
                   index == other.index &&
                   indexOfType == other.indexOfType &&
                   htmlId == other.htmlId &&
                   SameClasses(other.classes) &&
                   SameAttributes(other.attrs)
        }

        fun ToString(sb: StringBuilder)
        {
            var written = false
            elementName?.also {
                written = true
                sb.append(it)
            }
            htmlId?.also {
                written = true
                sb.append('#')
                sb.append(it)
            }
            classes?.also {
                classes ->
                written = true
                for (cls in classes) {
                    sb.append('.')
                    sb.append(cls)
                }
            }
            attrs?.also {
                attrs ->
                written = true
                for (attr in attrs) {
                    sb.append('[')
                    sb.append(attr.name)
                    attr.value?.also {
                        sb.append('=')
                        sb.append('"')
                        sb.append(it)//XXX escape
                        sb.append('"')
                    }
                    sb.append(']')
                }
            }
            if (index >= 0) {
                written = true
                sb.append(":nth-child(")
                sb.append(index + 1)
                sb.append(')')
            } else if (index != Int.MIN_VALUE) {
                written = true
                sb.append(":nth-last-child(")
                sb.append(-index)
                sb.append(')')
            }
            if (indexOfType >= 0) {
                written = true
                sb.append(":nth-of-type(")
                sb.append(indexOfType + 1)
                sb.append(')')
            } else if (indexOfType != Int.MIN_VALUE) {
                written = true
                sb.append(":nth-last-of-type(")
                sb.append(-indexOfType)
                sb.append(')')
            }
            if (isTop) {
                written = true
                sb.append(":root")
            }
            extractInfo?.also {
                extractInfo ->
                written = true
                for (ei in extractInfo) {
                    ei.ToString(sb)
                }
            }
            if (!written) {
                sb.append('*')
            }
        }

        override fun toString(): String
        {
            return StringBuilder().also { ToString(it) }.toString()
        }
    }

    /** Filtering context. */
    class Context(tagTransforms: Map<String, TagTransform>) {
        val tagTransforms = TreeMap<String, TagTransformContext>()

        init {
            for ((tagName, transform) in tagTransforms) {
                this.tagTransforms[tagName] = TagTransformContext(transform)
            }
        }

        fun TransformTag(tagName: String, value: String): String
        {
            val ctx = tagTransforms[tagName] ?: return value
            ctx.matcher.reset(value)
            return ctx.matcher.replaceAll(ctx.replacement)
        }
    }

    class TagTransformContext(t: TagTransform) {
        val matchPattern = Pattern.compile(t.matchPattern)
        val matcher = matchPattern.matcher("")
        val replacement = t.replacePattern
    }

    fun Clone(): HtmlFilter
    {
        val copy = HtmlFilter()
        copy.root = root.Clone()
        copy.curId = curId
        copy.IterateNodes(copy.root, null) {
            node, parent ->
            copy.nodes[node.id] = node
            node.parent = parent
        }
        copy.transforms = TreeMap()
        for ((key, value) in transforms) {
            copy.transforms[key] = value.copy()
        }
        return copy
    }

    /** May leave this filter in semi-merged state if exception occurs. */
    fun MergeWithAux(auxFilter: HtmlFilter)
    {
        var mainNode = GetLeaf()
        var auxNode = auxFilter.GetLeaf()
        while (true) {
            if (auxNode.extractInfo != null) {
                throw Error("Extraction info not allowed in auxiliary selector")
            }
            if (auxNode.isWildcard) {
                if (auxNode.parent!!.parent != null) {
                    throw Error("Wildcard node is allowed only after root in auxiliary selector")
                }
                return
            }
            if (auxNode.parent == null) {
                if (mainNode.parent != null) {
                    throw Error("Auxiliary node path mismatch (reached root)")
                }
                return
            }
            if (mainNode.isWildcard) {
                if (mainNode.parent!!.parent != null) {
                    throw Error("Wildcard node is allowed only after root in main selector")
                }
                /* Prepend path by auxiliary selector prefix. */
                auxNode.children.clear()
                val tail = mainNode.children.firstOrNull() ?: return
                tail.parent = auxNode
                auxNode.children.add(tail)
                root = auxFilter.root
                auxFilter.root = RootNode()
                ReassignIds()
                return
            }
            if (auxNode.indexOfType != Int.MIN_VALUE) {
                mainNode.index = Int.MIN_VALUE
                mainNode.indexOfType = auxNode.indexOfType
            }
            auxNode = auxNode.parent!!
            mainNode = mainNode.parent ?:
                throw Error("Main selector should not be shorter than auxiliary one")
        }
    }

    fun MergeWith(other: HtmlFilter)
    {
        val src = other.Clone()
        for (node in src.root.children) {
            MergeNode(root, node)
        }
        for ((key, value) in src.transforms) {
            transforms[key] = value
        }
    }

    fun Clear()
    {
        root.children.clear()
        transforms.clear()
        curId = 1
    }

    override fun toString(): String
    {
        /* Traverse tree, generate selector for each leaf node. */
        val sb = StringBuilder()
        IterateNodes(root, null) {
            node, _ ->
            if (node.children.isEmpty()) {
                if (sb.isNotEmpty()) {
                    sb.append(", ")
                }
                StrSelector(node, sb)
            }
        }
        return sb.toString()
    }

    /** Get list of selectors string representation. Each selector corresponds to one branch for
     * each leaf node.
     */
    fun GetSelectors(): List<String>
    {
        return GetSelectors(root)
    }

    /** Get list of selectors string representation. Each selector corresponds to one branch for
     * each leaf node reachable from the specified start node.
     */
    fun GetSelectors(startNode: Node): List<String>
    {
        val result = ArrayList<String>()
        val sb = StringBuilder()
        IterateNodes(startNode, startNode.parent) {
            node, _ ->
            if (node.children.isEmpty()) {
                StrSelector(node, sb)
                result.add(sb.toString())
                sb.clear()
            }
        }
        return result
    }

    fun GetNode(nodeId: Int): Node?
    {
        return nodes[nodeId]
    }

    fun DeleteNode(node: Node, deleteBranch: Boolean)
    {
        if (node === root) {
            throw IllegalArgumentException("Root node cannot be deleted")
        }
        var _node = node
        val parent = run {
            if (deleteBranch) {
                while (true) {
                    val parent = _node.parent ?: break
                    if (parent.children.size != 1 || parent.id == 0) {
                        return@run parent
                    }
                    _node = parent
                }
                null
            } else {
                _node.parent
            }
        }
        UnregisterNode(_node)
        if (parent != null) {
            parent.children.remove(_node)
        }
    }

    /** Restore parent links, e.g. after de-serialization. */
    @OmmFinalizer
    fun SetParents()
    {
        IterateNodes(root, null) {
            node, parent ->
            node.parent = parent
        }
    }

    @OmmFinalizer(qualifier = "mongo")
    fun ReassignIds()
    {
        curId = 0
        nodes.clear()
        ReassignIds(root)
    }

    fun MakeContext(): Context
    {
        return Context(transforms)
    }

    fun Apply(doc: HtmlDocument): Result
    {
        return Result().also { ApplyFilter(doc.root, root, it, MakeContext()) }
    }

    fun IterateNodes(startNode: Node, parentNode: Node?, cbk: (node: Node, parent: Node?) -> Unit)
    {
        cbk(startNode, parentNode)
        startNode.children.forEach {
            IterateNodes(it, startNode, cbk)
        }
    }

    companion object {
        fun ForDocNodes(docNodes: List<HtmlDocument.Node>, tagName: String, extractText: Boolean):
            HtmlFilter
        {
            return HtmlFilter().also { it.FromDocNodes(docNodes, tagName, extractText) }
        }

        /** Get attributes which are used for identifying element in path when automatically
         * extracting node selector.
         */
        private fun GetAccountedAttributes(node: HtmlDocument.ElementNode): List<Node.AttrNode>
        {
            if (node.name == "link") {
                val attr = node.GetAttribute("rel") ?: return emptyList()
                return listOf(Node.AttrNode().also { it.name = attr.name; it.value = attr.value })
            }
            if (node.name == "meta") {
                val attr = node.GetAttribute("name") ?: node.GetAttribute("property") ?:
                    return emptyList()
                return listOf(Node.AttrNode().also { it.name = attr.name; it.value = attr.value })
            }
            return emptyList()
        }

        private fun MatchAttributes(node: HtmlDocument.ElementNode,
                                    attrs: List<Node.AttrNode>?): Boolean
        {
            if (attrs == null) {
                /* Any node matches empty set of attributes. */
                return true
            }
            for (attr in attrs) {
                val attrNode = node.GetAttribute(attr.name) ?: return false
                if (attr.value == null) {
                    continue
                }
                if (attrNode.value != attr.value) {
                    return false
                }
            }
            return true
        }

        private fun CheckClassesUnique(node: HtmlDocument.ElementNode, classes: List<String>?):
            Boolean
        {
            val parent = node.parent ?: return true
            for (sibling in parent.children) {
                if (sibling === node) {
                    continue
                }
                if (sibling !is HtmlDocument.ElementNode) {
                    continue
                }
                if (sibling.MatchClasses(classes)) {
                    return false
                }
            }
            return true
        }

        private fun CheckAttrsUnique(node: HtmlDocument.ElementNode, attrs: List<Node.AttrNode>?):
            Boolean
        {
            val parent = node.parent ?: return true
            for (sibling in parent.children) {
                if (sibling === node) {
                    continue
                }
                if (sibling !is HtmlDocument.ElementNode) {
                    continue
                }
                if (MatchAttributes(sibling, attrs)) {
                    return false
                }
            }
            return true
        }

        /** @return True if classes list modified. */
        private fun IntersectClasses(node: Node, classes: List<String>): Boolean
        {
            val it = (node.classes ?: return false).iterator()
            var isModified = false
            while (it.hasNext()) {
                val cls = it.next()
                if (!classes.contains(cls)) {
                    it.remove()
                    isModified = true
                }
            }
            if (node.classes!!.isEmpty()) {
                node.classes = null
            }
            return isModified
        }

        /** @return True if attributes list modified. */
        private fun IntersectAttributes(node: Node, attrs: List<Node.AttrNode>): Boolean
        {
            val attrIt = (node.attrs ?: return false).iterator()
            var isModified = false
            while (attrIt.hasNext()) {
                val attr = attrIt.next()
                val otherAttr = attrs.find { it.name == attr.name }
                if (otherAttr == null) {
                    attrIt.remove()
                    isModified = true
                } else if (otherAttr.value != attr.value) {
                    attr.value = null
                    isModified = true
                }
            }
            if (node.attrs!!.isEmpty()) {
                node.attrs = null
            }
            return isModified
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var curId: Int = 1
    private val nodes = TreeMap<Int, Node>()

    private fun RootNode(): Node
    {
        return Node().apply { elementName = "" }
    }

    private fun ApplyFilter(docNode: HtmlDocument.ElementNode, node: Node, result: Result,
                            ctx: Context)
    {
        for (docChildNode in docNode.children) {
            if (docChildNode !is HtmlDocument.ElementNode) {
                continue
            }
            node.ForEachChild {
                childNode ->
                if (!childNode.Match(docChildNode)) {
                    return@ForEachChild
                }
                childNode.extractInfo?.also {
                    extractList ->
                    extractList.forEach {
                        ExtractNode(it, docChildNode, result, ctx)
                    }
                }
                if (childNode.HasChildren()) {
                    ApplyFilter(docChildNode, childNode, result, ctx)
                }
            }
        }
    }

    private fun ExtractNode(extractInfo: ExtractInfo, docNode: HtmlDocument.ElementNode,
                            result: Result, ctx: Context)
    {
        val resultNode = ResultNode()

        run {
            extractInfo.attrName?.also { attrName ->
                val text = docNode.GetAttribute(attrName)?.value ?: ""
                resultNode.text = ctx.TransformTag(extractInfo.tagName, text)
                return@run
            }

            if (extractInfo.extractText) {
                resultNode.text = ctx.TransformTag(extractInfo.tagName, docNode.InnerText())
            } else {
                resultNode.docNode = docNode.Clone()
            }
        }

        if ((extractInfo.extractText || extractInfo.attrName != null) &&
            resultNode.text.isNullOrEmpty()) {

            /* Discard tags with empty text value. */
            return
        }

        result.nodes.computeIfAbsent(extractInfo.tagName) { ArrayList() }.add(resultNode)
    }

    private fun MergeNode(parentNode: Node, node: Node)
    {
        val existingNode = parentNode.children.find { it.IsSameSelector(node) }
        if (existingNode == null) {
            ReassignIds(node)
            parentNode.children.add(node)
            node.parent = parentNode
        } else {
            for (child in node.children) {
                MergeNode(existingNode, child)
            }
            node.extractInfo?.also {
                for (info in it) {
                    existingNode.MergeExtractInfo(info)
                }
            }
        }
    }

    private fun FromDocNodes(docNodes: List<HtmlDocument.Node>, tagName: String, extractText: Boolean)
    {
        for (node in docNodes) {
            ProcessDocNode(node, tagName, extractText)
        }
    }

    private fun ProcessDocNode(docNode: HtmlDocument.Node, tagName: String, extractText: Boolean)
    {
        val chain = ArrayDeque<HtmlDocument.Node>()

        run {
            var curDocNode = docNode
            while (true) {
                curDocNode.parent?.also {
                    chain.addFirst(curDocNode)
                    curDocNode = it
                } ?: break
            }
        }

        var curNode = root
        for (curDocNode in chain) {
            if (curDocNode === docNode) {
                if (curDocNode is HtmlDocument.ElementNode) {
                    curNode = AddNodeForDoc(curNode, curDocNode)
                }
                val ei = run {
                    val list = curNode.extractInfo ?:
                        ArrayList<ExtractInfo>().also { curNode.extractInfo = it }
                    list.firstOrNull() ?: ExtractInfo().also { list.add(it) }
                }
                when (curDocNode) {
                    is HtmlDocument.ElementNode -> {
                        ei.extractText = extractText
                    }
                    is HtmlDocument.TextNode -> {
                        ei.extractText = true
                    }
                    is HtmlDocument.AttrNode -> {
                        ei.attrName = curDocNode.name
                    }
                    else -> {
                        throw Error("Unhandled node type ${curDocNode::class.qualifiedName}")
                    }
                }
                ei.tagName = tagName
            } else {
                if (curDocNode !is HtmlDocument.ElementNode) {
                    throw Error("Unexpected parent node type ${curDocNode::class.qualifiedName}")
                }
                curNode = AddNodeForDoc(curNode, curDocNode)
            }
        }
    }

    private fun ElementIndexNeeded(node: Node): Boolean
    {
        return !(
            node.htmlId != null ||
            node.elementName == "html" ||
            node.elementName == "head" ||
            node.elementName == "body" ||
            node.elementName == "title" ||
            (node.elementName == "link" && node.HasAttribute("rel")) ||
            (node.elementName == "meta" &&
                (node.HasAttribute("name") || node.HasAttribute("property"))))
    }

    private fun AddNodeForDoc(parentNode: Node, docNode: HtmlDocument.ElementNode): Node
    {
        val htmlId = docNode.GetHtmlId()
        val classes = docNode.GetClasses()
        val attrs = GetAccountedAttributes(docNode)
        val index = docNode.indexOfType
        return run {
            for (node in parentNode.children) {
                if (node.elementName == docNode.name) {
                    IntersectClasses(node, classes)
                    IntersectAttributes(node, attrs)
                    if (node.indexOfType != index) {
                        node.indexOfType = Int.MIN_VALUE
                    }
                    if (node.htmlId != htmlId) {
                        node.htmlId = null
                    }
                    return@run node
                }
            }
            return@run null
        } ?: Node().also {
            it.id = curId++
            nodes[it.id] = it
            parentNode.children.add(it)
            it.parent = parentNode
            it.elementName = docNode.name
            it.htmlId = htmlId
            if (!classes.isEmpty()) {
                it.classes = ArrayList(classes)
            }
            if (!attrs.isEmpty()) {
                it.attrs = ArrayList(attrs)
            }
            if (ElementIndexNeeded(it)) {
                it.indexOfType = index
            }
        }
    }

    private fun ReassignIds(node: Node)
    {
        node.id = curId++
        nodes[node.id] = node
        for (child in node.children) {
            ReassignIds(child)
        }
    }

    private fun GetLeaf(): Node
    {
        var node = root
        while (true) {
            if (node.children.isEmpty()) {
                break
            }
            if (node.children.size != 1) {
                throw Error("Filter branching is not allowed")
            }
            node = node.children.first()
        }
        return node
    }

    private fun StrSelector(node: Node, sb: StringBuilder)
    {
        val nodes = ArrayDeque<Node>()
        var curNode = node
        while(true) {
            nodes.addFirst(curNode)
            curNode = curNode.parent ?: break
            if (curNode === root) {
                break
            }
        }
        var wildcardSeen = false
        var nodeSeen = false
        for (_node in nodes) {
            if (_node.isWildcard) {
                wildcardSeen = true
                continue
            }
            if (!_node.isTop && nodeSeen) {
                if (wildcardSeen) {
                    sb.append(' ')
                } else {
                    sb.append(" > ")
                }
            }
            wildcardSeen = false
            nodeSeen = true
            _node.ToString(sb)
        }
    }

    /** Remove node with all its children from ID index. */
    private fun UnregisterNode(node: Node)
    {
        nodes.remove(node.id)
        for (child in node.children) {
            UnregisterNode(child)
        }
    }
}
