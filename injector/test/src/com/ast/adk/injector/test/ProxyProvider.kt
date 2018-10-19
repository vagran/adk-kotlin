package com.ast.adk.injector.test

import com.ast.adk.injector.*
import com.ast.adk.injector.Inject
import com.ast.adk.injector.Provides
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProxyProvider {

    inner class A(var i: Int)

    class B {
        @Inject
        lateinit var a: A

        var j: Int = 0
    }

    @Module
    inner class M {
        @Provides
        fun GetA(): A
        {
            return A(42)
        }

        @Provides
        fun GetB(b: B): B
        {
            b.j = 10
            return b
        }
    }

    @Component(modules = [M::class])
    class Comp {
        @Inject
        lateinit var b: B
    }

    @Test
    fun ProxyProviderTest()
    {
        val comp = DI.ComponentBuilder<Comp>().WithModule(M()).Build()
        assertEquals(42, comp.b.a.i)
        assertEquals(10, comp.b.j)
    }
}
