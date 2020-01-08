import com.ast.adk.async.Lock
import com.ast.adk.async.ReadWriteLock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.lang.Exception

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class LockTest {

    @Test
    fun LockTest()
    {
        val lock = Lock()
        assertEquals(42, lock.Synchronized {42}.Get())
        assertThrows(Exception::class.java) {
            lock.Synchronized<Int> { throw Error("test") }.Get()
        }
    }

    @Test
    fun ReadWriteLockTest()
    {
        val lock = ReadWriteLock()
        assertEquals(42, lock.SynchronizedRead {42}.Get())
        assertEquals(42, lock.SynchronizedWrite {42}.Get())
        assertThrows(Exception::class.java) {
            lock.SynchronizedRead<Int> { throw Error("test") }.Get()
        }
        assertThrows(Exception::class.java) {
            lock.SynchronizedWrite<Int> { throw Error("test") }.Get()
        }
    }
}
