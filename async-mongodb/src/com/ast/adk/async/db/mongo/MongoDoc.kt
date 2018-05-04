package com.ast.adk.async.db.mongo

import org.bson.Document

typealias MongoDocBuilderFunc = MongoDoc.() -> Unit

/** BSON document builder with more convenient syntax. */
class MongoDoc(builderFunc: MongoDocBuilderFunc): Document() {

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

    init {
        this.builderFunc()
    }
}