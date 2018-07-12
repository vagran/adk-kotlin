package com.ast.argparser

import com.ast.argparser.ArgParser.Option.Companion.SplitString
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.*
import kotlin.reflect.jvm.jvmErasure

class UsageError(message: String, cause: Throwable? = null): Exception(message, cause)

class ArgParser(args: Array<String>) {

    constructor(args: String): this(SplitString(args))

    inline fun <reified T: Any> MapOptions(): T
    {
        return MapOptions(T::class)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> MapOptions(cls: KClass<T>): T
    {
        var obj: T? = null
        for (ctr in cls.constructors) {
            if (ctr.visibility == KVisibility.PUBLIC && ctr.parameters.isEmpty()) {
                obj = ctr.call()
                break
            }
        }
        if (obj == null) {
            throw Error("No public default constructor found for the specified class: " +
                        cls.simpleName)
        }
        for (option in Option.ForClass(cls)) {
            val args = PopOptions(option)

            if (option.ann != null && option.ann.required && !option.hasArg) {
                throw Error("Option without arguments should not be required: ${option.propName}")
            }

            if (option.ann != null && option.ann.required && args.isEmpty()) {
                throw UsageError("Required option not specified: ${option.GetName()}")
            }

            if (option.ann != null && option.ann.count) {
                option.SetCount(obj, args.size)
                continue
            }

            if (!option.hasArg) {
                option.SetFlag(obj, !args.isEmpty())
                continue
            }

            if (args.isEmpty()) {
                continue
            }

            if (option.isCollection) {
                option.SetCollection(obj, args)
                continue
            }

            if (args.size > 1) {
                UsageError("Only one value allowed for option ${option.GetName()}")
            }
            option.SetValue(obj, args[0])
        }
        return obj
    }

    /**
     * The positional arguments are searched before still unprocessed options.
     * @param maxCount -1 for no limit.
     */
    fun GetPositionalArguments(minCount: Int, maxCount: Int = -1): List<String>
    {
        val result = PopArguments(maxCount)
        if (result.size < minCount) {
            throw UsageError("At least $minCount positional arguments expected, got ${result.size}")
        }
        return result
    }

    fun Finalize()
    {
        if (!tokens.isEmpty()) {
            val token = tokens.first()
            if (token.type == Token.Type.ARGUMENT) {
                throw UsageError("Unexpected argument: ${token.value}")
            }
            throw UsageError("Unrecognized option: ${token.toString()}")
        }
    }

    //XXX implement formatting
    class HelpTextBuilder {
        /** Insert some text. */
        fun Text(text: String)
        {
            //XXX
            buf.append(text)
            buf.append('\n')
        }

        fun Gap()
        {
            buf.append('\n')
        }

        inline fun <reified T> Options()
        {
            Options(T::class)
        }

        fun Options(cls: KClass<*>)
        {
            //XXX
            for (option in Option.ForClass(cls)) {
                buf.append(option.GetName())
                buf.append(' ')
                if (option.hasArg) {
                    if (option.ann != null && !option.ann.argName.isEmpty()) {
                        buf.append(option.ann.argName)
                        buf.append(' ')
                    }
                    if (option.ann != null && option.ann.required) {
                        buf.append('*')
                    }
                    buf.append('{')
                    buf.append(option.GetArgTypeName())
                    buf.append('}')
                }
                if (option.ann != null && !option.ann.description.isEmpty()) {
                    buf.append("  ")
                    buf.append(option.ann.description)
                }
                buf.append('\n')
            }
        }

        private val buf = StringBuilder()

        internal fun GetText(): String
        {
            return buf.toString()
        }
    }

    fun FormatHelp(providerFunc: HelpTextBuilder.() -> Unit): String
    {
        val b = HelpTextBuilder()
        b.providerFunc()
        return b.GetText()
    }

    fun PrintHelp(providerFunc: HelpTextBuilder.() -> Unit)
    {
        println(FormatHelp(providerFunc))
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private data class Token(val type: Type, val value: String) {
        enum class Type {
            SHORT_OPTION,
            LONG_OPTION,
            ARGUMENT
        }

        override fun toString(): String
        {
            return when (type) {
                Type.SHORT_OPTION -> "-$value"
                Type.LONG_OPTION -> "--$value"
                Type.ARGUMENT -> value
            }
        }
    }

    private class Option(cls: KClass<*>, val prop: KMutableProperty1<Any, Any>) {
        val propName = "${cls.simpleName}::${prop.name}"
        val shortName: String?
        val longName: String?
        val hasArg: Boolean
        val ann: CliOption? = prop.findAnnotation()
        val isCollection = prop.returnType.jvmErasure.isSubclassOf(MutableCollection::class)

        init {
            val isBoolean = prop.returnType.jvmErasure.isSubclassOf(Boolean::class)
            if (ann == null) {
                shortName = null
                longName = prop.name
                hasArg = !isBoolean
            } else {
                shortName = if (ann.shortName.isEmpty()) null else ann.shortName
                val altName = if (shortName == null) prop.name else null
                longName = if (ann.longName.isEmpty()) altName else ann.longName
                hasArg = !ann.count && (!isBoolean || ann.booleanArg)
                if (shortName != null && shortName.length != 1) {
                    throw Error("Short name should be exactly one character long: " +
                                "'$shortName' for $propName")
                }
                if (!isBoolean && ann.booleanArg) {
                    throw Error("booleanArg option is applicable only for boolean type property: " +
                                propName)
                }
                if (!hasArg && !ann.argName.isEmpty()) {
                    throw Error("Argument name specified for an option without argument: $propName")
                }
            }
        }

        companion object {
            @Suppress("UNCHECKED_CAST")
            fun ForClass(cls: KClass<*>): List<Option>
            {
                val list = ArrayList<Option>()
                for (prop in cls.declaredMemberProperties) {
                    if (prop.visibility != KVisibility.PUBLIC || prop !is KMutableProperty1) {
                        if (prop.findAnnotation<CliOption>() != null) {
                            throw Error("Mapped option should be mutable public property: " +
                                            "${cls.simpleName}::${prop.name}")
                        }
                        continue
                    }
                    list.add(Option(cls, prop as KMutableProperty1<Any, Any>))
                }
                return list
            }

            fun SplitString(s: String): Array<String>
            {
                //XXX should properly handle quoted elements
                return s.split(Regex("\\s+")).toTypedArray()
            }
        }

        fun GetName(): String
        {
            val buf = StringBuffer()
            if (shortName != null) {
                buf.append('-')
                buf.append(shortName)
                if (longName != null) {
                    buf.append('/')
                }
            }
            if (longName != null) {
                buf.append("--")
                buf.append(longName)
            }
            return buf.toString()
        }

        fun SetFlag(obj: Any, value: Boolean)
        {
            if (!prop.returnType.isSubtypeOf(Boolean::class.createType())) {
                Error("Boolean property type expected for flag option: $propName")
            }
            prop.set(obj, value)
        }

        fun SetCount(obj: Any, value: Int)
        {
            if (prop.returnType.jvmErasure != Int::class) {
                Error("Count option specified for non-integer property: $propName")
            }
            if (ann!!.required) {
                Error("Count option should not be required: $propName")
            }
            prop.set(obj, value)
        }

        fun SetValue(obj: Any, value: String)
        {
            when {
                prop.returnType.jvmErasure == String::class -> {
                    prop.set(obj, value)
                }

                prop.returnType.jvmErasure == Path::class -> {
                    prop.set(obj, Paths.get(value))
                }

                prop.returnType.jvmErasure == Byte::class -> {
                    prop.set(obj, try {
                        value.toByte()
                    } catch (e: Exception) {
                        throw UsageError("Cannot convert argument value to byte for option ${GetName()}", e)
                    })
                }

                prop.returnType.jvmErasure == Short::class -> {
                    prop.set(obj, try {
                        value.toShort()
                    } catch (e: Exception) {
                        throw UsageError("Cannot convert argument value to short for option ${GetName()}", e)
                    })
                }

                prop.returnType.jvmErasure == Int::class -> {
                    prop.set(obj, try {
                        value.toInt()
                    } catch (e: Exception) {
                        throw UsageError("Cannot convert argument value to integer for option ${GetName()}", e)
                    })
                }

                prop.returnType.jvmErasure == Long::class -> {
                    prop.set(obj, try {
                        value.toLong()
                    } catch (e: Exception) {
                        throw UsageError("Cannot convert argument value to long for option ${GetName()}", e)
                    })
                }

                prop.returnType.jvmErasure == Float::class -> {
                    prop.set(obj, try {
                        value.toFloat()
                    } catch (e: Exception) {
                        throw UsageError("Cannot convert argument value to float for option ${GetName()}", e)
                    })
                }

                prop.returnType.jvmErasure == Double::class -> {
                    prop.set(obj, try {
                        value.toDouble()
                    } catch (e: Exception) {
                        throw UsageError("Cannot convert argument value to double for option ${GetName()}", e)
                    })
                }

                prop.returnType.jvmErasure == Boolean::class -> {
                    val bVal = when (value.toLowerCase()) {
                        "0", "false", "off", "no" -> false
                        "1", "true", "on", "yes" -> true
                        else -> throw UsageError("Invalid value for boolean option ${GetName()}: $value")
                    }
                    prop.set(obj, bVal)
                }

                else -> {
                    throw Error("Unsupported property type: $propName: ${prop.returnType}")
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun SetCollection(obj: Any, values: List<String>)
        {
            val col = prop.get(obj) as MutableCollection<String>
            col.clear()
            col.addAll(values)
        }

        fun GetArgTypeName(): String
        {
            if (!hasArg) {
                throw Error("Option has no argument")
            }
            if (isCollection) {
                return "List"
            }
            return prop.returnType.jvmErasure.simpleName!!
        }
    }

    private val tokens = Tokenize(args)

    private fun Tokenize(args: Array<String>): MutableList<Token>
    {
        val result = LinkedList<Token>()
        for (arg in args) {
            when {
                arg.startsWith("--") -> {
                    result.add(Token(Token.Type.LONG_OPTION, arg.substring(2)))
                }

                arg.startsWith("-") -> {
                    arg.codePoints().skip(1).forEach {
                        c ->
                        result.add(Token(Token.Type.SHORT_OPTION, String(Character.toChars(c))))
                    }
                }

                else -> {
                    result.add(Token(Token.Type.ARGUMENT, arg))
                }
            }
        }
        return result
    }

    private fun PopOptions(option: Option): List<String>
    {
        val result = ArrayList<String>()
        var wantArg = false
        val it = tokens.iterator()
        var lastName = ""
        while (it.hasNext()) {
            val token = it.next()

            if (wantArg) {
                if (token.type != Token.Type.ARGUMENT) {
                    throw UsageError("Argument expected for option $lastName")
                }
                result.add(token.value)
                wantArg = false
                it.remove()
                continue
            }

            if ((token.type == Token.Type.SHORT_OPTION && token.value == option.shortName) ||
                (token.type == Token.Type.LONG_OPTION && token.value == option.longName)) {

                if (option.hasArg) {
                    wantArg = true
                } else {
                    result.add("")
                }
                lastName = token.toString()
                it.remove()
            }
        }
        if (wantArg) {
            throw UsageError("Argument expected for option $lastName")
        }
        return result
    }

    private fun PopArguments(maxCount: Int): List<String>
    {
        val result = ArrayList<String>()
        val it = tokens.iterator()
        var count = 0
        while ((maxCount == -1 || count < maxCount) && it.hasNext()) {
            val token = it.next()
            if (token.type != Token.Type.ARGUMENT) {
                break
            }
            result.add(token.value)
            count++
            it.remove()
        }
        return result
    }
}
