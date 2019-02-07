import com.ast.adk.log.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import kotlin.concurrent.thread


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Basic {

    //language=JSON5
    private val testConfigStr = """
{
    "settings": {
        "queueSize": 10000,
        "queueCheckInterval": 100,
        "overflowBlocks": true
    },
    "appenders": {
        "myConsoleAppender": {
            "type": "console",
            "target": "stdout",
            "pattern": "%{time:HH:mm:ss.SSS} [%thread] %{level:-5} %logger - %msg",
            "level": "trace"
        },
        "myFileAppender": {
            "type": "file",
            "path": "/tmp/adk-log-test.log",
            "maxSize": "100M",
            "maxTime": "1d",
            "level": "info"
        },
        "fastRollSize": {
            "type": "file",
            "path": "/tmp/adk-log-test-rolling-size.log",
            "maxSize": "10k"
        },
        "fastRollTime": {
            "type": "file",
            "path": "/tmp/adk-log-test-rolling-time.log",
            "maxTime": "10s",
            "compressOld": true,
            "preserveNum": 3
        }
    },
    "loggers": {
        "root": {
            "level": "debug",
            "appenders": ["myConsoleAppender", "myFileAppender"]
        },
        "my.test.logger": {
            "level": "warning",
            "appenders": ["myConsoleAppender"],
            "additiveAppenders": false
        },
        "my.test": {
            "level": "info"
        },
        "logger.console": {
            "level": "trace",
            "appenders": ["myConsoleAppender"],
            "additiveAppenders": false
        },
        "logger.fastRollSize": {
            "level": "trace",
            "appenders": ["myConsoleAppender", "fastRollSize"],
            "additiveAppenders": false
        },
        "logger.fastRollTime": {
            "level": "trace",
            "appenders": ["myConsoleAppender", "fastRollTime"],
            "additiveAppenders": false
        }
    }
}
"""

    @Test
    fun BasicTest()
    {
        val config = LogConfiguration.FromJson(testConfigStr)
        val logManager = LogManager()
        logManager.Initialize(config)
        val log = logManager.GetLogger("my.test.aaa")
        log.Info(Throwable("aaa"), "%d %f", 42, 13.0)
        logManager.Shutdown()
    }

    @Test
    fun DefaultLogTest()
    {
        val logManager = LogManager()
        logManager.Initialize(LogConfiguration.Default())
        val log = logManager.GetLogger("my.test.aaa")
        log.Info("%d %f", 42, 13.0)
        logManager.Shutdown()
    }

//    @Test
//    fun RollBySizeTest()
//    {
//        val config = LogConfiguration.FromJson(testConfigStr)
//        val logManager = LogManager()
//        logManager.Initialize(config)
//        val log = logManager.GetLogger("logger.fastRollSize")
//        for (i in 1..140) {
//            log.Info(Throwable("aaa"), "%d %f", 42, 13.0)
//            Thread.sleep(1000)
//        }
//        logManager.Shutdown()
//    }
//
//    @Test
//    fun RollByTimeTest()
//    {
//        val config = LogConfiguration.FromJson(testConfigStr)
//        val logManager = LogManager()
//        logManager.Initialize(config)
//        val log = logManager.GetLogger("logger.fastRollTime")
//        for (i in 1..140) {
//            log.Info(Throwable("aaa"), "%d %f", 42, 13.0)
//            Thread.sleep(1000)
//        }
//        logManager.Shutdown()
//    }

    @Test
    fun RedirectStderrTest()
    {
        val logManager = LogManager()
        logManager.Initialize(LogConfiguration.Default())
        logManager.RedirectStderr("logger.console")
        System.err.println("Test stderr message")
        logManager.Shutdown()
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
    fun VolatileQueueTest()
    {
        val queue = VolatileQueue<Int>(100)
        assertEquals(0, queue.size)
        for (j in 1..3) {
            for (i in 1..100) {
                queue.Push(i)
                assertEquals(i, queue.size)
            }
            for (i in 1..100) {
                val item = queue.Pop()
                assertEquals(i, item)
                assertEquals(100 - i, queue.size)
            }
        }
    }

    private fun TestQueue(queueSize: Int, numThreads: Int, totalMessages: Int,
                          emptyCheckInterval: Long)
    {
        val queue = LogQueue<Int>(queueSize, true, emptyCheckInterval)

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
                        assertTrue(queue.Push(msg))
                    }
                } catch (e: Throwable) {
                    producerError = e
                }
            }
        }

        var consumerError: Throwable? = null
        val numConsumed = AtomicInteger()
        val consumer = thread(name = "consumer") {
            try {
                while (true) {
                    queue.Pop() ?: break
                    numConsumed.incrementAndGet()
                }
            } catch (e: Throwable) {
                consumerError = e
            }
        }

        for (i in 0 until numThreads) {
            producers[i].join()
        }
        queue.Stop()
        consumer.join()

        println("numProduced: ${numProduced.get()}")
        if (producerError != null) {
            System.err.println("Producer error:")
            producerError!!.printStackTrace()
        }
        if (consumerError != null) {
            System.err.println("Consumer error:")
            producerError!!.printStackTrace()
        }
        assertEquals(totalMessages, numConsumed.get())
        assertNull(producerError)
        assertNull(consumerError)
    }

    @Test
    fun LogQueueTest()
    {
        TestQueue(1000, 1, 1_000_000, 100)
    }

    @Test
    fun LogQueueConcurrentTest()
    {
        TestQueue(1000, 4, 10_000_000, 100)
    }

    @Test
    fun LogQueuePartialTest()
    {
        TestQueue(1_000_000, 1, 900_000, 100)
    }

    @Test
    fun LogQueuePartialConcurrentTest()
    {
        TestQueue(10_000_000, 4, 9_900_000, 100)
    }

    @Test
    fun LoggerNameTest()
    {
        assertEquals(0, LoggerName("").components.size)
        assertEquals(listOf("test"), LoggerName("test").components)
        assertEquals(listOf("test"), LoggerName("...test...").components)
        assertEquals(listOf("test", "comp1", "comp2"),
                     LoggerName("test.comp1.comp2").components)
        assertEquals(listOf("test", "comp1", "comp2"),
                     LoggerName(".test..comp1.comp2.").components)
    }

    @Test
    fun PatternTest()
    {
        val pat = Pattern("%{time:HH:mm:ss.SSS} [%thread] %{level:-5} %logger - %msg%n")
        val msg = LogMessage()
        val time = OffsetDateTime.of(
            LocalDateTime.of(LocalDate.of(2000, 2, 5), LocalTime.of(15, 20, 30, 423_444_444)),
            ZoneOffset.ofHours(2))
        msg.timestamp = time.toInstant()
        msg.level = LogLevel.WARNING
        msg.msg = "Test message"
        msg.loggerName = "TestLogger"
        msg.threadName = "TestThread"

        assertEquals("15:20:30.423 [TestThread] WARN  TestLogger - Test message\n",
                     pat.FormatMessage(msg))
    }

    @Test
    fun JavaUtilLoggingTest()
    {
        java.util.logging.Logger.getLogger("TestLogger")
            .log(Level.WARNING, "aaa {0} {1}", arrayOf(42, "abc"))

        val config = LogConfiguration.FromJson(testConfigStr)
        val logManager = LogManager()
        logManager.Initialize(config)
        logManager.RedirectBuiltinLog("internal")

        val logger = java.util.logging.Logger.getLogger("TestLogger")
        logger.level = Level.ALL
        logger.log(Level.WARNING, "Test message {0} {1}", arrayOf(42, "abc"))

        logManager.Shutdown()
    }
}
