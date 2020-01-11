/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk

/** Random number generator based on CMWC4096 algorithm.  */
class Random(seed: Int = System.nanoTime().toInt()) {

    /* ****************************************************************************/
    private val Q = IntArray(4096)
    private var c = 362436
    private var idx = 4095
    private var gauss1: Double = 0.0
    private var gauss2: Double = 0.0
    private var gaussValid = false

    init {
        val PHI = -0x61c88647
        Q[0] = seed + PHI
        Q[1] = seed + PHI + PHI
        Q[2] = seed + PHI + PHI + PHI
        for (i in 3..4095) {
            Q[i] = Q[i - 3] + Q[i - 2] + PHI + i
        }
    }

    fun GetInt(): Int
    {
        idx = (idx + 1) and 4095
        val t = 18705L * Q[idx] + c
        c = (t shr 32).toInt()
        Q[idx] = (0xfffffffeL - t).toInt()
        return Q[idx]
    }

    fun GetLong(): Long
    {
        return (GetInt().toLong() shl 32) + GetInt()
    }

    /** Get float in range 0..1.  */
    fun GetFloat(): Float
    {
        var i = GetInt()
        if (i == Integer.MIN_VALUE) {
            i++
        }
        return Math.abs(i).toFloat() / Integer.MAX_VALUE
    }

    fun GetDouble(): Double
    {
        var l = GetLong()
        if (l == java.lang.Long.MIN_VALUE) {
            l++
        }
        return Math.abs(l).toDouble() / java.lang.Long.MAX_VALUE
    }

    /** Get random number from Gaussian distribution. Median is zero.
     *
     * @param variance Desired variance value of the distribution.
     */
    fun GetGaussian(variance: Double): Double
    {
        if (gaussValid) {
            gaussValid = false
            return Math.sqrt(variance * gauss1) * Math.sin(gauss2)
        }
        gaussValid = true
        gauss1 = GetDouble()
        if (gauss1 < 1e-100) {
            gauss1 = 1e-100
        }
        gauss1 = -2.0 * Math.log(gauss1)
        gauss2 = GetDouble() * Math.PI * 2.0
        return Math.sqrt(variance * gauss1) * Math.cos(gauss2)
    }

    /** Get random number from Gaussian distribution.
     *
     * @param deviation Desired deviation value of the distribution.
     * @param median Desired median value of the distribution.
     */
    fun GetGaussian(median: Double, deviation: Double): Double
    {
        return median + GetGaussian(deviation * deviation)
    }

    /** Return randomly selected number in range [0; range).  */
    fun SampleRange(range: Int): Int
    {
        val result = (GetFloat() * range).toInt()
        return if (result == range) {
            result - 1
        } else result
    }

    /** Return randomly selected number in range [start; end).  */
    fun SampleRange(start: Int, end: Int): Int
    {
        return start + SampleRange(end - start)
    }

}
