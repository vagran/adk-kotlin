/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.injector.test

import io.github.vagran.adk.injector.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FactoryTest {

    class A(var i: Int)

    class B
    @Inject constructor(val a1: A, @param:FactoryParam var j: Int) {
        @Inject
        lateinit var a: A
    }

    class C @Inject
    constructor(val a: A, val bFactory: DI.Factory<B>)

    class D {
        var i = 43
    }

    @Module
    class M {
        @Provides
        fun GetA(): A
        {
            return A(42)
        }
    }

    @Component(modules = [M::class])
    class Comp {
        @Inject
        lateinit var a: A
        @Inject
        lateinit var bFactory: DI.Factory<B>
        @Inject
        lateinit var c: C
        @Inject
        lateinit var dFactory: DI.Factory<D>
    }

    @Module
    class M3 {
        @Provides
        fun GetA(@FactoryParam i: Int): A
        {
            return A(i)
        }
    }

    @Component(modules = [M3::class])
    class Comp5 {
        @Inject
        lateinit var a: A
    }

    @Component(modules = [M::class])
    class Comp6 {
        @Inject
        lateinit var b: B
    }

    @Test
    fun Basic()
    {
        val comp = DI.CreateComponent<Comp>()
        assertEquals(42, comp.a.i)

        var b = comp.bFactory.Create(10)
        assertEquals(10, b.j)
        assertEquals(42, b.a.i)
        assertEquals(42, b.a1.i)

        assertEquals(42, comp.c.a.i)
        b = comp.c.bFactory.Create(11)
        assertEquals(11, b.j)
        assertEquals(42, b.a.i)
        assertEquals(42, b.a1.i)

        assertEquals(43, comp.dFactory.Create().i)
    }

    @Test
    fun FactoryParamInProviderFailure()
    {
        val msg = assertThrows<DI.Exception> {
            DI.CreateComponent<Comp5>()
        }.message!!
        assertTrue(msg.startsWith("Factory parameters not allowed in provider method"))
    }

    @Test
    fun FactoryClassInjectFailure()
    {
        val msg = assertThrows<DI.Exception> {
            DI.CreateComponent<Comp6>()
        }.message!!
        assertTrue(msg.startsWith("Direct injection not allowed for factory-produced class"))
    }

    @Test
    fun WrongFactoryParamsLessFailure()
    {
        val msg = assertThrows<DI.Exception> {
            val comp = DI.CreateComponent<Comp>()
            comp.bFactory.Create()
        }.message!!
        assertTrue(msg.startsWith("Insufficient number of factory arguments specified"))
    }

    @Test
    fun WrongFactoryParamsMoreFailure()
    {
        val msg = assertThrows<DI.Exception> {
            val comp = DI.CreateComponent<Comp>()
            comp.bFactory.Create(42, "aaa")
        }.message!!
        assertTrue(msg.startsWith("Too many factory arguments specified"))
    }

    @Test
    fun WrongFactoryParamsFailure()
    {
        val msg = assertThrows<DI.Exception> {
            val comp = DI.CreateComponent<Comp>()
            comp.bFactory.Create("aaa")
        }.cause!!.message!!
        assertTrue(msg.startsWith("argument type mismatch"))
    }
}
