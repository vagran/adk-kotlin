package com.ast.adk.log

class ConsoleAppender(config: Configuration.Appender):
    Appender(GetPattern(config), config.level) {

    override fun AppendMessageImpl(msg: LogMessage)
    {
        stream.println(pattern!!.FormatMessage(msg))
        msg.exception?.also {
            it.printStackTrace(stream)
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val stream = when (config.consoleParams!!.target) {
        Configuration.Appender.ConsoleParams.Target.STDOUT -> System.out
        Configuration.Appender.ConsoleParams.Target.STDERR -> System.err
    }

    private companion object {
        fun GetPattern(config: Configuration.Appender): Pattern
        {
            return if (config.pattern == null) {
                Pattern(Configuration.DEFAULT_PATTERN)
            } else {
                Pattern(config.pattern!!)
            }
        }
    }
}
