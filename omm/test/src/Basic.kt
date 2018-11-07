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
        clsNode.Initialize(params, { fp -> OmmClassNode.OmmFieldNode(fp) })

        val setter = clsNode.SpawnObject(null)
        setter.Set(clsNode.fields["a"]!!, 42)
        setter.Set(clsNode.fields["bb"]!!, 43)
        setter.Set(clsNode.fields["d"]!!, "test1")
        setter.Set(clsNode.fields["e"]!!, "test2")
        val obj = setter.Finalize() as D1

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
        clsNode.Initialize(params, { fp -> OmmClassNode.OmmFieldNode(fp) })

        val outer = C1(42)
        val setter = clsNode.SpawnObject(outer)
        setter.Set(clsNode.fields["s"]!!, "test")
        val obj = setter.Finalize() as C1.Inner

        assertEquals("test", obj.s)
        assertEquals(42, obj.outerI)
    }

    class C2 {
        lateinit var s: String
        lateinit var s2: String
    }

    @Test
    fun StaticClassTest1()
    {
        val params = OmmParams(requireLateinitVars = false)
        val clsNode = OmmClassNode<OmmClassNode.OmmFieldNode>(C2::class, params)
        clsNode.Initialize(params, { fp -> OmmClassNode.OmmFieldNode(fp) })

        val setter = clsNode.SpawnObject(null)
        setter.Set(clsNode.fields["s"]!!, "test")
        val obj = setter.Finalize() as C2

        assertEquals("test", obj.s)
    }
}
