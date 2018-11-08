package com.ast.adk.async.db.mongo.codecs

import com.ast.adk.async.db.mongo.MongoCodec
import com.ast.adk.async.db.mongo.MongoMapper
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

class ArrayCodec(type: KType, private val mapper: MongoMapper): MongoCodec<Array<*>> {

    @Suppress("UNCHECKED_CAST")
    override fun getEncoderClass(): Class<Array<*>>
    {
        return javaClass as Class<Array<*>>
    }

    override fun encode(writer: BsonWriter, obj: Array<*>, encoderContext: EncoderContext)
    {
        writer.writeStartArray()
        for (element in obj) {
            if (element == null) {
                writer.writeNull()
                continue
            }
            @Suppress("UNCHECKED_CAST")
            val codec = if (elementClass != null) {
                if (element::class != elementClass) {
                    throw IllegalArgumentException(
                        "Element type mismatch, expected $elementClass, have ${element::class}")
                }
                elementCodec
            } else {
                mapper.GetCodec(element::class) as Codec<Any>
            }
            codec.encode(writer, element, encoderContext)
        }
        writer.writeEndArray()
    }

    @Suppress("UNCHECKED_CAST")
    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Array<*>
    {
        val result = ArrayList<Any?>()
        reader.readStartArray()
        while (true) {
            val type = reader.readBsonType()
            if (type == BsonType.END_OF_DOCUMENT) {
                break
            }
            if (type == BsonType.NULL) {
                reader.readNull()
                result.add(null)
                continue
            }
            result.add(readElementCodec.decode(reader, decoderContext))
        }
        reader.readEndArray()
        if (elementClass != null) {
            val array = java.lang.reflect.Array.newInstance(elementClass.java, result.size)
            return result.toArray(array as Array<Any?>)
        }
        return result.toArray()
    }

    override fun Initialize(mapper: MongoMapper)
    {
        if (elementClass != null) {
            @Suppress("UNCHECKED_CAST")
            elementCodec = mapper.GetCodec(elementClass)as Codec<Any>
            readElementCodec = elementCodec
        } else {
            readElementCodec = mapper.GetCodec()
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val elementClass: KClass<*>? = type.arguments[0].type?.jvmErasure
    private lateinit var elementCodec: Codec<Any>
    private lateinit var readElementCodec: Codec<Any>
    private val javaClass = type.jvmErasure.java
}
