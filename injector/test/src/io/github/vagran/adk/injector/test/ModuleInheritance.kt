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
class ModuleInheritance {

    interface I {
        fun GetInt(): Int
    }

    interface I2 {
        fun GetString(): String
    }

    inner class A: I {
        override fun GetInt(): Int
        {
            return 42
        }
    }

    inner class B: I {
        override fun GetInt(): Int
        {
            return 43
        }
    }

    @Module
    abstract class MBase {
        @Provides
        abstract fun GetI(): I
    }

    @Module
    inner class M_A: MBase() {

        @Provides
        override fun GetI(): I
        {
            return A()
        }

        @Provides
        fun GetI2(): I2
        {
            return object: I2 {
                override fun GetString(): String
                {
                    return "aaa"
                }
            }
        }
    }

    @Module
    inner class M_B: MBase() {

        @Provides
        override fun GetI(): I
        {
            return B()
        }

        @Provides
        fun GetI2(): I2
        {
            return object: I2 {
                override fun GetString(): String
                {
                    return "bbb"
                }
            }
        }
    }

    @Component(modules = [MBase::class])
    class Comp {
        @Inject
        lateinit var i: I

        @Inject
        lateinit var i2: I2
    }

    @Component(modules = [MBase::class])
    class Comp2 {
        @Inject
        lateinit var i: I
    }

    @Test
    fun ModuleInheritanceTest()
    {
        val c1 = DI.ComponentBuilder<Comp>().WithModule(M_A()).Build()
        assertEquals(42, c1.i.GetInt())
        assertEquals("aaa", c1.i2.GetString())
        val c2 = DI.ComponentBuilder<Comp>().WithModule(M_B()).Build()
        assertEquals(43, c2.i.GetInt())
        assertEquals("bbb", c2.i2.GetString())
    }

    @Test
    fun RedundantModuleSubclassError()
    {
        val msg = assertThrows<DiException> {
            DI.ComponentBuilder<Comp>().WithModule(M_A()).WithModule(M_B()).Build()
        }.message!!
        assertTrue(msg.startsWith("Module instance provided twice"))
    }

    @Test
    fun AbstractModuleError()
    {
        val msg = assertThrows<DiException> {
            DI.CreateComponent<Comp>()
        }.message!!
        assertTrue(msg.startsWith("Cannot instantiate abstract module class"))
    }

    inner class M_C: MBase() {
        override fun GetI(): I
        {
            return A()
        }
    }

    @Test
    fun NotAnnotatedModuleError()
    {
        val msg = assertThrows<DiException> {
            DI.ComponentBuilder<Comp2>().WithModule(M_C()).Build()
        }.message!!
        assertTrue(msg.startsWith("Module class is not annotated with @Module"))
    }

    @Module(include = [M_A::class])
    inner class M_D: MBase() {
        override fun GetI(): I
        {
            return A()
        }
    }

    @Test
    fun IncludeModuleError() {
        val msg = assertThrows<DiException> {
            DI.ComponentBuilder<Comp2>().WithModule(M_D()).Build()
        }.message!!
        assertTrue(msg.startsWith("Include not allowed in inherited module instance"))
    }
}
