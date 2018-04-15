
import com.ast.adk.async.Deferred
import com.ast.adk.async.observable.Observable
import com.ast.adk.async.observable.ObservableSourceFunc
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class ObservableTest {

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

    fun F(h: ObservableSourceFunc<Int>)
    {

    }

    class A {
        operator fun invoke(): Deferred<Observable.Value<Int>>?
        {
            return null
        }
    }

    fun X()
    {
        F(A()::invoke)
    }
}
