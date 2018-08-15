package com.ast.adk.json.internal.codecs

import com.ast.adk.json.Json
import com.ast.adk.json.JsonCodec
import com.ast.adk.json.JsonReader
import com.ast.adk.json.JsonWriter

class AnyCodec: JsonCodec<Any> {

    override fun WriteNonNull(obj: Any, writer: JsonWriter, json: Json)
    {
        val cls = obj::class
        if (cls == Any::class) {
            writer.BeginObject()
            writer.EndObject()
            return
        }
        @Suppress("UNCHECKED_CAST")
        val codec = json.GetCodec(cls) as JsonCodec<Any>
        codec.WriteNonNull(obj, writer, json)
    }

    override fun ReadNonNull(reader: JsonReader, json: Json): Any {
        TODO("not implemented") //XXX
    }

}
