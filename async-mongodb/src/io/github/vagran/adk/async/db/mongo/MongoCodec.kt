package io.github.vagran.adk.async.db.mongo

import org.bson.codecs.Codec

/** Custom codecs may require two-step initialization for proper dependencies resolving. They should
 * implement this interface in such case.
 */
interface MongoCodec<T>: Codec<T> {
    fun Initialize(mapper: MongoMapper)
}
