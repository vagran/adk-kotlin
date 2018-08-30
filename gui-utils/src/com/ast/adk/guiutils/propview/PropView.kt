package com.ast.adk.guiutils.propview

import javafx.geometry.HPos
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.layout.*
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation

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
    private val rootCat: Category
    private val _props: T
    private val nodes = TreeMap<Int, Node>()
    private var curId = 1

    private abstract class Node(val id: Int, val name: String, val displayName: String) {
        abstract val isCategory: Boolean
    }

    private class Category(id: Int, name: String, displayName: String):
        Node(id, name, displayName) {

        override val isCategory = true

        val children = ArrayList<Node>()
        lateinit var uiNode: javafx.scene.Node
    }

    private class Field(id: Int, name: String, displayName: String):
        Node(id, name, displayName) {

        override val isCategory = false
    }

    init {
        root = StackPane().also {
            it.alignment = Pos.TOP_CENTER
        }
        _props = cls.createInstance();
        rootCat = CreateCategory("", _props, null, null)
        root.children.add(rootCat.uiNode)
    }

    private fun CreateCategory(name: String, obj: Any, ann: PropItem?, parentGrid: GridPane?): Category
    {
        val cls = obj::class
        val clsAnn = cls.findAnnotation<PropClass>()
        val displayName = run {
            if (ann != null && !ann.displayName.isEmpty()) {
                return@run ann.displayName
            }
            if (clsAnn != null && !clsAnn.displayName.isEmpty()) {
                return@run clsAnn.displayName
            }
            return@run name
        }
        val cat = Category(curId++, name, displayName)
        val isFlat = ann != null && ann.flat

        val catContainer = run {
            if (name.isEmpty()) {
                if (clsAnn != null && !clsAnn.displayName.isEmpty()) {
                    TitledPane().also {
                        it.text = clsAnn.displayName
                    }
                } else {
                    null
                }
            } else if (isFlat) {
                null
            } else {
                TitledPane().also {
                    it.text = displayName
                }
            }
        }

        val grid = parentGrid ?: run {
            val grid = GridPane()
            grid.columnConstraints.addAll(
                ColumnConstraints(50.0, Region.USE_COMPUTED_SIZE, Double.MAX_VALUE,
                                  Priority.SOMETIMES, HPos.LEFT, true),
                ColumnConstraints(50.0, Region.USE_COMPUTED_SIZE, Double.MAX_VALUE,
                                  Priority.SOMETIMES, HPos.LEFT, true))
            grid.add(Label("aaa"), 0, 0)//XXX
            grid.add(Label("bbb"), 1, 0)//XXX
            return@run grid
        }

        var curRow = grid.rowCount


        if (catContainer != null) {
            catContainer.content = grid
            cat.uiNode = catContainer
        } else {
            cat.uiNode = grid
        }

        return cat
    }
}
