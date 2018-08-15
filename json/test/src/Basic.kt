
import com.ast.adk.json.Json
import com.ast.adk.json.TypeToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.reflect.jvm.jvmErasure


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class BasicTest {

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

