package io.github.vagran.adk.injector

import kotlin.reflect.KClass

class DI {
    companion object {
        inline fun <reified T: Any> CreateComponent(): T
        {
            return CreateComponent(T::class)
        }

        fun <T: Any> CreateComponent(cls: KClass<T>): T
        {
            return ComponentBuilder(cls).Build()
        }

        inline fun <reified T: Any> ComponentBuilder(): ComponentBuilder<T>
        {
            return ComponentBuilder(T::class)
        }

        fun <T: Any> ComponentBuilder(cls: KClass<T>): ComponentBuilder<T>
        {
            return io.github.vagran.adk.injector.ComponentBuilder(cls)
        }
    }
}

class DiException(message: String, cause: Throwable? = null): RuntimeException(message, cause)

/** Factory for creating some injectable type T.  */
interface DiFactory<T> {

    /** Create instance of injectable type T.
     *
     * @param params Arguments for injectable constructor which have FactoryParam annotation.
     */
    fun Create(vararg params: Any?): T
}
