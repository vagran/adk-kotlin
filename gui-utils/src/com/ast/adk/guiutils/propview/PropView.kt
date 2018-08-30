package com.ast.adk.guiutils.propview

import javafx.geometry.HPos
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.layout.*
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.jvmErasure

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

    private class PropPath(val components: Array<String>) {
        companion object {
            val EMPTY = PropPath(emptyArray())

            fun Create(vararg components: String): PropPath
            {
                return PropPath(arrayOf(*components))
            }

            fun Parse(s: String): PropPath
            {
                return PropPath(s.split('.').toTypedArray())
            }
        }

        fun Append(component: String): PropPath
        {
            return PropPath(components + component)
        }

        fun Append(other: PropPath): PropPath
        {
            return PropPath(components + other.components)
        }

        override fun toString(): String
        {
            return components.joinToString(".")
        }
    }

    private abstract class Node(val id: Int, val name: String, val displayName: String) {
        abstract val isCategory: Boolean
    }

    private class Category(id: Int, name: String, displayName: String):
        Node(id, name, displayName) {

        override val isCategory = true

        val children = ArrayList<Node>()
        lateinit var uiNode: javafx.scene.Node
    }

    private class Item(id: Int, name: String, displayName: String):
        Node(id, name, displayName) {

        override val isCategory = false
    }

    init {
        root = StackPane().also {
            it.alignment = Pos.TOP_CENTER
        }
        _props = cls.createInstance();
        rootCat = CreateCategory("", _props, PropPath.EMPTY, null, null)
        root.children.add(rootCat.uiNode)
    }

    @Suppress("UNCHECKED_CAST")
    private fun CreateCategory(name: String,
                               obj: Any,
                               prefixPath: PropPath,
                               ann: PropItem?,
                               parentGrid: GridPane?): Category
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

        for (prop in cls.declaredMemberProperties) {
            if (prop.visibility != KVisibility.PUBLIC) {
                continue
            }
            val item = CreateItem(prop, obj)
            if (item != null) {
                //XXX
                continue
            }
            /* Treat as category. */
            var catObj = if (prop.isLateinit) {
                null
            } else {
                (prop as KProperty1<Any, Any?>).get(obj)
            }
            if (catObj == null) {
                if (prop !is KMutableProperty1) {
                    continue
                }
                try {
                    catObj = prop.returnType.jvmErasure.createInstance()
                    (prop as KMutableProperty1<Any, Any?>).set(obj, catObj)
                } catch (e: Throwable) {
                    throw Error(
                        "Failed to initialize category field ${prefixPath.Append(prop.name)}", e)
                }
            }
            val catAnn = prop.findAnnotation<PropItem>()
            val isCatFlat = catAnn != null && catAnn.flat
            val childCat = CreateCategory(prop.name, catObj, prefixPath.Append(prop.name),
                                          catAnn, if (isCatFlat) grid else null)
            cat.children.add(childCat)
            grid.add(childCat.uiNode, 0, curRow++, 2, 1)
        }

        if (catContainer != null) {
            catContainer.content = grid
            cat.uiNode = catContainer
        } else {
            cat.uiNode = grid
        }

        return cat
    }

    /** Create item based on the provided property if applicable. Returns null if the property is
     * not suitable for item creation.
     */
    private fun CreateItem(prop: KProperty1<*, *>, container: Any): Item?
    {
        prop.returnType.jvmErasure

        return null
    }
}
