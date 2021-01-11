/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.domain

import io.github.vagran.adk.async.Deferred
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows


class CommitHandler {
    var data: EntityInfo? = null

    suspend fun Commit(state: EntityInfo)
    {
        println("Commit: $state")
        data = state
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class ManagedStateTest {

    class A(id: String = "someId", loadFrom: EntityInfo? = null,
            commitHandler: EntityCommitHandler? = null):
        EntityBase(ManagedState(loadFrom, commitHandler = commitHandler)) {


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

        inner class B(loadFrom: EntityInfo? = null, parent: ManagedState.ParentRef? = null,
                      s: String = "nested-string"):
            EntityBase(ManagedState(loadFrom, parent,
                                    deleteHandler = object: EntityDeleteHandler<Int> {
                                        override fun Apply(id: Int)
                                        {
                                            bDeleted = true
                                        }

                                        override suspend fun Commit(id: Int)
                                        {
                                            bDeleteCommitted = true
                                        }
                                    })) {

            val s by state.Param(s)
            val i by state.Id(42)
        }

        val b by state.Param { CreateB() }.Factory(this::CreateB)
        var bDeleted = false
        var bDeleteCommitted = false

        fun CreateB(loadFrom: EntityInfo? = null, parent: ManagedState.ParentRef? = null): B
        {
            return B(loadFrom, parent)
        }

        val bList by state.Param { listOf(B(s = "list")) }.ElementFactory(this::CreateB)
        val bMap by state.Param { mapOf("z" to B(s = "map")) }.ElementFactory(this::CreateB)
    }

    fun <T> RunSuspend(block: suspend () -> T): T
    {
        try {
            return Deferred.WaitFunc(block)
        } catch (e: Throwable) {
            e.cause?.also { throw it }
            throw e
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun Basic()
    {

        val a = A("a")

        println(a.state)

        assertEquals("a", a.id)
        assertEquals("string", a.s)
        assertEquals("aaa", a.name)
        assertNull(a.desc)
        assertEquals(A.Type.T1, a.type)
        assertEquals(0, a.i)
        assertEquals(0.0, a.d)
        assertEquals(42, a.i2)
        assertEquals(43.0, a.d2)
        assertEquals("nested-string", a.b.s)
        assertEquals("list", a.bList[0].s)
        assertEquals("map", a.bMap.getValue("z").s)

        val checkInfo0 = {
            info: EntityInfo ->
            assertEquals("a", info["id"])
            assertEquals("aaa", info["name"])
            assertNull(info["desc"])
            assertEquals(A.Type.T1, info["type"])
            assertEquals(0, info["i"])
            assertEquals(0.0, info["d"])
            assertEquals("nested-string", (info["b"] as EntityInfo)["s"])
            assertEquals("list", (info["bList"] as List<EntityInfo>)[0]["s"])
            assertEquals("map", (info["bMap"] as Map<String, EntityInfo>).getValue("z")["s"])
        }

        val checkInfo1 = {
            info: EntityInfo ->
            checkInfo0(info)
            assertEquals(42, info["i2"])
        }

        val checkInfo2 = {
            info: EntityInfo ->
            checkInfo1(info)
            assertEquals(43.0, info["d2"])
        }

        run {
            val info = a.state.GetInfo()
            assertEquals(9, info.size)
            checkInfo0(info)
        }

        run {
            val info = a.state.GetInfo(1)
            assertEquals(10, info.size)
            checkInfo1(info)
        }

        run {
            val info = a.state.GetInfo(2)
            assertEquals(11, info.size)
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
                            "d" to 11.0,
                            "b" to mapOf("s" to "loaded", "i" to 10),
                            "bList" to listOf(
                                mapOf("s" to "item1"),
                                mapOf("s" to "item2")
                            ),
                            "bMap" to mapOf(
                                "a" to mapOf("s" to "itemA"),
                                "b" to mapOf("s" to "itemB")
                            )))

        assertEquals("myId", a.id)
        assertEquals("string", a.s)
        assertEquals("myName", a.name)
        assertEquals("myDesc", a.desc)
        assertEquals(A.Type.T2, a.type)
        assertEquals(41, a.i)
        assertEquals(11.0, a.d)
        assertEquals(42, a.i2)
        assertEquals(43.0, a.d2)
        assertEquals("loaded", a.b.s)
        assertEquals(10, a.b.i)
        assertEquals(2, a.bList.size)
        assertEquals("item1", a.bList[0].s)
        assertEquals("item2", a.bList[1].s)
        assertEquals(2, a.bMap.size)
        assertEquals("itemA", a.bMap.getValue("a").s)
        assertEquals("itemB", a.bMap.getValue("b").s)
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
        assertEquals("Wrong type provided for property 'i': kotlin.String is not subclass of kotlin.Int",
                     e.message)
    }

    @Test
    fun DeleteTest()
    {
        val a = A()
        RunSuspend { a.b.state.Delete() }
        assertTrue(a.bDeleted)
        assertTrue(a.bDeleteCommitted)
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
        class C(loadFrom: EntityInfo) {
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
        val e = assertThrows<IllegalStateException> {
            RunSuspend { a.state.Mutate(mapOf("s" to "someString")) }
        }
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
        val c = CommitHandler()
        val a = A(commitHandler = c::Commit)

        RunSuspend {
            a.state.Mutate {
                a.name = "myName"
                assertEquals("myName", a.name)
            }
        }
        assertEquals("myName", a.name)
        assertEquals("someId", c.data!!["id"])
        assertEquals("myName", c.data!!["name"])
    }

    @Test
    fun NestedTransactionTest()
    {
        val c = CommitHandler()
        val a = A(commitHandler = c::Commit)

        RunSuspend {
            a.state.Mutate {
                a.name = "myName"
                assertEquals("myName", a.name)
                RunSuspend {
                    a.state.Mutate {
                        a.i2 = 100
                    }
                }
            }
        }
        assertEquals("myName", a.name)
        assertEquals("someId", c.data!!["id"])
        assertEquals("myName", c.data!!["name"])
        assertEquals(100, c.data!!["i2"])
    }

    @Test
    fun TransactionFail()
    {
        val a = A(loadFrom = mapOf("name" to "aaa"))

        assertThrows<Error> {
            RunSuspend {
                a.state.Mutate {
                    a.name = "myName"
                    assertEquals("myName", a.name)
                    throw Error("in mutation")
                }
            }
        }
        val e = assertThrows<Error> { a.name }
        assertEquals("Accessing object with invalid state", e.message)
    }
//
//    @Test
//    fun TransactionFailOnValidation()
//    {
//        val a = A(loadFrom = mapOf("name" to "aaa"))
//
//        a.state.Mutate {
//            a.even = 42
//        }
//        assertEquals(42, a.even)
//
//        val e = assertThrows<ManagedState.ValidationError> {
//            a.state.Mutate {
//                a.even = 3
//                assertEquals(3, a.even)
//            }
//        }
//        assertEquals("Property 'even' validation error: Should be even", e.message)
//        assertEquals(42, a.even)
//    }

    @Test
    fun MutateEnumByString()
    {
        val c = CommitHandler()
        val a = A(commitHandler = c::Commit)

        RunSuspend {
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
                RunSuspend {
                    a.state.Mutate(mapOf("type" to "INVALID"))
                }
            }
            assertEquals("Failed to convert enum value from string for property 'type'", e.message)
        }
    }

    @Test
    fun MutateIntByDouble()
    {
        val a = A()

        RunSuspend {
            a.state.Mutate(mapOf("i2" to 45.0))
        }
        assertEquals(45, a.i2)
    }

    @Test
    fun MutateDoubleByInt()
    {
        val a = A()
        RunSuspend {
            a.state.Mutate(mapOf("d2" to 45))
        }
        assertEquals(45.0, a.d2)
    }

    @Test
    fun SharedStateFailure()
    {
        class C1: EntityBase() {
            val i by state(0)
        }
        val c1 = C1()

        class C2 {
            val i by c1.state(0)
        }
        val e = assertThrows<Error> {
            C2()
        }
        assertEquals("Managed object reference mismatch, " +
                     "possibly attempting to share one state between several objects",
                     e.message)

    }
}
