/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.html.json

import io.github.vagran.adk.html.HtmlDocument
import io.github.vagran.adk.json.*

class HtmlDocumentNodeJsonCodec: JsonCodec<HtmlDocument.Node> {

    override fun WriteNonNull(obj: HtmlDocument.Node, writer: JsonWriter, json: Json)
    {
        writer.BeginObject()
        writer.WriteName("id")
        writer.Write(obj.id)
        obj.tags?.also {
            tags ->
            writer.WriteName("tags")
            writer.BeginArray()
            for (tag in tags) {
                writer.Write(tag)
            }
            writer.EndArray()
        }

        if (obj is HtmlDocument.TextNode) {
            writer.WriteName("text")
            writer.Write(obj.text)

        } else if (obj is HtmlDocument.ElementNode) {
            writer.WriteName("name")
            writer.Write(obj.name)
            if (obj.attrs.isNotEmpty()) {
                writer.WriteName("attrs")
                WriteAttrs(obj.attrs, writer)
            }
            if (!obj.children.isEmpty()) {
                writer.WriteName("children")
                writer.BeginArray()
                for (child in obj.children) {
                    WriteNonNull(child, writer, json)
                }
                writer.EndArray()
            }
        }
        writer.EndObject()
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): HtmlDocument.Node
    {
        throw Error("Operation not supported")
    }

    private fun WriteAttrs(attrs: List<HtmlDocument.AttrNode>, writer: JsonWriter)
    {
        writer.BeginArray()
        for (attr in attrs) {
            writer.BeginObject()
            writer.WriteName("id")
            writer.Write(attr.id)
            writer.WriteName("name")
            writer.Write(attr.name)
            attr.value?.also {
                writer.WriteName("value")
                writer.Write(it)
            }
            writer.EndObject()
        }
        writer.EndArray()
    }
}

class HtmlDocumentJsonCodec: JsonCodec<HtmlDocument> {

    override fun Initialize(json: Json)
    {
        nodeCodec = json.GetCodec()
    }

    override fun WriteNonNull(obj: HtmlDocument, writer: JsonWriter, json: Json)
    {
        nodeCodec.WriteNonNull(obj.root, writer, json)
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): HtmlDocument
    {
        throw Error("Operation not supported")
    }

    private lateinit var nodeCodec: JsonCodec<HtmlDocument.Node>
}

val htmlJsonCodecsRegistry = JsonCodecRegistry().apply {
    subclassCodecs[HtmlDocument.Node::class] = { HtmlDocumentNodeJsonCodec() }
    classCodecs[HtmlDocument::class] = { HtmlDocumentJsonCodec() }
}
