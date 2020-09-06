/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class ManagedStateTest {

    class A(id: String, loadFrom: Map<String, Any?>? = null) {
        val state = ManagedState(loadFrom)

        enum class Type {
            T1,
            T2
        }

        val id: String by state.Id(id)
        val s: String by state("string")
        var name by state.Param("aaa")
        val desc by state.Param<String?>()
        val type by state.Param(Type.T1)
        val i by state.Param<Int>()
        val d by state.Param<Double>()
        val i2 by state.Param(42)
        val d2 by state.Param(43.0)
        private var internalCounter by state(0).InfoLevel(1)
    }

    @Test
    fun Basic()
    {

        val a = A("a")

        assertEquals("a", a.id)
        assertEquals("string", a.s)
        assertEquals("aaa", a.name)
        assertNull(a.desc)
        assertEquals(A.Type.T1, a.type)
        assertEquals(0, a.i)
        assertEquals(0.0, a.d)
        assertEquals(42, a.i2)
        assertEquals(43.0, a.d2)
    }

    @Test
    fun LoadTest()
    {
        val a = A("", mapOf("id" to "myId",
                            "name" to "myName",
                            "desc" to "myDesc",
                            "type" to A.Type.T2,
                            "i" to 41,
                            "d" to 11.0))

        assertEquals("myId", a.id)
        assertEquals("string", a.s)
        assertEquals("myName", a.name)
        assertEquals("myDesc", a.desc)
        assertEquals(A.Type.T2, a.type)
        assertEquals(41, a.i)
        assertEquals(11.0, a.d)
        assertEquals(42, a.i2)
        assertEquals(43.0, a.d2)
    }

    @Test
    fun LoadEnumStringTest()
    {
        val a = A("", mapOf("type" to "T2"))
        assertEquals(A.Type.T2, a.type)
    }

    @Test
    fun LoadBadEnumStringTest()
    {
        val e = assertThrows<Error> { A("", mapOf("type" to "NON_VALUE")) }
        assertEquals("Failed to convert enum value from loaded string for property 'type'", e.message)
    }

    @Test
    fun LoadIntFromDoubleTest()
    {
        val a = A("", mapOf("i" to 41.0))
        assertEquals(41, a.i)
    }

    @Test
    fun LoadDoubleFromIntTest()
    {
        val a = A("", mapOf("d" to 41))
        assertEquals(41.0, a.d)
    }

    @Test
    fun LoadBadTypeTest()
    {
        val e = assertThrows<Error> { A("", mapOf("i" to "string")) }
        assertEquals("Wrong type returned for property 'i': kotlin.String is not subclass of kotlin.Int",
                     e.message)
    }

    @Test
    fun NoInitializer()
    {
        class C {
            val state = ManagedState()

            val s by state.Param<String>()
        }

        val e = assertThrows<Error> { C() }
        assertEquals("No value provided for non-nullable property 's'", e.message)
    }

    @Test
    fun NullLoadToNonNullable()
    {
        class C(loadFrom: Map<String, Any?>) {
            val state = ManagedState(loadFrom)

            val s by state.Param<String>()
        }

        val e = assertThrows<Error> { C(mapOf("s" to null)) }
        assertEquals("Null value loaded for non-nullable property 's'", e.message)
    }
}
