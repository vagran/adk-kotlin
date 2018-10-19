package com.ast.adk.injector

import kotlin.reflect.KClass

class DI {
    companion object {
        fun <T: Any> CreateComponent(cls: KClass<T>): T
        {
            return ComponentBuilder(cls).Build()
        }

        fun <T: Any> ComponentBuilder(cls: KClass<T>): ComponentBuilder<T>
        {
            return ComponentBuilder(cls)
        }
    }
}

class DiException: RuntimeException {
    constructor(message: String, cause: Throwable): super(message, cause)
    constructor(message: String): super(message)
    constructor(cause: Throwable): super(cause)
}

/** Factory for creating some injectable type T.  */
interface DiFactory<T> {

    /** Create instance of injectable type T.
     *
     * @param params Arguments for injectable constructor which have FactoryParam annotation.
     */
    fun Create(vararg params: Any?): T
}
