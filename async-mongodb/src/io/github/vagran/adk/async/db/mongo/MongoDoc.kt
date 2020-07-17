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

    class UpdateDocs(val filter: Document, val update: Document)

    companion object {
        /** Create update documents based on map which has fields to set new values for. ID field
         * also should be present.
         */
        fun SetUpdate(data: Map<String, Any?>, idFieldName: String = "id",
                      idIsObjectId: Boolean = true): UpdateDocs
        {
            val id = data[idFieldName]
            val filter = MongoDoc("_id", if (idIsObjectId) ObjectId(id as String) else id)
            val update = Document()
            for ((key, value) in data) {
                if (key != idFieldName) {
                    update.append(key, value)
                }
            }
            return UpdateDocs(filter, Document("\$set", update))
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
