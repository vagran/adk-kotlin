/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */


import io.github.vagran.adk.async.Context
import io.github.vagran.adk.async.Deferred
import io.github.vagran.adk.async.ScheduledThreadContext
import io.github.vagran.adk.async.Task
import io.github.vagran.adk.async.observable.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class ObservableTest {

    lateinit var ctx: ScheduledThreadContext
    lateinit var ctx2: ScheduledThreadContext
    lateinit var ctx3: ScheduledThreadContext

    @BeforeEach
    fun SetupEach()
    {
        ctx = ScheduledThreadContext("test")
        ctx.Start()
        ctx2 = ScheduledThreadContext("test2")
        ctx2.Start()
        ctx3 = ScheduledThreadContext("test3")
        ctx3.Start()
    }

    @AfterEach
    fun TeardownEach()
    {
        ctx.Stop()
        ctx2.Stop()
        ctx3.Stop()
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
                System.err.format("Unexpected value after completed: %d%n", value.value)
                isFailed = true
                return null
            }
            if (!HasNextExpected()) {
                System.err.format("Unexpected value after end of expected data%n")
                isFailed = true
                return null
            }
            val expected = NextExpected()
            if (expected != null) {
                if (expected != value.value) {
                    System.err.format("Unexpected value: %d/%d%n", value.value, expected)
                    isFailed = true
                    return null
                }
            } else if (value.value != null) {
                System.err.format("Unexpected value: %d/null%n", value.value)
                isFailed = true
                return null
            }
            return Deferred.ForResult(true)
        }

        override fun OnComplete()
        {
            if (isComplete) {
                System.err.format("Double completion%n")
                isFailed = true
                return
            }
            if (HasNextExpected()) {
                System.err.format("Too few values seen: %d%n", curPos)
                isFailed = true
                return
            }
            if (isErrorExpected) {
                System.err.format("Normal completion when error expected%n")
                isFailed = true
                return
            }
            isComplete = true
            onComplete.SetResult(Unit)
        }

        override fun OnError(error: Throwable)
        {
            if (isComplete) {
                System.err.println("Unexpected error after completed")
                error.printStackTrace()
                isFailed = true
                return
            }
            if (!isErrorExpected) {
                System.err.println("Unexpected error")
                error.printStackTrace()
                isFailed = true
                return
            }
            if (HasNextExpected()) {
                System.err.format("Error at unexpected index: %d%n", curPos)
                error.printStackTrace(System.err)
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
                System.err.format("Get() called after completed%n")
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
        return Observable.Source { Task.CreateDef { src.Get().Await() }.Submit(ctx).result }
    }

    fun GetTestSource(vararg values: Int?, needError: Boolean = false): Observable.Source<Int?>
    {
        val iterator = values.iterator()
        return Observable.Source func@ {
            if (iterator.hasNext()) {
                return@func Deferred.ForResult(Observable.Value.Of(iterator.next()))
            }
            if (needError) {
                return@func Deferred.ForError(Error("test"))
            }
            return@func Deferred.ForResult(Observable.Value.None())
        }
    }

    fun GetTestSuspendSource(vararg values: Int?, needError: Boolean = false):
        ObservableSuspendSourceFunc<Int?>
    {
        val iterator = values.iterator()
        return suspend func@ {
            if (iterator.hasNext()) {
                return@func Observable.Value.Of(iterator.next())
            }
            if (needError) {
                throw Error("test")
            }
            return@func Observable.Value.None()
        }
    }

    fun <T> InContext(subscriber: Observable.Subscriber<T>, ctx: Context): Observable.Subscriber<T>
    {
        return object: Observable.Subscriber<T> {
            override fun OnNext(value: Observable.Value<T>): Deferred<Boolean>?
            {
                return Task.CreateDef {
                    val def = subscriber.OnNext(value) ?: return@CreateDef true
                    def.Await(ctx)
                }.Submit(ctx).result
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
        val src = GetContextSource(GetTestSource(*values), ctx)
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
    fun UnsubscribeResubscribeTest()
    {
        val src = PushSource<Int>()
        val obs = Observable.Create(src)
        obs.Subscribe().Unsubscribe()
        val sub = TestSubscriber(1)
        obs.Subscribe(sub)
        src.Push(1)
        src.Complete()
        assertFalse(sub.IsFailed())
    }

    @Test
    fun BasicSuspendSource()
    {
        val values = arrayOf(42, 45, null, 2, 3)
        val src = GetTestSuspendSource(*values)
        val observable = Observable.Create(src)
        val sub = TestSubscriber(*values)
        observable.Subscribe(sub)
        assertFalse(sub.IsFailed())
    }

    @Test
    fun BasicSuspendSourceError()
    {
        val values = arrayOf(42, 45, null, 2, 3)
        val src = GetTestSuspendSource(*values, needError = true)
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

    @Test
    fun FilterTest()
    {
        val src = TestRangeSource(1, 5)
        val observable = Observable.Create(src).Filter { x -> x != 2 && x != 3 }

        val sub = TestSubscriber(1, 4, 5)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun FilterTestContext()
    {
        val src = TestRangeSource(1, 5)
        val observable = Observable.Create(src).Filter({ x -> x != 2 && x != 3 })

        val sub = TestSubscriber(1, 4, 5)
        observable.Subscribe(InContext(sub, ctx))
        sub.onComplete.WaitComplete()

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun MapFilterTest()
    {
        val src = TestRangeSource(1, 5)
        val observable: Observable<Int?> = Observable.Create(src)
                .Filter({ x -> x != 2 && x != 3 }).Map({ x -> x!! * 10 })

        val sub = TestSubscriber(10, 40, 50)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun QueueTest()
    {
        val src = TestRangeSource(1, 5)
        val observable = Observable.Create(src).Queue(3, false)

        val sub = RangeTestSubscriber(1, 5)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun QueueDropTest()
    {
        val src = TestRangeSource(1, 5)
        val observable = Observable.Create(src).Queue(3, true)

        val sub = RangeTestSubscriber(3, 3)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun QueueLongTest()
    {
        val numValues = 5000
        val src = TestRangeSource(1, numValues)
        val observable = Observable.Create(src).Queue(10, false)

        val sub = RangeTestSubscriber(1, numValues)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun QueueDropLongTest()
    {
        val numValues = 5000
        val queueSize = 10
        val src = TestRangeSource(1, numValues)
        val observable = Observable.Create(src).Queue(queueSize, true)

        val sub = RangeTestSubscriber(numValues - queueSize + 1, queueSize)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun QueueLongTestContext()
    {
        val numValues = 5000
        val src = TestRangeSource(1, numValues)
        val observable = Observable.Create(src).Queue(10, false)

        val sub = RangeTestSubscriber(1, numValues)
        observable.Subscribe(InContext(sub, ctx))
        sub.onComplete.WaitComplete()

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun QueueDropLongTestContext()
    {
        val numValues = 5000
        val queueSize = 10
        val src = TestRangeSource(1, numValues)
        val observable = Observable.Create(src).Queue(queueSize, true)

        val sub = RangeTestSubscriber(numValues - queueSize + 1, queueSize)
        observable.Subscribe(InContext(sub, ctx))
        sub.onComplete.WaitComplete()

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun QueueErrorTest()
    {
        val src = TestRangeSource(1, 5)
        src.SetError()
        val observable = Observable.Create(src).Queue(3, false)

        val sub = RangeTestSubscriber(1, 5)
        sub.SetExpectedError()
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(src.IsFailed())
    }

    @Test
    fun PushSourceTest()
    {
        val src = PushSource<Int?>()
        val observable = Observable.Create(src).Queue(5, false)

        val sub = RangeTestSubscriber(1, 5)
        observable.Subscribe(sub)

        for (i in 1..5) {
            src.Push(i)
        }
        src.Complete()

        assertFalse(sub.IsFailed())
    }

    @Test
    fun PushSourceErrorTest()
    {
        val src = PushSource<Int?>()
        val observable = Observable.Create(src).Queue(5, false)

        val sub = RangeTestSubscriber(1, 5)
        sub.SetExpectedError()
        observable.Subscribe(sub)

        for (i in 1..5) {
            src.Push(i)
        }
        src.Error(Error("Expected error"))

        assertFalse(sub.IsFailed())
    }

    @Test
    fun AwaitableSubscriptionTest()
    {
        val values = arrayOf(42, 45, null, 2, 3)
        val src = GetTestSource(*values)
        val observable = Observable.Create(src)
        val sub1 = observable.Subscribe()

        val src2 = PushSource<Int?>()
        val observable2 = Observable.Create(src2)
        val sub2 = TestSubscriber(*values)
        observable2.Subscribe(sub2)

        Task.CreateDef {
            do {
                val v = sub1.Await()
                if (v.isSet) {
                    src2.Push(v.value)
                } else {
                    src2.Complete()
                    break
                }
            } while (true)
        }.Submit(ctx)

        sub2.onComplete.WaitComplete()

        assertFalse(sub2.IsFailed())
    }

    @Test
    fun AwaitableSubscriptionErrorTest()
    {
        val src = TestRangeSource(1, 5)
        src.SetError()
        val observable = Observable.Create(src)
        val sub1 = observable.Subscribe()

        val src2 = PushSource<Int?>()
        val observable2 = Observable.Create(src2)
        val sub2 = RangeTestSubscriber(1, 5)
        sub2.SetExpectedError()
        observable2.Subscribe(sub2)

        Task.CreateDef {
            do {
                val v = try {
                    sub1.Await()
                } catch (e: Error) {
                    src2.Error(e)
                    return@CreateDef
                }
                if (v.isSet) {
                    src2.Push(v.value)
                } else {
                    src2.Complete()
                    break
                }
            } while (true)
        }.Submit(ctx)

        sub2.onComplete.WaitComplete()

        assertFalse(src.IsFailed())
        assertFalse(sub2.IsFailed())
    }

    @Test
    fun MergeTest()
    {
        val s1 = Observable.From(0..99)
        val s2 = Observable.From(100..199)
        val s3 = Observable.From(200..299)
        val m = Observable.Merge(s1, s2, s3)
        val seen = HashSet<Int>()
        var errorSeen: Throwable? = null
        var endSeen = false
        var failed = false
        m.Subscribe {
            value, error ->
            if (error != null) {
                errorSeen = error
                return@Subscribe null
            }
            if (errorSeen != null || endSeen) {
                System.err.println("Unexpected value after end")
                failed = true
                return@Subscribe null
            }
            if (value.isSet) {
                seen.add(value.value)
            } else {
                endSeen = true
            }
            null
        }
        assertFalse(failed)
        assertNull(errorSeen)
        assertTrue(endSeen)
        for (i in 0..299) {
            assertTrue(seen.contains(i))
        }
        assertEquals(300, seen.size)
    }

    @Test
    fun MergePullTest()
    {
        val s1 = PushSource<Int>()
        val s2 = PushSource<Int>()
        val s3 = PushSource<Int>()
        val m = Observable.Merge(Observable.Create(s1),
                                 Observable.Create(s2),
                                 Observable.Create(s3))
        val seen = HashSet<Int>()
        var errorSeen: Throwable? = null
        var endSeen = false
        var failed = false
        m.Subscribe {
            value, error ->
            if (error != null) {
                errorSeen = error
                return@Subscribe null
            }
            if (errorSeen != null || endSeen) {
                System.err.println("Unexpected value after end")
                failed = true
                return@Subscribe null
            }
            if (value.isSet) {
                seen.add(value.value)
            } else {
                endSeen = true
            }
            null
        }
        assertFalse(failed)
        assertFalse(endSeen)

        s1.Push(1)
        assertTrue(1 in seen)

        s2.Push(2)
        assertTrue(2 in seen)

        s3.Push(3)
        assertTrue(3 in seen)

        s1.Error(Error("test1"))
        assertFalse(failed)
        assertNull(errorSeen)

        s2.Push(4)
        assertTrue(4 in seen)

        s3.Error(Error("test3"))
        assertFalse(failed)
        assertNull(errorSeen)

        s2.Push(5)
        assertTrue(5 in seen)

        s2.Complete()
        assertFalse(failed)
        assertFalse(endSeen)
        assertNotNull(errorSeen)
        assertEquals("test1", errorSeen!!.message)
    }

    @Test
    fun MergeTestUnequalLength()
    {
        val s1 = Observable.From(0..99)
        val s2 = Observable.From(100..149)
        val s3 = Observable.From(200..279)
        val m = Observable.Merge(s1, s2, s3)
        val seen = HashSet<Int>()
        var errorSeen: Throwable? = null
        var endSeen = false
        var failed = false
        m.Subscribe {
            value, error ->
            if (error != null) {
                errorSeen = error
                return@Subscribe null
            }
            if (errorSeen != null || endSeen) {
                System.err.println("Unexpected value after end")
                failed = true
                return@Subscribe null
            }
            if (value.isSet) {
                seen.add(value.value)
            } else {
                endSeen = true
            }
            null
        }
        assertFalse(failed)
        assertNull(errorSeen)
        assertTrue(endSeen)
        for (i in 0..99) {
            assertTrue(seen.contains(i))
        }
        for (i in 100..149) {
            assertTrue(seen.contains(i))
        }
        for (i in 200..279) {
            assertTrue(seen.contains(i))
        }
        assertEquals(230, seen.size)
    }

    @Test
    fun MergeTestMultithreaded()
    {
        val s1 = Observable.Create(GetContextSource(TestRangeSource(0, 100), ctx))
        val s2 = Observable.Create(GetContextSource(TestRangeSource(100, 100), ctx2))
        val s3 = Observable.Create(GetContextSource(TestRangeSource(200, 100), ctx3))
        val m = Observable.Merge(s1, s2, s3)
        val seen = HashSet<Int?>()
        var errorSeen: Throwable? = null
        var endSeen = false
        var failed = false
        val done = Deferred.Create<Unit>()
        m.Subscribe {
            value, error ->
            if (error != null) {
                errorSeen = error
                done.SetResult(Unit)
                return@Subscribe null
            }
            if (errorSeen != null || endSeen) {
                System.err.println("Unexpected value after end")
                failed = true
                done.SetResult(Unit)
                return@Subscribe null
            }
            if (value.isSet) {
                seen.add(value.value)
            } else {
                endSeen = true
                done.SetResult(Unit)
            }
            null
        }
        done.WaitComplete()
        assertFalse(failed)
        assertNull(errorSeen)
        assertTrue(endSeen)
        for (i in 0..299) {
            assertTrue(seen.contains(i))
        }
        assertEquals(300, seen.size)
    }

    @Test
    fun MergeTestMultithreadedFail()
    {
        val s1 = Observable.Create(GetContextSource(TestRangeSource(0, 100), ctx))
        val s2 = Observable.Create(GetContextSource(TestRangeSource(100, 100), ctx2))
        val src = TestRangeSource(200, 10)
        src.SetError()
        val s3 = Observable.Create(GetContextSource(src, ctx3))
        val m = Observable.Merge(s1, s2, s3, delayError = false)
        val seen = HashSet<Int?>()
        var errorSeen: Throwable? = null
        var endSeen = false
        var failed = false
        val done = Deferred.Create<Unit>()
        m.Subscribe {
            value, error ->
            if (error != null) {
                errorSeen = error
                done.SetResult(Unit)
                return@Subscribe null
            }
            if (errorSeen != null || endSeen) {
                System.err.println("Unexpected value after end")
                failed = true
                done.SetResult(Unit)
                return@Subscribe null
            }
            if (value.isSet) {
                seen.add(value.value)
            } else {
                endSeen = true
                done.SetResult(Unit)
            }
            null
        }
        done.WaitComplete()
        assertFalse(failed)
        assertNotNull(errorSeen)
        assertEquals("Expected error", errorSeen!!.message)
        assertFalse(endSeen)
        assertTrue(seen.size < 300)
    }

    @Test
    fun MergeFailTest()
    {
        val s1 = Observable.Create(GetTestSource(1, 2, 3))
        val s2 = Observable.Create(GetTestSource(4, 5, 6))
        val s3 = Observable.Create(GetTestSource(7, needError = true))
        val m = Observable.Merge(s1, s2, s3)
        val seen = HashSet<Int?>()
        var errorSeen: Throwable? = null
        var endSeen = false
        var failed = false
        m.Subscribe {
            value, error ->
            if (error != null) {
                errorSeen = error
                return@Subscribe null
            }
            if (errorSeen != null || endSeen) {
                System.err.println("Unexpected value after end")
                failed = true
                return@Subscribe null
            }
            if (value.isSet) {
                seen.add(value.value)
            } else {
                endSeen = true
            }
            null
        }
        assertFalse(failed)
        assertNotNull(errorSeen)
        assertEquals("test", errorSeen!!.message)
        assertFalse(endSeen)
        for (i in 1..7) {
            assertTrue(seen.contains(i))
        }
        assertEquals(7, seen.size)
    }

    @Test
    fun MergeFailNoDelayErrorTest()
    {
        val s1 = Observable.Create(GetTestSource(1, 2, 3))
        val s2 = Observable.Create(GetTestSource(4, 5, 6))
        val s3 = Observable.Create(GetTestSource(7, needError = true))
        val m = Observable.Merge(s1, s2, s3, delayError = false)
        val seen = HashSet<Int?>()
        var errorSeen: Throwable? = null
        var endSeen = false
        var failed = false
        m.Subscribe {
            value, error ->
            if (error != null) {
                errorSeen = error
                return@Subscribe null
            }
            if (errorSeen != null || endSeen) {
                System.err.println("Unexpected value after end")
                failed = true
                return@Subscribe null
            }
            if (value.isSet) {
                seen.add(value.value)
            } else {
                endSeen = true
            }
            null
        }
        assertFalse(failed)
        assertNotNull(errorSeen)
        assertEquals("test", errorSeen!!.message)
        assertFalse(endSeen)
        for (i in arrayOf(1, 4, 7, 2, 5)) {
            assertTrue(seen.contains(i))
        }
        assertEquals(5, seen.size)
    }

    @Test
    fun ConcatTest()
    {
        val s1 = TestRangeSource(1, 3)
        val s2 = TestRangeSource(4, 3)
        val s3 = TestRangeSource(7, 3)
        val observable = Observable.Concat(Observable.Create(s1),
                                           Observable.Create(s2),
                                           Observable.Create(s3))

        val sub = RangeTestSubscriber(1, 9)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(s1.IsFailed())
        assertFalse(s2.IsFailed())
        assertFalse(s3.IsFailed())
    }

    @Test
    fun ConcatTestTwoSources()
    {
        val s1 = TestRangeSource(1, 3)
        val s2 = TestRangeSource(4, 3)
        val observable = Observable.Concat(Observable.Create(s1),
                                           Observable.Create(s2))

        val sub = RangeTestSubscriber(1, 6)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(s1.IsFailed())
        assertFalse(s2.IsFailed())
    }

    @Test
    fun ConcatTestOneSource()
    {
        val s1 = TestRangeSource(1, 3)
        val observable = Observable.Concat(Observable.Create(s1))

        val sub = RangeTestSubscriber(1, 3)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(s1.IsFailed())
    }

    @Test
    fun ConcatTestNoSource()
    {
        val observable = Observable.Concat<Int>()

        val sub = RangeTestSubscriber(0, 0)
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
    }

    @Test
    fun ConcatTestError()
    {
        val s1 = TestRangeSource(1, 3)
        val s2 = TestRangeSource(4, 3)
        val s3 = TestRangeSource(7, 3)
        s3.SetError()
        val observable = Observable.Concat(Observable.Create(s1),
                                           Observable.Create(s2),
                                           Observable.Create(s3))

        val sub = RangeTestSubscriber(1, 9)
        sub.SetExpectedError()
        observable.Subscribe(sub)

        assertFalse(sub.IsFailed())
        assertFalse(s1.IsFailed())
        assertFalse(s2.IsFailed())
        assertFalse(s3.IsFailed())
    }
}
