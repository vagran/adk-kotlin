
import com.ast.adk.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.reflect.full.createType
import kotlin.reflect.jvm.jvmErasure

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
        println(result)
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
        println(result)
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
        val d = true
        @JsonTransient val e = "ignored"
    }

    @Test
    fun MappedWrite()
    {
        val obj = MappedClass()
        obj.a = "AAA"
        obj.b = 42
        obj.c = 2.5
        val json = Json(true)
        val result = json.ToJson(obj)
        assertEquals("""
            {
              "a": "AAA",
              "b": 42,
              "CCC": 2.5,
              "d": true
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
                "b: 42,
                "CCC": 4.5
            } /* EOF */
        """
        val result = json.FromJson<MappedClass>(sampleJson) ?: fail()
        assertEquals("я abc \n\b \u00A9", result.a)
        assertEquals(42, result.b)
        assertEquals(4.5, result.c, 0.0001)
    }

    //XXX check nullable/non-nullable list elements

//    @Test
//    fun TypeTokenTest()
//    {
//        assertEquals(String::class, TypeToken.Create<String>().type.jvmErasure)
//
//        var tt: TypeToken<*> = TypeToken.Create<List<*>>()
//        assertEquals(List::class, tt.type.jvmErasure)
//        assertNull(tt.type.arguments[0].type)
//
//        tt = TypeToken.Create<List<String>>()
//        assertEquals(List::class, tt.type.jvmErasure)
//        assertEquals(String::class, tt.type.arguments[0].type!!.jvmErasure)
//
//        tt = TypeToken.Create(String::class)
//        assertEquals(String::class, tt.type.jvmErasure)
//    }

}

