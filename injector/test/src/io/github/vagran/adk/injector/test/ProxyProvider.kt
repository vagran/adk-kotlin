package io.github.vagran.adk.injector.test

import io.github.vagran.adk.injector.*
import io.github.vagran.adk.injector.Inject
import io.github.vagran.adk.injector.Provides
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProxyProvider {

    inner class A(var i: Int)

    open class B {
        @Inject
        lateinit var a: A

        var j: Int = 0
    }

    class C {
        lateinit var b: B
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

        @Provides
        @Named("42")
        fun GetB42(b: B): B
        {
            b.j = 42
            return b
        }

        @Provides
        @Singleton
        fun GetC(@Named("42") b: B): C
        {
            return C().also { it.b = b }
        }
    }

    @Component(modules = [M::class])
    class Comp {
        @Inject
        lateinit var b: B
        @Inject
        lateinit var c: C
    }

    @Test
    fun ProxyProviderTest()
    {
        val comp = DI.ComponentBuilder<Comp>().WithModule(M()).Build()
        assertEquals(42, comp.b.a.i)
        assertEquals(10, comp.b.j)
        assertEquals(42, comp.c.b.j)
    }
}
