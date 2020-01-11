package io.github.vagran.adk.injector.test

import io.github.vagran.adk.injector.Component
import io.github.vagran.adk.injector.DI
import io.github.vagran.adk.injector.DiException
import io.github.vagran.adk.injector.Inject
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
        val msg = assertThrows<DiException> {
            DI.CreateComponent(Comp::class)
        }.message!!
        assertTrue(msg.startsWith("Circular dependency detected"))
    }
}
