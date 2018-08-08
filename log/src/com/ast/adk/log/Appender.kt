package com.ast.adk.log

abstract class Appender(protected val pattern: Pattern,
                        protected val level: LogLevel) {

    abstract fun AppendMessage(msg: LogMessage)
}
