package com.ast.adk.injector

import java.util.*
import java.util.function.Predicate
import kotlin.Array
import kotlin.reflect.*
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

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
    private companion object {
        /** Represents placeholder for factory parameter in a constructor. */
        val FACTORY_PARAM = DepRef(TypeKey(Nothing::class, null, TypeKey.Type.FACTORY))
    }

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

    /** Dependency reference. Graph link. */
    private class DepRef(val key: TypeKey) {
        /** Resolved node after linkage stage complete. */
        var node: Node? = null
    }

    private class InjectableField(
        val field: KMutableProperty1<*, *>?,
        val dep: DepRef?
    )

    private class NodeParams(
        val key: TypeKey,
        /** Describes where the node is originated from. */
        val origin: String,
        val isSingleton: Boolean
    ) {
        /**  Provider module if any. */
        var providerModule: Any? = null
        var providerMethod: KCallable<*>? = null
        var providerDeps: Array<DepRef>? = null

        /** Injectable constructor if any. */
        var injectCtr: KCallable<*>? = null
        /** Dependencies injected in constructor, element may be null for factory parameter. */
        var ctrDeps: Array<DepRef>? = null

        /** Injectable fields if any. */
        var injectFields: Array<InjectableField>? = null
    }

    /** Dependency graph node.  */
    private class Node(params: NodeParams) {
        val key: TypeKey = params.key
        /** Describes where the node is originated from. */
        val origin: String = params.origin
        val isSingleton: Boolean = params.isSingleton

        /**  Provider module if any. */
        val providerModule: Any? = params.providerModule
        val providerMethod: KCallable<*>? = params.providerMethod
        val providerDeps: Array<DepRef>? = params.providerDeps

        /** Injectable constructor if any. */
        val injectCtr: KCallable<*>? = params.injectCtr
        /** Dependencies injected in constructor, element may be null for factory parameter. */
        val ctrDeps: Array<DepRef>? = params.ctrDeps

        /** Injectable fields if any. */
        val injectFields: Array<InjectableField>? = params.injectFields

        /** Singleton instance stored here for singleton node. Factory instance for factory node. */
        var singletonInstance: Any? = null


        /** Check if node constructor (if any) has factory parameters placeholders.  */
        fun HasFactoryParams(): Boolean
        {
            if (ctrDeps == null) {
                return false
            }
            for (ref in ctrDeps) {
                if (ref === FACTORY_PARAM) {
                    return true
                }
            }
            return false
        }

        /** Get all dependencies which satisfy the specified predicate. The predicate may be null to
         * return all dependencies.
         * @return List of matched dependencies, null if no match.
         */
        fun GetDependencies(predicate: Predicate<DepRef>? = null): List<DepRef>?
        {
            val result = ArrayList<DepRef>()
            if (providerDeps != null) {
                for (ref in providerDeps) {
                    if (predicate == null || predicate.test(ref)) {
                        result.add(ref)
                    }
                }
            }
            if (ctrDeps != null) {
                for (ref in ctrDeps) {
                    if (predicate == null || predicate.test(ref)) {
                        result.add(ref)
                    }
                }
            }
            if (injectFields != null) {
                for (f in injectFields) {
                    if (predicate == null || predicate.test(f.dep!!)) {
                        result.add(f.dep!!)
                    }
                }
            }
            return if (result.size == 0) null else result
        }

        /** Get list of unresolved dependencies. Null if all dependencies resolved.  */
        fun GetUnresolvedDependencies(): List<DepRef>?
        {
            return GetDependencies(Predicate { dep -> dep.node == null })
        }

        private fun GetDependentParameters(deps: Array<DepRef>,
                                           factoryParams: Array<out Any?>?): Array<Any?>
        {
            var curFactoryParamIdx = 0
            val result = Array(deps.size) {
                i ->
                val dep = deps[i]
                if (dep !== FACTORY_PARAM) {
                    return@Array dep.node!!.Create()
                } else {
                    /* Factory parameter placeholder. */
                    if (curFactoryParamIdx >= factoryParams!!.size) {
                        throw DiException("Insufficient number of factory arguments specified")
                    }
                    return@Array factoryParams[curFactoryParamIdx++]
                }
            }
            if (factoryParams != null && curFactoryParamIdx < factoryParams.size) {
                throw DiException("Too many factory arguments specified")
            }
            return result
        }

        /** Instantiate the node.  */
        fun Create(): Any
        {
            return if (isSingleton) {
                synchronized(this) {
                    return singletonInstance ?: _Create(null).also { singletonInstance = it }
                }
            } else if (key.type == TypeKey.Type.FACTORY) {
                /* Factory instance is stored here during compilation. */
                singletonInstance!!
            } else {
                _Create(null)
            }
        }

        private fun _Create(factoryParams: Array<out Any?>?): Any
        {
            return Any()//XXX

//            val result: Any
//            if (providerModule != null) {
//                if (providerMethod == null) {
//                    throw IllegalStateException()
//                }
//                try {
//                    if (providerDeps != null) {
//                        return providerMethod.call(providerModule,
//                                                   *GetDependentParameters(providerDeps, null))!!
//                    } else {
//                        return providerMethod.call(providerModule)!!
//                    }
//                } catch (e: IllegalAccessException) {
//                    throw DiException("Failed to invoke provider: $origin", e)
//                } catch (e: InvocationTargetException) {
//                    throw DiException("Failed to invoke provider: $origin", e)
//                }
//
//            } else {
//                try {
//                    result = injectCtr!!.call(*GetDependentParameters(ctrDeps, factoryParams)!!)
//                } catch (e: InstantiationException) {
//                    throw DiException("Failed to invoke constructor: $origin", e)
//                } catch (e: IllegalAccessException) {
//                    throw DiException("Failed to invoke constructor: $origin", e)
//                } catch (e: InvocationTargetException) {
//                    throw DiException("Failed to invoke constructor: $origin", e)
//                }
//            }
//            /* Inject fields. */
//            injectFields?.also {
//                injectFields ->
//                for (f in injectFields) {
//                    try {
//                        f.field!!.set(result, f.dep!!.node!!.Create())
//                    } catch (e: IllegalAccessException) {
//                        throw DiException("Failed to set injectable field: " + f.field!!.getName(), e)
//                    }
//
//                }
//            }
//            return result
        }

        inner class FactoryImpl: DiFactory<Any> {
            override fun Create(vararg params: Any?): Any
            {
                return _Create(params)
            }
        }
    }

    private fun ThrowNotAnnotatedModule(moduleCls: KClass<*>)
    {
        throw DiException("Module class is not annotated with @Module: " + moduleCls.qualifiedName)
    }

    /** Get qualifier annotations from the specified annotations array.  */
    private fun GetQualifiers(annotations: List<Annotation>): List<Annotation>
    {
        val qualifiers = ArrayList<Annotation>()
        for (ann in annotations) {
            ann::class.findAnnotation<Qualifier>()?.also {
                qualifiers.add(it)
            }
        }
        return qualifiers
    }

    private fun IsFactoryParam(annotations: List<Annotation>): Boolean
    {
        for (ann in annotations) {
            if (ann is FactoryParam) {
                return true
            }
        }
        return false
    }


    /** Get produced value type for factory parameter of provider or constructor. Null of not factory.  */
    private fun GetFactoryParamClass(paramType: KType): KClass<*>?
    {
        if (paramType.jvmErasure != DiFactory::class) {
            return null
        }
        val proj = paramType.arguments[0]
        val type = proj.type ?: throw DiException("Star projection not allowed for factory type")
        if (proj.variance != KVariance.INVARIANT) {
            throw DiException("Only invariant type projection allowed for factory type")
        }
        return type.classifier as KClass<*>
    }

