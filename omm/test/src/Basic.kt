import com.ast.adk.omm.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertNull

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

        var isFinalized = false

        @Suppress("UNUSED_PARAMETER")
        @OmmFinalizer
        fun Finalize(arg: Int = 42)
        {
            isFinalized = true
        }
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
        assertTrue(obj.isFinalized)
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

    class C3 {
        @OmmField(qualifier = "A")
        var s1: String? = null
        @OmmField(qualifier = "B")
        var s2: String? = null
        @OmmField
        var s3: String? = null

        var finalizer: Int = 0
        var commonFinalizer: Int = 0

        @OmmFinalizer(qualifier = "A")
        fun FinalizeA()
        {
            finalizer = 1
        }

        @OmmFinalizer(qualifier = "B")
        fun FinalizeB()
        {
            finalizer = 2
        }

        @OmmFinalizer
        fun FinalizeC()
        {
            commonFinalizer = 1
        }
    }

    @Test
    fun QualifiersTest()
    {
        run {
            val params = OmmParams(qualifier = "A", annotatedOnlyFields = true)
            val clsNode = OmmClassNode<OmmClassNode.OmmFieldNode>(C3::class, params)
            clsNode.Initialize(params, { fp -> OmmClassNode.OmmFieldNode(fp) })

            assertEquals(2, clsNode.fields.size)
            val setter = clsNode.SpawnObject(null)
            setter.Set(clsNode.fields["s1"]!!, "test1")
            setter.Set(clsNode.fields["s3"]!!, "test3")
            val obj = setter.Finalize() as C3

            assertEquals("test1", obj.s1)
            assertNull(obj.s2)
            assertEquals("test3", obj.s3)
            assertEquals(1, obj.finalizer)
            assertEquals(1, obj.commonFinalizer)
        }

        run {
            val params = OmmParams(qualifier = "B", annotatedOnlyFields = true)
            val clsNode = OmmClassNode<OmmClassNode.OmmFieldNode>(C3::class, params)
            clsNode.Initialize(params, { fp -> OmmClassNode.OmmFieldNode(fp) })

            assertEquals(2, clsNode.fields.size)
            val setter = clsNode.SpawnObject(null)
            setter.Set(clsNode.fields["s2"]!!, "test2")
            setter.Set(clsNode.fields["s3"]!!, "test3")
            val obj = setter.Finalize() as C3

            assertNull(obj.s1)
            assertEquals("test2", obj.s2)
            assertEquals("test3", obj.s3)
            assertEquals(2, obj.finalizer)
            assertEquals(1, obj.commonFinalizer)
        }

        run {
            val params = OmmParams(qualifier = "A", annotatedOnlyFields = true,
                                   qualifiedOnly = true)
            val clsNode = OmmClassNode<OmmClassNode.OmmFieldNode>(C3::class, params)
            clsNode.Initialize(params, { fp -> OmmClassNode.OmmFieldNode(fp) })

            assertEquals(1, clsNode.fields.size)
            val setter = clsNode.SpawnObject(null)
            setter.Set(clsNode.fields["s1"]!!, "test1")
            val obj = setter.Finalize() as C3

            assertEquals("test1", obj.s1)
            assertNull(obj.s2)
            assertNull(obj.s3)
            assertEquals(1, obj.finalizer)
            assertEquals(0, obj.commonFinalizer)
        }

        run {
            val params = OmmParams(annotatedOnlyFields = true)
            val clsNode = OmmClassNode<OmmClassNode.OmmFieldNode>(C3::class, params)
            clsNode.Initialize(params, { fp -> OmmClassNode.OmmFieldNode(fp) })

            assertEquals(1, clsNode.fields.size)
            val setter = clsNode.SpawnObject(null)
            setter.Set(clsNode.fields["s3"]!!, "test3")
            val obj = setter.Finalize() as C3

            assertNull(obj.s1)
            assertNull(obj.s2)
            assertEquals("test3", obj.s3)
            assertEquals(0, obj.finalizer)
            assertEquals(1, obj.commonFinalizer)
        }
    }
}
