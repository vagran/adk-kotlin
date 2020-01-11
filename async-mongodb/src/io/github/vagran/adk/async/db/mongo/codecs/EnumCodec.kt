package io.github.vagran.adk.async.db.mongo.codecs

import io.github.vagran.adk.async.db.mongo.MongoMapper
import io.github.vagran.adk.omm.OmmError
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import java.util.*
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

class EnumCodec(private val type: KType, private val mapper: MongoMapper): Codec<Enum<*>> {

    @Suppress("UNCHECKED_CAST")
    override fun getEncoderClass(): Class<Enum<*>>
    {
        return javaClass as Class<Enum<*>>
    }

    override fun encode(writer: BsonWriter, obj: Enum<*>, encoderContext: EncoderContext)
    {
        EncodeEnum(writer, obj, mapper.ommParams.enumByName)
    }

    @Suppress("UNCHECKED_CAST")
    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Enum<*>
    {
        return DecodeEnum(reader, mapper.ommParams.enumByName)
    }

    fun EncodeEnum(writer: BsonWriter, obj: Enum<*>, enumByName: Boolean)
    {
        if (enumByName) {
            writer.writeString(obj.name)
        } else {
            writer.writeInt32(obj.ordinal)
        }
    }

    fun DecodeEnum(reader: BsonReader, enumByName: Boolean): Enum<*>
    {
        return if (enumByName) {
            val name = reader.readString()
            names[name] ?: throw OmmError("Unrecognized enum $type value name: $name")
        } else {
            val index = reader.readInt32()
            if (index >= values.size) {
                throw OmmError("Enum $type value index out of range: $index")
            }
            values[index]
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val javaClass = type.jvmErasure.java
    private val values: Array<Enum<*>>
    private val names: Map<String, Enum<*>>

    init {
        val _values = type.jvmErasure.java.enumConstants
        names = TreeMap()
        values = Array(_values.size) {
            idx ->
            val value = _values[idx] as Enum<*>
            names[value.name] = value
            value
        }
    }
}
