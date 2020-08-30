/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk

import io.github.vagran.adk.math.KdRotation
import io.github.vagran.adk.math.Matrix
import io.github.vagran.adk.math.Vector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

fun CheckEquals(expected: Vector, actual: Vector, delta: Double = 0.001)
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

        assertEquals(a, a2, 0.00001)

        assertEquals(x2.magnitude, y2.magnitude, 0.00001)
    }
}
