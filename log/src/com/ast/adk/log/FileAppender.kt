package com.ast.adk.log

import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class FileAppender(config: Configuration.Appender):
    Appender(GetPattern(config), config.level) {

    override fun AppendMessageImpl(msg: LogMessage)
    {
        file.write(pattern!!.FormatMessage(msg))
        file.newLine()
        msg.exception?.also {
            it.printStackTrace(printWriter)
        }
    }

    override fun Close()
    {
        file.close()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    val file = Files.newBufferedWriter(config.fileParams!!.path,
                                       StandardOpenOption.WRITE,
                                       StandardOpenOption.APPEND,
                                       StandardOpenOption.CREATE)
    val printWriter = PrintWriter(file)

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
