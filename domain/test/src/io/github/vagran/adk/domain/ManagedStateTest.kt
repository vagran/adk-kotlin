/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.domain

import io.github.vagran.adk.async.Deferred
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class ManagedStateTest {

    class A(id: String = "someId", loadFrom: Map<String, Any?>? = null) {
        val state = ManagedState(loadFrom)

        enum class Type {
            T1,
            T2,
            T3,
            T4
        }

        enum class InfoGroup {
            G1,
            G2
        }

        val id: String by state.Id(id)
        val s: String by state("string")
        var name by state.Param("aaa")
        val desc by state.Param<String?>()
        var type by state.Param(Type.T1)
        val i by state.Param<Int>()
        val d by state.Param<Double>()
        var i2 by state.Param(42).InfoLevel(1)
        var d2 by state.Param(43.0).InfoLevel(2)
        private var internalCounter by state(0).InfoLevel(1).InfoGroup(InfoGroup.G1)
        var even by state(0).Validator {
            v ->
            if (v % 2 != 0) {
                throw Error("Should be even")
            }
        }
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

        val checkInfo0 = {
            info: Map<String, Any?> ->
            assertEquals("a", info["id"])
            assertEquals("aaa", info["name"])
            assertNull(info["desc"])
            assertEquals(A.Type.T1, info["type"])
            assertEquals(0, info["i"])
            assertEquals(0.0, info["d"])
        }

        val checkInfo1 = {
            info: Map<String, Any?> ->
            checkInfo0(info)
            assertEquals(42, info["i2"])
        }

        val checkInfo2 = {
            info: Map<String, Any?> ->
            checkInfo1(info)
            assertEquals(43.0, info["d2"])
        }

        run {
            val info = a.state.GetInfo()
            assertEquals(6, info.size)
            checkInfo0(info)
        }

        run {
            val info = a.state.GetInfo(1)
            assertEquals(7, info.size)
            checkInfo1(info)
        }

        run {
            val info = a.state.GetInfo(2)
            assertEquals(8, info.size)
            checkInfo2(info)
        }

        run {
            val info = a.state.GetInfo(1, A.InfoGroup.G1)
            assertEquals(2, info.size)
            assertEquals("a", info["id"])
            assertEquals(0, info["internalCounter"])
        }

        run {
            val info = a.state.GetInfo(1, A.InfoGroup.G2)
            assertEquals(1, info.size)
            assertEquals("a", info["id"])
        }
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
        val a = A(loadFrom = mapOf("type" to "T2"))
        assertEquals(A.Type.T2, a.type)
    }

    @Test
    fun LoadBadEnumStringTest()
    {
        val e = assertThrows<IllegalArgumentException> { A("", mapOf("type" to "NON_VALUE")) }
        assertEquals("Failed to convert enum value from string for property 'type'", e.message)
    }

    @Test
    fun LoadIntFromDoubleTest()
    {
        val a = A(loadFrom = mapOf("i" to 41.0))
        assertEquals(41, a.i)
    }

    @Test
    fun LoadDoubleFromIntTest()
    {
        val a = A(loadFrom = mapOf("d" to 41))
        assertEquals(41.0, a.d)
    }

    @Test
    fun LoadBadTypeTest()
    {
        val e = assertThrows<IllegalArgumentException> { A(loadFrom = mapOf("i" to "string")) }
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

    @Test
    fun ModifyOutsideTransaction()
    {
        val a = A()
        val e = assertThrows<IllegalStateException> { a.name = "someName" }
        assertEquals("Attempt to modify property outside of transaction", e.message)
    }

    @Test
    fun ChangeImmutableProperty()
    {
        val a = A()
        val e = assertThrows<IllegalStateException> { a.state.Mutate(mapOf("s" to "someString")) }
        assertEquals("Attempting to change immutable property: 's'", e.message)
    }

    @Test
    fun IdRedefinition()
    {
        class C {
            val state = ManagedState()
            val id by state.Id(0)
            val id2 by state.Id(42)
        }

        val e = assertThrows<IllegalStateException> { C() }
        assertEquals("ID already specified by property 'id', redefining by 'id2'", e.message)
    }

    @Test
    fun TransactionTest()
    {
        val a = A()

        a.state.Mutate {
            a.name = "myName"
            assertEquals("myName", a.name)
        }
        assertEquals("myName", a.name)
    }

    @Test
    fun TransactionTestAsyncWithSyncHandler()
    {
        val a = A()

        Deferred.ForFunc {
            a.state.MutateAsync {
                a.name = "myName"
                assertEquals("myName", a.name)
            }
        }.Get()
        assertEquals("myName", a.name)
    }

    @Test
    fun TransactionFail()
    {
        val a = A(loadFrom = mapOf("name" to "aaa"))

        assertThrows<Error> {
            a.state.Mutate {
                a.name = "myName"
                assertEquals("myName", a.name)
                throw Error("in mutation")
            }
        }
        assertEquals("aaa", a.name)
    }

    @Test
    fun TransactionFailOnValidation()
    {
        val a = A(loadFrom = mapOf("name" to "aaa"))

        a.state.Mutate {
            a.even = 42
        }
        assertEquals(42, a.even)

        val e = assertThrows<ManagedState.ValidationError> {
            a.state.Mutate {
                a.even = 3
                assertEquals(3, a.even)
            }
        }
        assertEquals("Property 'even' validation error: Should be even", e.message)
        assertEquals(42, a.even)
    }

    @Test
    fun MutateEnumByString()
    {
        val a = A()

        assertEquals(A.Type.T1, a.type)
        a.state.Mutate {
            a.type = A.Type.T2
            assertEquals(A.Type.T2, a.type)
        }
        assertEquals(A.Type.T2, a.type)

        a.state.Mutate(mapOf("type" to A.Type.T3))
        assertEquals(A.Type.T3, a.type)

        a.state.Mutate(mapOf("type" to "T4"))
        assertEquals(A.Type.T4, a.type)

        val e = assertThrows<IllegalArgumentException> {
            a.state.Mutate(mapOf("type" to "INVALID"))
        }
        assertEquals("Failed to convert enum value from string for property 'type'", e.message)
    }

    @Test
    fun MutateIntByDouble()
    {
        val a = A()

        a.state.Mutate(mapOf("i2" to 45.0))
        assertEquals(45, a.i2)
    }

    @Test
    fun MutateDoubleByInt()
    {
        val a = A()

        a.state.Mutate(mapOf("d2" to 45))
        assertEquals(45.0, a.d2)
    }
}
