
import com.ast.adk.async.Deferred
import com.ast.adk.async.ScheduledThreadContext
import com.ast.adk.async.observable.Observable
import com.ast.adk.utils.Log
import org.apache.logging.log4j.Logger
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.stream.Stream
import kotlin.test.assertFalse
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class ObservableTest {

    lateinit var log: Logger
    lateinit var ctx: ScheduledThreadContext

    @BeforeAll
    fun Setup()
    {
        Log.InitTestLogging()
        log = Log.GetLogger("ObservableTest")
    }

    @BeforeEach
    fun SetupEach()
    {
        ctx = ScheduledThreadContext("test")
        ctx.Start()
    }

    @AfterEach
    fun TeardownEach()
    {
        ctx.Stop()
    }

    @Test
    fun ValueTest1()
    {
        val x = Observable.Value.None<Int>()
        assertFalse(x.isSet)
        assertThrows<Exception>("Value is not set") { x.value }
    }

    @Test
    fun ValueTest2()
    {
        val x = Observable.Value.Of<Int?>(null)
        assertTrue(x.isSet)
        assertNull(x.value)
    }

    @Test
    fun ValueTest3()
    {
        val x = Observable.Value.Of(42)
        assertTrue(x.isSet)
        assertEquals(42, x.value)
    }

    inner class TestSubscriber: Observable.Subscriber<Int?> {

        val onComplete = Deferred.Create<Unit>()

        constructor(vararg expectedSequence: Int?)
        {
            expectedIt = expectedSequence.iterator()
        }

        constructor(expectedStream: Stream<Int?>)
        {
            expectedIt = expectedStream.iterator()
        }

        fun SetExpectedError()
        {
            isErrorExpected = true
        }

        override fun OnNext(value: Observable.Value<Int?>): Deferred<Boolean>?
        {
            if (isComplete) {
                log.error("Unexpected value after completed: %d", value)
                isFailed = true
                return null
            }
            if (!HasNextExpected()) {
                log.error("Unexpected value after end of expected data")
                isFailed = true
                return null
            }
            val expected = NextExpected()
            if (expected != null) {
                if (expected != value.value) {
                    log.error("Unexpected value: %d/%d", value, expected)
                    isFailed = true
                    return null
                }
            } else if (value.value != null) {
                log.error("Unexpected value: %d/null", value.value)
                isFailed = true
                return null
            }
            return Deferred.ForResult(true)
        }

        override fun OnComplete()
        {
            if (isComplete) {
                log.error("Double completion")
                isFailed = true
                return
            }
            if (HasNextExpected()) {
                log.error("Too few values seen: %d", curPos)
                isFailed = true
                return
            }
            if (isErrorExpected) {
                log.error("Normal completion when error expected")
                isFailed = true
                return
            }
            isComplete = true
            onComplete.SetResult(Unit)
        }

        override fun OnError(error: Throwable)
        {
            if (isComplete) {
                log.error("Unexpected error after completed", error)
                isFailed = true
                return
            }
            if (!isErrorExpected) {
                log.error("Unexpected error", error)
                isFailed = true
                return
            }
            if (HasNextExpected()) {
                log.error("Error at unexpected index: %d\n%s", curPos, Log.GetStackTrace(error))
                isFailed = true
                return
            }
            isComplete = true
            onComplete.SetResult(Unit)
        }

        private val expectedIt: Iterator<Int?>
        private var isComplete = false
        private var isFailed = false
        private var isErrorExpected = false
        private var curPos = 0

        private fun NextExpected(): Int?
        {
            curPos++
            return expectedIt.next()
        }

        private fun HasNextExpected(): Boolean
        {
            return expectedIt.hasNext()
        }
    }

    inner class TestRangeSource(private var start: Int,
                                private var count: Int,
                                private val step: Int = 1):
            Observable.Source<Int> {

        override fun Get(): Deferred<Observable.Value<Int>>
        {
            if (complete) {
                log.error("Get() called after completed")
                failed = true
                return Deferred.ForResult(Observable.Value.None())
            }
            if (count == 0) {
                complete = true
                return if (setError) {
                    Deferred.ForError(Error("Expected error"))
                } else {
                    Deferred.ForResult(Observable.Value.None())
                }
            }
            val ret = start
            start += step
            count--
            return Deferred.ForResult(Observable.Value.Of(ret))
        }

        fun IsFailed(): Boolean
        {
            return !complete || failed
        }

        fun SetError()
        {
            setError = true
        }

        private var complete: Boolean = false
        private var failed: Boolean = false
        private var setError: Boolean = false
    }

    fun RangeTestSubscriber(start: Int, count: Int, step: Int = 1): TestSubscriber
    {
        var cur = start
        return TestSubscriber(Stream.generate {
            val ret = cur
            cur += step
            ret
        }.limit(count.toLong()))
    }
}
