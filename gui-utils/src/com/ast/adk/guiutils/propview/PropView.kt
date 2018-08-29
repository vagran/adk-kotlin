package com.ast.adk.guiutils.propview

import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import kotlin.reflect.KClass


class PropView<T> private constructor() {

    companion object {
        /** Create properties view based on mapped model class.
         * If top level title is needed the provided class should have name attribute set in its
         * PropClass annotation.
         */
        inline fun <reified T: Any> Create(): PropView<T>
        {
            return Create(T::class)
        }

        fun <T: Any> Create(cls: KClass<T>): PropView<T>
        {
            //XXX
            return PropView()
        }
    }

    /** Root node. */
    val root: Parent

    /** Current state of the properties. */
    val props: T
        get() {
            TODO()
        }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private lateinit var rootCls: ClassDesc

    init {
        root = StackPane().also {
            it.children.add(Label("props here"))
        }
    }

    private class ClassDesc {

    }

    private class FieldDesc {

    }
}
