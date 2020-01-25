/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.btree

import io.github.vagran.adk.Assert
import io.github.vagran.adk.LocalId
import java.util.*
import kotlin.collections.ArrayList

enum class BTreeModifyResult {
    /** Entry not found, should not be returned by modification function. */
    NOT_FOUND,
    /** New key collided with existing entry, should not be returned by modification function. */
    KEY_COLLISION,
    /** Entry not changed. */
    NO_CHANGE,
    /** Entry changed but key left unchanged. */
    CHANGED,
    /** Key and (possibly) payload was changed. */
    KEY_CHANGED,
    /** Entry should be deleted. */
    DELETE
}
typealias ModifyFunc<TPayload> = (TPayload) -> BTreeModifyResult

/** B-tree with versioning support. It uses copy-on-write approach for separate nodes when creating
 * new revision.
 * It is not thread-safe for modification. Read-only concurrent access is supported.
 * @param rootNodeId Root node defines the whole tree, null to create new tree. If the the specified
 * root node has different revision, the tree nodes will be copied on write attempt.
 */
class BTree<TKey: Comparable<TKey>, TPayload: BTreePayload<TKey>>(
    val storage: BTreeStorage<TKey, TPayload>,
    val revision: LocalId,
    private val rootNodeId: LocalId?,
    val config: Config) {

    data class Config(
        /** Maximal number of children nodes. */
        val order: Int)

    class NodeHeader {
        lateinit var id: LocalId
        lateinit var revision: LocalId
        /** ID of previous version node if forked. */
        var prevVersion: LocalId? = null
        /** Number of entries in this node. */
        var numEntries: Int = 0
        /** Total number of entries in this node and all its descendants. Updated for new nodes
         * during commit preparation.
         */
        var totalCount: Int = 0

        fun Clone(): NodeHeader
        {
            return NodeHeader().also {
                it.id = id
                it.revision = revision
                it.prevVersion = prevVersion
                it.numEntries = numEntries
                it.totalCount = totalCount
            }
        }
    }

    @Suppress("JoinDeclarationAndAssignment",
              "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS",
              "UNCHECKED_CAST")
    class Node<TKey: Comparable<TKey>, TPayload: BTreePayload<TKey>>() {
        lateinit var hdr: NodeHeader
        /** Array of TPayload elements. */
        lateinit var entries: Array<Any?>
        /** Null for leaf node. */
        var children: Array<LocalId?>? = null

        val order get() = entries.size + 1
        val isLeaf get() = children == null

        constructor(order: Int, revision:LocalId, isLeaf: Boolean):
            this()
        {
            hdr = NodeHeader()
            hdr.id = LocalId()
            hdr.revision = revision
            entries = arrayOfNulls(order - 1)
            if (!isLeaf) {
                children = arrayOfNulls<LocalId?>(order)
            }
        }

        fun Clone(): Node<TKey, TPayload>
        {
            return Node<TKey, TPayload>()
                .also {
                it.hdr = hdr.Clone()
                it.entries = arrayOfNulls(entries.size)
                for (i in 0 until hdr.numEntries) {
                    it.entries[i] = (entries[i] as TPayload).Clone()
                }
                children?.also {
                    children ->
                    it.children = arrayOfNulls(children.size)
                    System.arraycopy(children, 0, it.children, 0, hdr.numEntries + 1)
                }
            }
        }

        fun Fork(revision: LocalId): Node<TKey, TPayload>
        {
            return Clone().also {
                it.hdr.prevVersion = it.hdr.id
                it.hdr.id = LocalId()
                it.hdr.revision = revision
            }
        }

        data class SplitResult<TKey: Comparable<TKey>, TPayload: BTreePayload<TKey>> (
            val leftNode: Node<TKey, TPayload>,
            val rightNode: Node<TKey, TPayload>,
            var median: TPayload
        )

        fun Split(revision: LocalId): SplitResult<TKey, TPayload>
        {
            val medianIdx = order / 2 - 1
            val median = entries[medianIdx] as TPayload

            val rightNode = Node<TKey, TPayload>(order, revision, isLeaf)
            System.arraycopy(entries, medianIdx + 1, rightNode.entries, 0,
                             entries.size - medianIdx - 1)
            if (!isLeaf) {
                System.arraycopy(children, medianIdx + 1, rightNode.children, 0,
                                 children!!.size - medianIdx - 1)
            }
            rightNode.hdr.numEntries = entries.size - medianIdx - 1

            val leftNode = if (hdr.revision == revision) {
                for (i in medianIdx until hdr.numEntries) {
                    entries[i] = null
                }
                if (!isLeaf) {
                    for (i in medianIdx + 1 .. hdr.numEntries) {
                        children!![i] = null
                    }
                }
                this
            } else {
                Node<TKey, TPayload>(order, revision, isLeaf).also {
                    System.arraycopy(entries, 0, it.entries, 0, medianIdx)
                    if (!isLeaf) {
                        System.arraycopy(children, 0, it.children, 0, medianIdx + 1)
                    }
                }
            }
            leftNode.hdr.numEntries = medianIdx

            return SplitResult(
                leftNode,
                rightNode,
                median
            )
        }

        /**
         * @param childIdx Child index of the split node.
         */
        fun Insert(split: SplitResult<TKey, TPayload>, childIdx: Int)
        {
            Assert(!isLeaf)
            Assert(hdr.numEntries < entries.size)
            Assert(childIdx <= hdr.numEntries)

            if (childIdx < hdr.numEntries) {
                System.arraycopy(entries, childIdx, entries, childIdx + 1,
                                 hdr.numEntries - childIdx)
                System.arraycopy(children, childIdx + 1, children, childIdx + 2,
                                 hdr.numEntries - childIdx)
            }
            entries[childIdx] = split.median
            children!![childIdx] = split.leftNode.hdr.id
            children!![childIdx + 1] = split.rightNode.hdr.id
            hdr.numEntries++
        }

        /** Insert new entry in a leaf node.
         * @param idx Insertion position. Should be previously obtained by Find() method.
         */
        fun Insert(idx: Int, entry: TPayload)
        {
            Assert(isLeaf)
            Assert(hdr.numEntries < entries.size)
            Assert(idx <= hdr.numEntries)

            if (idx < hdr.numEntries) {
                System.arraycopy(entries, idx, entries, idx + 1, hdr.numEntries - idx)
            }
            entries[idx] = entry
            hdr.numEntries++
        }

        /** Remove entry from a leaf node. */
        fun Remove(idx: Int)
        {
            Assert(isLeaf)
            Assert(idx < hdr.numEntries)

            if (idx < hdr.numEntries - 1) {
                System.arraycopy(entries, idx + 1, entries, idx, hdr.numEntries - idx - 1)
            }
            hdr.numEntries--
            entries[hdr.numEntries] = null
        }

        /** Find position for the specified key.
         * @param startIdx Index to start search from. Returned index cannot be less then this value
         * even if the corresponding entry is not actually the first one with key greater than the
         * specified one.
         * @return Entry index which is the first non-less element. In case all entries are less
         * than the specified key the returned index points one past last entry. It also can be
         * interpreted as child node index where the entry can be further found if this node is not
         * a leaf.
         */
        fun Find(key: TKey, startIdx: Int = 0): Int
        {
            Assert(startIdx <= hdr.numEntries)
            var low = startIdx
            var high = hdr.numEntries - 1

            while (low <= high) {
                val mid = low + high ushr 1
                val midKey = (entries[mid]!! as TPayload).GetKey()
                val cmp = midKey.compareTo(key)
                when {
                    cmp < 0 -> low = mid + 1
                    cmp > 0 -> high = mid - 1
                    else -> return mid
                }
            }
            /* No exact match */
            return low
        }

        fun GetEntry(idx: Int): TPayload
        {
            Assert(idx < hdr.numEntries)
            return entries[idx]!! as TPayload
        }

        fun SetEntry(idx: Int, entry: TPayload)
        {
            Assert(idx < hdr.numEntries)
            entries[idx] = entry
        }

        fun GetChildId(idx: Int): LocalId
        {
            Assert(!isLeaf)
            Assert(idx <= hdr.numEntries)
            return children!![idx]!!
        }

        /** Rotate entries right around the specified median entry. */
        fun RotateRight(medianIdx: Int, leftNode: Node<TKey, TPayload>,
                        rightNode: Node<TKey, TPayload>)
        {
            Assert(!isLeaf)
            System.arraycopy(rightNode.entries, 0, rightNode.entries, 1,
                             rightNode.hdr.numEntries)
            rightNode.entries[0] = entries[medianIdx]
            entries[medianIdx] = leftNode.entries[leftNode.hdr.numEntries - 1]
            leftNode.entries[leftNode.hdr.numEntries - 1] = null
            if (!leftNode.isLeaf) {
                Assert(!rightNode.isLeaf)
                System.arraycopy(rightNode.children, 0, rightNode.children, 1,
                                 rightNode.hdr.numEntries + 1)
                rightNode.children!![0] = leftNode.children!![leftNode.hdr.numEntries]
                leftNode.children!![leftNode.hdr.numEntries] = null
            }
            rightNode.hdr.numEntries++
            leftNode.hdr.numEntries--
        }

        /** Rotate entries left around the specified median entry. */
        fun RotateLeft(medianIdx: Int, leftNode: Node<TKey, TPayload>,
                       rightNode: Node<TKey, TPayload>)
        {
            Assert(!isLeaf)
            leftNode.entries[leftNode.hdr.numEntries] = entries[medianIdx]
            entries[medianIdx] = rightNode.entries[0]
            System.arraycopy(rightNode.entries, 1, rightNode.entries, 0,
                             rightNode.hdr.numEntries - 1)
            rightNode.entries[rightNode.hdr.numEntries - 1] = null
            if (!leftNode.isLeaf) {
                Assert(!rightNode.isLeaf)
                leftNode.children!![leftNode.hdr.numEntries + 1] = rightNode.children!![0]
                System.arraycopy(rightNode.children, 1, rightNode.children, 0,
                                 rightNode.hdr.numEntries)
                rightNode.children!![rightNode.hdr.numEntries] = null
            }
            rightNode.hdr.numEntries--
            leftNode.hdr.numEntries++
        }

        /** Merge two child nodes around the specified median entry.
         * @return Resulted merged node.
         */
        fun Merge(medianIdx: Int, leftNode: Node<TKey, TPayload>, rightNode: Node<TKey, TPayload>):
            Node<TKey, TPayload>
        {
            Assert(!isLeaf)
            val mergedNode = Node<TKey, TPayload>(entries.size + 1, hdr.revision, leftNode.isLeaf)
            System.arraycopy(leftNode.entries, 0, mergedNode.entries, 0, leftNode.hdr.numEntries)
            mergedNode.entries[leftNode.hdr.numEntries] = entries[medianIdx]
            System.arraycopy(rightNode.entries, 0, mergedNode.entries, leftNode.hdr.numEntries + 1,
                             rightNode.hdr.numEntries)
            mergedNode.hdr.numEntries = leftNode.hdr.numEntries + 1 + rightNode.hdr.numEntries
            if (!leftNode.isLeaf) {
                Assert(!rightNode.isLeaf)
                System.arraycopy(leftNode.children, 0, mergedNode.children, 0,
                                 leftNode.hdr.numEntries + 1)
                System.arraycopy(rightNode.children, 0, mergedNode.children,
                                 leftNode.hdr.numEntries + 1, rightNode.hdr.numEntries + 1)
            }
            children!![medianIdx] = mergedNode.hdr.id
            if (medianIdx < hdr.numEntries - 1) {
                System.arraycopy(entries, medianIdx + 1, entries, medianIdx,
                                 hdr.numEntries - medianIdx - 1)
                System.arraycopy(children, medianIdx + 2, children, medianIdx + 1,
                                 hdr.numEntries - medianIdx - 1)
            }
            hdr.numEntries--
            entries[hdr.numEntries] = null
            children!![hdr.numEntries + 1] = null
            return mergedNode
        }
    }

    /** Cursor is used for querying the tree. Multiple concurrent cursors are supported however tree
     * modification is not permitted with concurrent queries.
     */
    inner class Cursor internal constructor() {
        /** Get next matching element. The cursor is never moved backward so specifying the key
         * value less than the one specified in previous calls is not meaningful (just current
         * element will be returned in such case).
         * @return Either element which matches the key or the first element from current position
         * which key is greater than the specified key. Null is returned if no more elements.
         * Specifying the same key twice in consequential calls will not advance the cursor and will
         * return the same result.
         */
        suspend fun Next(key: TKey): TPayload?
        {
            if (endReached) {
                return null
            }

            if (isInitialState) {
                val root = GetRoot() ?: return null
                if (!root.isLeaf) {
                    FindNode(key)
                }
            } else {
                if (!TrimParent(key) && curNode == null) {
                    /* Points to median entry, should prevent from looking backward if the specified
                     * key is less than median value.
                     */
                    val se = ctx.StackTop()
                    val entry = se.node.GetEntry(se.childIdx)
                    if (key <= entry.GetKey()) {
                        return entry
                    }
                }
                FindNode(key)
            }

            curNode?.also {
                node ->
                entryIdx = node.Find(key, entryIdx)
                if (entryIdx < node.hdr.numEntries) {
                    return node.GetEntry(entryIdx)
                }
                curNode = null
                /* Since parent chain is verified and all indices are valid, the result should be
                 * the first median encountered in parent chain.
                 */
                while (ctx.stack.size > 0) {
                    val se = ctx.StackTop()
                    if (se.childIdx >= se.node.hdr.numEntries) {
                        ctx.PopNode()
                        continue
                    }
                    return se.node.GetEntry(se.childIdx)
                }
                endReached = true
                return null
            }

            /* Gets there after FindNode() so it should point to a matching median. */
            val se = ctx.StackTop()
            val e = se.node.GetEntry(se.childIdx)
            Assert(e.GetKey() == key)
            return e
        }

        /** Get next element in the index. Null is returned if index end is reached. */
        suspend fun Next(): TPayload?
        {
            if (endReached) {
                return null
            }

            if (isInitialState) {
                val root = GetRoot() ?: return null
                if (!root.isLeaf) {
                    FindLeaf()
                }
                return curNode!!.GetEntry(0)
            }

            var wantMedian = false
            curNode?.also {
                node ->
                entryIdx++
                if (entryIdx < node.hdr.numEntries) {
                    return node.GetEntry(entryIdx)
                }
                curNode = null
                wantMedian = true
            }

            while (true) {
                if (ctx.stackEmpty) {
                    endReached = true
                    return null
                }
                val se = ctx.StackTop()
                if (se.childIdx >= se.node.hdr.numEntries) {
                    ctx.PopNode()
                    continue
                }
                if (wantMedian) {
                    return se.node.GetEntry(se.childIdx)
                }
                se.childIdx++
                FindLeaf()
                return curNode!!.GetEntry(0)
            }
        }

        // /////////////////////////////////////////////////////////////////////////////////////////

        private val ctx = QueryContext()
        /** Current entry index in the current leaf node. */
        private var entryIdx = 0
        /** Current leaf node. Null if currently on internal node (top of context stack). */
        private var curNode: Node<TKey, TPayload>? = null
        private var endReached = false

        private val isInitialState get() = ctx.stackEmpty && curNode == null

        /** Initializes root node.
         * @return Null if tree is empty, root node otherwise.
         */
        private suspend fun GetRoot(): Node<TKey, TPayload>?
        {
            /* The first call */
            if (!rootCreated) {
                endReached = true
                return null
            }
            val root = storage.GetNode(curRootNodeId).Await()
                ?: throw Error("Root node not found")
            if (root.isLeaf) {
                if (root.hdr.numEntries == 0) {
                    endReached = true
                    return null
                }
                curNode = root
                entryIdx = 0
            } else {
                ctx.PushNode(root, 0)
            }
            return root
        }

        /** Find first leaf node starting from context stack top. */
        private suspend fun FindLeaf()
        {
            val se = ctx.StackTop()
            var node = se.node
            var childIdx = se.childIdx
            while (true) {
                val nodeId = node.GetChildId(childIdx)
                node = storage.GetNode(nodeId).Await()
                    ?: throw Error("Child node not found: $nodeId")
                if (node.isLeaf) {
                    curNode = node
                    entryIdx = 0
                    break
                }
                ctx.PushNode(node, 0)
                childIdx = 0
            }
        }

        /** Sets the stack and current leaf node to the node where the specified key can be found.
         * The leaf entry index is set to zero. The function only descends the tree and never
         * ascends it. The result may point past the last entry if nothing found. The result also
         * may point to internal node in context stack if exact match found.
         */
        private suspend fun FindNode(key: TKey)
        {
            if (curNode != null) {
                return
            }
            while (true) {
                val se = ctx.StackTop()
                se.childIdx = se.node.Find(key, se.childIdx)
                if (se.childIdx < se.node.hdr.numEntries &&
                    se.node.GetEntry(se.childIdx).GetKey() == key) {

                    return
                }
                val nodeId = se.node.GetChildId(se.childIdx)
                val node = storage.GetNode(nodeId).Await()
                    ?: throw Error("Child node not found: $nodeId")
                if (node.isLeaf) {
                    curNode = node
                    entryIdx = 0
                    return
                } else {
                    ctx.PushNode(node, 0)
                }
            }
        }

        /** Check parent chain to ensure it currently points to a branch where a key can be found.
         * In case some parent entry has too small value the branch is trimmed up to the node where
         * new child should be found (the index is not advanced).
         * @return True if parent chain trimmed or child index needs adjustment, false if current
         * parent chain is valid.
         */
        private fun TrimParent(key: TKey): Boolean
        {
            val n = ctx.stack.size
            for (i in 0 until n) {
                val se = ctx.stack[i]
                if (se.childIdx >= se.node.hdr.numEntries) {
                    continue
                }
                if (se.node.GetEntry(se.childIdx).GetKey() < key) {
                    if (i + 1 < n) {
                        ctx.stack.subList(i + 1, n).clear()
                    }
                    curNode = null
                    return true
                }
            }
            return false
        }
    }


    fun CreateCursor(): Cursor
    {
        return Cursor()
    }

    /** Convenience wrapper for finding just one entry.
     * @return Matching entry or null if no such key.
     */
    suspend fun Find(key: TKey): TPayload?
    {
        val cursor = CreateCursor()
        val e = cursor.Next(key) ?: return null
        if (e.GetKey() == key) {
            return e
        }
        return null
    }

    /** Insert the specified entry.
     * @param override True to override existing entry if any, false to leave it unchanged.
     * @return true if new entry inserted, false if the tree already has a node with the specified
     *  key (which is replaced if `override` is true and left unchanged otherwise).
     */
    suspend fun Insert(entry: TPayload, override: Boolean = false): Boolean
    {
        val ctx = QueryContext()
        if (!Insert(ctx, entry, override)) {
            return false
        }
        ctx.Commit()
        return true
    }

    /** Delete node with the specified key.
     * @return True if deleted, false if the key not found.
     */
    suspend fun Delete(key: TKey): Boolean
    {
        val ctx = QueryContext()
        if (DeleteOrModify(ctx, key, null) == BTreeModifyResult.NOT_FOUND) {
            return false
        }
        ctx.Commit()
        return true
    }

    suspend fun Modify(key: TKey, override: Boolean = false,
                       modifyFunc: ModifyFunc<TPayload>): BTreeModifyResult
    {
        val ctx = QueryContext()
        val result = DeleteOrModify(ctx, key, modifyFunc)
        if (result == BTreeModifyResult.NOT_FOUND || result == BTreeModifyResult.NO_CHANGE) {
            return result
        }
        if (result == BTreeModifyResult.KEY_CHANGED) {
            if (!Insert(ctx, ctx.targetEntry!!, override) && !override) {
                return BTreeModifyResult.KEY_COLLISION
            }
        }
        ctx.Commit()
        return result
    }

    /** Get root node ID. Null if not yet created. */
    fun GetRootId(): LocalId?
    {
        if (!rootCreated) {
            return null
        }
        return curRootNodeId
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private lateinit var curRootNodeId: LocalId
    private var rootCreated = rootNodeId != null
    private val minEntries = (config.order / 2) - 1
    private val maxEntries = config.order - 1

    private inner class StackEntry<TKey: Comparable<TKey>, TPayload: BTreePayload<TKey>> (
        var node: Node<TKey, TPayload>,
        /** Can be -1 for indicating not yet started children traversal in search procedures. */
        var childIdx: Int
    )

    private inner class QueryContext {
        val stack = ArrayList<StackEntry<TKey, TPayload>>()
        /** Modified queued for write-back. */
        val dirtyNodes = TreeMap<LocalId, Node<TKey, TPayload>>()
        /** Newly created nodes queued for writing. */
        val newNodes = TreeMap<LocalId, Node<TKey, TPayload>>()
        /** Nodes queued for deletion. */
        val deletedNodes = TreeMap<LocalId, Node<TKey, TPayload>>()
        /** New root if replaced. */
        var newRoot: Node<TKey, TPayload>? = null
        /** Entry being manipulated for some operations. */
        var targetEntry: TPayload? = null

        val stackEmpty get() = stack.isEmpty()

        fun SetNewRoot(node: Node<TKey, TPayload>)
        {
            newRoot = node
            SetDirty(node, true)
        }

        fun PushNode(node: Node<TKey, TPayload>, childIdx: Int)
        {
            stack.add(StackEntry(node, childIdx))
        }

        fun PopNode()
        {
            Assert(stack.size > 0)
            stack.removeAt(stack.size - 1)
        }

        suspend fun GetNode(nodeId: LocalId, modify: Boolean = false): Node<TKey, TPayload>
        {
            var node = dirtyNodes[nodeId]
            if (node != null) {
                return node
            }
            node = newNodes[nodeId]
            if (node != null) {
                return node
            }
            node = storage.GetNode(nodeId).Await()
                ?: throw Error("Node not found: $nodeId")
            if (modify) {
                return ModifyNode(node)
            }
            return node
        }

        /** Commit node modification, forking it if necessary and adding to dirty list. Parent chain
         * is modified as necessary. The node should be in current parent chain.
         * @param stackIdx Index of the stack element for the modified node, -1 if it is child of
         * current stack top node, 0 if it is stack top node, 1 if node preceding stack top and so
         * on.
         * @return Node to use for modification.
         */
        fun ModifyNode(node: Node<TKey, TPayload>, stackIdx: Int = -1): Node<TKey, TPayload>
        {
            if (node.hdr.revision == revision) {
                SetDirty(node, false)
                return node
            }
            val newNode = node.Fork(revision)
            SetDirty(newNode, true)
            if (stackIdx != -1) {
                stack[stack.size - 1 - stackIdx].node = newNode
                if (stackIdx == stack.size - 1) {
                    newRoot = newNode
                }
            } else if (stackEmpty) {
                newRoot = newNode
            }
            if (stackIdx + 1 < stack.size) {
                val parentStackItem = stack[stack.size - 2 - stackIdx]
                val parentNode = ModifyNode(parentStackItem.node, stackIdx + 1)
                parentNode.children!![parentStackItem.childIdx] = newNode.hdr.id
            }
            return newNode
        }

        fun StackTop(): StackEntry<TKey, TPayload>
        {
            return stack.last()
        }

        fun SetDirty(node: Node<TKey, TPayload>, isNew: Boolean)
        {
            Assert(node.hdr.revision == revision)
            if (isNew) {
                newNodes[node.hdr.id] = node
            } else {
                dirtyNodes[node.hdr.id] = node
            }
        }

        fun DeleteNode(node: Node<TKey, TPayload>)
        {
            if (node.hdr.revision != revision) {
                /* Other revision node is just not longer referenced in current revision. */
                return
            }
            if (newNodes.remove(node.hdr.id) == null) {
                deletedNodes[node.hdr.id] = node
            }
        }

        /** Commit all changes into the storage. */
        suspend fun Commit()
        {
            for (node in deletedNodes.values) {
                storage.RemoveNode(node.hdr.id).Await()
                dirtyNodes.remove(node.hdr.id)
            }
            for (node in newNodes.values) {
                storage.StoreNode(node).Await()
            }
            for (node in dirtyNodes.values) {
                storage.StoreNode(node).Await()
            }
            newRoot?.also {
                curRootNodeId = it.hdr.id
                rootCreated = true
            }
        }
    }

    init {
        if (rootNodeId != null) {
            curRootNodeId = rootNodeId
        }
        if (config.order < 6) {
            throw Error("Order should be at least 6")
        }
    }

    /** Find leaf node to take an entry for median replacement from.
     * @param node Node with the median to replace. Its parent chain should be properly defined in
     *  the context stack.
     * @param isLeft True to get the rightmost leaf node from the left side from median, false for
     *  leftmost leaf node from the right side from median.
     * @return
     *  first: Leaf node to get replacement entry from.
     *  second: Stack items for returned node to append to context stack.
     */
    private suspend fun FindMedianEntryNode(ctx: QueryContext, node: Node<TKey, TPayload>,
                                            medianIdx: Int, isLeft: Boolean):
        Pair<Node<TKey, TPayload>, ArrayList<StackEntry<TKey, TPayload>>>
    {
        val stack = ArrayList<StackEntry<TKey, TPayload>>()
        var idx = if (isLeft) medianIdx else medianIdx + 1
        var curNode = node
        while (true) {
            stack.add(StackEntry(curNode, idx))
            curNode = ctx.GetNode(curNode.GetChildId(idx))
            if (curNode.isLeaf) {
                break
            }
            idx = if (isLeft) curNode.hdr.numEntries else 0
        }
        return Pair(curNode, stack)
    }

    /** Delete or modify node with the specified key. Context has TargetEntry set upon return.
     * @return True if deleted, false if the key not found.
     */
    @Suppress("DuplicatedCode")
    private suspend fun DeleteOrModify(ctx: QueryContext, key: TKey,
                                       modifyFunc: ModifyFunc<TPayload>?): BTreeModifyResult
    {
        var node: Node<TKey, TPayload>
        node = ctx.newRoot ?:
            if (rootCreated) {
                ctx.GetNode(curRootNodeId)
            } else {
                return BTreeModifyResult.NOT_FOUND
            }

        var result = BTreeModifyResult.DELETE
        while (true) {
            val idx = node.Find(key)
            if (node.isLeaf) {
                if (idx >= node.hdr.numEntries || node.GetEntry(idx).GetKey() != key) {
                    return BTreeModifyResult.NOT_FOUND
                }
                node = ctx.ModifyNode(node)
                val e = node.GetEntry(idx)
                ctx.targetEntry = e
                if (modifyFunc != null) {
                    result = modifyFunc(e)
                    if (result == BTreeModifyResult.NOT_FOUND ||
                        result == BTreeModifyResult.KEY_COLLISION) {

                        throw Error("Disallowed modifyFunc return value")
                    }
                    if (result == BTreeModifyResult.NO_CHANGE) {
                        return BTreeModifyResult.NO_CHANGE
                    }
                    if (result == BTreeModifyResult.CHANGED) {
                        break
                    }
                }
                node.Remove(idx)
                break
            } else {
                if (idx < node.hdr.numEntries && node.GetEntry(idx).GetKey() == key) {
                    /* Find leaf entry for replacement. */
                    node = ctx.ModifyNode(node)
                    val e = node.GetEntry(idx)
                    ctx.targetEntry = e
                    if (modifyFunc != null) {
                        result = modifyFunc(e)
                        if (result == BTreeModifyResult.NOT_FOUND ||
                            result == BTreeModifyResult.KEY_COLLISION) {

                            throw Error("Disallowed modifyFunc return value")
                        }
                        if (result == BTreeModifyResult.NO_CHANGE) {
                            return BTreeModifyResult.NO_CHANGE
                        }
                        if (result == BTreeModifyResult.CHANGED) {
                            break
                        }
                    }
                    val (leftNode, leftStack) = FindMedianEntryNode(ctx, node, idx, true)
                    val (rightNode, rightStack) = FindMedianEntryNode(ctx, node, idx, false)
                    if (leftNode.hdr.numEntries > rightNode.hdr.numEntries) {
                        ctx.stack.addAll(leftStack)
                        val srcIdx = leftNode.hdr.numEntries - 1
                        node.SetEntry(idx, leftNode.GetEntry(srcIdx))
                        node = ctx.ModifyNode(leftNode)
                        node.Remove(srcIdx)
                    } else {
                        ctx.stack.addAll(rightStack)
                        node.SetEntry(idx, rightNode.GetEntry(0))
                        node = ctx.ModifyNode(rightNode)
                        node.Remove(0)
                    }
                    break
                }
                ctx.PushNode(node, idx)
                node = ctx.GetNode(node.GetChildId(idx))
            }
        }

        /* Re-balance up to the root if necessary. */
        if (result != BTreeModifyResult.CHANGED) {
            while (!ctx.stackEmpty && node.hdr.numEntries < minEntries) {
                val se = ctx.StackTop()
                val idx = se.childIdx
                val parentNode = ctx.ModifyNode(se.node, 0)
                val leftNode = if (idx > 0) {
                    se.childIdx = idx - 1
                    ctx.GetNode(parentNode.GetChildId(idx - 1))
                } else {
                    null
                }
                if (leftNode != null && leftNode.hdr.numEntries > minEntries) {
                    /* Rotate right. */
                    val siblingNode = ctx.ModifyNode(leftNode)
                    parentNode.RotateRight(idx - 1, siblingNode, node)
                    break
                }
                val rightNode = if (idx < parentNode.hdr.numEntries) {
                    se.childIdx = idx + 1
                    ctx.GetNode(parentNode.GetChildId(idx + 1))
                } else {
                    null
                }
                if (rightNode != null && rightNode.hdr.numEntries > minEntries) {
                    /* Rotate left. */
                    val siblingNode = ctx.ModifyNode(rightNode)
                    parentNode.RotateLeft(idx, node, siblingNode)
                    break
                }
                /* Merge with any of the available sibling nodes. */
                val mergedNode = if (leftNode != null) {
                    ctx.DeleteNode(leftNode)
                    se.childIdx = idx - 1
                    parentNode.Merge(idx - 1, leftNode, node)
                } else {
                    ctx.DeleteNode(rightNode!!)
                    se.childIdx = idx
                    parentNode.Merge(idx, node, rightNode)
                }
                ctx.DeleteNode(node)
                if (parentNode.hdr.numEntries == 0) {
                    /* The merged node became last leaf with empty root, make it new root. */
                    ctx.PopNode()
                    Assert(ctx.stackEmpty)
                    ctx.DeleteNode(parentNode)
                    ctx.SetNewRoot(mergedNode)
                    break
                } else {
                    ctx.SetDirty(mergedNode, true)
                }
                node = parentNode
                ctx.PopNode()
            }
        }
        ctx.stack.clear()
        return result
    }

    /** Insert the specified entry.
     * @param override True to override existing entry if any.
     * @return true if new entry inserted, false if the tree already has a node with the specified
     *  key (which is overridden if allowed).
     */
    private suspend fun Insert(ctx: QueryContext, entry: TPayload, override: Boolean): Boolean
    {
        val key = entry.GetKey()

        var node: Node<TKey, TPayload>
        node = ctx.newRoot ?:
            if (rootCreated) {
                 ctx.GetNode(curRootNodeId)
            } else {
                Node<TKey, TPayload>(config.order, revision, true).also {
                    ctx.SetNewRoot(it)
                }
            }

        while (true) {
            if (node.hdr.numEntries == maxEntries) {
                val split = node.Split(revision)
                ctx.SetDirty(split.leftNode, split.leftNode !== node)
                ctx.SetDirty(split.rightNode, split.rightNode !== node)
                val medianKey = split.median.GetKey()
                if (key == medianKey && override) {
                    split.median = entry
                }
                if (ctx.stackEmpty) {
                    /* New root */
                    val newRoot = Node<TKey, TPayload>(config.order, revision, false)
                    newRoot.Insert(split, 0)
                    ctx.SetNewRoot(newRoot)
                } else {
                    /* Insert median into parent. */
                    val stackItem = ctx.StackTop()
                    /* Node in stack item is replaced in this call if necessary. */
                    ctx.ModifyNode(stackItem.node, 0)
                    stackItem.node.Insert(split, stackItem.childIdx)
                }

                /* Set current node to be one of the split result nodes. */
                if (key == medianKey) {
                    return false
                }
                node = if (key <= medianKey) split.leftNode else split.rightNode
            }

            /* Insert entry if leaf, find child node if not. */
            val idx = node.Find(key)
            if (idx < node.hdr.numEntries && node.GetEntry(idx).GetKey() == key) {
                if (override) {
                    node.SetEntry(idx, entry)
                }
                return false
            }
            if (node.isLeaf) {
                node = ctx.ModifyNode(node)
                node.Insert(idx, entry)
                break
            } else {
                ctx.PushNode(node, idx)
                node = ctx.GetNode(node.GetChildId(idx))
            }
        }

        return true
    }
}
