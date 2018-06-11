package com.ast.adk.diff

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

    fun Calculate()
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
            Finalize()
            return
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

        Finalize()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    /** Step index when X maximal value is reached. Negative until set. */
    private var maxDx = -1
    /** Step index when Y maximal value is reached. Negative until set. */
    private var maxDy = -1
    private var curD = 0

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

    private fun Finalize()
    {

    }

    private fun GetX(d: Int, k: Int): Int
    {
        if (d % 2 != k % 2) {
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

        } else if (maxDx > maxDy) {
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
