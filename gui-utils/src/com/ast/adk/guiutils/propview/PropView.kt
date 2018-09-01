package com.ast.adk.guiutils.propview

import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

private typealias ValueSetter<T> = (value: T) -> Unit
private typealias ValueGetter<T> = () -> T

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
        for (item in items) {
            item.Update(true)
        }
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
    private val items = ArrayList<Item>()
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

    @Suppress("LeakingThis")
    private abstract inner class Node(val id: Int, val path: PropPath, val displayName: String) {
        abstract val isCategory: Boolean
        val name get() = path.components.lastOrNull() ?: ""

        init {
            nodes[id] = this
            if (this is Item) {
                items.add(this)
            }
        }
    }

    private inner class Category(id: Int, path: PropPath, displayName: String):
        Node(id, path, displayName) {

        override val isCategory = true

        val children = ArrayList<Node>()
        lateinit var uiNode: javafx.scene.Node
    }

    private inner class Item(id: Int, path: PropPath, displayName: String):
        Node(id, path, displayName) {

        override val isCategory = false
        lateinit var fieldGetter: ValueGetter<Any?>
        var fieldSetter: ValueSetter<Any?>? = null
        lateinit var displayGetter: ValueGetter<Any?>
        lateinit var displaySetter: ValueSetter<Any?>
        lateinit var uiNode: javafx.scene.Node
        var lastValue: Any? = null

        fun Update(objToDisplay: Boolean)
        {
            if (objToDisplay) {
                lastValue = fieldGetter()
                displaySetter(lastValue)
            } else {
                fieldSetter?.invoke(displayGetter())
            }
        }
    }

    init {
        root = ScrollPane().also {
            it.minWidth = Region.USE_PREF_SIZE
            it.minHeight = 150.0
            it.isFitToWidth = true
        }
        _props = cls.createInstance()
        rootCat = CreateCategory(PropPath.EMPTY, _props, null, null)
        root.content = rootCat.uiNode
        Update()
    }

    private fun CreateCategory(path: PropPath,
                               obj: Any,
                               ann: PropItem?,
                               parentGrid: GridPane?): Category
    {
        val cls = obj::class
        val clsAnn = cls.findAnnotation<PropClass>()
        val name = path.components.lastOrNull() ?: ""
        val displayName = run {
            if (ann != null && !ann.displayName.isEmpty()) {
                return@run ann.displayName
            }
            if (clsAnn != null && !clsAnn.displayName.isEmpty()) {
                return@run clsAnn.displayName
            }
            return@run name
        }

        val cat = Category(curId++, path, displayName)

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
            grid.hgap = 2.0
            grid.vgap = 2.0
            grid.padding = Insets(3.0)
            return@run grid
        }

        var curRow = grid.rowCount

        for (prop in GetClassProperties(cls)) {
            if (prop.visibility != KVisibility.PUBLIC) {
                continue
            }
            val item = CreateItem(path.Append(prop.name), prop, obj)
            if (item != null) {
                grid.add(Label(item.displayName), 0, curRow)
                grid.add(item.uiNode, 1, curRow)
                curRow++
                continue
            }
            /* Treat as category. */
            var catObj = if (prop.isLateinit) {
                null
            } else {
                prop.get(obj)
            }
            if (catObj == null) {
                if (prop !is KMutableProperty1) {
                    continue
                }
                try {
                    catObj = prop.returnType.jvmErasure.createInstance()
                    prop.set(obj, catObj)
                } catch (e: Throwable) {
                    throw Error(
                        "Failed to initialize category field $path", e)
                }
            }
            val catAnn = prop.findAnnotation<PropItem>()
            val isCatFlat = catAnn != null && catAnn.flat
            val childCat = CreateCategory(path.Append(prop.name), catObj, catAnn,
                                          if (isCatFlat) grid else null)
            cat.children.add(childCat)
            if (!isCatFlat) {
                grid.add(childCat.uiNode, 0, curRow++, 2, 1)
            }
        }

        if (catContainer != null) {
            catContainer.content = grid
            cat.uiNode = catContainer
        } else {
            cat.uiNode = grid
        }

        return cat
    }

    /* Get list of properties in enumeration order with optional order override via annotation. */
    @Suppress("UNCHECKED_CAST")
    private fun GetClassProperties(cls: KClass<*>): List<KProperty1<Any, Any?>>
    {
        class Entry(val order: Int, val prop: KProperty1<Any, Any?>)

        val list = ArrayList<Entry>()
        for (prop in cls.declaredMemberProperties) {
            if (prop.visibility != KVisibility.PUBLIC) {
                continue
            }
            val ann = prop.findAnnotation<PropItem>()
            val order = ann?.order ?: -1
            list.add(Entry(order, prop as KProperty1<Any, Any?>))
        }
        val array = list.toTypedArray()

        while (true) {
            var moved = false
            for (i in 0 until array.size) {
                val e1 = array[i]
                if (e1.order == -1) {
                    continue
                }
                for (j in i + 1 until array.size) {
                    val e2 = array[j]
                    if (e2.order == -1 || e2.order >= e1.order) {
                        continue
                    }
                    System.arraycopy(array, i, array, i + 1, j - i)
                    array[i] = e2
                    moved = true
                    break
                }
            }
            if (!moved) {
                break
            }
        }
        return array.map { it.prop }
    }

    /** Create item based on the provided property if applicable. Returns null if the property is
     * not suitable for item creation.
     */
    private fun CreateItem(path: PropPath, prop: KProperty1<Any, Any?>, container: Any): Item?
    {
        val cls = prop.returnType.jvmErasure
        val ann = prop.findAnnotation<PropItem>()
        val displayName = run {
            if (ann != null && !ann.displayName.isEmpty()) {
                return@run ann.displayName
            }
            return@run prop.name
        }
        val isReadonly = ann?.readonly ?: true

        if (cls.isSubclassOf(CustomPropertyItem::class)) {
            val item = Item(curId++, path, displayName)
            val instance = prop.get(container) as CustomPropertyItem
            item.fieldGetter = { instance.toString() }
            if (!isReadonly) {
                item.fieldSetter = { instance.Parse(it as String) }
            }
            item.uiNode = TextField().also {
                textField ->
                item.displayGetter = { textField.text }
                item.displaySetter = { textField.text = it as String }
                if (!isReadonly) {
                    textField.focusedProperty().addListener {
                        _, _, isFocused ->
                        if (!isFocused) {
                            OnItemChanged(item)
                        }
                    }
                }
            }
            return item
        }

        run {
            val converter: (String) -> Any?
            when (cls) {
                Int::class -> {
                    converter = { it.toInt() }
                }
                Long::class -> {
                    converter = { it.toLong() }
                }
                Float::class -> {
                    converter = { it.toFloat() }
                }
                Double::class -> {
                    converter = { it.toDouble() }
                }
                String::class -> {
                    converter = { it }
                }
                else -> return@run
            }

            val item = Item(curId++, path, displayName)

            item.fieldGetter = { prop.get(container).toString() }

            item.fieldSetter = if (prop is KMutableProperty1 && !isReadonly) {
                { prop.set(container, it) }
            } else {
                null
            }

            item.uiNode = TextField().also {
                textField ->
                if (item.fieldSetter == null) {
                    textField.isDisable = true
                } else {
                    textField.focusedProperty().addListener {
                        _, _, isFocused ->
                        if (!isFocused) {
                            OnItemChanged(item)
                        }
                    }
                }
                item.displayGetter = {
                    try {
                        converter(textField.text)
                    } catch (e: NumberFormatException) {
                        throw Exception("Failed to parse number: ${e.message}", e)
                    }
                }
                item.displaySetter = { textField.text = it.toString() }
            }
            return item
        }

        return null
    }

    private fun Error(msg: String)
    {
        val alert = Alert(Alert.AlertType.ERROR)
        alert.title = "Error"
        alert.headerText = null
        alert.contentText = msg
        alert.showAndWait()
    }

    private fun OnItemChanged(item: Item)
    {
        try {
            item.Update(false)
        } catch (error: Throwable) {
            item.displaySetter(item.lastValue)
            Error("Value change failed: ${item.path}\n${error.message}")
            item.uiNode.requestFocus()
        }
    }
}
