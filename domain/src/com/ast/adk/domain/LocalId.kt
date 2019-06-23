package com.ast.adk.domain

import com.ast.adk.json.*
import java.io.Serializable
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger


/**
 * Application-instance-locally-unique monotonically increased ID.
 */
@JsonClass(codec = LocalIdJsonCodec::class)
class LocalId(val value: Long): Comparable<LocalId>, Serializable {

    constructor():
        this(NextValue())

    constructor(s: String):
        this(java.lang.Long.parseLong(s, 16))

    val isZero: Boolean get() = value == 0L

    override fun equals(other: Any?): Boolean
    {
        val _other = other as? LocalId ?: return false
        return value == _other.value
    }

    override fun hashCode(): Int
    {
        return java.lang.Long.hashCode(value)
    }

    override fun compareTo(other: LocalId): Int
    {
        return value.compareTo(other.value)
    }

    override fun toString(): String
    {
        return value.toString(16)
    }

    companion object {
        private val counter = AtomicInteger(System.nanoTime().toInt() and 0x7fffffff)
        private val wrapTs = AtomicInteger(0)
        private const val serialVersionUID = 1L
        private const val WRAP_THRESHOLD = 0xc0000000

        private fun GetValue(timestamp: Long, counter: Int): Long
        {
            return (timestamp shl 32) or (counter.toLong() and 0xffffffffL)
        }

        private fun NextValue(): Long
        {
            val ts = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            val cnt = counter.incrementAndGet()
            if (cnt > WRAP_THRESHOLD) {
                /* Ensure wrapping occurs with next timestamp increase. */
                val _ts = ts.toInt()
                loop@ while (true) {
                    val _wrapTs = wrapTs.get()
                    val wrapTsNew: Int
                    when (_wrapTs) {
                        0 -> {
                            /* Initial state. */
                            wrapTsNew = _ts
                        }
                        0xffffffff.toInt() -> {
                            /* Wrapping in progress. */
                            continue@loop
                        }
                        _ts -> {
                            /* Waiting for next second. */
                            break@loop
                        }
                        else -> {
                            /* Can wrap now. */
                            wrapTsNew = 0xffffffff.toInt()
                        }
                    }
                    if (!wrapTs.compareAndSet(_wrapTs, wrapTsNew)) {
                        continue
                    }
                    if (wrapTsNew == 0xffffffff.toInt()) {
                        counter.set(0)
                        wrapTs.set(0)
                        return NextValue()
                    }
                }
            }
            return GetValue(ts, cnt)
        }

        val ZERO = LocalId(0L)
    }
}


class LocalIdJsonCodec: JsonCodec<LocalId> {

    override fun WriteNonNull(obj: LocalId, writer: JsonWriter, json: Json)
    {
        writer.Write(obj.toString())
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): LocalId
    {
        return LocalId(reader.ReadString())
    }
}
