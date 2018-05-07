
import com.ast.adk.Log
import com.ast.adk.async.*
import org.apache.logging.log4j.Logger
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.experimental.*
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

    //XXX
    @Test
    fun TestResume()
    {
        val ctx = ThreadContext("test")
        ctx.Start()

        val _def = Deferred.ForResult(42)
        val def = Task.CreateDef({

             ctx.ResumeIn()
//             _def.Await()
             throw Throwable("aaa")
//            try {
//
//                suspendCoroutine { cont: Continuation<Unit> ->
//                    cont.resumeWithException(Throwable("aaa"))
//                }
//            } catch (e: Throwable) {
//                throw Throwable("bbb", e)
//            }

            42
        }).Submit(ctx).result

        VerifyDefResult(42, def)

        ctx.Stop()
    }

    //XXX
    @Test
    fun TestCoroutine()
    {
        var c: Continuation<Unit>? = null
        suspend {
            suspendCoroutine {
                cont: Continuation<Unit> ->
                c = cont
            }
            throw Throwable("A")
        }
        .createCoroutine(object: Continuation<Unit> {
            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resume(value: Unit)
            {
                throw Throwable("C")
            }

            override fun resumeWithException(exception: Throwable)
            {
                throw Throwable("B", exception)
            }
        }).resume(Unit)

        c!!.resume(Unit)
    }

    @Test
    fun UnitTaskTest()
    {
        val ctx = ThreadContext("test")
        ctx.Start()

//        var result: Int? = null
        val def = Task.Create({}).Submit(ctx).result

        VerifyDefResult(Unit, def)

//        assertEquals(42, result!!)

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
    fun Test4()
    {
        val ctx = ThreadContext("test")
        ctx.Start()

        val def1 = Task.Create({
            42
        }).Submit(ctx).result

        val def2 = Deferred.ForFunc({def1.Await()})

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

    @Test
    fun TimersTest1()
    {
        val ctx = ScheduledThreadContext("test")
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
    fun TimersTest2()
    {
        val ctx = ScheduledThreadContext("test")
        ctx.Start()

        val task = Task.Create({
            42
        })
        val def1 = task.result
        ctx.SubmitScheduled(task, 1000)

        val def2 = Task.CreateDef({
            def1.Await()
        }).Submit(ctx).result

        VerifyDefResult(42, def2)

        ctx.Stop()
    }

    @Test
    fun TimersTest3()
    {
        val ctx = ScheduledThreadContext("test")
        ctx.Start()

        val def = Task.CreateDef({
            ctx.Delay(1000L)
            42
        }).Submit(ctx).result

        VerifyDefResult(42, def)

        ctx.Stop()
    }

    @Test
    fun WaitMultiple()
    {
        val ctx = ThreadContext("ctx")
        ctx.Start()

        val def1 = Task.Create({
            3
        }).Submit(ctx).result

        val def2 = Task.Create({
            5
        }).Submit(ctx).result

        val def3 = Task.Create({
            7
        }).Submit(ctx).result

        val def = Deferred.When(def1, def2, def3)

        var invoked = false
        def.Subscribe {
            _, error ->
            assertNull(error)
            invoked = true
        }

        def.WaitComplete()
        assertTrue(invoked)
        assertEquals(3, def1.Get())
        assertEquals(5, def2.Get())
        assertEquals(7, def3.Get())

        ctx.Stop()
    }

    @Test
    fun WaitMultipleError()
    {
        val ctx = ThreadContext("ctx")
        ctx.Start()

        val def1 = Task.Create({
            3
        }).Submit(ctx).result

        val def2 = Task.Create({
            throw Error("aaa")
        }).Submit(ctx).result

        val def3 = Task.Create({
            7
        }).Submit(ctx).result

        val def = Deferred.When(def1, def2, def3)

        var invoked = false
        def.Subscribe {
            _, error ->
            assertEquals("aaa", error!!.message)
            invoked = true
        }

        def.WaitComplete()
        assertTrue(invoked)
        assertEquals(3, def1.Get())
        assertThrows<Exception>("aaa", {def2.Get()})
        assertEquals(7, def3.Get())

        ctx.Stop()
    }

    @Test
    fun TaskThrottlerSyncTest()
    {
        /* Test for potential deep recursion. */
        val it = (1..5_000_000).iterator()
        TaskThrottler(8, {
            if (!it.hasNext()) {
                return@TaskThrottler null
            }
            return@TaskThrottler Deferred.ForResult(it.next())
        }, false).Run().WaitComplete()
        assertFalse(it.hasNext())
    }
}
