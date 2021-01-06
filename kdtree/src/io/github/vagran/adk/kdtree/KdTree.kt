/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.kdtree

import io.github.vagran.adk.math.Vector
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.set

/**
 * KD-tree implementation.
 * @param <TData> Type of data attached to each key.
 */
class KdTree<TData>(private val vectorSize: Int,
                    private val maxLeafKeys: Int) {

    class Result<TData>(val coord: Vector,
                        val data: TData) {

        //XXX
//        /** Remove associated entry from the index. This does not perform index re-balancing so
//         * should be used only for some sparse filtering.
//         */
//        fun Remove()
//        {
//            entry.Remove()
//        }
//
//        private val entry: Entry<TData>? = null
    }

    val size get() = entries.size

    /**
     * Add entry to the tree.
     *
     * @param pt Coordinate of the entry.
     * @param data User data attached to the entry.
     * @return Assigned entry ID which can be used to delete this entry later.
     */
    fun Add(pt: Vector, data: TData): Long
    {
        EnsurePointSize(pt)
        val e = Entry(nextEntryId++, data, pt)
        entries[e.id] = e

        var node: Node<TData>? = FindNode(pt)
        if (node == null) {
            node = Node(0)
            node.entries = ArrayList(maxLeafKeys)
            root = node
        } else if (node.numEntries >= maxLeafKeys) {
            node = SplitNode(node, pt)
        }
        node.AddEntry(e, comparators[node.axisIdx])
        return e.id
    }

    /**
     * Delete entry from the tree.
     *
     * @param entryId ID previously returned by Add() method.
     * @return True if entry found and deleted, false if entry not found.
     */
    fun Delete(entryId: Long): Boolean
    {
        val e: Entry<TData> = entries.remove(entryId) ?: return false
        var node = e.node
        node.DeleteEntry(e)
        while (true) {
            val parent = node.parent ?: break
            if (!TryMergeNode(parent)) {
                break
            }
            node = parent
        }
        return true
    }

    /** Find at most maxCount entries near the specified coordinates. The result is sorted by
     * distance in ascending order.
     */
    fun FindKnn(pt: Vector, maxCount: Int): List<Result<TData>>
    {
        EnsurePointSize(pt)
        val result = KnnResult<TData>(pt, maxCount)
        var node = FindNode(pt)
        if (node != null) {
            node.KnnFromLeaf(result)
            while (true) {
                node = node?.parent ?: break
                KnnWalkDown(node, result, true)
            }
        }
        result.Sort()
        return result.result
    }

    /**
     * Find entries in the specified radius.
     * @param x X coordinate of area center.
     * @param y Y coordinate of area center.
     * @param radius Radius to find entries in.
     * @param maxCount Find at most so many entries, -1 for no limit.
     * @return List of found entries.
     */
    fun FindInRadius(pt: Vector, radius: Double,
                     maxCount: Int = -1): List<Result<TData>>
    {
        EnsurePointSize(pt)
        val result: ArrayList<Result<TData>> =
            if (maxCount == -1) ArrayList() else ArrayList(maxCount)
        root?.also {
            FindEntriesInRadius(it, pt, radius, result, maxCount)
        }
        return result
    }

    /** Delete all content of the tree.  */
    fun Clear()
    {
        root = null
        entries.clear()
        nextEntryId = 1
    }

    /** Set user data for the specified entry.  */
    fun SetData(entryId: Long, data: TData)
    {
        val e = entries[entryId] ?: throw IllegalArgumentException("Specified node not found")
        e.data = data
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private class Entry<TData>(val id: Long,
                               var data: TData,
                               val coord: Vector) {

        lateinit var node: Node<TData>

        fun ToResult(): Result<TData>
        {
            val result = Result(coord.Clone(), data)
//            result.entry = this
            return result
        }

        fun GetCoord(axisIdx: Int): Double
        {
            return coord[axisIdx]
        }

        fun IsInRadius(pt: Vector, radius: Double): Boolean
        {
            return coord.DistanceSquared(pt) <= radius * radius
        }

//        fun Remove()
//        {
//            node.entries.remove(this)
//        }
    }

    private class EntryComparator(private val axisIdx: Int): Comparator<Entry<*>> {

        override fun compare(e1: Entry<*>, e2: Entry<*>): Int
        {
            return e1.coord[axisIdx].compareTo(e2.coord[axisIdx])
        }
    }

    private class Node<TData>(var axisIdx: Int = 0) {
        /** Edge coordinate value. */
        var edge = 0.0

        /** Attached entries sorted in edge coordinate order. Null for non-leaf node.  */
        var entries: ArrayList<Entry<TData>>? = null

        /** Left child node has entries with edge-coordinates less than this node edge.  */
        var left: Node<TData>? = null

        /** Right child node has entries with edge-coordinates greater or equal to this node edge.  */
        var right: Node<TData>? = null

        /** Parent node, null for root.  */
        var parent: Node<TData>? = null

        val numEntries get() = entries?.size ?: 0

        fun SetParent(parent: Node<TData>, vectorSize: Int)
        {
            this.parent = parent
            axisIdx = parent.axisIdx + 1
            if (axisIdx == vectorSize) {
                axisIdx = 0
            }
        }

        fun UpdateEntriesNode()
        {
            for (e in entries!!) {
                e.node = this
            }
        }

        fun AddEntry(entry: Entry<TData>, comparator: Comparator<Entry<*>>)
        {
            entries!!.add(entry)
            entry.node = this
            /* Keep array sorted. */
            entries!!.sortWith(comparator)
        }

        fun KnnFromLeaf(result: KnnResult<TData>)
        {
            for (e in entries!!) {
                result.TryAdd(e)
            }
        }

        fun DeleteEntry(entry: Entry<TData>)
        {
            entries!!.remove(entry)
        }
    }

    private class KnnResult<TData>(val coord: Vector,
                                   val maxCount: Int) {

        val result = ArrayList<Result<TData>>(maxCount)
        var maxDistanceSq = 0.0

        /** Check if the specified entry should be added and add if it should.
         *
         * @param e Entry to check.
         * @return True if the entry added, false if it did not pass the check.
         */
        fun TryAdd(e: Entry<TData>): Boolean
        {
            var distanceSq = coord.DistanceSquared(e.coord)
            val numResults = result.size
            if (numResults == 0) {
                maxDistanceSq = distanceSq
                result.add(e.ToResult())
                return true
            }
            if (numResults < maxCount) {
                result.add(e.ToResult())
                if (distanceSq > maxDistanceSq) {
                    maxDistanceSq = distanceSq
                }
                return true
            }
            if (distanceSq >= maxDistanceSq) {
                return false
            }
            var replaced = false
            var newMaxDistanceSq = distanceSq
            for (i in 0 until numResults) {
                val res = result[i]
                distanceSq = res.coord.DistanceSquared(coord)
                if (!replaced && distanceSq == maxDistanceSq) {
                    replaced = true
                    result[i] = e.ToResult()
                } else if (distanceSq > newMaxDistanceSq) {
                    newMaxDistanceSq = distanceSq
                }
            }
            maxDistanceSq = newMaxDistanceSq
            return true
        }

        fun IsFull(): Boolean
        {
            return result.size == maxCount
        }

        fun Sort()
        {
            result.sortWith(Comparator { r1: Result<TData>,
                                         r2: Result<TData> ->

                val dist1 = r1.coord.DistanceSquared(coord)
                val dist2 = r2.coord.DistanceSquared(coord)
                dist1.compareTo(dist2)
            })
        }

    }

    /** All entries in the tree indexed by ID.  */
    private val entries = HashMap<Long, Entry<TData>>()

    /** Next ID for new entry.  */
    private var nextEntryId = 1L

    /** Tree root node. Root node edge coordinate is the first vector component.  */
    private var root: Node<TData>? = null

    private val comparators = Array(vectorSize) { axisIdx -> EntryComparator(axisIdx) }

    private fun EnsurePointSize(pt: Vector)
    {
        if (pt.size != vectorSize) {
            throw IllegalArgumentException(
                "Vector size does not match tree dimensions: ${pt.size} != $vectorSize")
        }
    }

    private fun FindNode(pt: Vector): Node<TData>?
    {
        var node = root ?: return null
        while (true) {
            if (node.entries != null) {
                return node
            }
            val coord = pt[node.axisIdx]
            node = if (coord < node.edge) node.left!! else node.right!!
        }
    }

    /**
     * Split node for adding new entry. May be not split if all entries have the same coordinates.
     * @param node Node to split.
     * @param pt Coordinate of the new entry.
     * @return New child node to insert the entry to.
     */
    private fun SplitNode(node: Node<TData>, pt: Vector): Node<TData>
    {
        val entries = node.entries ?: throw Error("Non-leaf node split")
        /* Entries are kept sorted so entries can be scanned sequentially. */
        val axisIdx = node.axisIdx
        var edgeEntryIdx = node.numEntries / 2
        val edgeEntry = entries[edgeEntryIdx]
        var edgeCoord = edgeEntry.GetCoord(axisIdx)
        var diffCoordFound = false

        /* Check if there are entries for the left leaf. */
        for (prevIdx in edgeEntryIdx - 1 downTo 0) {
            val prevEntry = entries[prevIdx]
            val prevEdgeCoord = prevEntry.GetCoord(axisIdx)
            if (prevEdgeCoord != edgeCoord) {
                break
            }
            edgeEntryIdx = prevIdx
            if (!diffCoordFound && prevEntry.coord != edgeEntry.coord) {
                diffCoordFound = true
            }
        }

        if (edgeEntryIdx == 0) {
            /* Check right leaf. */
            for (nextIdx in entries.size / 2 + 1 until entries.size) {
                val nextEntry: Entry<TData> = entries[nextIdx]
                val nextEdgeCoord: Double = nextEntry.GetCoord(axisIdx)
                if (nextEdgeCoord != edgeCoord) {
                    edgeEntryIdx = nextIdx
                    edgeCoord = entries[edgeEntryIdx].GetCoord(axisIdx)
                    break
                }
                if (!diffCoordFound && nextEntry.coord != edgeEntry.coord) {
                    diffCoordFound = true
                }
            }
        }
        if (edgeEntryIdx == 0 && !diffCoordFound) {
            /* No split if all entries has the same coordinate. */
            return node
        }
        node.edge = edgeCoord
        val leftNode = Node<TData>()
        node.left = leftNode
        leftNode.SetParent(node, vectorSize)
        val rightNode = Node<TData>()
        node.right = rightNode
        rightNode.SetParent(node, vectorSize)
        val leftEntries = node.entries!!
        leftNode.entries = leftEntries
        val rightEntries = ArrayList<Entry<TData>>(maxLeafKeys)
        rightNode.entries = rightEntries
        for (i in edgeEntryIdx until leftEntries.size) {
            rightEntries.add(leftEntries[i])
        }
        leftEntries.subList(edgeEntryIdx, leftEntries.size).clear()
        leftNode.UpdateEntriesNode()
        rightNode.UpdateEntriesNode()
        node.entries = null
        return if (pt[axisIdx] < node.edge) leftNode else rightNode
    }

    private fun TryMergeNode(node: Node<TData>): Boolean
    {
        val leftNode = node.left!!
        val rightNode = node.right!!
        val leftEntries = leftNode.entries ?: return false
        val rightEntries = rightNode.entries ?: return false
        if (leftEntries.size + rightEntries.size > maxLeafKeys) {
            return false
        }
        node.entries = leftEntries
        leftEntries.addAll(rightEntries)
        node.UpdateEntriesNode()
        node.left = null
        node.right = null
        leftEntries.sortWith(comparators[node.axisIdx])
        return true
    }

    private fun KnnWalkDown(node: Node<TData>,
                            result: KnnResult<TData>,
                            isFirstParent: Boolean)
    {
        if (node.entries != null) {
            node.KnnFromLeaf(result)
            return
        }
        val edgeDist = result.coord[node.axisIdx] - node.edge
        if (!isFirstParent) {
            KnnWalkDown(if (edgeDist < 0) node.left!! else node.right!!, result, false)
        }
        if (!result.IsFull() || edgeDist * edgeDist <= result.maxDistanceSq) {
            KnnWalkDown(if (edgeDist < 0) node.right!! else node.left!!, result, false)
        }
    }

    /**
     * @return True if results limit reached.
     */
    private fun FindEntriesInRadius(node: Node<TData>,
                                    pt: Vector,
                                    radius: Double,
                                    result: MutableList<Result<TData>>,
                                    maxResultCount: Int): Boolean
    {
        node.entries?.also { entries ->
            for (e in entries) {
                if (e.IsInRadius(pt, radius)) {
                    result.add(e.ToResult())
                    if (maxResultCount != -1 && result.size >= maxResultCount) {
                        return true
                    }
                }
            }
            return false
        }
        val edgeDist = pt[node.axisIdx] - node.edge
        if (FindEntriesInRadius(if (edgeDist < 0) node.left!! else node.right!!, pt, radius, result,
                                maxResultCount)) {
            return true
        }
        return if (edgeDist >= 0 && edgeDist < radius || edgeDist < 0 && -edgeDist <= radius) {
            FindEntriesInRadius(if (edgeDist < 0) node.right!! else node.left!!, pt, radius, result,
                                maxResultCount)
        } else false
    }
}
