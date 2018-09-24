import com.ast.adk.async.Deferred
import com.ast.adk.async.Map
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.fail
import kotlin.coroutines.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class DeferredTest {

    @Test
    fun Callbacks1()
    {
        val def = Deferred.ForResult(42)
        var cbkRes: Int? = null
        def.Subscribe {
            r, e ->
            cbkRes = r
            assertNull(e)
        }
        assertEquals(42, cbkRes)
    }

    @Test
    fun Callbacks2()
    {
        val def: Deferred<Int> = Deferred.Create()
        var cbkRes: Int? = null
        def.Subscribe {
            r, e ->
            cbkRes = r
            assertNull(e)
        }
        def.SetResult(42)
        assertEquals(42, cbkRes)
    }

    @Test
    fun Callbacks3()
    {
        val def: Deferred<Int> = Deferred.ForError(Throwable("aaa"))
        var cbkE: Throwable? = null
        def.Subscribe {
            r, e ->
            assertNull(r)
            cbkE = e
        }
        assertEquals("aaa", cbkE!!.message)
    }

    @Test
    fun Callbacks4()
    {
        val def: Deferred<Int> = Deferred.Create()
        var cbkE: Throwable? = null
        def.Subscribe {
            r, e ->
            assertNull(r)
            cbkE = e
        }
        def.SetError(Throwable("aaa"))
        assertEquals("aaa", cbkE!!.message)
    }

    @Test
    fun CallbacksNull1()
    {
        val def: Deferred<Int?> = Deferred.ForResult(null)
        var cbkRes: Int? = 42
        def.Subscribe {
            r, e ->
            cbkRes = r
            assertNull(e)
        }
        assertNull(cbkRes)
    }

    @Test
    fun CallbacksNull2()
    {
        val def: Deferred<Int?> = Deferred.Create()
        var cbkRes: Int? = 42
        def.Subscribe {
            r, e ->
            cbkRes = r
            assertNull(e)
        }
        def.SetResult(null)
        assertNull(cbkRes)
    }

    @Test
    fun Async1()
    {
        val def: Deferred<Int> = Deferred.Create()
        var res: Int? = null
        var done = false
        suspend {
            res = def.Await()
        }.createCoroutine(object: Continuation<Unit> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<Unit>)
            {
                result.fold({ done = true }, { fail(it) })
            }
        }).resume(Unit)
        assertFalse(done)
        assertNull(res)
        def.SetResult(42)
        assertTrue(done)
        assertEquals(42, res)
    }

    @Test
    fun Async2()
    {
        val def: Deferred<Int> = Deferred.Create()
        var res: Int? = null
        var resError: Throwable? = null
        var done = false
        suspend {
            res = def.Await()
        }.createCoroutine(object: Continuation<Unit> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<Unit>)
            {
                result.fold({ done = true },
                            { resError = it; done = true })
            }
        }).resume(Unit)
        assertFalse(done)
        assertNull(res)
        def.SetError(Throwable("aaa"))
        assertTrue(done)
        assertNull(res)
        assertEquals("aaa", resError!!.message)
    }

    @Test
    fun AsyncNull()
    {
        val def: Deferred<Int?> = Deferred.Create()
        var res: Int? = null
        var done = false
        suspend {
            res = def.Await()
        }.createCoroutine(object: Continuation<Unit> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<Unit>)
            {
                result.fold({ done = true }, { fail(it) })
            }
        }).resume(Unit)
        assertFalse(done)
        assertNull(res)
        def.SetResult(null)
        assertTrue(done)
        assertNull(res)
    }

    @Test
    fun MapTest()
    {
        val def = Deferred.ForResult(42)
        var cbkRes: String? = null
        def.Map { x -> (x + 10).toString() }
            .Subscribe {
            r, e ->
            cbkRes = r
            assertNull(e)
        }
        assertEquals("52", cbkRes)
    }

    @Test
    fun MapErrorTest()
    {
        val def = Deferred.ForError<Int>(Error("test"))
        var cbkRes: String? = null
        def.Map {
            _, e ->
            assertNotNull(e)
            return@Map e?.message
        }
            .Subscribe {
                r, e ->
                cbkRes = r
                assertNull(e)
            }
        assertEquals("test", cbkRes)
    }
}
