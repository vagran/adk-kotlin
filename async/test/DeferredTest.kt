import com.ast.adk.async.Deferred
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.fail
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.coroutines.experimental.createCoroutine
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class DeferredTest {

    @Test
    fun Callbacks1()
    {
        val def = Deferred.ForResult(42)
        var cbkRes: Int? = null
        def.Subscribe({
            r, e ->
            cbkRes = r
            assertNull(e)
        })
        assertEquals(42, cbkRes)
    }

    @Test
    fun Callbacks2()
    {
        val def: Deferred<Int> = Deferred.Create()
        var cbkRes: Int? = null
        def.Subscribe({
            r, e ->
            cbkRes = r
            assertNull(e)
        })
        def.SetResult(42)
        assertEquals(42, cbkRes)
    }

    @Test
    fun Callbacks3()
    {
        val def: Deferred<Int> = Deferred.ForError(Throwable("aaa"))
        var cbkE: Throwable? = null
        def.Subscribe({
            r, e ->
            assertNull(r)
            cbkE = e
        })
        assertEquals("aaa", cbkE!!.message)
    }

    @Test
    fun Callbacks4()
    {
        val def: Deferred<Int> = Deferred.Create()
        var cbkE: Throwable? = null
        def.Subscribe({
            r, e ->
            assertNull(r)
            cbkE = e
        })
        def.SetError(Throwable("aaa"))
        assertEquals("aaa", cbkE!!.message)
    }

    @Test
    fun CallbacksNull1()
    {
        val def: Deferred<Int?> = Deferred.ForResult(null)
        var cbkRes: Int? = 42
        def.Subscribe({
            r, e ->
            cbkRes = r
            assertNull(e)
        })
        assertEquals(null, cbkRes)
    }

    @Test
    fun CallbacksNull2()
    {
        val def: Deferred<Int?> = Deferred.Create()
        var cbkRes: Int? = 42
        def.Subscribe({
            r, e ->
            cbkRes = r
            assertNull(e)
        })
        def.SetResult(null)
        assertEquals(null, cbkRes)
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
            override val context: CoroutineContext
                get() = EmptyCoroutineContext

            override fun resume(value: Unit) {
                done = true
            }

            override fun resumeWithException(exception: Throwable) {
                fail(exception)
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
            override val context: CoroutineContext
                get() = EmptyCoroutineContext

            override fun resume(value: Unit) {
                done = true
            }

            override fun resumeWithException(exception: Throwable) {
                resError = exception
                done = true
            }
        }).resume(Unit)
        assertFalse(done)
        assertNull(res)
        def.SetError(Throwable("aaa"))
        assertTrue(done)
        assertNull(res)
        assertEquals("aaa", resError!!.message)
    }
}