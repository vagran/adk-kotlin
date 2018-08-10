package com.ast.adk.log

abstract class Logger(val name: String,
                      val thresholdLevel: LogLevel) {

    fun Trace(msg: String)
    {
        if (LogLevel.TRACE < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.TRACE, null, msg)
    }

    fun Trace(msg: String, vararg params: Any?)
    {
        if (LogLevel.TRACE < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.TRACE, null, java.lang.String.format(msg, *params))
    }

    fun Trace(e: Throwable?, msg: String)
    {
        if (LogLevel.TRACE < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.TRACE, e, msg)
    }

    fun Trace(e: Throwable?, msg: String, vararg params: Any?)
    {
        if (LogLevel.TRACE < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.TRACE, e, java.lang.String.format(msg, *params))
    }

    fun Trace(msgFunc: () -> String)
    {
        if (LogLevel.TRACE < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.TRACE, null, msgFunc())
    }

    fun Trace(e: Throwable?, msgFunc: () -> String)
    {
        if (LogLevel.TRACE < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.TRACE, e, msgFunc())
    }


    fun Debug(msg: String)
    {
        if (LogLevel.DEBUG < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.DEBUG, null, msg)
    }

    fun Debug(msg: String, vararg params: Any?)
    {
        if (LogLevel.DEBUG < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.DEBUG, null, java.lang.String.format(msg, *params))
    }

    fun Debug(e: Throwable?, msg: String)
    {
        if (LogLevel.DEBUG < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.DEBUG, e, msg)
    }

    fun Debug(e: Throwable?, msg: String, vararg params: Any?)
    {
        if (LogLevel.DEBUG < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.DEBUG, e, java.lang.String.format(msg, *params))
    }

    fun Debug(msgFunc: () -> String)
    {
        if (LogLevel.DEBUG < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.DEBUG, null, msgFunc())
    }

    fun Debug(e: Throwable?, msgFunc: () -> String)
    {
        if (LogLevel.DEBUG < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.DEBUG, e, msgFunc())
    }


    fun Info(msg: String)
    {
        if (LogLevel.INFO < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.INFO, null, msg)
    }

    fun Info(msg: String, vararg params: Any?)
    {
        if (LogLevel.INFO < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.INFO, null, java.lang.String.format(msg, *params))
    }

    fun Info(e: Throwable?, msg: String)
    {
        if (LogLevel.INFO < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.INFO, e, msg)
    }

    fun Info(e: Throwable?, msg: String, vararg params: Any?)
    {
        if (LogLevel.INFO < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.INFO, e, java.lang.String.format(msg, *params))
    }

    fun Info(msgFunc: () -> String)
    {
        if (LogLevel.INFO < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.INFO, null, msgFunc())
    }

    fun Info(e: Throwable?, msgFunc: () -> String)
    {
        if (LogLevel.INFO < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.INFO, e, msgFunc())
    }


    fun Warning(msg: String)
    {
        if (LogLevel.WARNING < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.WARNING, null, msg)
    }

    fun Warning(msg: String, vararg params: Any?)
    {
        if (LogLevel.WARNING < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.WARNING, null, java.lang.String.format(msg, *params))
    }

    fun Warning(e: Throwable?, msg: String)
    {
        if (LogLevel.WARNING < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.WARNING, e, msg)
    }

    fun Warning(e: Throwable?, msg: String, vararg params: Any?)
    {
        if (LogLevel.WARNING < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.WARNING, e, java.lang.String.format(msg, *params))
    }

    fun Warning(msgFunc: () -> String)
    {
        if (LogLevel.WARNING < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.WARNING, null, msgFunc())
    }

    fun Warning(e: Throwable?, msgFunc: () -> String)
    {
        if (LogLevel.WARNING < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.WARNING, e, msgFunc())
    }


    fun Error(msg: String)
    {
        if (LogLevel.ERROR < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.ERROR, null, msg)
    }

    fun Error(msg: String, vararg params: Any?)
    {
        if (LogLevel.ERROR < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.ERROR, null, java.lang.String.format(msg, *params))
    }

    fun Error(e: Throwable?, msg: String)
    {
        if (LogLevel.ERROR < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.ERROR, e, msg)
    }

    fun Error(e: Throwable?, msg: String, vararg params: Any?)
    {
        if (LogLevel.ERROR < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.ERROR, e, java.lang.String.format(msg, *params))
    }

    fun Error(msgFunc: () -> String)
    {
        if (LogLevel.ERROR < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.ERROR, null, msgFunc())
    }

    fun Error(e: Throwable?, msgFunc: () -> String)
    {
        if (LogLevel.ERROR < thresholdLevel) {
            return
        }
        WriteLog(LogLevel.ERROR, e, msgFunc())
    }


    fun Log(level: LogLevel, exception: Throwable?, msg: String)
    {
        if (level < thresholdLevel) {
            return
        }
        WriteLog(level, exception, msg)
    }

    fun Log(level: LogLevel, exception: Throwable?, msgFunc: () -> String)
    {
        if (level < thresholdLevel) {
            return
        }
        WriteLog(level, exception, msgFunc())
    }

    protected abstract fun WriteLog(level: LogLevel, exception: Throwable?, msgText: String)
}
