/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.math

import java.lang.StringBuilder
import kotlin.math.acos
import kotlin.math.sqrt

/** Arbitrary sized vector. */
class Vector(val v: DoubleArray) {

    companion object {
        fun Zero(size: Int): Vector
        {
            return Vector(DoubleArray(size))
        }

        fun One(size: Int, axisIdx: Int): Vector
        {
            if (axisIdx >= size) {
                throw IllegalArgumentException("Axis index out of range")
            }
            return Vector(DoubleArray(size) { idx -> if (idx == axisIdx) 1.0 else 0.0 })
        }

        fun Coords(vararg coords: Double): Vector
        {
            return Vector(coords)
        }
    }

    val size get() = v.size

    val magnitudeSquared: Double get()
    {
        var sum = 0.0
        for (d in v) {
            sum += d * d
        }
        return sum
    }

    val magnitude get() = sqrt(magnitudeSquared)

    operator fun get(idx: Int): Double
    {
        return v[idx]
    }

    operator fun set(idx: Int, value: Double)
    {
        v[idx] = value
    }

    override fun equals(other: Any?): Boolean
    {
        other as Vector
        for (i in v.indices) {
            if (v[i] != other[i]) {
                return false
            }
        }
        return true
    }

    operator fun plus(other: Vector): Vector
    {
        return Zip(other) { d1, d2 -> d1 + d2 }
    }

    operator fun plusAssign(other: Vector)
    {
        ZipInplace(other) { d1, d2 -> d1 + d2 }
    }

    operator fun minus(other: Vector): Vector
    {
        return Zip(other) { d1, d2 -> d1 - d2 }
    }

    operator fun minusAssign(other: Vector)
    {
        ZipInplace(other) { d1, d2 -> d1 - d2 }
    }

    operator fun times(other: Vector): Vector
    {
        return Zip(other) { d1, d2 -> d1 * d2 }
    }

    operator fun timesAssign(other: Vector)
    {
        ZipInplace(other) { d1, d2 -> d1 * d2 }
    }

    operator fun times(a: Double): Vector
    {
        return Map { d -> d * a }
    }

    operator fun timesAssign(a: Double)
    {
        MapInplace { d -> d * a }
    }

    infix fun dot(other: Vector): Double
    {
        EnsureSameSize(other)
        var sum = 0.0
        val v1 = v
        val v2 = other.v
        for (idx in 0 until size) {
            sum += v1[idx] * v2[idx]
        }
        return sum
    }

    fun DistanceSquared(other: Vector): Double
    {
        EnsureSameSize(other)
        var sum = 0.0
        val v1 = v
        val v2 = other.v
        for (idx in 0 until size) {
            val d = v1[idx] - v2[idx]
            sum += d * d
        }
        return sum
    }

    fun Distance(other: Vector): Double
    {
        return sqrt(DistanceSquared(other))
    }

    fun Normalized(): Vector
    {
        val s = 1.0 / magnitude
        return Map { d -> d * s}
    }

    fun Normalize()
    {
        val s = 1.0 / magnitude
        MapInplace { d -> d * s}
    }

    fun Set(other: Vector)
    {
        EnsureSameSize(other)
        System.arraycopy(other.v, 0, v, 0, v.size)
    }

    fun Clone(): Vector
    {
        return Vector(v.copyOf())
    }

    fun ToMatrix(): Matrix
    {
        return Matrix(this)
    }

    fun Angle(other: Vector): Double
    {
        return acos((this dot other) / (magnitude * other.magnitude))
    }

    override fun toString(): String
    {
        val sb = StringBuilder()
        sb.append("[")
        for ((idx, d) in v.withIndex()) {
            sb.append(d)
            if (idx != v.size - 1) {
                sb.append(", ")
            }
        }
        sb.append("]")
        return sb.toString()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private inline fun Zip(other: Vector, func: (Double, Double) -> Double): Vector
    {
        EnsureSameSize(other)
        val v1 = v
        val v2 = other.v
        return Vector(DoubleArray(size) { idx -> func(v1[idx], v2[idx]) })
    }

    private inline fun ZipInplace(other: Vector, func: (Double, Double) -> Double): Vector
    {
        EnsureSameSize(other)
        val v1 = v
        val v2 = other.v
        for (idx in 0 until size) {
            v1[idx] = func(v1[idx], v2[idx])
        }
        return this
    }

    private inline fun Map(func: (Double) -> Double): Vector
    {
        val v1 = v
        return Vector(DoubleArray(size) { idx -> func(v1[idx]) })
    }

    private inline fun MapInplace(func: (Double) -> Double): Vector
    {
        val v1 = v
        for (idx in 0 until size) {
            v1[idx] = func(v1[idx])
        }
        return this
    }

    private fun EnsureSameSize(other: Vector)
    {
        if (size != other.size) {
            throw IllegalArgumentException("The specified vector has different size: ${other.size}")
        }
    }
}
