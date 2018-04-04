
import com.ast.adk.async.Task
import com.ast.adk.utils.Log
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class TaskTest {

    @BeforeAll
    fun Setup()
    {
        Log.InitTestLogging()
    }

    @Test
    fun Test1()
    {

        Task.Create({
            42
        })
    }
}
