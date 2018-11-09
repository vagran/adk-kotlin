package com.ast.adk.async.db.mongo.codecs

import com.ast.adk.async.db.mongo.MongoCodec
import com.ast.adk.async.db.mongo.MongoMapper
import com.ast.adk.omm.GetDefaultConstructor
import com.ast.adk.omm.OmmError
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.jvm.jvmErasure

private typealias MapConstructorFunc = () -> Any

class MapCodec(private val type: KType, private val mapper: MongoMapper): MongoCodec<Map<*, *>> {

    @Suppress("UNCHECKED_CAST")
    override fun getEncoderClass(): Class<Map<*, *>>
    {
        return type.jvmErasure.java as Class<Map<*, *>>
    }

    override fun encode(writer: BsonWriter, obj: Map<*, *>, encoderContext: EncoderContext)
    {
        writer.writeStartDocument()
        for ((key, element) in obj) {
            writer.writeName(key.toString())
            if (element == null) {
                writer.writeNull()
                continue
            }
            @Suppress("UNCHECKED_CAST")
            val codec = if (elementClass != null) {
                if (!elementClass.isInstance(element)) {
                    throw IllegalArgumentException(
                        "Element type mismatch, expected $elementClass, have ${element::class}")
                }
                elementCodec
            } else {
                mapper.GetCodec(element::class) as Codec<Any>
            }
            codec.encode(writer, element, encoderContext)
        }
        writer.writeEndDocument()
    }

    @Suppress("UNCHECKED_CAST")
    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Map<*, *>
    {
        if (constructor == null) {
            throw OmmError("No constructor found for $type")
        }
        val result = constructor.invoke() as MutableMap<String, Any?>
        reader.readStartDocument()
        while (true) {
            val type = reader.readBsonType()
            if (type == BsonType.END_OF_DOCUMENT) {
                break
            }
            result[reader.readName()] = readElementCodec.decode(reader, decoderContext)
        }
        reader.readEndDocument()
        return result
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
    private val elementClass: KClass<*>? = GetElementClass(type)
    private lateinit var elementCodec: Codec<Any>
    private lateinit var readElementCodec: Codec<Any>
    private val constructor: MapConstructorFunc?

    init {
        val defCtr = try {
            val ctr = GetDefaultConstructor(type.jvmErasure, KVisibility.PUBLIC)
            if (ctr.outerCls == null) {
                ctr
            } else {
                null
            }
        } catch(e: Exception) {
            null
        }
        constructor = when {
            defCtr != null -> {
                { defCtr.Construct(null) }
            }
            type.jvmErasure == Map::class || type.jvmErasure == MutableMap::class -> {
                { HashMap<String, Any?>() }
            }
            else -> null
        }
    }

    private companion object {
        fun GetElementClass(type: KType): KClass<*>?
        {
            if (type.arguments.size < 2) {
                return null
            }
            return type.arguments[1].type?.jvmErasure
        }
    }
}
