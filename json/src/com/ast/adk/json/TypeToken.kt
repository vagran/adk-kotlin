package com.ast.adk.json

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.jvm.internal.Reflection
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.jvm.jvmErasure


open class TypeToken<T> {

    companion object {
        inline fun <reified T> Create(): TypeToken<T>
        {
            return object: TypeToken<T>() {}
        }

        fun <T: Any> Create(cls: KClass<T>): TypeToken<T>
        {
            return TypeToken(cls.createType())
        }
    }

    val type: KType

    override fun equals(other: Any?): Boolean
    {
        return type == (other as TypeToken<*>).type
    }

    override fun hashCode(): Int
    {
        return type.hashCode()
    }

    override fun toString(): String
    {
        return type.toString()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private constructor(type: KType)
    {
        this.type = type
    }

    protected constructor()
    {
        val st = GetSuperType()
        if (st.jvmErasure != TypeToken::class) {
            throw IllegalStateException("TypeToken superclass expected, have $st")
        }
        type = st.arguments[0].type!!
    }

    private fun GetSuperType(): KType
    {
        /* This is workaround for https://youtrack.jetbrains.net/issue/KT-26143
         * Normally it should just return this::class.supertypes[0]
         */
        return CreateType(this::class.java.genericSuperclass)
    }

    private fun CreateType(javaType: Type): KType
    {
        if (javaType is Class<*>) {
            return Reflection.getOrCreateKotlinClass(javaType).createType()
        }
        val genType = javaType as ParameterizedType
        val cls = Reflection.getOrCreateKotlinClass(genType.rawType as Class<*>)
        val projections = ArrayList<KTypeProjection>()
        for (type in genType.actualTypeArguments) {
            projections.add(GetTypeParam(type))
        }
        return cls.createType(projections, true)
    }

    private fun GetTypeParam(javaType: Type): KTypeProjection
    {
        if (javaType !is WildcardType) {
            return KTypeProjection.invariant(CreateType(javaType))
        }
        if (!javaType.lowerBounds.isEmpty()) {
            return KTypeProjection.contravariant(CreateType(javaType.lowerBounds[0]))
        }
        if (javaType.upperBounds[0] == java.lang.Object::class.java) {
            return KTypeProjection.STAR
        }
        return KTypeProjection.covariant(CreateType(javaType.upperBounds[0]))
    }
}
