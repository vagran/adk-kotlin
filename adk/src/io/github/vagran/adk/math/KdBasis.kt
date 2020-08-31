/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.math


/** Get orthonormalized basis from linearly independent set of vectors. Modified Gram–Schmidt
 * process is used.
 */
fun KdOrhtonormalizeBasis(v: Array<Vector>): Array<Vector>
{
    val k = v[0].size
    if (v.size > k) {
        throw IllegalArgumentException("Too many vectors for basis")
    }
    val result = Array(k) { Vector.Zero(k) }
    val uPrev = Vector.Zero(k)
    for (iVtr in v.indices) {
        uPrev.Set(v[iVtr])
        for (i in 0 until iVtr) {
            uPrev -= result[i] * (result[i] dot uPrev)
        }
        uPrev.Normalize()
        result[iVtr].Set(uPrev)
    }
    return result
}
