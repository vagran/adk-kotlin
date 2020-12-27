/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.db.mongo

import org.bson.Document
import org.bson.types.ObjectId

typealias MongoDocBuilderFunc = MongoDoc.() -> Unit

/** BSON document builder with more convenient syntax. */
class MongoDoc(builderFunc: MongoDocBuilderFunc): Document() {

    class UpdateDocs(val filter: Document, val update: Document,
                     val arrayFilters: List<Document>? = null)

    companion object {
        /** Create update documents based on map which has fields to set new values for. ID field
         * also should be present.
         */
        fun SetUpdate(data: Map<String, Any?>, idFieldName: String = "_id",
                      idIsObjectId: Boolean = true): UpdateDocs
        {
            val id = GetId(data, idFieldName, idIsObjectId)
            val filter = MongoDoc("_id", id)
            val update = Document()
            for ((key, value) in data) {
                if (key != idFieldName) {
                    update.append(key, value)
                }
            }
            return UpdateDocs(filter, Document("\$set", update))
        }

        /** Create update documents for array element update. It assumes that the element has
         * ID field (with name `idFieldName`) which is matched with one in the update data. Then
         * specified fields in the array element are set.
         */
        fun SetArrayUpdate(docId: Any, arrayFieldName: String, data: Map<String, Any?>,
                           idFieldName: String = "_id", idIsObjectId: Boolean = true): UpdateDocs
        {
            val id = GetId(data, idFieldName, idIsObjectId)
            val filter = MongoDoc("_id", docId)
            val update = Document()
            for ((key, value) in data) {
                if (key != idFieldName) {
                    update.append("$arrayFieldName.\$[element].$key", value)
                }
            }
            val arrayFilters = listOf(Document("element.$idFieldName", id))
            return UpdateDocs(filter, Document("\$set", update), arrayFilters)
        }

        /** Create update documents for array element removal. It assumes that the element has
         * ID field (with name `idFieldName`) which is matched with the elementId.
         */
        fun SetArrayRemove(docId: Any, arrayFieldName: String, elementId: Any,
                           idFieldName: String = "_id"): UpdateDocs
        {
            val filter = MongoDoc("_id", docId)
            val update = Document("\$pull",
                                  Document(arrayFieldName, Document(idFieldName, elementId)))
            return UpdateDocs(filter, update)
        }

        private fun GetId(data: Map<String, Any?>, idFieldName: String, idIsObjectId: Boolean): Any
        {
            val id = data[idFieldName] ?: throw Error("ID field missing: $idFieldName")
            return if (idIsObjectId && id !is ObjectId) {
                ObjectId(id as String)
            } else {
                id
            }
        }
    }

    constructor(key: String, value: Any?):
        this({
            V(key, value)
        })

    fun V(key: String, value: Any?)
    {
        append(key, value)
    }

    fun V(key: String, builderFunc: MongoDocBuilderFunc)
    {
        append(key, MongoDoc(builderFunc))
    }

    infix fun String.to(value: Any?)
    {
        append(this@to, value)
    }

    infix fun String.to(builderFunc: MongoDocBuilderFunc)
    {
        append(this@to, MongoDoc(builderFunc))
    }

    init {
        this.builderFunc()
    }
}
