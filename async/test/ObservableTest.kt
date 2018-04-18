
import com.ast.adk.async.Context
import com.ast.adk.async.Deferred
import com.ast.adk.async.ScheduledThreadContext
import com.ast.adk.async.Task
import com.ast.adk.async.observable.From
import com.ast.adk.async.observable.Map
import com.ast.adk.async.observable.MapOperatorFunc
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

        fun IsFailed(shouldComplete: Boolean = true): Boolean
        {
            return isFailed || HasNextExpected() || (shouldComplete && !isComplete)
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
            Observable.Source<Int?> {

        override fun Get(): Deferred<Observable.Value<Int?>>
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

    fun <T> GetContextSource(src: Observable.Source<T>, ctx: Context): Observable.Source<T>
    {
        return object: Observable.Source<T> {
            override fun Get(): Deferred<Observable.Value<T>>
            {
                return Task.CreateDef({ src.Get().Await() }).Submit(ctx).result
            }
        }
    }

    fun GetTestSource(vararg values: Int?, needError: Boolean = false): Observable.Source<Int?>
    {
        return object: Observable.Source<Int?> {
            var curPos = 0

            override fun Get(): Deferred<Observable.Value<Int?>>
            {
                if (curPos == values.size) {
                    if (needError) {
                        return Deferred.ForError(Error("test"))
                    } else {
                        return Deferred.ForResult(Observable.Value.None())
                    }
                }
                val def = Deferred.ForResult(Observable.Value.Of(values[curPos]))
                curPos++
                return def
            }
        }
    }

    fun <T> InContext(subscriber: Observable.Subscriber<T>, ctx: Context): Observable.Subscriber<T>
    {
        return object: Observable.Subscriber<T> {
            override fun OnNext(value: Observable.Value<T>): Deferred<Boolean>?
            {
                return Task.CreateDef({
                    val def = subscriber.OnNext(value) ?: return@CreateDef true
                    def.Await(ctx)
                }).Submit(ctx).result
            }

            override fun OnComplete()
            {
                subscriber.OnComplete()
            }

            override fun OnError(error: Throwable)
            {
                subscriber.OnError(error)
            }
        }
    }

    @Test
    fun Basic()
    {
        val values = arrayOf(42, 45, null, 2, 3)
        val src = GetTestSource(*values)
        val observable = Observable.Create(src)
        val sub = TestSubscriber(*values)
        observable.Subscribe(sub)
        assertFalse(sub.IsFailed())
    }

    @Test
    fun BasicCtx()
    {
        val values = arrayOf(42, 45, null, 2, 3)
        val src = GetTestSource(*values)
        val observable = Observable.Create(src)
        val sub = TestSubscriber(*values)
        observable.Subscribe(InContext(sub, ctx))
        sub.onComplete.WaitComplete()
        assertFalse(sub.IsFailed())
    }

    @Test
    fun BasicError()
    {
        val values = arrayOf(42, 45, null, 2, 3)
        val src = GetTestSource(*values, needError = true)
        val observable = Observable.Create(src)
        val sub = TestSubscriber(*values)
        sub.SetExpectedError()
        observable.Subscribe(sub)
        assertFalse(sub.IsFailed())
    }

    @Test
    fun BasicErrorCtx()
    {
        val values = arrayOf(42, 45, null, 2, 3)
        val src = GetTestSource(*values, needError = true)
        val observable = Observable.Create(src)
        val sub = TestSubscriber(*values)
        sub.SetExpectedError()
        observable.Subscribe(InContext(sub, ctx))
        sub.onComplete.WaitComplete()
        assertFalse(sub.IsFailed())
    }

    @Test
    fun ArraySource()
    {
        val observable = Observable.From(1, 2, 3, null, 5)

        val sub = TestSubscriber(1, 2, 3, null, 5)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
    }

    @Test
    fun CollectionSource()
    {
        val list = listOf(1, 2, 3, null, 5)

        val observable = Observable.From(list)

        val sub = TestSubscriber(*list.toTypedArray())
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
    }

    @Test
    fun StreamSource()
    {
        val list = listOf(1, 2, 3, null, 5)

        val observable = Observable.From(list.stream())

        val sub = TestSubscriber(*list.toTypedArray())
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
    }

    @Test
    fun RangeSource()
    {
        val observable = Observable.From<Int?>(1..5)

        val sub = TestSubscriber(1, 2, 3, 4, 5)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
    }

    @Test
    fun TestRangeSource()
    {
        val src = TestRangeSource(1, 5)
        val observable = Observable.Create(src)

        val sub = TestSubscriber(1, 2, 3, 4, 5)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun LongRangeSource()
    {
        val numValues = 5000
        val src = TestRangeSource(1, numValues)
        val observable = Observable.Create(src)

        val sub = RangeTestSubscriber(1, numValues)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun LongSourceContext()
    {
        val numValues = 5000
        val src = TestRangeSource(1, numValues)
        val observable = Observable.Create(src)

        val sub = RangeTestSubscriber(1, numValues)
        observable.Subscribe(InContext(sub, ctx))
        sub.onComplete.WaitComplete()

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun MultipleSubscribers()
    {
        val numValues = 5000
        val src = TestRangeSource(1, numValues)
        val observable = Observable.Create(src, false)

        val numSubscribers = 100
        val sub = arrayOfNulls<TestSubscriber>(numSubscribers)
        for (i in 0 until numSubscribers) {
            sub[i] = RangeTestSubscriber(1, numValues)
            observable.Subscribe(InContext(sub[i]!!, ctx))
        }
        observable.Connect()
        for (i in 0 until numSubscribers) {
            sub[i]!!.onComplete.WaitComplete()
            assertFalse(sub[i]!!.IsFailed())
        }
        assertFalse(src.IsFailed())
    }

    @Test
    fun MultipleSubscribersNoContext()
    {
        val numValues = 5000
        val src = TestRangeSource(1, numValues)
        val observable = Observable.Create(src, false)

        val numSubscribers = 100
        val sub = arrayOfNulls<TestSubscriber>(numSubscribers)
        for (i in 0 until numSubscribers) {
            sub[i] = RangeTestSubscriber(1, numValues)
            observable.Subscribe(sub[i]!!)
        }
        observable.Connect()
        for (i in 0 until numSubscribers) {
            sub[i]!!.onComplete.WaitComplete()
            assertFalse(sub[i]!!.IsFailed())
        }
        assertFalse(src.IsFailed())
    }

    @Test
    fun SubscribeAfterComplete()
    {
        val src = TestRangeSource(1, 5)
        val observable = Observable.Create(src)

        val sub = TestSubscriber(1, 2, 3, 4, 5)
        observable.Subscribe(sub)
        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())

        val sub2 = TestSubscriber()
        observable.Subscribe(sub2)
        assertFalse(sub2.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun SubscribeAfterFailure()
    {
        val src = TestRangeSource(1, 5)
        src.SetError()
        val observable = Observable.Create(src)

        val sub = TestSubscriber(1, 2, 3, 4, 5)
        sub.SetExpectedError()
        observable.Subscribe(sub)
        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())

        val sub2 = TestSubscriber()
        sub2.SetExpectedError()
        observable.Subscribe(sub2)
        assertFalse(sub2.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun MapTest()
    {
        val src = TestRangeSource(1, 5)
        val observable = Observable.Create(src).Map<Int?, Int?> { x -> x!! * 10 }

        val sub = TestSubscriber(10, 20, 30, 40, 50)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun MapTestContext()
    {
        val src = TestRangeSource(1, 5)
        val observable = Observable.Create(src).Map<Int?, Int?> { x -> x!! * 10 }

        val sub = TestSubscriber(10, 20, 30, 40, 50)
        observable.Subscribe(InContext(sub, ctx))
        sub.onComplete.WaitComplete()

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun MapTestSuspend()
    {
        val func: MapOperatorFunc<Int?, Int?> = {
            value: Int? ->
            Task.Create { value!! * 10 }.Submit(ctx).result.Await()
        }

        val src = TestRangeSource(1, 5)
        val observable = Observable.Create(src).Map(func)

        val sub = TestSubscriber(10, 20, 30, 40, 50)
        observable.Subscribe(sub)
        sub.onComplete.WaitComplete()

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun MapTestSuspendContext()
    {
        val func: MapOperatorFunc<Int?, Int?> = {
            value: Int? ->
            Task.Create { value!! * 10 }.Submit(ctx).result.Await()
        }

        val src = TestRangeSource(1, 5)
        val observable = Observable.Create(src).Map(func)

        val sub = TestSubscriber(10, 20, 30, 40, 50)
        observable.Subscribe(InContext(sub, ctx))
        sub.onComplete.WaitComplete()

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun MapLongTest()
    {
        val numValues = 5000
        val src = TestRangeSource(1, numValues)
        val observable = Observable.Create(src).Map<Int?, Int?> { x -> x!! * 10 }

        val sub = RangeTestSubscriber(10, numValues, 10)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun MapLongTestContext()
    {
        val numValues = 5000
        val src = TestRangeSource(1, numValues)
        val observable = Observable.Create(src).Map<Int?, Int?> { x -> x!! * 10 }

        val sub = RangeTestSubscriber(10, numValues, 10)
        observable.Subscribe(InContext(sub, ctx))
        sub.onComplete.WaitComplete()

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }
}
