package io.github.vagran.adk.injector.test

import io.github.vagran.adk.injector.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Qualifiers {

    interface I {
        fun GetInt(): Int

        fun SetInt(i: Int)
    }

    class A(var i: Int): I {

        override fun GetInt(): Int
        {
            return i
        }

        override fun SetInt(i: Int)
        {
            this.i = i
        }
    }

    class B {
        @Inject
        @Named("NotDefined")
        lateinit var i: I
    }

    @Module
    class M {
        @Provides
        fun GetI(): I
        {
            return A(42)
        }
    }

    @Module(include = [M3::class])
    class M_ {
        @Provides
        fun GetI(): I
        {
            return A(42)
        }
    }

    @Module
    class M2 {
        @Provides
        fun GetI(): I
        {
            return A(42)
        }

        @Provides
        @Named("NotDefined")
        fun GetIQ(i: I): I
        {
            assertEquals(42, i.GetInt())
            i.SetInt(10)
            return i
        }
    }

    @Module
    class M3 {
        @Provides
        @Named("NotDefined")
        fun GetIQ(i: I): I
        {
            assertEquals(42, i.GetInt())
            i.SetInt(10)
            return i
        }
    }

    @Component(modules = [M::class])
    class Comp {
        @Inject
        lateinit var b: B
    }

    @Component(modules = [M2::class])
    class Comp2 {
        @Inject
        lateinit var b: B
    }

    @Component(modules = [M::class, M3::class])
    class Comp3 {
        @Inject
        lateinit var b: B
    }

    @Component(modules = [M2::class, M3::class])
    class Comp4 {
        @Inject
        lateinit var b: B
    }

    @Component(modules = [M_::class])
    class Comp5 {
        @Inject
        lateinit var b: B
    }

    @Test
    fun UnresolvedQualifierFailure()
    {
        val msg = assertThrows<DiException> {
            DI.CreateComponent<Comp>()
        }.message!!
        assertTrue(msg.startsWith("Unresolved qualified injection"))
    }

    @Test
    fun ResolvedQualifier()
    {
        val comp = DI.CreateComponent<Comp2>()
        assertEquals(10, comp.b.i.GetInt())
    }

    @Test
    fun ResolvedQualifierOverride()
    {
        val comp = DI.ComponentBuilder<Comp>().OverrideModule(M2()).Build()
        assertEquals(10, comp.b.i.GetInt())
    }

    @Test
    fun ResolvedQualifierAdditionalModule()
    {
        val comp = DI.CreateComponent<Comp3>()
        assertEquals(10, comp.b.i.GetInt())
    }

    @Test
    fun ResolvedQualifierIncludedModule()
    {
        val comp = DI.CreateComponent<Comp5>()
        assertEquals(10, comp.b.i.GetInt())
    }

    @Test
    fun DuplicatedProviderFailure()
    {
        val msg = assertThrows<DiException> {
            DI.CreateComponent<Comp4>()
        }.message!!
        assertTrue(msg.startsWith("Duplicated provider"))
    }

    @Test
    fun UndeclaredModuleFailure()
    {
        val msg = assertThrows<DiException> {
            DI.ComponentBuilder<Comp>().WithModule(M2()).Build()
        }.message!!
        assertTrue(msg.startsWith("Specified module instance not declared in component modules"))
    }
}
