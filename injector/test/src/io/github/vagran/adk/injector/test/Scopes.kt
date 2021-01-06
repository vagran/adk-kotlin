/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.injector.test

import io.github.vagran.adk.injector.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Scopes {

    class A

    @Singleton
    class B

    class C(val i: Int)

    class Scope(val i: Int): DI.Scope()

    class BadScope: DI.Scope()

    class D {
        @Inject
        var scope: Scope? = null
    }

    @Module
    class M {
        @Singleton(perScope = true)
        @Provides
        fun GetC(scope: Scope?): C
        {
            return C(scope?.i ?: 0)
        }
    }

    class E {
        @Inject
        lateinit var b: B
        @Inject
        lateinit var bFactory: DI.Factory<B>

        @Inject
        lateinit var c: C
        @Inject
        lateinit var cFactory: DI.Factory<C>
    }

    @Component(modules = [M::class])
    class Comp {
        @Inject
        lateinit var a: A
        @Inject
        lateinit var b: B
        @Inject
        lateinit var c: C
        @Inject
        lateinit var d: D
        @Inject
        lateinit var e: E

        @Inject
        lateinit var cFactory: DI.Factory<C>
        @Inject
        lateinit var eFactory: DI.Factory<E>
    }

    @Test
    fun TestNoScope()
    {
        val comp = DI.CreateComponent<Comp>()
        assertEquals(0, comp.c.i)
        assertNull(comp.d.scope)

        assertTrue(comp.b === comp.e.b)
        val bGlobal = comp.e.bFactory.Create()
        assertTrue(comp.b === bGlobal)

        val bScoped = comp.e.bFactory.CreateScoped(Scope(1))
        assertTrue(comp.b === bScoped)

        assertTrue(comp.c === comp.e.c)
        val cGlobal = comp.e.cFactory.Create()
        assertTrue(comp.c === cGlobal)

        val scope1 = Scope(42)
        val scope2 = Scope(55)

        val e1 = comp.eFactory.CreateScoped(scope1)
        val e2 = comp.eFactory.CreateScoped(scope2)

        assertTrue(comp.b === e1.b)
        assertTrue(comp.b === e2.b)
        assertEquals(42, e1.c.i)
        assertEquals(55, e2.c.i)

        assertTrue(e1.c === e1.cFactory.Create())
        assertTrue(e2.c === e2.cFactory.Create())
        assertTrue(e1.c === e2.cFactory.CreateScoped(scope1))
        assertTrue(e2.c === e1.cFactory.CreateScoped(scope2))
    }

    @Test
    fun TestScoped()
    {
        val comp = DI.ComponentBuilder<Comp>().WithScope(Scope(42)).Build()
        assertEquals(42, comp.c.i)
        assertEquals(42, comp.d.scope!!.i)
        assertTrue(comp.c === comp.cFactory.Create())
        assertEquals(42, comp.e.c.i)
        assertTrue(comp.c === comp.e.c)
    }

    @Test
    fun TestBadScope()
    {
        val msg = assertThrows<DI.Exception> {
            DI.ComponentBuilder<Comp>().WithScope(BadScope()).Build()
        }.message
        assertEquals("Failed to set injectable field: c", msg)
    }
}
