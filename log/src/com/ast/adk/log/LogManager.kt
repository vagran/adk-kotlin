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

        appenderThread.start()
    }

    fun Shutdown()
    {
        queue.Stop()
        appenderThread.join()
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

        return LoggerImpl(level, appenders, name)
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private lateinit var config: Configuration
    private lateinit var queue: LogQueue<LogMessage>
    private val appenders = TreeMap<String, Appender>()
    private val appenderThread = Thread(this::AppenderThreadFunc)

    inner class LoggerImpl(thresholdLevel: LogLevel,
                           private val appenders: List<Appender>,
                           private val loggerName: String):
        Logger(thresholdLevel) {

        override fun WriteLog(level: LogLevel, msgText: String, exception: Throwable?)
        {
            val msg = LogMessage()
            msg.SetTimestampNow()
            msg.level = level
            msg.msg = msgText
            msg.exception = exception
            msg.loggerName = loggerName
            msg.appenders = appenders
            if (envMask.IsSet(EnvMask.Resource.THREAD_NAME)) {
                msg.threadName = Thread.currentThread().name
            }
            queue.Push(msg)
        }

        private val envMask = EnvMask()

        init {
            for (appender in appenders) {
                envMask.Merge(appender.envMask)
            }
        }
    }

    private fun CreateAppender(appenderConfig: Configuration.Appender): Appender
    {
        return when (appenderConfig.type) {
            Configuration.Appender.Type.CONSOLE -> ConsoleAppender(appenderConfig)
            Configuration.Appender.Type.FILE -> FileAppender(appenderConfig)
        }
    }

    private fun AppenderThreadFunc()
    {
        while (true) {
            val msg = queue.Pop() ?: break
            for (appender in msg.appenders) {
                appender.AppendMessage(msg)
            }
        }
    }
}
