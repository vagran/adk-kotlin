package com.ast.adk.guiutils.propview

import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import java.util.*
import kotlin.reflect.KClass

private typealias FieldSetter<T> = (value: T) -> Unit
private typealias FieldGetter<T> = () -> T

class PropView<T: Any> private constructor(cls: KClass<T>) {

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
            return PropView(cls)
        }
    }

    /** Root node. */
    val root: Parent

    /** Current state of the properties. */
    val props: T
        get() = _props

    /** Update displayed values from the current state. */
    fun Update()
    {
        TODO()
    }

    /** Update displayed value for the item with the specified ID. */
    fun Update(id: Int)
    {
        TODO()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private lateinit var rootCat: Category
    private val _props: T
    private val nodes = TreeMap<Int, Node>()

    private abstract class Node(val id: Int, val name: String, val displayName: String) {
        abstract val isCategory: Boolean
    }

    private class Category(id: Int, name: String, displayName: String):
        Node(id, name, displayName) {

        override val isCategory = true

        val children = ArrayList<Node>()
    }

    private class Field(id: Int, name: String, displayName: String):
        Node(id, name, displayName) {

        override val isCategory = false
    }

    init {
        root = StackPane().also {
            it.children.add(Label("props here"))
        }

        //XXX
    }
}
