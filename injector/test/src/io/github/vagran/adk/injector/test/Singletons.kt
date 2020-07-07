/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.injector.test

import io.github.vagran.adk.injector.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Singletons {

    class A

    @Singleton
    class B

    class C

    class D @Inject constructor(@param:FactoryParam val i: Int)

    @Module
    class M {
        @Singleton
        @Provides
        fun GetC(): C
        {
            return C()
        }

        @Singleton
        @Provides
        fun GetD(factory: DI.Factory<D>): D
        {
            return factory.Create(42)
        }
    }

    @Component(modules = [M::class])
    class Comp {
        @Inject
        lateinit var a1: A
        @Inject
        lateinit var a2: A
        @Inject
        lateinit var b1: B
        @Inject
        lateinit var b2: B
        @Inject
        lateinit var c1: C
        @Inject
        lateinit var c2: C
        @Inject
        lateinit var d1: D
        @Inject
        lateinit var d2: D
    }

    @Test
    fun Test()
    {
        val comp = DI.CreateComponent<Comp>()
        assertTrue(comp.a1 !== comp.a2)
        assertTrue(comp.b1 === comp.b2)
        assertTrue(comp.c1 === comp.c2)
        assertTrue(comp.d1 === comp.d2)
    }
}
