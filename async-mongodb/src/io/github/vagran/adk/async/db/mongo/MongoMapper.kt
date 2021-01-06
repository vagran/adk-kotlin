/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.db.mongo

import io.github.vagran.adk.LocalId
import io.github.vagran.adk.TypeToken
import io.github.vagran.adk.async.db.mongo.codecs.*
import io.github.vagran.adk.async.db.mongo.codecs.MapCodec
import io.github.vagran.adk.omm.OmmParams
import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.Document
import org.bson.codecs.*
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

typealias MongoCodecProvider = (type: KType) -> Codec<*>

class MongoMapper(
    serializeNulls: Boolean = false,
    val allowUnmatchedFields: Boolean = false,
    requireAllFields: Boolean = false,
    annotatedOnlyFields: Boolean = false,
    acceptedVisibility: KVisibility = KVisibility.PUBLIC,
    requireLateinitVars: Boolean = true,
    allowInnerClasses: Boolean = true,
    qualifier: String? = "mongo",
    qualifiedOnly: Boolean = false,
    setAccessible: Boolean = false,
    typeCodecs: Map<KType, Codec<*>> = emptyMap(),
    classCodecs: Map<KClass<*>, MongoCodecProvider> = emptyMap(),
    subclassCodecs: Map<KClass<*>, MongoCodecProvider> = emptyMap()
): CodecRegistry {

    override fun <T: Any> get(cls: Class<T>): Codec<T>
    {
        return GetCodec(cls.kotlin)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> GetCodec(type: KType): Codec<T>
    {
        return CreateIfAbsent(type) {
            GetCustomCodec(type) as Codec<T>? ?: registry.get(type.jvmErasure.java) as Codec<T>
        }
    }

    fun <T> GetCodec(type: TypeToken<T>): Codec<T>
    {
        return GetCodec(type.type)
    }

    fun <T: Any> GetCodec(cls: KClass<T>): Codec<T>
    {
        return GetCodec(TypeToken.Create(cls))
    }

    inline fun <reified T> GetCodec(): Codec<T> = GetCodec(TypeToken.Create())

    @Suppress("UNCHECKED_CAST")
    fun Encode(obj: Any?): BsonDocument
    {
        val writer = BsonDocumentWriter(BsonDocument())
        if (obj == null) {
            writer.writeNull()
        } else {
            val codec: Codec<Any> = GetCodec(obj::class) as Codec<Any>
            codec.encode(writer, obj, EncoderContext.builder().build())
        }
        return writer.document
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    internal val ommParams = OmmParams(serializeNulls = serializeNulls,
                                       requireAllFields = requireAllFields,
                                       annotatedOnlyFields = annotatedOnlyFields,
                                       acceptedVisibility = acceptedVisibility,
                                       allowInnerClasses = allowInnerClasses,
                                       requireLateinitVars = requireLateinitVars,
                                       qualifier = qualifier,
                                       qualifiedOnly = qualifiedOnly,
                                       setAccessible = setAccessible)
    private val codecs = ConcurrentHashMap<KType, Codec<*>>()
    private val builtinCodecProviders = listOf(BsonValueCodecProvider(),
                                               ValueCodecProvider(),
                                               PrimitiveValueCodecProvider(),
                                               DocumentCodecProvider())
    private val registry =
        CodecRegistries.fromProviders(builtinCodecProviders + CustomCodecProvider())
    private val classCodecs = HashMap<KClass<*>, MongoCodecProvider>()
    private val subclassCodecs = HashMap<KClass<*>, MongoCodecProvider>()

    init {
        this.classCodecs[LocalDateTime::class] = { LocalDateTimeCodec() }
        this.classCodecs[BitSet::class] = { BitSetCodec() }
        this.classCodecs[LocalId::class] = { LocalIdCodec() }

        this.subclassCodecs[Path::class] = { PathCodec() }

        codecs.putAll(typeCodecs)
        this.classCodecs.putAll(classCodecs)
        this.subclassCodecs.putAll(subclassCodecs)
    }

    private class PrimitiveValueCodecProvider: CodecProvider {

        private val codecs: Map<KClass<*>, Codec<*>>

        init {
            codecs = HashMap()
            codecs[Boolean::class] = BooleanCodec()
            codecs[Char::class] = CharacterCodec()
            codecs[Byte::class] = ByteCodec()
            codecs[Short::class] = ShortCodec()
            codecs[Int::class] = IntegerCodec()
            codecs[Long::class] = LongCodec()
            codecs[Float::class] = FloatCodec()
            codecs[Double::class] = DoubleCodec()
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T: Any> get(cls: Class<T>, registry: CodecRegistry): Codec<T>?
        {
            return codecs[cls.kotlin] as? Codec<T>
        }
    }

    private inner class CustomCodecProvider: CodecProvider {

        @Suppress("UNCHECKED_CAST")
        override fun <T: Any> get(cls: Class<T>, registry: CodecRegistry): Codec<T>
        {
            val type = TypeToken.Create(cls.kotlin).type
            return CreateIfAbsent(type) {
                GetCustomCodec(type) as Codec<T>? ?: MappedClassCodec(type)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <T> CreateIfAbsent(type: KType, fabric: () -> Codec<T>): Codec<T>
    {
        var codec = codecs[type]
        if (codec != null) {
            return codec as Codec<T>
        }
        codec = fabric()
        val existingCodec = codecs.putIfAbsent(type, codec)
        if (existingCodec != null) {
            return existingCodec as Codec<T>
        }
        if (codec is MongoCodec) {
            codec.Initialize(this)
        }
        return codec
    }

    private fun GetCustomCodec(type: KType): Codec<*>?
    {
        val jvmErasure = type.jvmErasure

        for ((cls, provider) in classCodecs) {
            if (jvmErasure == cls) {
                return provider(type)
            }
        }

        for ((cls, provider) in subclassCodecs) {
            if (jvmErasure.isSubclassOf(cls)) {
                return provider(type)
            }
        }

        ommParams.FindAnnotation<MongoClass>(jvmErasure)?.also {
            ann ->
            if (ann.codec != Unit::class) {
                if (!ann.codec.isSubclassOf(Codec::class)) {
                    throw Error("Invalid codec class provided in annotation for $jvmErasure")
                }
                return ann.codec.createInstance() as Codec<*>
            }
        }

        if (jvmErasure.isSubclassOf(List::class) || jvmErasure.isSubclassOf(Collection::class)) {
            return ListCodec(type, this)
        }
        if (jvmErasure != Document::class && jvmErasure.isSubclassOf(Map::class)) {
            return MapCodec(type, this)
        }

        if (jvmErasure.isSubclassOf(IntArray::class)) {
            return IntArrayCodec()
        }
        if (jvmErasure.isSubclassOf(LongArray::class)) {
            return LongArrayCodec()
        }
        if (jvmErasure.isSubclassOf(FloatArray::class)) {
            return FloatArrayCodec()
        }
        if (jvmErasure.isSubclassOf(DoubleArray::class)) {
            return DoubleArrayCodec()
        }

        if (jvmErasure.java.isArray && !jvmErasure.java.componentType.isPrimitive) {
            return ArrayCodec(type, this)
        }

        if (jvmErasure.isSubclassOf(Enum::class)) {
            return EnumCodec(type, this)
        }

        if (jvmErasure == Any::class) {
            return AnyCodec(this)
        }

        return null
    }
}
