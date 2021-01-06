/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.math

import io.github.vagran.adk.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

const val TOLERANCE = 0.00001

fun CheckEquals(expected: Vector, actual: Vector, delta: Double = TOLERANCE)
{
    assertEquals(expected.size, actual.size)
    for (i in 0 until expected.size) {
        assertEquals(expected[i], actual[i], delta)
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatrixTest {

    @Test
    fun IdentityTest()
    {
        val v = Vector.Coords(42.0, 1.0, 2.0, 3.0, 5.0, 6.0)
        val m = Matrix.Identity(6)
        val v2 = m * v
        CheckEquals(v, v2)
    }

    @Test
    fun Rotation3D()
    {
        val a = 0.5
        val x1 = Vector.Coords(1.0, 0.0, 1.0)
        val x2 = Vector.Coords(1.0, 3.0, 1.0)
        val u = Vector.Coords(1.0, 0.0, 0.0)
        val v = Vector.Coords(0.0, 0.0, 1.0)
        val R = KdRotation(u, v, a)
        val y1 = R * x1
        val y2 = R * x2
        val a2 = x1.Angle(y1)

        assertEquals(a, a2, TOLERANCE)

        assertEquals(x2.magnitude, y2.magnitude, TOLERANCE)
    }

    @Test
    fun OrthonormalizeBasisTest()
    {
        val k = 10
        val rnd = Random()
        val b1 = Array(k) {
            Vector(DoubleArray(k) {
                rnd.GetDouble()
            })
        }
        val b2 = KdOrhtonormalizeBasis(b1)
        for (i in 0 until k) {
            assertEquals(1.0, b2[i].magnitude, TOLERANCE)
            for (j in 0 until k) {
                if (i == j) {
                    continue
                }
                assertEquals(0.0, b2[i] dot b2[j], TOLERANCE)
            }
        }
    }
}
