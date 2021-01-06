/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.injector.test

import io.github.vagran.adk.injector.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class Basic {

    @Qualifier
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Q_A

    @Qualifier
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Q_B(val value: Int)

    interface I1 {
        fun GetInt(): Int
    }

    class A(val s: String) {

        fun GetString(): String
        {
            return s
        }
    }

    class B(val i: Int): I1 {

        override fun GetInt(): Int
        {
            return i
        }
    }

    class C @Inject
    constructor(var i1c: I1, var ac: A, @param:Q_A var aac: A, @param:Q_B(3) var ab3c: A) {
        @Inject
        lateinit var a1: A
        @Inject
        lateinit var a2: A
        @Inject
        lateinit var i1: I1
        @Inject
        lateinit var i2: I1

        @Inject
        @Q_A
        lateinit var a: A
        @Inject
        @Q_B(1)
        lateinit var b1: A
        @Inject
        @Q_B(2)
        lateinit var b2: A
        var i3: I1? = null
        var b4: A? = null

        fun GetInt1(): Int
        {
            return i1.GetInt()
        }

        fun GetInt2(): Int
        {
            return i2.GetInt()
        }

        fun GetString1(): String
        {
            return a1.GetString()
        }

        fun GetString2(): String
        {
            return a2.GetString()
        }
    }

    @Module
    class M1(var i: Int) {

        @Provides
        @Singleton
        fun GetI1(): I1
        {
            return B(i++)
        }

        @Provides
        @Named("qualified")
        fun GetC(c: C, i1: I1, @Q_B(4) b4: A): C
        {
            c.i3 = i1
            c.b4 = b4
            return c
        }
    }

    @Module
    class M2 {
        var i: Int = 0

        @Provides
        fun GetA(): A
        {
            return A("A" + Integer.toString(i++))
        }

        @Provides
        @Q_A
        fun GetA_A(): A
        {
            return A("A")
        }

        @Provides
        @Q_B(1)
        fun GetA_B1(): A
        {
            return A("B1")
        }

        @Provides
        @Q_B(2)
        fun GetA_B2(): A
        {
            return A("B2")
        }

        @Provides
        @Q_B(3)
        fun GetA_B3(): A
        {
            return A("B3")
        }

        @Provides
        @Q_B(4)
        val a_B4: A get() = A("B4")
    }

    @Component(modules = [M1::class, M2::class])
    class Comp {
        @Inject
        private lateinit var c: C
        @Inject
        @Named("qualified")
        private lateinit var c2: C

        fun GetC(): C = c

        fun GetC2(): C = c2
    }

    @Test
    fun BasicGraph() {
        val comp = DI.ComponentBuilder<Comp>().WithModule(M1(42)).Build()
        assertEquals(42, comp.GetC().GetInt1())
        assertEquals(42, comp.GetC().GetInt2())
        assertEquals("A1", comp.GetC().GetString1())
        assertEquals("A2", comp.GetC().GetString2())
        assertEquals("A", comp.GetC().a.GetString())
        assertEquals("B1", comp.GetC().b1.GetString())
        assertEquals("B2", comp.GetC().b2.GetString())

        assertEquals(42, comp.GetC().i1c.GetInt())
        assertEquals("A0", comp.GetC().ac.GetString())
        assertEquals("A", comp.GetC().aac.GetString())
        assertEquals("B3", comp.GetC().ab3c.GetString())

        assertNull(comp.GetC().i3)
        assertNull(comp.GetC().b4)

        assertEquals(42, comp.GetC2().i3!!.GetInt())
        assertEquals("B4", comp.GetC2().b4!!.GetString())
    }

    @Test
    fun NonDefaultModuleConstructorFailure()
    {
        val msg = assertThrows<DI.Exception> {
            DI.CreateComponent<Comp>()
        }.message
        assertEquals("Module default constructor not found: io.github.vagran.adk.injector.test.Basic.M1", msg)
    }

    @Test
    fun NonComponentConstructionFailure()
    {
        val msg = assertThrows<DI.Exception> {
            DI.CreateComponent<M1>()
        }.message
        assertEquals(
            "Root component class is not annotated with @Component: io.github.vagran.adk.injector.test.Basic.M1",
            msg)
    }

    @Test
    fun NotAnnotatedModuleFailure()
    {
        val msg = assertThrows<DI.Exception> {
            DI.ComponentBuilder<Comp>().WithModule(M1(42)).OverrideModule(B(0)).Build()
        }.message
        assertEquals(
            "Module class is not annotated with @Module: io.github.vagran.adk.injector.test.Basic.B",
            msg)
    }
}
