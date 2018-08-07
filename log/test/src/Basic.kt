import com.ast.adk.log.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Basic {

    @Test
    fun BasicTest()
    {
        //language=JSON5
        val configStr = """
{
    "appenders": {
        "myConsoleAppender": {
            "type": "console",
            "target": "stdout",
            "pattern": "%{time:HH:mm:ss.SSS} [%thread] %{level:-5} %logger - %msg%n",
            "level": "trace"
        },
        "myFileAppender": {
            "type": "file",
            "path": "/tmp/adk-log-test.log",
            "maxSize": "100M",
            "maxTime": "1d",
            "level": "info"
        }
    },
    "loggers": {
        "root": {
            "level": "debug",
            "appenders": ["myConsoleAppender", "myFileAppender"]
        },
        "my.test.logger": {
            "level": "warning"
        },
        "my.test": {
            "level": "info"
        }
    }
}
        """
        val config = Configuration.FromJson(configStr)

        val log = Logger()
        log.Info("%d %f", 42, 13.0)
    }

    @Test
    fun ParseDurationTest()
    {
        val d = ParseDuration("1d 2h3m 4s")
        assertEquals(1, d.toDays())
        assertEquals(2, d.toHoursPart())
        assertEquals(3, d.toMinutesPart())
        assertEquals(4, d.toSecondsPart())
    }

    @Test
    fun ParseSizeTest()
    {
        assertEquals(42L, ParseSize("42"))
        assertEquals(42L * 1024L, ParseSize("42k"))
        assertEquals(42L * 1024L * 1024L, ParseSize("42M"))
        assertEquals(42L * 1024L * 1024L * 1024, ParseSize("42G"))
    }

    @Test
    fun LogQueueTest()
    {
        val queue = LogQueue<Int>(1000, true)

        val totalMessages = 1_000_000

        var consumerError: Throwable? = null
        val consumer = thread(name = "consumer") {
            try {
                for (i in 1..totalMessages) {
                    val msg = queue.Pop()
                    assertEquals(i, msg)
                }
                assertNull(queue.Pop())
            } catch (e: Throwable) {
                consumerError = e
            }
        }

        var producerError: Throwable? = null
        val producer = thread(name = "producer") {
            try {
                for (i in 1..totalMessages) {
                    assertTrue(queue.Push(i))
                }
            } catch (e: Throwable) {
                producerError = e
            }
        }

        producer.join()
        queue.Stop()
        consumer.join()

        assertNull(producerError)
        assertNull(consumerError)
    }

    @Test
    fun LogQueueConcurrentTest()
    {
        val queue = LogQueue<Int>(1000, true)

        val totalMessages = 10_000_000
        val numThreads = 4
        val numProduced = AtomicInteger()
        var producerError: Throwable? = null
        val producers = Array(numThreads) {
            idx ->
            thread(name = "producer-$idx") {
                try {
                    while (true) {
                        val msg = numProduced.incrementAndGet()
                        if (msg > totalMessages) {
                            break
                        }
                        queue.Push(msg)
                    }
                } catch (e: Throwable) {
                    producerError = e
                }
            }
        }

        var consumerError: Throwable? = null
        val numConsumed = AtomicInteger()
        val consumers = Array(numThreads) {
            idx ->
            thread(name = "consumer-$idx") {
                try {
                    while (true) {
                        queue.Pop() ?: break
                        numConsumed.incrementAndGet()
                    }
                } catch (e: Throwable) {
                    consumerError = e
                }
            }
        }

        for (i in 0 until numThreads) {
            producers[i].join()
        }
        queue.Stop()
        for (i in 0 until numThreads) {
            consumers[i].join()
        }

        assertEquals(totalMessages, numConsumed.get())
        assertNull(producerError)
        assertNull(consumerError)
    }
}
