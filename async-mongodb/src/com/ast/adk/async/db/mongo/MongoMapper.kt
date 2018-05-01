package com.ast.adk.async.db.mongo

import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.codecs.*
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import kotlin.reflect.KClass

class MongoMapper {

    companion object {
        fun ForClasses(vararg classes: KClass<*>): CodecRegistry
        {
            return ForClasses(classes.asIterable())
        }

        /** Create codec registry for the specified mapped classes. Nested classes (ones used as
         * types of mapped fields) are recognized automatically and are not needed to be specified
         * in this method arguments).
         * Example of the result usage:
         * {@code
         * database.getCollection("Items", Item::class.java).withCodecRegistry(registry)
         * }
         */
        fun ForClasses(classes: Iterable<KClass<*>>): CodecRegistry
        {
            return Builder(classes).Build()
        }

        @Suppress("UNCHECKED_CAST")
        fun <T: Any> EncodeObject(codecRegistry: CodecRegistry, obj: T): BsonDocument
        {
            val codec = codecRegistry.get(obj::class.java) as Codec<T>
            val writer = BsonDocumentWriter(BsonDocument())
            codec.encode(writer, obj, EncoderContext.builder().build())
            return writer.document
        }
    }

    private class Builder(private val mappedClasses: Iterable<KClass<*>>) {

        fun Build(): CodecRegistry
        {
            return CodecRegistries.fromProviders(
                BsonValueCodecProvider(),
                ValueCodecProvider(),
                DocumentCodecProvider())
        }
    }
}