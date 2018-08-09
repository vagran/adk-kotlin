package com.ast.adk.log

class FileAppender(config: Configuration.Appender):
    Appender(GetPattern(config), config.level) {

    override fun AppendMessageImpl(msg: LogMessage)
    {
        //XXX
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

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
