package com.ast.adk.log

import java.io.PrintStream
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

        isInitialized = true
    }

    fun Shutdown()
    {
        queue.Stop()
        appenderThread.join()
        for (appender in appenders.values) {
            appender.Close()
        }
    }

    fun GetLogger(name: String): Logger
    {
        return synchronized(loggers) {
            loggers.computeIfAbsent(name) { CreateLogger(name) }
        }
    }

    fun RedirectStderr(loggerName: String = "STDERR", level: LogLevel = LogLevel.ERROR)
    {
        System.setErr(PrintStream(LoggingOutputStream(GetLogger(loggerName), level), true))
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var isInitialized = false
    private lateinit var config: Configuration
    private lateinit var queue: LogQueue<LogMessage>
    private val appenders = TreeMap<String, Appender>()
    private val appenderThread = Thread(this::AppenderThreadFunc, "AdkLogAppender")
    private val loggers = HashMap<String, Logger>()

    inner class LoggerImpl(name: String,
                           thresholdLevel: LogLevel,
                           private val appenders: List<Appender>):
        Logger(name, thresholdLevel) {

        override fun WriteLog(level: LogLevel, exception: Throwable?, msgText: String)
        {
            val msg = LogMessage()
            msg.SetTimestampNow()
            msg.level = level
            msg.msg = msgText
            msg.exception = exception
            msg.loggerName = name
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

    private fun CreateLogger(name: String): Logger
    {
        if (!isInitialized) {
            throw Error("Attempting to get logger before initialized: $name")
        }
        val loggerParams = config.ConfigureLogger(name)

        var level: LogLevel? = null
        for (appender in loggerParams.appenders) {
            if (level == null || (appender.level != null && appender.level!! < level)) {
                level = appender.level
            }
        }
        if (level == null || loggerParams.level > level) {
            level = loggerParams.level
        }

        val appenders = ArrayList<Appender>()
        for (appenderConfig in loggerParams.appenders) {
            appenders.add(this.appenders[appenderConfig.name]!!)
        }

        return LoggerImpl(name, level, appenders)
    }
}
