package com.ast.adk.log

class Logger {

    fun Trace(msg: String)
    {
        if (LogLevel.TRACE < thresholdLevel) {
            return
        }
        _Log(LogLevel.TRACE, msg, null)
    }

    fun Trace(msg: String, vararg params: Any?)
    {
        if (LogLevel.TRACE < thresholdLevel) {
            return
        }
        _Log(LogLevel.TRACE, java.lang.String.format(msg, *params), null)
    }

    fun Trace(e: Throwable, msg: String)
    {
        if (LogLevel.TRACE < thresholdLevel) {
            return
        }
        _Log(LogLevel.TRACE, msg, e)
    }

    fun Trace(e: Throwable, msg: String, vararg params: Any?)
    {
        if (LogLevel.TRACE < thresholdLevel) {
            return
        }
        _Log(LogLevel.TRACE, java.lang.String.format(msg, *params), e)
    }


    fun Debug(msg: String)
    {
        if (LogLevel.DEBUG < thresholdLevel) {
            return
        }
        _Log(LogLevel.DEBUG, msg, null)
    }

    fun Debug(msg: String, vararg params: Any?)
    {
        if (LogLevel.DEBUG < thresholdLevel) {
            return
        }
        _Log(LogLevel.DEBUG, java.lang.String.format(msg, *params), null)
    }

    fun Debug(e: Throwable, msg: String)
    {
        if (LogLevel.DEBUG < thresholdLevel) {
            return
        }
        _Log(LogLevel.DEBUG, msg, e)
    }

    fun Debug(e: Throwable, msg: String, vararg params: Any?)
    {
        if (LogLevel.DEBUG < thresholdLevel) {
            return
        }
        _Log(LogLevel.DEBUG, java.lang.String.format(msg, *params), e)
    }


    fun Info(msg: String)
    {
        if (LogLevel.INFO < thresholdLevel) {
            return
        }
        _Log(LogLevel.INFO, msg, null)
    }

    fun Info(msg: String, vararg params: Any?)
    {
        if (LogLevel.INFO < thresholdLevel) {
            return
        }
        _Log(LogLevel.INFO, java.lang.String.format(msg, *params), null)
    }

    fun Info(e: Throwable, msg: String)
    {
        if (LogLevel.INFO < thresholdLevel) {
            return
        }
        _Log(LogLevel.INFO, msg, e)
    }

    fun Info(e: Throwable, msg: String, vararg params: Any?)
    {
        if (LogLevel.INFO < thresholdLevel) {
            return
        }
        _Log(LogLevel.INFO, java.lang.String.format(msg, *params), e)
    }


    fun Warning(msg: String)
    {
        if (LogLevel.WARNING < thresholdLevel) {
            return
        }
        _Log(LogLevel.WARNING, msg, null)
    }

    fun Warning(msg: String, vararg params: Any?)
    {
        if (LogLevel.WARNING < thresholdLevel) {
            return
        }
        _Log(LogLevel.WARNING, java.lang.String.format(msg, *params), null)
    }

    fun Warning(e: Throwable, msg: String)
    {
        if (LogLevel.WARNING < thresholdLevel) {
            return
        }
        _Log(LogLevel.WARNING, msg, e)
    }

    fun Warning(e: Throwable, msg: String, vararg params: Any?)
    {
        if (LogLevel.WARNING < thresholdLevel) {
            return
        }
        _Log(LogLevel.WARNING, java.lang.String.format(msg, *params), e)
    }


    fun Error(msg: String)
    {
        if (LogLevel.ERROR < thresholdLevel) {
            return
        }
        _Log(LogLevel.ERROR, msg, null)
    }

    fun Error(msg: String, vararg params: Any?)
    {
        if (LogLevel.ERROR < thresholdLevel) {
            return
        }
        _Log(LogLevel.ERROR, java.lang.String.format(msg, *params), null)
    }

    fun Error(e: Throwable, msg: String)
    {
        if (LogLevel.ERROR < thresholdLevel) {
            return
        }
        _Log(LogLevel.ERROR, msg, e)
    }

    fun Error(e: Throwable, msg: String, vararg params: Any?)
    {
        if (LogLevel.ERROR < thresholdLevel) {
            return
        }
        _Log(LogLevel.ERROR, java.lang.String.format(msg, *params), e)
    }


    fun Log(level: LogLevel, msg: String, exception: Throwable?)
    {
        if (level < thresholdLevel) {
            return
        }
        _Log(level, msg, exception)
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var thresholdLevel: LogLevel = LogLevel.TRACE

    private fun _Log(level: LogLevel, msg: String, exception: Throwable?)
    {

    }
}
