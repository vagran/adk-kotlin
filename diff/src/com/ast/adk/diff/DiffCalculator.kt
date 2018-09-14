package com.ast.adk.diff

import java.util.*

typealias Diff = Collection<DiffCalculator.DiffEntry>

/** Calculates difference between two sequences using Myers algorithm. */
class DiffCalculator(private val data: DataAccessor) {

    /** Provides random access to input data sequences being compared. */
    interface DataAccessor {
        /** Check equality of input sequences arbitrary elements at the specified indices. */
        fun CheckEqual(idx1: Int, idx2: Int): Boolean

        /** First sequence length. */
        val length1: Int
        /** Second sequence length. */
        val length2: Int
    }

    /** Accessor for comparing strings. */
    class StringAccessor(private val string1: CharSequence, private val string2: CharSequence):
        DataAccessor {

        override fun CheckEqual(idx1: Int, idx2: Int): Boolean
        {
            return string1[idx1] == string2[idx2]
        }

        override val length1: Int
            get() = string1.length

        override val length2: Int
            get() = string2.length
    }

    /**
     * Entry of the calculated difference. Indices define sub-sequences in the input sequences.
     * Insertion is performed into the first sequence (both indices are pointing to insertion
     * position) from the second one (indices are the first and past the last inserted element, i.e.
     * exclusive range).
     * Deletion is performed from the first sequence, indices are the first and past the last
     * inserted element (exclusive range). The second sequence indices are pointing to current
     * position in it.
     * Equal is defining equal preserved sub-sequences, indices define range exclusively in both
     * sequences.
     */
    data class DiffEntry(val type: Type,
                         val idx1Start: Int,
                         val idx1End: Int,
                         val idx2Start: Int,
                         val idx2End: Int) {
        enum class Type {
            INSERTION,
            DELETION,
            EQUAL
        }
    }

