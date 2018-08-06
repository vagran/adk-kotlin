import com.ast.adk.log.Configuration
import com.ast.adk.log.ParseDuration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals


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
}
