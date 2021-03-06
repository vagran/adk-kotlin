/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */


import io.github.vagran.adk.async.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class TaskTest {

    private fun <T> VerifyDefResult(expected: T, def: Deferred<T>)
    {
        assertEquals(expected, def.WaitComplete().Get())
    }

    private fun VerifyDefError(expectedMessage: String, def: Deferred<*>)
    {
        val defError: Throwable? = try {
            def.WaitComplete().Get()
            null
        } catch (e: Throwable) {
            e.cause
        }
        assertEquals(expectedMessage, defError!!.message)
    }

    @Test
    fun Test1()
    {
        val ctx = ThreadContext("test")
        ctx.Start()

        val def = Task.Create {
            42
        }.Submit(ctx).result

        VerifyDefResult(42, def)

        ctx.Stop()
    }

    @Test
    fun UnitTaskTest()
    {
        val ctx = ThreadContext("test")
        ctx.Start()

        var result: Int? = null
        val def = Task.Create { result = 42 }.Submit(ctx).result

        VerifyDefResult(Unit, def)

        assertEquals(42, result!!)

        ctx.Stop()
    }

    @Test
    fun UnitTaskTest_Stability()
    {
        for (i in 1..1000) {
            UnitTaskTest()
        }
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

        val def1 = Task.Create {
            42
        }.Submit(ctx).result

        val def2 = Task.CreateDef {
            def1.Await()
        }.Submit(ctx).result

        VerifyDefResult(42, def2)

        ctx.Stop()
    }

    @Test
    fun Test4()
    {
        val ctx = ThreadContext("test")
        ctx.Start()

        val def1 = Task.Create {
            42
        }.Submit(ctx).result

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

        val def1 = Task.Create {
            println("in task 1")
            assertSame(ctx1.thread, Thread.currentThread())
            42
        }.Submit(ctx1).result

        val def2 = Task.CreateDef {
            println("in task 2, before suspend")
            assertSame(ctx1.thread, Thread.currentThread())
            val x = def1.Await(ctx2)
            println("in task 2, after suspend")
            assertSame(ctx2.thread, Thread.currentThread())
            ctx1.ResumeIn()
            println("in task 2, after context switch")
            assertSame(ctx1.thread, Thread.currentThread())
            x
        }.Submit(ctx1).result

        VerifyDefResult(42, def2)

        ctx1.Stop()
        ctx2.Stop()
    }

    @Test
    fun ThreadContinuationTest_Stability()
    {
        for (i in 1..1000) {
            ThreadContinuationTest()
        }
    }

    @Test
    fun ThreadContinuationErrorTest()
    {
        val ctx1 = ThreadContext("ctx1")
        ctx1.Start()
        val ctx2 = ThreadContext("ctx2")
        ctx2.Start()

        val def1 = Task.Create {
            println("in task 1")
            assertSame(ctx1.thread, Thread.currentThread())
            throw Exception("test")
        }.Submit(ctx1).result

        val def2 = Task.CreateDef {
            println("in task 2, before suspend")
            assertSame(ctx1.thread, Thread.currentThread())
            try {
                def1.Await(ctx2)
            } catch (e: Exception) {
                println("in task 2, after suspend")
                assertSame(ctx2.thread, Thread.currentThread())
                throw e
            }
        }.Submit(ctx1).result

        VerifyDefError("test", def2)

        ctx1.Stop()
        ctx2.Stop()
    }

    @Test
    fun ThreadContinuationErrorTest_Stability()
    {
        for (i in 1..1000) {
            ThreadContinuationErrorTest()
        }
    }

    @Test
    fun TimersTest1()
    {
        val ctx = ScheduledThreadContext("test")
        ctx.Start()

        val def1 = Task.Create {
            42
        }.Submit(ctx).result

        val def2 = Task.CreateDef {
            def1.Await()
        }.Submit(ctx).result

        VerifyDefResult(42, def2)

        ctx.Stop()
    }

    @Test
    fun TimersTest2()
    {
        val ctx = ScheduledThreadContext("test")
        ctx.Start()

        val task = Task.Create {
            42
        }
        val def1 = task.result
        ctx.SubmitScheduled(task, 1000)

        val def2 = Task.CreateDef {
            def1.Await()
        }.Submit(ctx).result

        VerifyDefResult(42, def2)

        ctx.Stop()
    }

    @Test
    fun TimersTest3()
    {
        val ctx = ScheduledThreadContext("test")
        ctx.Start()

        val def = Task.CreateDef {
            ctx.Delay(1000L)
            42
        }.Submit(ctx).result

        VerifyDefResult(42, def)

        ctx.Stop()
    }

    @Test
    fun WaitMultiple()
    {
        val ctx = ThreadContext("ctx")
        ctx.Start()

        val def1 = Task.Create {
            3
        }.Submit(ctx).result

        val def2 = Task.Create {
            5
        }.Submit(ctx).result

        val def3 = Task.Create {
            7
        }.Submit(ctx).result

        val def = Deferred.WhenAll(def1, def2, def3)

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

        val def1 = Task.Create {
            3
        }.Submit(ctx).result

        val def2 = Task.Create {
            throw Error("aaa")
        }.Submit(ctx).result

        val def3 = Task.Create {
            7
        }.Submit(ctx).result

        val def = Deferred.WhenAll(def1, def2, def3)

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
        TaskThrottler(8, false) {
            if (!it.hasNext()) {
                return@TaskThrottler null
            }
            return@TaskThrottler Deferred.ForResult(it.next())
        }.Run().WaitComplete()
        assertFalse(it.hasNext())
    }
}