    fun Calculate(): Diff
    {
        /* Initialize the first node in the edit graph. Perform diagonal traverse if possible. */
        val n = data.length1
        val m = data.length2
        var x = 0
        for (i in 0 until Math.min(n, m)) {
            if (data.CheckEqual(i, i)) {
                x++
            } else {
                break
            }
        }
        editGraph.Push(x)
        if (x >= n) {
            maxDx = 0
        }
        if (x >= m) {
            maxDy = 0
        }
        if (x == data.length1 && x == data.length2) {
            /* Input sequences are equal. */
            return Finalize()
        }

        steps@ for (d in 1 .. n + m) {
            curD = d
            val minK =
                if (maxDy < 0) {
                    -d
                } else {
                    -maxDy + (d - maxDy)
                }
            val maxK =
                if (maxDx < 0) {
                    d
                } else {
                    maxDx - (d - maxDx)
                }
            for (k in minK .. maxK step 2) {
                curK = k
                x =
                    if (k == -d || (k != d && GetX(d - 1, k -1) < GetX(d - 1, k + 1))) {
                        GetX(d - 1, k + 1)
                    } else {
                        GetX(d - 1, k - 1) + 1
                    }
                var y = x - k
                while (x < n && y < m && data.CheckEqual(x, y)) {
                    x++
                    y++
                }
                editGraph.Push(x)
                if (maxDx < 0 && x >= n) {
                    maxDx = d
                }
                if (maxDy < 0 && y >= m) {
                    maxDy = d
                }
                if (x >= n && y >= m) {
                    break@steps
                }
            }
        }

        return Finalize()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    /** Step index when X maximal value is reached. Negative until set. */
    private var maxDx = -1
    /** Step index when Y maximal value is reached. Negative until set. */
    private var maxDy = -1
    private var curD = 0
    private var curK = 0

    private val editGraph: IntVector = IntVector(32)

    /** Dynamic array of integer values. */
    private class IntVector(initialCapacity: Int) {

        fun Push(value: Int)
        {
            EnsureCapacity(size + 1)
            data[size] = value
            size++
        }

        operator fun get(index: Int): Int
        {
            if (index >= size) {
                throw IndexOutOfBoundsException()
            }
            return data[index]
        }

        operator fun set(index: Int, value: Int)
        {
            if (index >= size) {
                throw IndexOutOfBoundsException()
            }
            data[index] = value
        }

        fun Clear()
        {
            if (data.size > alignment) {
                data = IntArray(alignment)
            }
            size = 0
        }

        private var data: IntArray = IntArray(initialCapacity)
        private var size = 0
        private val factor = 1.618033988749895
        /** Must be power of two. */
        private val alignment = 16

        private fun EnsureCapacity(newSize: Int)
        {
            if (size + newSize > data.size) {
                var newCapacity = ((data.size * factor).toInt() and (alignment - 1).inv()) + alignment
                if (newCapacity < newSize) {
                    newCapacity = (newSize and (alignment - 1)) + alignment
                }
                data = data.copyOf(newCapacity)
            }
        }
    }

    private class DiffBuilder {
        private val diff = ArrayDeque<DiffEntry>()
        private var lastEntry: DiffEntry? = null

        /** Accept single element moves on edit graph in reverse order. */
        fun Move(prevX: Int, prevY: Int, newX: Int, newY: Int)
        {
            val type = when {
                newX == prevX -> DiffEntry.Type.INSERTION
                newY == prevY -> DiffEntry.Type.DELETION
                else -> DiffEntry.Type.EQUAL
            }
            val e = DiffEntry(type, prevX, newX, prevY, newY)
            if (lastEntry == null) {
                lastEntry = e
            } else if (e.type != lastEntry!!.type) {
                diff.addFirst(lastEntry)
                lastEntry = e
            } else {
                lastEntry = FoldEntries(e, lastEntry!!)
            }
        }

        fun FoldEntries(e1: DiffEntry, e2: DiffEntry): DiffEntry
        {
            return DiffEntry(e1.type, e1.idx1Start, e2.idx1End, e1.idx2Start, e2.idx2End)
        }

        fun Finalize(): Diff
        {
            if (lastEntry != null) {
                diff.addFirst(lastEntry)
                lastEntry = null
            }
            return diff
        }
    }

    private fun Finalize(): Diff
    {
        val db = DiffBuilder()

        /* Restore backtrace. */
        var x = data.length1
        var y = data.length2
        for (d in curD downTo 0) {
            val prevX: Int
            val prevY: Int
            if (d == 0) {
                prevX = 0
                prevY = 0
            } else {
                val k = x - y
                val prevK =
                    if (k == -d || (k != d && GetX(d - 1, k - 1) < GetX(d - 1, k + 1))) {
                        k + 1
                    } else {
                        k - 1
                    }
                prevX = GetX(d - 1, prevK)
                prevY = prevX - prevK
            }

            /* Diagonal move. */
            while (x > prevX && y > prevY) {
                db.Move(x - 1, y - 1, x, y)
                x--
                y--
            }
            /* Downward or rightward move. */
            if (d > 0) {
                db.Move(prevX, prevY, x, y)
            }
            x = prevX
            y = prevY
        }

        editGraph.Clear()
        return db.Finalize()
    }


    private fun GetX(d: Int, k: Int): Int
    {
        if (d % 2 != (if (k >= 0) k % 2 else -k % 2)) {
            throw Error("Oddity does not match")
        }
        var idx: Int
        var minK: Int
        var maxK: Int

        if ((maxDx < 0 || d <= maxDx) && (maxDy < 0 || d <= maxDy)) {
            minK = -d
            maxK = d

            idx = (1 + d) * d / 2

        } else if (maxDx >= 0 && d > maxDx && maxDy >= 0 && d > maxDy) {
            minK = -maxDy + (d - maxDy)
            maxK = maxDx - (d - maxDx)

            if (maxDx > maxDy) {
                idx = (1 + maxDy) * maxDy / 2
                idx += (maxDx - maxDy) * (maxDy + 1)
                idx += (maxDy + 1 + (maxK - minK) / 2) * (d - maxDx - 1) / 2
            } else {
                idx = (1 + maxDx) * maxDx / 2
                idx += (maxDy - maxDx) * (maxDx + 1)
                idx += (maxDx + 1 + (maxK - minK) / 2) * (d - maxDy - 1) / 2
            }

        } else if (maxDx < 0 || (maxDy >= 0 && maxDx > maxDy)) {
            minK = -maxDy + (d - maxDy)
            maxK = d

            idx = (1 + maxDy) * maxDy / 2
            idx += (d - maxDy) * (maxDy + 1)

        } else {
            minK = -d
            maxK = maxDx - (d - maxDx)

            idx = (1 + maxDx) * maxDx / 2
            idx += (d - maxDx) * (maxDx + 1)
        }

        if (k < minK || k > maxK) {
            throw Error("K out of range: $k / [$minK; $maxK]")
        }

        idx += (k - minK) / 2
        return editGraph[idx]
    }
}
