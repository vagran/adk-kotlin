package com.ast.adk.json.internal.codecs

import com.ast.adk.json.*

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

    override fun ReadNonNull(reader: JsonReader, json: Json): Any
    {
        val token = reader.Peek()
        when (token.type) {
            JsonToken.Type.EOF -> {
                throw JsonReadError("Unexpected end of file")
            }
            JsonToken.Type.BOOLEAN -> {
                return reader.ReadBoolean()
            }
            JsonToken.Type.NUMBER -> {
                return reader.ReadDouble()
            }
            JsonToken.Type.STRING -> {
                return reader.ReadString()
            }
            JsonToken.Type.BEGIN_ARRAY -> {
                return listCodec.ReadNonNull(reader, json)
            }
            JsonToken.Type.BEGIN_OBJECT -> {
                return mapCodec.ReadNonNull(reader, json)
            }
            else -> throw JsonReadError("Unexpected token: $token")
        }
    }

    override fun Initialize(json: Json)
    {
        listCodec = json.GetCodec()
        mapCodec = json.GetCodec()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private lateinit var listCodec: JsonCodec<List<Any?>>
    private lateinit var mapCodec: JsonCodec<Map<String, Any?>>
}
