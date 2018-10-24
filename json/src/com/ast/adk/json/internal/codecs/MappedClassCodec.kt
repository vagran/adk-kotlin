package com.ast.adk.json.internal.codecs

import com.ast.adk.json.*
import com.ast.adk.json.internal.ConstructorFunc
import com.ast.adk.json.internal.GetDefaultConstructor
import java.lang.reflect.Modifier
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure

private typealias GetterFunc = (obj: Any) -> Any?
private typealias SetterFunc = (obj: Any, value: Any?) -> Unit

class MappedClassCodec<T>(private val type: KType): JsonCodec<T> {

    override fun WriteNonNull(obj: T, writer: JsonWriter, json: Json)
    {
        delegatedRepresentationField?.also {
            desc ->
            val value = desc.getter(obj as Any)
            if (value == null) {
                writer.WriteNull()
            } else {
                desc.codec.WriteNonNull(value, writer, json)
            }
            return
        }

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

        delegatedRepresentationField?.also {
            desc ->
            if (desc.setter == null) {
                throw JsonReadError(
                    "Attempted to set read-only delegated representation field for $type")
            }
            val value = desc.codec.Read(reader, json)
            if (value == null && !desc.isNullable) {
                throw JsonReadError(
                    "Null value for non-nullable delegated representation field in $type")
            }
            desc.setter.invoke(obj, value)
            return obj as T
        }

        reader.BeginObject()
        val setMask = BooleanArray(fields.size)
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
            if (setMask[desc.index]) {
                throw JsonReadError("Duplicated field $name for $type")
            }
            setMask[desc.index] = true
            val value = desc.codec.Read(reader, json)
            if (value == null && !desc.isNullable) {
                throw JsonReadError("Null value for non-nullable field $name in $type")
            }
            desc.setter.invoke(obj, value)
        }
        reader.EndObject()
        for ((name, desc) in fields) {
            if (desc.isRequired && !setMask[desc.index]) {
                throw JsonReadError("Required field not set: $name in $type")
            }
        }
        return obj as T
    }

    override fun Initialize(json: Json)
    {
        val cls = type.jvmErasure
        allowUnmatchedFields = clsAnn?.allowUnmatchedFields ?: json.allowUnmatchedFields
        val requiredDefault = clsAnn?.requireAllFields ?: json.requireAllFields
        val annotatedOnly = clsAnn?.annotatedOnly ?: json.annotatedOnly
        constructor = GetDefaultConstructor(cls)
        for (curCls in cls.allSuperclasses + cls) {
            for (prop in curCls.declaredMemberProperties) {
                if (Modifier.isTransient(prop.javaField?.modifiers ?: 0) ||
                    prop.findAnnotation<JsonTransient>() != null) {

                    continue
                }
                val fieldAnn = prop.findAnnotation<JsonField>()
                if (fieldAnn == null && annotatedOnly) {
                    continue
                }
                if (prop.visibility != KVisibility.PUBLIC) {
                    if (fieldAnn != null) {
                        throw IllegalArgumentException(
                            "Mapped field should be public: ${curCls.qualifiedName}::${prop.name}")
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
                        "Duplicated field name: ${curCls.qualifiedName}::${prop.name}")
                }
                val field = FieldDesc(prop, json, fieldAnn, requiredDefault, fields.size)
                if (fieldAnn != null && fieldAnn.delegatedRepresentation) {
                    delegatedRepresentationField?.also {
                        throw Error("Delegated representation field redefined in $prop")
                    }
                    delegatedRepresentationField = field
                } else {
                    fields[name] = field
                }
            }
        }
        if (delegatedRepresentationField != null) {
            fields.clear()
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    @Suppress("UNCHECKED_CAST")
    private class FieldDesc(property: KProperty1<*, *>,
                            json: Json,
                            fieldAnn: JsonField?,
                            requiredDefault: Boolean,
                            val index: Int) {
        val getter: GetterFunc
        val setter: SetterFunc?
        val codec: JsonCodec<Any>
        val isRequired: Boolean
        val isNullable: Boolean

        init {
            getter = { obj -> (property as KProperty1<Any, Any?>).get(obj) }
            setter =
                if (property is KMutableProperty1) {
                    { obj, value -> (property as KMutableProperty1<Any, Any?>).set(obj, value) }
                } else {
                    null
                }
            codec = json.GetCodec(property.returnType)

            var isRequired = requiredDefault
            if (property.isLateinit) {
                isRequired = true
            }
            if (fieldAnn != null) {
                if (fieldAnn.required) {
                    isRequired = true
                } else if (fieldAnn.optional) {
                    isRequired = false
                }
            }
            this.isRequired = setter != null && isRequired
            isNullable = property.returnType.isMarkedNullable
        }
    }

    private val clsAnn: JsonClass? = type.jvmErasure.findAnnotation()
    private var allowUnmatchedFields = false
    private val fields = HashMap<String, FieldDesc>()
    private var constructor: ConstructorFunc? = null
    private var delegatedRepresentationField: FieldDesc? = null
}
