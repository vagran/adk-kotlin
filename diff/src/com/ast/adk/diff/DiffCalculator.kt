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

    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    /** Step index when X maximal value is reached. Negative until set. */
    private var maxDx = -1
    /** Step index when Y maximal value is reached. Negative until set. */
    private var maxDy = -1

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
}
