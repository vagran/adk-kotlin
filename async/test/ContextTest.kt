import com.ast.adk.async.Message
import com.ast.adk.async.ThreadContext
import com.ast.adk.utils.Log
import org.apache.logging.log4j.Logger
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.fail

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class ContextTest {

    lateinit var log: Logger

    @BeforeAll
    fun Setup()
    {
        Log.InitTestLogging()
        log = Log.GetLogger("AsyncTest")
    }

    @Test
    fun Test1()
    {
        val ctx = ThreadContext("test")
        ctx.Start()
        var invoked = false
        ctx.Submit(object: Message {
            override fun Invoke()
            {
                invoked = true
            }

            override fun Reject(error: Throwable)
            {
                fail(error)
            }
        })
        ctx.Stop()
        assertTrue(invoked)
    }
}