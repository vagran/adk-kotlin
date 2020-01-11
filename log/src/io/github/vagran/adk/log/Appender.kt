package io.github.vagran.adk.log

abstract class Appender(protected val pattern: Pattern?,
                        protected val level: LogLevel?) {

    open val envMask: EnvMask
        get() = pattern?.envMask ?: EnvMask()

    fun AppendMessage(msg: LogMessage)
    {
        if (level == null || msg.level >= level) {
            AppendMessageImpl(msg)
        }
    }

    abstract fun AppendMessageImpl(msg: LogMessage)

    open fun Close() {}
}
