/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.injector.test

import io.github.vagran.adk.injector.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttributesTest {

    @Attribute
    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class IntAttr(val i: Int)

    class A(val i: Int)

    class B @Inject constructor(attrs: DI.Attributes) {
        val i: Int = attrs.Get<IntAttr>()!!.i
    }

    class C {
        @Inject
        lateinit var attrs: DI.Attributes
    }

    class D @Inject constructor(attrs: DI.Attributes) {
        val i: Int = attrs.Get<IntAttr>()!!.i
        @Inject
        lateinit var e: E
    }

    class E @Inject constructor(attrs: DI.Attributes) {
        init {
            assertTrue(attrs.list.isEmpty())
        }
    }

    @Module
    class M {
        @Provides
        fun GetA(attrs: DI.Attributes): A
        {
            return A(attrs.Get<IntAttr>()!!.i)
        }

        @Provides
        fun GetD(@IntAttr(77) d: D): D
        {
            return d
        }
    }

    @Component(modules = [M::class])
    class Comp @Inject constructor(@param:IntAttr(55) val b: B) {
        @Inject
        @IntAttr(42)
        lateinit var a: A

        @Inject
        @IntAttr(66)
        lateinit var c: C

        @Inject
        lateinit var d: D
    }

    @Test
    fun Test()
    {
        val comp = DI.CreateComponent<Comp>()
        Assertions.assertEquals(42, comp.a.i)
        Assertions.assertEquals(55, comp.b.i)
        Assertions.assertEquals(66, comp.c.attrs.Get<IntAttr>()!!.i)
        Assertions.assertEquals(77, comp.d.i)
    }
}