//    private fun GetInjectableField(field: KProperty<*>): InjectableField
//    {
//        val nif = InjectableField()
//        nif.field = field
//        val type = field.type
//        val qualifiers = GetQualifiers(field.declaredAnnotations)
//        val key: TypeKey
//        if (type == DiFactory<*>::class.java) {
//            if (!qualifiers.isEmpty()) {
//                throw DiException(String.format(
//                    "Qualifiers not allowed for factory field: %s::%s",
//                    field.declaringClass.name, field.name))
//            }
//            val factoryType =
//                (field.genericType as ParameterizedType).actualTypeArguments[0] as Class<*>
//            key = TypeKey(factoryType, null, TypeKey.Type.FACTORY)
//        } else {
//            key = TypeKey(type, qualifiers)
//        }
//        nif.dep = Node.DepRef(key)
//        return nif
//    }

    private fun DetectCircularDeps(startNode: Node, stack: Deque<Node>)
    {
        /* Check if the node is already in the stack. */
        if (stack.contains(startNode)) {
            val sb = StringBuilder()
            var node: Node
            do {
                node = stack.removeLast()
                sb.append(String.format(" <- %s [%s]", node.key, node.origin))
            } while (node !== startNode)
            throw DiException("Circular dependency detected: <loop>" + sb.toString())
        }
        /* Traverse node dependencies. */
        stack.addLast(startNode)
        val deps = startNode.GetDependencies()
        if (deps != null) {
            for (dep in deps) {
                DetectCircularDeps(dep.node!!, stack)
            }
        }
        stack.removeLast()
    }

    /** Create nodes for providers in the specified module.  */
    private fun CreateModuleNodes(module: Any): List<Node>
    {
        val moduleCls = module::class
        if (moduleCls.findAnnotation<Module>() == null) {
            ThrowNotAnnotatedModule(moduleCls)
        }

        val nodes = ArrayList<Node>()
        val methods = moduleCls.declaredMemberFunctions
        for (method in methods) {
            if (method.findAnnotation<Provides>() != null) {
                nodes.add(CreateProviderNode(module, method))
            }
        }
        return nodes
    }

    /** Create node for provider method in the specified module.  */
    private fun CreateProviderNode(module: Any, method: KFunction<*>): Node
    {
        val type = method.returnType.jvmErasure
        val qualifiers = GetQualifiers(method.annotations)
        val key = TypeKey(type, qualifiers)
        val origin = "Module " + method.name
        val nodeParams = NodeParams(key, origin, method.findAnnotation<Singleton>() != null)

        nodeParams.providerModule = module
        method.isAccessible = true
        nodeParams.providerMethod = method
        /* Check for proxy parameter only for unqualified provider. */
        nodeParams.providerDeps =
            NodeDepRefsFromParameters(method, if (key.qualifiers == null) type else null)

        return Node(nodeParams)
    }

    /** Get array of node dependencies references from method or constructor parameters.
     * @param providerType Return type for provider method, null for constructor. Used to detect
     * proxy providers.
     */
    private fun NodeDepRefsFromParameters(callable: KCallable<*>,
                                          providerType: KClass<*>?): Array<DepRef>
    {
        return Array(callable.parameters.size) {
            i ->
            val param = callable.parameters[i]
            val paramAnn = param.annotations
            val paramType = param.type
            if (IsFactoryParam(paramAnn)) {
                if (providerType != null) {
                    throw DiException(
                        "Factory parameters not allowed in provider method: " + callable.name)
                }
                return@Array FACTORY_PARAM
            }
            val factoryType = GetFactoryParamClass(paramType)
            val qualifiers = GetQualifiers(paramAnn)

            if (factoryType != null) {
                if (!qualifiers.isEmpty()) {
                    throw DiException(
                        "Qualifiers not allowed for factory parameter: " + callable.name)
                }
                return@Array DepRef(TypeKey(factoryType, null, TypeKey.Type.FACTORY))

            } else {
                /* Detect proxy provider parameter. */
                val isProxy = providerType != null &&
                        qualifiers.isEmpty() &&
                        paramType.jvmErasure == providerType
                return@Array DepRef(
                    TypeKey(paramType.jvmErasure, qualifiers,
                            if (isProxy) TypeKey.Type.PROXY else TypeKey.Type.REGULAR))
            }
        }
    }


}
