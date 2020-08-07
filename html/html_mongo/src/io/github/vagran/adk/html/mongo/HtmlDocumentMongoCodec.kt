/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.html.mongo

import io.github.vagran.adk.async.db.mongo.MongoCodec
import io.github.vagran.adk.async.db.mongo.MongoMapper
import io.github.vagran.adk.html.HtmlDocument

import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

class HtmlDocumentNodeMongoCodec: Codec<HtmlDocument.Node> {

    @Suppress("UNCHECKED_CAST")
    override fun getEncoderClass(): Class<HtmlDocument.Node>
    {
        return HtmlDocument.Node::class.java
    }

    override fun encode(writer: BsonWriter, obj: HtmlDocument.Node, encoderContext: EncoderContext)
    {
        writer.writeStartDocument()

        if (obj is HtmlDocument.TextNode) {
            writer.writeName("text")
            writer.writeString(obj.text)

        } else if (obj is HtmlDocument.ElementNode) {
            writer.writeName("name")
            writer.writeString(obj.name)
            if (obj.attrs.isNotEmpty()) {
                writer.writeName("attrs")
                WriteAttrs(obj.attrs, writer)
            }
            if (!obj.children.isEmpty()) {
                writer.writeName("children")
                writer.writeStartArray()
                for (child in obj.children) {
                    encode(writer, child, encoderContext)
                }
                writer.writeEndArray()
            }
        }
        writer.writeEndDocument()
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): HtmlDocument.Node
    {
        throw Error("Operation not supported")
    }

    private fun WriteAttrs(attrs: List<HtmlDocument.AttrNode>, writer: BsonWriter)
    {
        writer.writeStartArray()
        for (attr in attrs) {
            writer.writeStartDocument()
            writer.writeName("name")
            writer.writeString(attr.name)
            if (attr.value != null) {
                writer.writeName("value")
                writer.writeString(attr.value)
            }
            writer.writeEndDocument()
        }
        writer.writeEndArray()
    }
}

class HtmlDocumentMongoCodec: MongoCodec<HtmlDocument> {

    override fun getEncoderClass(): Class<HtmlDocument>
    {
        return HtmlDocument::class.java
    }

    override fun Initialize(mapper: MongoMapper)
    {
        nodeCodec = mapper.GetCodec()
    }

    override fun encode(writer: BsonWriter, obj: HtmlDocument, encoderContext: EncoderContext)
    {
        nodeCodec.encode(writer, obj.root, encoderContext)
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): HtmlDocument
    {
        throw Error("Operation not supported")
    }

    private lateinit var nodeCodec: Codec<HtmlDocument.Node>
}

