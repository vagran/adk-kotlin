package com.ast.adk.log

import java.util.*
import kotlin.collections.ArrayList

class LogManager {

    fun Initialize(config: Configuration)
    {
        this.config = config
        queue = LogQueue(config.settings.queueSize,
                         config.settings.overflowBlocks,
                         config.settings.queueCheckInterval)

        for (appenderConfig in config.appenders) {
            appenders[appenderConfig.name] = CreateAppender(appenderConfig)
        }
    }

    fun Shutdown()
    {
        queue.Stop()
    }

    fun GetLogger(name: String): Logger
    {
        val loggerParams = config.ConfigureLogger(name)

        var level = LogLevel.MAX
        for (appender in loggerParams.appenders) {
            if (appender.level != null && appender.level!! < level) {
                level = appender.level!!
            }
        }
        if (loggerParams.level > level) {
            level = loggerParams.level
        }

        val appenders = ArrayList<Appender>()
        synchronized(this.appenders) {
            for (appenderConfig in loggerParams.appenders) {
                appenders.add(this.appenders[appenderConfig.name]!!)
            }
        }

        //XXX
        return LoggerImpl(level)
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private lateinit var config: Configuration
    private lateinit var queue: LogQueue<LogMessage>
    private val appenders = TreeMap<String, Appender>()

    inner class LoggerImpl(thresholdLevel: LogLevel):
        Logger(thresholdLevel) {

        override fun WriteLog(level: LogLevel, msg: String, exception: Throwable?)
        {

            TODO("not implemented") //XXX
        }

    }

    private fun CreateAppender(appenderConfig: Configuration.Appender): Appender
    {
        TODO()
    }
}
