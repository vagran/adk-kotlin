import com.ast.di.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertSame


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiTest {
    @Test
    fun Basic()
    {
        val c = DI.CreateComponent(Comp::class.java)
        assertEquals(42, c.i1.GetInt())
        val b = c.bFactory.Create(43)
        assertEquals(42, b.i1.GetInt())
        assertEquals(43, b.value)
        assertSame(c.i1, b.i1);
        assertEquals(42, c.GetI1_1().GetInt())
    }
}

interface I1 {
    fun GetInt(): Int
}

class A(private val value: Int): I1 {

    override fun GetInt(): Int
    {
        return value;
    }
}

class B @Inject constructor(val i1: I1, @FactoryParam val value: Int)

@Module
class M {

    @Provides
    @Singleton
    fun GetI1(): I1 = A(42)
}

@Component(modules = [M::class])
internal class Comp {
    @Inject lateinit var i1: I1
    @Inject private lateinit var i1_1: I1
    @Inject lateinit var bFactory: DiFactory<B>

    fun GetI1_1() = i1_1
}