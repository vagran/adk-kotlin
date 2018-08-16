
import com.ast.adk.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*
import kotlin.reflect.full.createType
import kotlin.reflect.jvm.jvmErasure
import kotlin.test.assertEquals
import kotlin.test.assertNull

data class Custom(val i: Int)

class CustomCodec: JsonCodec<Custom> {
    companion object {
        val codecs = mapOf(Custom::class.createType() to CustomCodec())
    }

    override fun WriteNonNull(obj: Custom, writer: JsonWriter, json: Json)
    {
        writer.BeginObject()
        writer.WriteName("value")
        writer.Write(Integer.toHexString(obj.i))
        writer.EndObject()
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): Custom
    {
        reader.BeginObject()
        val name = reader.ReadName()
        if (name != "value") {
            throw JsonReadError("Value field expected, have $name")
        }
        val value = Integer.parseInt(reader.ReadString(), 16)
        reader.EndObject()
        return Custom(value)
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class BasicTest {

    @Test
    fun CustomWrite()
    {
        val json = Json(true, additionalCodecs = CustomCodec.codecs)
        val value = Custom(42)
        val result = json.ToJson(value)
        assertEquals("""
            {
              "value": "2a"
            }
        """.trimIndent(), result)
    }

    @Test
    fun ListWrite()
    {
        val json = Json(true)
        val result = json.ToJson(listOf("a", "b", "c"))
        assertEquals("""
            [
              "a",
              "b",
              "c"
            ]
        """.trimIndent(), result)
    }

    @Test
    fun IntListWrite()
    {
        val json = Json(true)
        val result = json.ToJson(listOf(1, 2, 3))
        assertEquals("""
            [
              1,
              2,
              3
            ]
        """.trimIndent(), result)
    }

    @Test
    fun LongListWrite()
    {
        val json = Json(true)
        val result = json.ToJson(listOf(1L, 2L, 3L))
        assertEquals("""
            [
              1,
              2,
              3
            ]
        """.trimIndent(), result)
    }

    @Test
    fun CustomListWrite()
    {
        val json = Json(true, additionalCodecs = CustomCodec.codecs)
        val result = json.ToJson(listOf(Custom(42), Custom(43), Custom(44)))
        assertEquals("""
            [
              {
                "value": "2a"
              },
              {
                "value": "2b"
              },
              {
                "value": "2c"
              }
            ]
        """.trimIndent(), result)
    }

    @Test
    fun MapWrite()
    {
        val json = Json(true)
        val result = json.ToJson(mapOf('a' to "aaa", 'b' to "bbb", 'c' to "ccc"))
        assertEquals("""
            {
              "a": "aaa",
              "b": "bbb",
              "c": "ccc"
            }
        """.trimIndent(), result)
    }

    @Test
    fun CustomMapWrite()
    {
        val json = Json(true, additionalCodecs = CustomCodec.codecs)
        val result = json.ToJson(mapOf(42 to Custom(42), 43 to Custom(43), 44 to Custom(44)))
        assertEquals("""
            {
              "42": {
                "value": "2a"
              },
              "43": {
                "value": "2b"
              },
              "44": {
                "value": "2c"
              }
            }
        """.trimIndent(), result)
    }

    @Test
    fun ObjectArrayWrite()
    {
        val json = Json(true)
        val result = json.ToJson(arrayOf("a", "b", "c"))
        assertEquals("""
            [
              "a",
              "b",
              "c"
            ]
        """.trimIndent(), result)
    }

    @Test
    fun IntArrayWrite()
    {
        val json = Json(true)
        val result = json.ToJson(intArrayOf(1, 2, 3))
        assertEquals("""
            [
              1,
              2,
              3
            ]
        """.trimIndent(), result)
    }

    @Test
    fun LongArrayWrite()
    {
        val json = Json(true)
        val result = json.ToJson(longArrayOf(1, 2, 3))
        assertEquals("""
            [
              1,
              2,
              3
            ]
        """.trimIndent(), result)
    }

    @Test
    fun DoubleArrayWrite()
    {
        val json = Json(true)
        val result = json.ToJson(doubleArrayOf(1.0, 2.5, 3.0))
        assertEquals("""
            [
              1.0,
              2.5,
              3.0
            ]
        """.trimIndent(), result)
    }

    class MappedClass {
        lateinit var a: String
        var b: Int = 0
        @JsonField(name = "CCC")
        var c: Double = 0.0
        var d = false
        var e = 0L
        var f: String? = "aaa"
        @JsonTransient val ignored = "ignored"
    }

    @Test
    fun MappedWrite()
    {
        val obj = MappedClass()
        obj.a = "AAA"
        obj.b = 42
        obj.c = 2.5
        obj.d = true
        obj.e = 123
        obj.f = null
        val json = Json(true)
        val result = json.ToJson(obj)
        assertEquals("""
            {
              "a": "AAA",
              "b": 42,
              "CCC": 2.5,
              "d": true,
              "e": 123,
              "f": null
            }
        """.trimIndent(), result)
    }

    @Test
    fun MappedRead()
    {
        val json = Json(true)
        val sampleJson = """
            /* Sample comment * * / */
            {
                "a": "я abc \n\b \u00A9" ,
                "b": 42,
                "CCC": 4.5,
                "d": true,
                "e": 123,
                "f": null
            } /* EOF */
        """
        val result = json.FromJson<MappedClass>(sampleJson) ?: fail()
        assertEquals("я abc \n\b \u00A9", result.a)
        assertEquals(42, result.b)
        assertEquals(4.5, result.c, 0.0001)
        assertTrue(result.d)
        assertEquals(123L, result.e)
        assertNull(result.f)
    }

    @Test
    fun LateInitNotSet()
    {
        val json = Json(true)
        val sampleJson = "{}"
        assertThrows(JsonReadError::class.java) {
            json.FromJson<MappedClass>(sampleJson)
        }
    }

    @Test
    fun NullValueForNonNullable()
    {
        val json = Json(true)
        val sampleJson = """{ "a": null }"""
        assertThrows(JsonReadError::class.java) {
            json.FromJson<MappedClass>(sampleJson)
        }
    }

    @Test
    fun NullRead()
    {
        val json = Json(true)
        val sampleJson = "  null/**/"
        val result = json.FromJson<IntArray>(sampleJson)
        assertNull(result)
    }

    @Test
    fun EmptyArray()
    {
        val json = Json(true)
        val sampleJson = "[]"
        val result = json.FromJson<Any?>(sampleJson) ?: fail()
        assertEquals(0, (result as List<*>).size)
    }

    @Test
    fun EmptyMap()
    {
        val json = Json(true)
        val sampleJson = "{}"
        val result = json.FromJson<Any?>(sampleJson) ?: fail()
        assertEquals(0, (result as Map<*, *>).size)
    }

    @Test
    fun IntArrayRead()
    {
        val json = Json(true)
        val sampleJson = "[1,2,3]"
        val result = json.FromJson<IntArray>(sampleJson) ?: fail()
        assertEquals(3, result.size)
        assertEquals(1, result[0])
        assertEquals(2, result[1])
        assertEquals(3, result[2])
    }

    @Test
    fun LongArrayRead()
    {
        val json = Json(true)
        val sampleJson = "[1,2,3]"
        val result = json.FromJson<LongArray>(sampleJson) ?: fail()
        assertEquals(3, result.size)
        assertEquals(1L, result[0])
        assertEquals(2L, result[1])
        assertEquals(3L, result[2])
    }

    @Test
    fun DoubleArrayRead()
    {
        val json = Json(true)
        val sampleJson = "[1.5,2.5,3.5]"
        val result = json.FromJson<DoubleArray>(sampleJson) ?: fail()
        assertEquals(3, result.size)
        assertEquals(1.5, result[0], 0.00001)
        assertEquals(2.5, result[1], 0.00001)
        assertEquals(3.5, result[2], 0.00001)
    }

    @Test
    fun ArrayRead()
    {
        val json = Json(true)
        val sampleJson = """ [ "a", null, "c" ] """
        val result = json.FromJson<Array<String?>>(sampleJson) ?: fail()
        assertEquals(3, result.size)
        assertEquals("a", result[0])
        assertNull(result[1])
        assertEquals("c", result[2])
    }

    @Test
    fun MappedArrayRead()
    {
        val json = Json(true)
        val sampleJson = """ [ {"a": "abc", "b": 42}, null, {"a": "def", "b": 43 } ] """
        val result = json.FromJson<Array<MappedClass>>(sampleJson) ?: fail()
        assertEquals(3, result.size)
        assertEquals("abc", result[0].a)
        assertEquals(42, result[0].b)
        assertNull(result[1])
        assertEquals("def", result[2].a)
        assertEquals(43, result[2].b)
    }

    class MyList<T>: ArrayList<T>() {
        val marker = 42
    }

    @Test
    fun ListRead()
    {
        val json = Json(true)
        val sampleJson = """ [ "a", "b", "c"] """
        val list = json.FromJson<MyList<String>>(sampleJson) ?: fail()
        assertEquals(3, list.size)
        assertEquals(42, list.marker)
        assertEquals("a", list[0])
        assertEquals("b", list[1])
        assertEquals("c", list[2])
    }

    @Test
    fun AbstractListRead()
    {
        val json = Json(true)
        val sampleJson = """ [ "a", "b", "c"] """
        val list = json.FromJson<List<String>>(sampleJson) ?: fail()
        assertEquals(3, list.size)
        assertEquals("a", list[0])
        assertEquals("b", list[1])
        assertEquals("c", list[2])
    }

    class MyMap<T, U>: TreeMap<T, U>() {
        val marker = 42
    }

    @Test
    fun MapRead()
    {
        val json = Json(true)
        val sampleJson = """ {"a": 1, "b": 2, "c": null} """
        val map = json.FromJson<MyMap<String, Int>>(sampleJson) ?: fail()
        assertEquals(3, map.size)
        assertEquals(42, map.marker)
        assertEquals(1, map["a"])
        assertEquals(2, map["b"])
        assertNull(map["c"])
    }

    @Test
    fun AbstractMapRead()
    {
        val json = Json(true)
        val sampleJson = """ {"a": 1, "b": 2, "c": null} """
        val map = json.FromJson<Map<String, Int>>(sampleJson) ?: fail()
        assertEquals(3, map.size)
        assertEquals(1, map["a"])
        assertEquals(2, map["b"])
        assertNull(map["c"])
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun AnyRead()
    {
        val json = Json(true)
        val sampleJson = """
            {
                "a": [1,2,3],
                "b": 4,
                "c": {
                    "d": "abc",
                    "e": null
                }
            }
        """
        val obj = json.FromJson<Any?>(sampleJson) ?: fail()
        val map = obj as Map<String, Any?>
        val list = map["a"] as List<Any?>
        assertEquals(3, map.size)
        assertEquals(3, list.size)
        assertEquals(1.0, list[0])
        assertEquals(2.0, list[1])
        assertEquals(3.0, list[2])
        assertEquals(4.0, map["b"] as Double, 0.0001)
        val map2 = map["c"] as Map<String, Any?>
        assertEquals(2, map2.size)
        assertEquals("abc", map2["d"])
        assertNull(map2["e"])
    }

    //XXX check nullable/non-nullable list elements

    @Test
    fun TypeTokenTest()
    {
        assertEquals(String::class, TypeToken.Create<String>().type.jvmErasure)

        var tt: TypeToken<*> = TypeToken.Create<List<*>>()
        assertEquals(List::class, tt.type.jvmErasure)
        assertNull(tt.type.arguments[0].type)

        tt = TypeToken.Create<List<String>>()
        assertEquals(List::class, tt.type.jvmErasure)
        assertEquals(String::class, tt.type.arguments[0].type!!.jvmErasure)

        tt = TypeToken.Create(String::class)
        assertEquals(String::class, tt.type.jvmErasure)
    }
}

