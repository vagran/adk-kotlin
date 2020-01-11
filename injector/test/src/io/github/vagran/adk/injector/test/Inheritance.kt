/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.injector.test

import io.github.vagran.adk.injector.*
import io.github.vagran.adk.injector.Provides
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Inheritance {

    interface I {
        fun GetInt(): Int
    }

    class A(var i: Int): I {

        override fun GetInt(): Int
        {
            return i
        }
    }

    open class B {
        @Inject
        lateinit var i: I
    }

    class C: B()

    @Module
    class M {
        @Provides
        fun GetI(): I
        {
            return A(42)
        }
    }

    @Component(modules = [M::class])
    class Comp
    {
        @Inject
        lateinit var c: C
    }

    @Test
    fun InheritanceTest()
    {
        val comp = DI.CreateComponent<Comp>()
        assertEquals(42, comp.c.i.GetInt())
    }
}
