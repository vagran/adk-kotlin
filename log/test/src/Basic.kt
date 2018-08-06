import com.ast.adk.log.internal.Tmp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Basic {

    @Test
    fun BasicTest()
    {
        assertEquals(42, Tmp().a)
    }
}
