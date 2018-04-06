
import com.ast.adk.async.Deferred
import com.ast.adk.async.Task
import com.ast.adk.async.ThreadContext
import com.ast.adk.utils.Log
import org.apache.logging.log4j.Logger
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class TaskTest {

    lateinit var log: Logger

    @BeforeAll
    fun Setup()
    {
        Log.InitTestLogging()
        log = Log.GetLogger("TaskTest")
    }

    private fun <T> VerifyDefResult(expected: T, def: Deferred<T>)
    {
        var defResult: T? = null
        def.Subscribe { result, error ->
            assertNull(error)
            defResult = result
        }
        def.WaitComplete()
        assertEquals(expected, defResult)
    }

    private fun VerifyDefError(expectedMessage: String, def: Deferred<*>)
    {
        var defError: Throwable? = null
        def.Subscribe { result, error ->
            assertNull(result)
            defError = error
        }
        def.WaitComplete()
        assertEquals(expectedMessage, defError!!.message)
    }

    @Test
    fun Test1()
    {
        val ctx = ThreadContext("test")
        ctx.Start()

        val def = Task.Create({
            42
        }).Submit(ctx).result

        VerifyDefResult(42, def)

        ctx.Stop()
    }

    @Test
    fun Test2()
    {
        val ctx = ThreadContext("test")
        ctx.Start()

        val def = Task.CreateDef(suspend {
            42
        }).Submit(ctx).result

        VerifyDefResult(42, def)

        ctx.Stop()
    }

    @Test
    fun Test3()
    {
        val ctx = ThreadContext("test")
        ctx.Start()

        val def1 = Task.Create({
            42
        }).Submit(ctx).result

        val def2 = Task.CreateDef({
            def1.Await()
        }).Submit(ctx).result

        VerifyDefResult(42, def2)

        ctx.Stop()
    }

    @Test
    fun ThreadContinuationTest()
    {
        val ctx1 = ThreadContext("ctx1")
        ctx1.Start()
        val ctx2 = ThreadContext("ctx2")
        ctx2.Start()

        val def1 = Task.Create({
            log.info("in task 1")
            assertSame(ctx1.thread, Thread.currentThread())
            42
        }).Submit(ctx1).result

        val def2 = Task.CreateDef({
            log.info("in task 2, before suspend")
            assertSame(ctx1.thread, Thread.currentThread())
            val x = def1.Await(ctx2)
            log.info("in task 2, after suspend")
            assertSame(ctx2.thread, Thread.currentThread())
            ctx1.ResumeIn()
            log.info("in task 2, after context switch")
            assertSame(ctx1.thread, Thread.currentThread())
            x
        }).Submit(ctx1).result

        VerifyDefResult(42, def2)

        ctx1.Stop()
        ctx2.Stop()
    }

    @Test
    fun ThreadContinuationErrorTest()
    {
        val ctx1 = ThreadContext("ctx1")
        ctx1.Start()
        val ctx2 = ThreadContext("ctx2")
        ctx2.Start()

        val def1 = Task.Create({
            log.info("in task 1")
            assertSame(ctx1.thread, Thread.currentThread())
            throw Exception("test")
        }).Submit(ctx1).result

        val def2 = Task.CreateDef({
            log.info("in task 2, before suspend")
            assertSame(ctx1.thread, Thread.currentThread())
            try {
                def1.Await(ctx2)
            } catch (e: Exception) {
                log.info("in task 2, after suspend")
                assertSame(ctx2.thread, Thread.currentThread())
                throw e
            }
        }).Submit(ctx1).result

        VerifyDefError("test", def2)

        ctx1.Stop()
        ctx2.Stop()
    }
}
