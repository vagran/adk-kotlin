
import com.ast.adk.async.Task
import com.ast.adk.async.ThreadContext
import com.ast.adk.utils.Log
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertNull


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class TaskTest {

    @BeforeAll
    fun Setup()
    {
        Log.InitTestLogging()
    }

    @Test
    fun Test1()
    {
        val ctx = ThreadContext("test")
        ctx.Start()

        val def = Task.Create({
            42
        }).Submit(ctx).result

        var defResult: Int? = null
        def.Subscribe { result, error ->
            assertNull(error)
            defResult = result
        }

        ctx.Stop()
        assertEquals(42, defResult)
    }

    @Test
    fun Test2()
    {
        val ctx = ThreadContext("test")
        ctx.Start()

        val def = Task.CreateDef(suspend {
            42
        }).Submit(ctx).result

        var defResult: Int? = null
        def.Subscribe { result, error ->
            assertNull(error)
            defResult = result
        }

        ctx.Stop()
        assertEquals(42, defResult)
    }
}
