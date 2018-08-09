package com.ast.adk.log

import java.time.format.DateTimeFormatter
import java.util.*

typealias RegExp = java.util.regex.Pattern

class Pattern(str: String) {

    val envMask = EnvMask()

    fun FormatMessage(msg: LogMessage): String
    {
        val args = Array(params.size) {
            idx ->
            params[idx].Extract(msg)
        }
        return msgFormat.format(*args)
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private companion object {
        val regExp = RegExp.compile("""(%\w+)|(%\{.+?})""")
    }

    private val msgFormat: String
    private val params: Array<ParamDesc>

    private enum class Reference(val refName: String,
                                 val isStringFormat: Boolean,
                                 val constString: String?,
                                 val resource: EnvMask.Resource?) {
        TIME("time", false, null, null),
        THREAD("thread", true, null, EnvMask.Resource.THREAD_NAME),
        LOGGER("logger", true, null, null),
        LEVEL("level", true, null, null),
        MESSAGE("msg", true, null, null),
        NEW_LINE("n", false, "%n", null);

        companion object {
            fun ByRefName(name: String): Reference
            {
                for (ref in values()) {
                    if (ref.refName == name) {
                        return ref
                    }
                }
                throw IllegalArgumentException("Bad reference in pattern: $name")
            }
        }
    }

    private class ParamDesc {
        lateinit var ref: Reference
        var format: String? = null
        var timeFormatter: DateTimeFormatter? = null

        fun Initialize()
        {
            if (ref == Reference.TIME) {
                if (format != null) {
                    timeFormatter = DateTimeFormatter.ofPattern(format)
                } else {
                    timeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                }
            }
        }

        fun Extract(msg: LogMessage): String
        {
            return when (ref) {
                Reference.TIME -> timeFormatter!!.format(msg.localTime)
                Reference.THREAD -> msg.threadName!!
                Reference.LOGGER -> msg.loggerName
                Reference.LEVEL -> msg.level.displayName
                Reference.MESSAGE -> msg.msg
                else -> throw Error("Unexpected reference type: $ref")
            }
        }
    }

    init {
        val msgFormat = StringBuilder()
        val params = ArrayList<ParamDesc>()
        val matcher = regExp.matcher(str)
        var curPos = 0
        while (true) {
            if (!matcher.find(curPos)) {
                break
            }
            val group1 = matcher.group(1)
            val group2 = matcher.group(2)
            val desc = ParamDesc()
            if (group1 != null) {
                desc.ref = Reference.ByRefName(group1.substring(1))
            } else {
                val sepIdx = group2.indexOf(':')
                if (sepIdx != -1) {
                    desc.ref = Reference.ByRefName(group2.substring(2, sepIdx))
                    desc.format = group2.substring(sepIdx + 1, group2.length - 1)
                } else {
                    desc.ref = Reference.ByRefName(group2.substring(2, group2.length - 1))
                }
            }
            msgFormat.append(str.substring(curPos, matcher.start(0)))
            if (desc.ref.constString != null) {
                msgFormat.append(desc.ref.constString)
            } else {
                msgFormat.append('%')
                if (desc.ref.isStringFormat && desc.format != null) {
                    msgFormat.append(desc.format)
                }
                msgFormat.append('s')
                desc.Initialize()
                params.add(desc)
            }
            curPos = matcher.end(0)
        }
        msgFormat.append(str.substring(curPos))
        this.msgFormat = msgFormat.toString()
        this.params = params.toTypedArray()

        for (param in params) {
            if (param.ref.resource != null) {
                envMask.Set(param.ref.resource!!)
            }
        }
    }
}
