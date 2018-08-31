package com.ast.adk.guiutils.propview
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.stage.Stage


class Props {
    @PropItem(order = 1)
    var someString = "initial value"
    @PropItem(displayName = "Some integer", order = 2)
    var someInt = 42
    @PropItem(order = 3)
    var custom = CustomItem()

    lateinit var subProps: SubProps
    @PropItem(displayName = "second subprops")
    val subProps2 = SubProps()

    lateinit var subProps3: OtherSubProps
    @PropItem(flat = true)
    lateinit var subProps4: OtherSubProps
}

@PropClass(displayName = "Sub-properties")
class SubProps {
    var value = 3.14
}

class OtherSubProps: ValidatedProperties {
    var i = 43

    override fun Validate(fieldName: String, value: Any?)
    {
        when (fieldName) {
            "i" -> {
                val i = value as Int
                if (i < 40 || i > 50) {
                    throw ValidationError("Value should be in range 40..50")
                }
            }
        }
    }
}

class CustomItem: CustomPropertyItem {
    var value: Int = 0

    override fun Parse(s: String)
    {
        value = s.toInt(16)
    }

    override fun toString(): String
    {
        return value.toString(16)
    }
}


class GuiApplication: Application() {

    override fun start(stage: Stage)
    {
        this.stage = stage

        val propView = PropView.Create<Props>()

        val root = StackPane()
        root.children.add(propView.root)

        stage.title = "ADK gui-utils"
        stage.scene = Scene(root, 300.0, 250.0)
        stage.show()
    }

    private lateinit var stage: Stage
}

fun main(args: Array<String>)
{
    Application.launch(GuiApplication::class.java)
}
