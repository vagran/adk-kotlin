/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.db.mongo.codecs

import io.github.vagran.adk.async.db.mongo.MongoCodec
import io.github.vagran.adk.async.db.mongo.MongoMapper
import io.github.vagran.adk.omm.GetDefaultConstructor
import io.github.vagran.adk.omm.OmmError
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

private typealias ListConstructorFunc = () -> Any

class ListCodec(private val type: KType, private val mapper: MongoMapper): MongoCodec<Collection<*>> {

    @Suppress("UNCHECKED_CAST")
    override fun getEncoderClass(): Class<Collection<*>>
    {
        return type.jvmErasure.java as Class<Collection<*>>
    }

    override fun encode(writer: BsonWriter, obj: Collection<*>, encoderContext: EncoderContext)
    {
        writer.writeStartArray()
        for (element in obj) {
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
        writer.writeEndArray()
    }

    @Suppress("UNCHECKED_CAST")
    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Collection<*>
    {
        if (constructor == null) {
            throw OmmError("No constructor found for $type")
        }
        val result = constructor.invoke() as MutableCollection<Any?>
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
    private val constructor: ListConstructorFunc?

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
            type.jvmErasure == List::class || type.jvmErasure == MutableList::class ||
            type.jvmErasure == Collection::class || type.jvmErasure == MutableCollection::class -> {
                { ArrayList<Any?>() }
            }
            else -> null
        }
    }

    private companion object {
        fun GetElementClass(type: KType): KClass<*>?
        {
            return type.arguments[0].type?.jvmErasure
        }
    }
}
