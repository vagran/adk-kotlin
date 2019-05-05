package com.ast.adk.domain

import com.ast.adk.Random
import com.ast.adk.json.*
import java.io.Serializable
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger


/**
 * Application-instance-locally-unique ID.
 */
@JsonClass(codec = LocalIdJsonCodec::class)
class LocalId(val value: Long): Comparable<LocalId>, Serializable {

    constructor():
        this(GetValue(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
                      counter.incrementAndGet()))

    constructor(s: String):
        this(java.lang.Long.parseLong(s, 16))

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
        return java.lang.Long.compare(value, other.value)
    }

    override fun toString(): String
    {
        return java.lang.Long.toString(value, 16)
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private companion object {
        val counter = AtomicInteger(Random().GetInt())
        const val serialVersionUID = 1L

        fun GetValue(timestamp: Long, counter: Int): Long
        {
            return (timestamp shl 32) or (counter.toLong() and 0xffffffffL)
        }
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
