
import com.ast.adk.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.reflect.full.createType
import kotlin.reflect.jvm.jvmErasure

data class Custom(val i: Int)

class CustomCodec: JsonCodec<Custom> {
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
        val json = Json(true, additionalCodecs = mapOf(Custom::class.createType() to CustomCodec()))
        val value = Custom(42)
        val result = json.ToJson(value)
        assertEquals("""
            {
              "value": "2a"
            }
        """.trimIndent(), result)
    }

    @Test
    fun Basic()
    {
        val json = Json()
        json.GetCodec<List<String>>()
        json.FromJson<List<String>>("")

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

