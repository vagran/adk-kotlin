
import com.ast.adk.Log
import com.ast.adk.async.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.concurrent.atomic.AtomicLong

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class ContextTest {

    @BeforeAll
    fun Setup()
    {
        Log.InitTestLogging()
    }

    @Test
    fun ThreadContextTest()
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

    @Test
    fun RunTest()
    {
        val ctx = ThreadContext("test")
        ctx.Start()
        var invoked = false
        ctx.Run {invoked = true}
        ctx.Stop()
        assertTrue(invoked)
    }

    @Test
    fun WrapTest()
    {
        val ctx = ThreadContext("test")
        ctx.Start()
        var invoked = false
        (ctx.Wrap {invoked = true; Unit}).invoke()
        ctx.Stop()
        assertTrue(invoked)
    }

    @Test
    fun ThreadPoolContextTest()
    {
        val numValues = 500_000L
        val numCores = Runtime.getRuntime().availableProcessors()
        val it = (1L..numValues).iterator()
        val ctx = ThreadPoolContext("testPool", numCores)
        ctx.Start()
        val sum = AtomicLong(0)

        TaskThrottler(numCores, {
            val value = synchronized(it) {
                if (!it.hasNext()) {
                    return@TaskThrottler null
                }
                it.nextLong()
            }
            return@TaskThrottler Task.Create {
                sum.addAndGet(value)
            }.Submit(ctx).result
        }).Run().WaitComplete()

        Assertions.assertFalse(it.hasNext())
        assertEquals(numValues * (1 + numValues) / 2, sum.get())

        ctx.Stop()
    }
}
