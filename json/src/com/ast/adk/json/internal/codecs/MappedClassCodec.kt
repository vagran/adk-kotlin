package com.ast.adk.json.internal.codecs

import com.ast.adk.json.*
import java.lang.reflect.Modifier
import kotlin.reflect.*
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure

private typealias ConstructorFunc = () -> Any
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

    override fun ReadNonNull(reader: JsonReader, json: Json): T
    {
        TODO("not implemented") //XXX
    }

    override fun Initialize(json: Json)
    {
        val cls = type.jvmErasure
        constructor = GetConstructor(cls)
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

    private val fields = HashMap<String, FieldDesc>()
    private var constructor: ConstructorFunc? = null

    private fun GetConstructor(cls: KClass<*>): ConstructorFunc?
    {
        if (cls.isAbstract || cls.isSealed || cls.objectInstance != null) {
            return null
        }
        for (ctr in cls.constructors) {
            val defCtr = CheckConstructor(ctr)
            if (defCtr != null) {
                if (ctr.visibility != KVisibility.PUBLIC) {
                    return null
                }
                return defCtr
            }
        }
        return null
    }

    private fun CheckConstructor(ctr: KFunction<*>): ConstructorFunc?
    {
        for (paramIdx in 0 until ctr.parameters.size) {
            val param = ctr.parameters[paramIdx]
            if (!param.isOptional) {
                return null
            }
        }
        return { ctr.callBy(emptyMap()) as Any }
    }
}
