/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class EntityTest {

    data class D(
        val a: Int = 42,
        val b: String = "abc",
        val c: Int)

    @Test
    fun MutateTest()
    {
        val obj = D(43, "def", 44)

        val m1 = MutateEntity(obj::copy, mapOf("b" to "test"))
        assertEquals(m1.a, 43)
        assertEquals(m1.b, "test")
        assertEquals(m1.c, 44)

        val m2 = MutateEntity(obj::copy, mapOf("c" to 45))
        assertEquals(m2.a, 43)
        assertEquals(m2.b, "def")
        assertEquals(m2.c, 45)
    }

}
