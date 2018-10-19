package com.ast.adk.injector

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*
import java.util.function.Predicate
import kotlin.reflect.KClass

internal class DependencyGraph(private val rootClass: KClass<*>,
                               private val modules: List<Any>,
                               private val overrideModules: List<Any>) {


    fun Compile()
    {
        //XXX
    }

    fun CreateRoot(): Any
    {
        //XXX
        return Any()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////


    /** Injected types are identified by type and qualifiers if any. This type aggregates this
     * information and ensures proper comparison.
     */
    private class TypeKey(val cls: KClass<*>,
                          qualifiers: Collection<Annotation>?,
                          val type: Type = Type.REGULAR) {

        /** Can be null if no qualifiers.  */
        val qualifiers: List<Annotation>?

        enum class Type {
            REGULAR,
            /** Indicates proxy provider dependency of unqualified injectable class.  */
            PROXY,
            /** Factory for the specified class.  */
            FACTORY
        }

        init {
            if (qualifiers != null && !qualifiers.isEmpty()) {
                val _qualifiers = ArrayList(qualifiers)
                /* Name used only for sorting so it still is safe for working with class names
                 * obfuscations.
                 */
                _qualifiers.sortWith(
                    Comparator.comparing<Annotation, String> { a -> a::class.qualifiedName})
                this.qualifiers = _qualifiers
            } else {
                this.qualifiers = null
            }
        }

        override fun equals(other: Any?): Boolean
        {
            return equals(other as TypeKey)
        }

        fun equals(other: TypeKey): Boolean
        {
            if (cls != other.cls || type != other.type) {
                return false
            }
            if (qualifiers == null != (other.qualifiers == null)) {
                return false
            }
            if (qualifiers == null) {
                return true
            }
            val n = qualifiers.size
            if (n != other.qualifiers!!.size) {
                return false
            }
            /* Qualifiers are sorted so they can be compared one by one. */
            for (i in 0 until n) {
                if (qualifiers[i] != other.qualifiers[i]) {
                    return false
                }
            }
            return true
        }

        override fun hashCode(): Int
        {
            var h = cls.hashCode()
            if (qualifiers != null) {
                for (a in qualifiers) {
                    h = h xor a.hashCode()
                }
            }
            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (type) {
                DependencyGraph.TypeKey.Type.PROXY -> h *= 13
                DependencyGraph.TypeKey.Type.FACTORY -> h *= 17
            }
            return h
        }

        override fun toString(): String
        {
            val sb = StringBuilder()
            if (qualifiers != null) {
                for (a in qualifiers) {
                    if (sb.isNotEmpty()) {
                        sb.append(' ')
                    }
                    sb.append(a.toString())
                }
            }
            if (sb.isNotEmpty()) {
                sb.append(' ')
            }
            sb.append(cls.simpleName)
            sb.append(" (")
            sb.append(type.toString())
            sb.append(")")
            return sb.toString()
        }
    }


    /** Dependency graph node.  */
    private class Node(val key: TypeKey,
                       /** Describes where the node is originated from. */
                       val origin: String,
                       val isSingleton: Boolean) {

        /**  Provider module if any. */
        var providerModule: Any? = null
        var providerMethod: Method? = null
        var providerDeps: Array<DepRef>? = null

        /** Injectable constructor if any. */
        var injectCtr: Constructor<*>? = null
        /** Dependencies injected in constructor, element may be null for factory parameter. */
        var ctrDeps: Array<DepRef?>? = null

        /** Injectable fields if any. */
        var injectFields: Array<InjectableField>? = null

        /** Singleton instance stored here for singleton node. Factory instance for factory node. */
        var singletonInstance: Any? = null

        /** Dependency reference. Graph link. */
        class DepRef(val key: TypeKey) {
            /** Resolved node after linkage stage complete. */
            var node: Node? = null
        }

        class InjectableField {
            var field: Field? = null
            var dep: DepRef? = null
        }


        /** Check if node constructor (if any) has factory parameters placeholders.  */
        fun HasFactoryParams(): Boolean
        {
            ctrDeps?.also {
                ctrDeps->
                for (ref in ctrDeps) {
                    if (ref == null) {
                        return true
                    }
                }
            }
            return false
        }

        /** Get all dependencies which satisfy the specified predicate. The predicate may be null to
         * return all dependencies.
         * @return List of matched dependencies, null if no match.
         */
        fun GetDependencies(predicate: Predicate<DepRef>?): List<DepRef>?
        {
            val result = ArrayList<DepRef>()
            providerDeps?.also {
                providerDeps ->
                for (ref in providerDeps) {
                    if (predicate == null || predicate.test(ref)) {
                        result.add(ref)
                    }
                }
            }
            ctrDeps?.also {
                ctrDeps ->
                for (ref in ctrDeps) {
                    if (predicate == null || predicate.test(ref)) {
                        result.add(ref)
                    }
                }
            }
            injectFields?.also {
                injectFields ->
                for (f in injectFields) {
                    if (predicate == null || predicate.test(f.dep)) {
                        result.add(f.dep)
                    }
                }
            }
            return if (result.size == 0) null else result
        }
    }
}
