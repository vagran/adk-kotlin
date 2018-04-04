import com.ast.adk.async.Deferred
import com.ast.adk.utils.Log
import org.apache.logging.log4j.Logger
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.coroutines.experimental.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class AsyncTest {

    lateinit var log: Logger

    @BeforeAll
    fun Setup()
    {
        Log.InitTestLogging()
        log = Log.GetLogger("AsyncTest")
    }

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
            override val context: CoroutineContext = EmptyCoroutineContext

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


    @Test
    fun ContextTest()
    {
        log.debug("aaa")
        val def = Deferred.ForResult(42)
        def.Subscribe({ a, _ -> log.info(a) })

        val def2: Deferred<Int> = Deferred.ForError(Throwable("aaa"))
        def2.SetResult(42)
    }
}
