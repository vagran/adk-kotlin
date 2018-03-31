import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.coroutines.experimental.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class AsyncTest {

    @Test
    fun Basic()
    {
        var a = 0
        val b = suspend {
            a++
            Aaaa()
        }
        var result = 0
        b.createCoroutine(object: Continuation<Int> {
            override val context: CoroutineContext
                get() = EmptyCoroutineContext

            override fun resume(value: Int) {
                result = value
            }

            override fun resumeWithException(exception: Throwable) {
                throw Throwable("failed", exception)
            }
        }).resume(Unit)
    }

    private suspend fun Aaaa(): Int
    {
        suspendCoroutine {
            c: Continuation<Int> ->
            c.resumeWithException(Throwable("aaaa"))
        }
        return 42
    }
}