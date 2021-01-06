/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.injector.test

import io.github.vagran.adk.injector.Component
import io.github.vagran.adk.injector.DI
import io.github.vagran.adk.injector.Inject
import io.github.vagran.adk.injector.Singleton
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CircularDependency {

    class A {
        @Inject
        lateinit var b: B
    }

    class B {
        @Inject
        var a: A? = null
    }

    @Component
    class Comp {
        @Inject
        var a: A? = null
    }

    @Test
    fun ResolvedQualifier() {
        val msg = assertThrows<DI.Exception> {
            DI.CreateComponent(Comp::class)
        }.message!!
        assertTrue(msg.startsWith("Circular dependency detected"))
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FactoryCircularDependency {

    @Singleton
    class A {
        @Inject
        lateinit var bFactory: DI.Factory<B>
    }

    @Singleton
    class B {
        @Inject
        lateinit var a: A
    }

    @Component
    class Comp {
        @Inject
        lateinit var a: A
    }

    @Test
    fun Run()
    {
        val comp = DI.CreateComponent<Comp>()
        val b = comp.a.bFactory.Create()
        assertTrue(b.a === comp.a)
    }
}
