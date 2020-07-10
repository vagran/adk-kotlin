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

    class E(val i: Int)

    @AdditionalRefs([H::class])
    class F {
        var i: Int = 0
        @Inject
        lateinit var graph: DI.Graph
    }

    class G(val i: Int)

    class H @Inject constructor(@param:FactoryParam val i: Int)

    @Module
    class M {
        @Provides
        fun GetA(): A
        {
            return A(42)
        }

        @Provides
        fun GetE(): E
        {
            return E(55)
        }

        @Provides
        fun GetF(f: F): F
        {
            f.i = 66
            return f
        }

        @Provides
        fun GetG(@FactoryParam i: Int): G
        {
            return G(i)
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
        @Inject
        lateinit var eFactory: DI.Factory<E>
        @Inject
        lateinit var fFactory: DI.Factory<F>
        @Inject
        lateinit var gFactory: DI.Factory<G>
    }

    @Module
    class M4 {
        @Provides
        fun GetA(): A
        {
            return A(42)
        }

        @Provides
        fun GetB(b: B): B
        {
            return b
        }
    }

    @Component(modules = [M::class])
    class Comp6 {
        @Inject
        lateinit var b: B
    }

    @Component(modules = [M4::class])
    class Comp7 {
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

        val e = comp.eFactory.Create()
        assertEquals(55, e.i)

        val f = comp.fFactory.Create()
        assertEquals(66, f.i)

        val g = comp.gFactory.Create(77)
        assertEquals(77, g.i)

        assertEquals(42, f.graph.Create<A>().i)
        assertEquals(88, f.graph.Create<G>(88).i)
        assertEquals(99, f.graph.Create<H>(99).i)
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
    fun FactoryClassInjectProxyFailure()
    {
        val msg = assertThrows<DI.Exception> {
            DI.CreateComponent<Comp7>()
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
