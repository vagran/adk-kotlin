/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.math

import java.lang.StringBuilder

/** Arbitrary sized matrix.
 * @param m Matrix data in row-major order.
 */
class Matrix(val rows: Int, val cols: Int, val m: DoubleArray) {

    companion object {
        fun Zero(rows: Int, cols: Int): Matrix
        {
            return Matrix(rows, cols, DoubleArray(rows * cols))
        }

        fun Ones(rows: Int, cols: Int): Matrix
        {
            return Matrix(rows, cols, DoubleArray(rows * cols) { 1.0 })
        }

        fun Identity(size: Int): Matrix
        {
            return Matrix(size, size, DoubleArray(size * size) {
                idx ->
                val row = idx / size
                val col = idx - row * size
                if (row == col) 1.0 else 0.0
            })
        }

        fun Concat(vararg vectors: Vector): Matrix
        {
            val rows = vectors[0].size
            val cols = vectors.size
            for (v in vectors) {
                if (v.size != rows) {
                    throw IllegalArgumentException("All vectors should have the same size")
                }
            }
            return Matrix(rows, cols, DoubleArray(rows * cols) {
                idx ->
                val row = idx / cols
                val col = idx - row * cols
                vectors[col][row]
            })
        }

        fun Concat(vectors: Collection<Vector>): Matrix
        {
            return Concat(*vectors.toTypedArray())
        }
    }

    constructor(v: Vector): this(v.size, 1, v.v.copyOf())

    operator fun get(row: Int, col: Int): Double
    {
        EnsureInRange(row, col)
        return m[row * cols + col]
    }

    operator fun set(row: Int, col: Int, value: Double)
    {
        EnsureInRange(row, col)
        m[row * cols + col] = value
    }

    operator fun plus(other: Matrix): Matrix
    {
        return Zip(other) { d1, d2 -> d1 + d2 }
    }

    operator fun plusAssign(other: Matrix)
    {
        ZipInplace(other) { d1, d2 -> d1 + d2 }
    }

    operator fun minus(other: Matrix): Matrix
    {
        return Zip(other) { d1, d2 -> d1 - d2 }
    }

    operator fun minusAssign(other: Matrix)
    {
        ZipInplace(other) { d1, d2 -> d1 - d2 }
    }

    operator fun times(a: Double): Matrix
    {
        return Map { d -> d * a }
    }

    operator fun timesAssign(a: Double)
    {
        MapInplace { d -> d * a }
    }

    operator fun times(other: Matrix): Matrix
    {
        if (cols != other.rows) {
            throw IllegalArgumentException("Second matrix height does not match this matrix width")
        }
        val mr = DoubleArray(rows * other.cols) {
            idx ->
            val row = idx / other.cols
            val col = idx - row * other.cols
            var s = 0.0
            for (i in 0 until cols) {
                s += this[row, i] * other[i, col]
            }
            s
        }
        return Matrix(rows, other.cols, mr)
    }

    operator fun times(v: Vector): Vector
    {
        return (this * v.ToMatrix()).ToVector()
    }

    fun T(): Matrix
    {
        val tm = DoubleArray(rows * cols) {
            idx ->
            val row = idx / rows
            val col = idx - row * rows
            this[col, row]
        }
        return Matrix(cols, rows, tm)
    }

    fun Clone(): Matrix
    {
        return Matrix(rows, cols, m.copyOf())
    }

    fun ToVector(): Vector
    {
        if (cols != 1) {
            throw IllegalArgumentException("Should have one column")
        }
        return Vector(m.copyOf())
    }

    override fun toString(): String
    {
        val sb = StringBuilder()
        sb.append("[")
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                sb.append(this[row, col])
                if (col != cols - 1) {
                    sb.append(", ")
                } else if (row != rows - 1) {
                    sb.append("; ")
                }
            }
        }
        sb.append("]")
        return sb.toString()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    init {
        if (rows * cols != m.size) {
            throw IllegalArgumentException("Data size does not match matrix size")
        }
    }

    private fun EnsureInRange(row: Int, col: Int)
    {
        if (row >= rows) {
            throw IllegalArgumentException("Row index out of range: $row/$rows")
        }
        if (col >= cols) {
            throw IllegalArgumentException("Column index out of range: $col/$cols")
        }
    }

    private inline fun Zip(other: Matrix, func: (Double, Double) -> Double): Matrix
    {
        EnsureSameSize(other)
        val m1 = m
        val m2 = other.m
        return Matrix(rows, cols, DoubleArray(rows * cols) { idx -> func(m1[idx], m2[idx]) })
    }

    private inline fun ZipInplace(other: Matrix, func: (Double, Double) -> Double): Matrix
    {
        EnsureSameSize(other)
        val m1 = m
        val m2 = other.m
        for (idx in 0 until rows * cols) {
            m1[idx] = func(m1[idx], m2[idx])
        }
        return this
    }

    private inline fun Map(func: (Double) -> Double): Matrix
    {
        val m1 = m
        return Matrix(rows, cols, DoubleArray(rows * cols) { idx -> func(m1[idx]) })
    }

    private inline fun MapInplace(func: (Double) -> Double): Matrix
    {
        val m1 = m
        for (idx in 0 until rows * cols) {
            m1[idx] = func(m1[idx])
        }
        return this
    }

    private fun EnsureSameSize(other: Matrix)
    {
        if (rows != other.rows) {
            throw IllegalArgumentException("The specified matrix has different height: ${other.rows}")
        }
        if (cols != other.cols) {
            throw IllegalArgumentException("The specified matrix has different width: ${other.cols}")
        }
    }
}
