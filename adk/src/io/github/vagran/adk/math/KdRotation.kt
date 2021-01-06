/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.math

import kotlin.math.cos
import kotlin.math.sqrt

/** Generate rotation matrix in K-dimensional space in the (hyper-)plane specified by two
 * orthonormal vectors u and v. Basis vectors orthonormality should be ensured by caller.
  */
fun KdRotation(u: Vector, v: Vector, angle: Double): Matrix
{
    val k = u.size
    if (k < 2) {
        throw IllegalArgumentException("Too small dimensionality")
    }
    if (v.size != k) {
        throw IllegalArgumentException("Plane basis vectors have different size")
    }
    val cosA = cos(angle)
    val sinA = sqrt(1.0 - cosA * cosA)
    val R = Matrix(2, 2, doubleArrayOf(cosA, -sinA, sinA, cosA))
    if (k == 2) {
        return R
    }
    val uM = u.ToMatrix()
    val vM = v.ToMatrix()
    val Q = Matrix.Identity(k) - uM * uM.T() - vM * vM.T()
    val uv = Matrix.Concat(u, v)
    return Q + uv * R * uv.T()
}
