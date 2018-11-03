import com.ast.adk.omm.OmmClassNode
import com.ast.adk.omm.OmmField
import com.ast.adk.omm.OmmIgnore
import com.ast.adk.omm.OmmParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class BasicTest {

    data class D1(
        val a: Int,
        @OmmField(name = "bb")
        val b: Int,
        val c: String = "abc",
        val d: String = "def"
    ) {
        lateinit var e: String
        @OmmIgnore
        lateinit var f: String
    }

    @Test
    fun DataClassTest1()
    {
        val params = OmmParams()
        val clsNode = OmmClassNode<OmmClassNode.OmmFieldNode>(D1::class, params)
        clsNode.Initialize(params) { fp -> OmmClassNode.OmmFieldNode(fp) }

        val args = clsNode.DataClassArguments()
        args.Add(clsNode.fields["a"]!!, 42)
        args.Add(clsNode.fields["bb"]!!, 43)
        args.Add(clsNode.fields["d"]!!, "test1")
        args.Add(clsNode.fields["e"]!!, "test2")
        val obj = args.Construct() as D1

        assertEquals(42, obj.a)
        assertEquals(43, obj.b)
        assertEquals("abc", obj.c)
        assertEquals("test1", obj.d)
        assertEquals("test2", obj.e)
        assertFalse(clsNode.fields.containsKey("f"))
    }

    class C1(var i: Int) {
        inner class Inner {
            lateinit var s: String

            val outerI get() = i
        }
    }

    @Test
    fun InnerClassTest1()
    {
        val params = OmmParams(allowInnerClasses = true)
        val clsNode = OmmClassNode<OmmClassNode.OmmFieldNode>(C1.Inner::class, params)
        clsNode.Initialize(params) { fp -> OmmClassNode.OmmFieldNode(fp) }

        val outer = C1(42)
        val obj = clsNode.defCtr!!.Construct(outer) as C1.Inner
        clsNode.fields["s"]!!.setter!!.invoke(obj, "test")

        assertEquals("test", obj.s)
        assertEquals(42, obj.outerI)
    }
}
