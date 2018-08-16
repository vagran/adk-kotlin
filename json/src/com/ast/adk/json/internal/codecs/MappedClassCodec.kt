package com.ast.adk.json.internal.codecs

import com.ast.adk.json.*
import com.ast.adk.json.internal.ConstructorFunc
import com.ast.adk.json.internal.GetDefaultConstructor
import kotlin.reflect.*
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.jvmErasure

private typealias GetterFunc = (obj: Any) -> Any?
private typealias SetterFunc = (obj: Any, value: Any?) -> Unit

class MappedClassCodec<T>(private val type: KType): JsonCodec<T> {

    override fun WriteNonNull(obj: T, writer: JsonWriter, json: Json)
    {
        writer.BeginObject()
        for ((name, desc) in fields) {
            val value = desc.getter(obj as Any)
            if (value == null) {
                if (json.serializeNulls) {
                    writer.WriteName(name)
                    writer.WriteNull()
                }
                continue
            }
            writer.WriteName(name)
            desc.codec.WriteNonNull(value, writer, json)
        }
        writer.EndObject()
    }

    @Suppress("UNCHECKED_CAST")
    override fun ReadNonNull(reader: JsonReader, json: Json): T
    {
        val obj = constructor?.invoke() ?:
            throw IllegalStateException("Mapped class constructor not available: $type")
        reader.BeginObject()
        while (true) {
            if (!reader.HasNext()) {
                break
            }
            val name = reader.ReadName()
            val desc = fields[name]
            if (desc == null) {
                if (!allowUnmatchedFields) {
                    throw JsonReadError("Unmatched field $name for $type")
                }
                reader.SkipValue()
                continue
            }
            if (desc.setter == null) {
                throw JsonReadError("Attempted to set read-only field $name for $type")
            }
            val value = desc.codec.Read(reader, json)
            desc.setter.invoke(obj, value)
        }
        reader.EndObject()
        return obj as T
    }

    override fun Initialize(json: Json)
    {
        val cls = type.jvmErasure
        allowUnmatchedFields = clsAnn?.allowUnmatchedFields ?: json.allowUnmatchedFields
        constructor = GetDefaultConstructor(cls)
        for (prop in cls.declaredMemberProperties) {
            if (prop.findAnnotation<JsonTransient>() != null) {
                continue
            }
            val fieldAnn = prop.findAnnotation<JsonField>()
            if (prop.visibility != KVisibility.PUBLIC) {
                if (fieldAnn != null) {
                    throw IllegalArgumentException(
                        "Mapped field should be public: ${cls.qualifiedName}::${prop.name}")
                }
                continue
            }
            val name = if (fieldAnn != null && !fieldAnn.name.isEmpty()) {
                fieldAnn.name
            } else {
                prop.name
            }
            if (name in fields) {
                throw IllegalArgumentException(
                    "Duplicated field name: ${cls.qualifiedName}::${prop.name}")
            }
            fields[name] = FieldDesc(prop, json)
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    @Suppress("UNCHECKED_CAST")
    private class FieldDesc(property: KProperty1<*, *>, json: Json) {
        val getter: GetterFunc
        val setter: SetterFunc?
        val codec: JsonCodec<Any>

        init {
            getter = { obj -> (property as KProperty1<Any, Any?>).get(obj) }
            setter =
                if (property is KMutableProperty1) {
                    { obj, value -> (property as KMutableProperty1<Any, Any?>).set(obj, value) }
                } else {
                    null
                }
            codec = json.GetCodec(property.returnType)
        }
    }

    private val clsAnn: JsonClass? = type.jvmErasure.findAnnotation()
    private var allowUnmatchedFields = false
    private val fields = HashMap<String, FieldDesc>()
    private var constructor: ConstructorFunc? = null
}
