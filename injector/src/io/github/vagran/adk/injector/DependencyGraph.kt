/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.injector

import java.util.*
import java.util.function.Predicate
import kotlin.Array
import kotlin.collections.ArrayList
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.jvmErasure

internal class DependencyGraph(private val rootClass: KClass<*>,
                               private val modules: List<Any>,
                               private val overrideModules: List<Any>) {


    fun Compile()
    {
        val providers = GatherProviders()
        providers.forEach { node -> nodes[node.key] = node }

        val rootKey = TypeKey(rootClass, null)
        nodes[rootKey]?.also {
            throw DI.Exception(
                "Component class ${rootClass.qualifiedName} should not be provided by modules, " +
                "found in ${it.origin}")
        }

        rootNode = CreateClassNode(rootKey, true)
        nodes[rootKey] = rootNode

        /* Resolve links in iterative passes. */
        var numResolved: Int
        do {
            numResolved = 0
            val nodesToCheck = ArrayList(nodes.values)
            for (node in nodesToCheck) {
                val deps = node.GetUnresolvedDependencies() ?: continue
                for (dep in deps) {
                    var depNode: Node? = nodes[dep.key]
                    if (depNode == null) {
                        depNode = when (dep.key.type) {
                            TypeKey.Type.SCOPE -> CreateScopeNode(dep.key)
                            TypeKey.Type.FACTORY -> CreateFactoryNode(dep.key)
                            TypeKey.Type.ATTRIBUTES -> CreateAttributesNode(dep.key)
                            TypeKey.Type.GRAPH -> CreateGraphNode(dep.key)
                            else -> {
                                if (dep.key.qualifiers != null) {
                                    throw DI.Exception(
                                        "Unresolved qualified injection ${dep.key} in ${node.origin}")
                                }
                                CreateClassNode(dep.key, false)
                            }
                        }
                        nodes[depNode.key] = depNode
                    }
                    dep.node = depNode
                }
                numResolved++
            }
        } while (numResolved != 0)

        /* Create factory nodes for all registered unqualified classes to make them dynamically
         * instantiable via graph injection.
         */
        val classKeys = nodes.keys.filter { it.type == TypeKey.Type.REGULAR && it.qualifiers == null }
        for (key in classKeys) {
            val factoryKey = TypeKey(key.cls, null, TypeKey.Type.FACTORY)
            var node = nodes[factoryKey]
            if (node != null) {
                continue
            }
            node = CreateFactoryNode(factoryKey)
            node.factoryDep!!.node = nodes[key]
            nodes[factoryKey] = node
        }

        /* Recursively traverse the graph and detect circular dependencies if any. */
        val stack = ArrayDeque<Node>()
        DetectCircularDeps(rootNode, stack)

        isCompiled = true
    }

    fun CreateRoot(scope: DI.Scope? = null, attrs: DI.Attributes? = null): Any
    {
        if (!isCompiled) {
            throw IllegalStateException("Should be compiled before instantiation")
        }
        return rootNode.Create(scope, attrs)!!
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val nodes = HashMap<TypeKey, Node>()
    private lateinit var rootNode: Node
    private var isCompiled = false
    private val graphImpl = GraphImpl()

    private companion object {
        /** Represents placeholder for factory parameter in a constructor. */
        val FACTORY_PARAM = DepRef(TypeKey(Nothing::class, null, TypeKey.Type.FACTORY))
        val EMPTY_ATTRIBUTES = DI.Attributes(emptyList())
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
            FACTORY,
            /** Scope object. */
            SCOPE,
            /** Attributes list. */
            ATTRIBUTES,
            /** Dependency graph object. */
            GRAPH
        }

        init {
            if (qualifiers != null && !qualifiers.isEmpty()) {
                val _qualifiers = ArrayList(qualifiers)
                /* Name used only for sorting so it still is safe for working with class names
                 * obfuscations.
                 */
                _qualifiers.sortWith(
                    Comparator.comparing<Annotation, String> { it::class.qualifiedName})
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
                Type.PROXY -> h *= 13
                Type.FACTORY -> h *= 17
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
    private class DepRef(val key: TypeKey,
                         val refType: Type = Type.NONE,
                         val attributes: DI.Attributes? = null) {

        enum class Type {
            NONE,
            CONSTRUCTOR_ARG,
            PROVIDER_ARG,
            FIELD,
            ADDITIONAL_REF
        }

        /** Resolved node after linkage stage complete. */
        var node: Node? = null

        fun GetAttributes(attrs: DI.Attributes?): DI.Attributes?
        {
            if (key.type == TypeKey.Type.REGULAR || key.type == TypeKey.Type.PROXY) {
                return attributes
            }
            return attrs
        }
    }

    private class InjectableField(
        val field: KMutableProperty1<Any, Any?>,
        val dep: DepRef
    )

    private enum class SingletonType {
        NONE,
        GLOBAL,
        SCOPED
    }

    private class NodeParams(
        val key: TypeKey,
        /** Describes where the node is originated from. */
        val origin: String,
        val singletonType: SingletonType
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

        /** Dependency for factory (class node). */
        var factoryDep: DepRef? = null
        /** Additional references declared via AdditionalRefs annotation. */
        var additionalRefs: Array<DepRef>? = null
    }

    /** Dependency graph node.  */
    private class Node(params: NodeParams) {
        val key: TypeKey = params.key
        /** Describes where the node is originated from. */
        val origin: String = params.origin
        val singletonType: SingletonType = params.singletonType
        val isScope: Boolean get() = key.type == TypeKey.Type.SCOPE

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
        /** Dependency for factory (class node). */
        val factoryDep: DepRef? = params.factoryDep
        /** Additional references declared via AdditionalRefs annotation. */
        val additionalRefs: Array<DepRef>? = params.additionalRefs

        /** Singleton instance stored here for singleton node. Factory instance (bound to global
         * scope) for factory node.
         */
        @Volatile var singletonInstance: Any? = null


        override fun toString(): String
        {
            return "$key @ $origin"
        }

        /** Check if node constructor or provider has factory parameters placeholders.  */
        fun HasFactoryParams(): Boolean
        {
            if (ctrDeps != null) {
                for (ref in ctrDeps) {
                    if (ref === FACTORY_PARAM) {
                        return true
                    }
                }
            }
            if (providerDeps != null) {
                for (ref in providerDeps) {
                    if (ref === FACTORY_PARAM) {
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
                    if (predicate == null || predicate.test(f.dep)) {
                        result.add(f.dep)
                    }
                }
            }
            if (factoryDep != null) {
                if (predicate == null || predicate.test(factoryDep)) {
                    result.add(factoryDep)
                }
            }
            if (additionalRefs != null) {
                for (ref in additionalRefs) {
                    if (predicate == null || predicate.test(ref)) {
                        result.add(ref)
                    }
                }
            }
            return if (result.size == 0) null else result
        }

        /** Get list of unresolved dependencies. Null if all dependencies resolved.  */
        fun GetUnresolvedDependencies(): List<DepRef>?
        {
            return GetDependencies(Predicate { dep -> dep !== FACTORY_PARAM && dep.node == null })
        }

        /** Prepare arguments array for injectable function. Required arguments specified via
         * dependency references array. Constructor may contain factory parameters which are
         * provided in factoryParams.
         */
        private fun GetDependentParameters(scope: DI.Scope?, attrs: DI.Attributes?,
                                           deps: Array<DepRef>,
                                           factoryParams: Array<out Any?>?): Array<Any?>
        {
            var curFactoryParamIdx = 0
            val result = Array(deps.size) {
                i ->
                val dep = deps[i]
                if (dep !== FACTORY_PARAM) {
                    return@Array dep.node!!.Create(scope, dep.GetAttributes(attrs))
                } else {
                    /* Factory parameter placeholder. */
                    if (curFactoryParamIdx >= factoryParams!!.size) {
                        throw DI.Exception("Insufficient number of factory arguments specified for $key")
                    }
                    return@Array factoryParams[curFactoryParamIdx++]
                }
            }
            if (factoryParams != null && curFactoryParamIdx < factoryParams.size) {
                throw DI.Exception("Too many factory arguments specified for $key")
            }
            return result
        }

        /** Instantiate the node. Null can be returned for scope node in global scope. */
        fun Create(scope: DI.Scope?, attrs: DI.Attributes?,
                   factoryParams: Array<out Any?>? = null): Any?
        {
            return when {
                singletonType == SingletonType.GLOBAL ||
                (singletonType == SingletonType.SCOPED && scope == null) -> {
                    singletonInstance ?: synchronized(this) {
                        singletonInstance ?: CreateImpl(scope, attrs, factoryParams)
                            .also { singletonInstance = it }
                    }
                }

                singletonType == SingletonType.SCOPED -> {
                    scope!!.CreateSingleton(key) { CreateImpl(scope, attrs, factoryParams) }
                }

                /* Factory instance is stored here during compilation. */
                key.type == TypeKey.Type.FACTORY -> {
                    if (scope != null) {
                        FactoryImpl(scope)
                    } else {
                        singletonInstance!!
                    }
                }

                key.type == TypeKey.Type.ATTRIBUTES -> {
                    attrs ?: EMPTY_ATTRIBUTES
                }

                key.type == TypeKey.Type.SCOPE -> scope

                key.type == TypeKey.Type.GRAPH -> singletonInstance

                else -> CreateImpl(scope, attrs, factoryParams)
            }
        }

        private fun CreateImpl(scope: DI.Scope?, attrs: DI.Attributes?,
                               factoryParams: Array<out Any?>?): Any
        {
            val result: Any

            if (providerModule != null) {
                if (providerMethod == null) {
                    throw IllegalStateException()
                }
                return try {
                    if (providerDeps != null) {
                        providerMethod.call(providerModule,
                                            *GetDependentParameters(scope, attrs, providerDeps,
                                                                    factoryParams))!!
                    } else {
                        providerMethod.call(providerModule)!!
                    }
                } catch (e: Exception) {
                    throw DI.Exception("Failed to invoke provider: $origin", e)
                }

            } else {
                if (injectCtr == null) {
                    throw IllegalStateException()
                }
                result = try {
                    if (ctrDeps != null) {
                        injectCtr.call(*GetDependentParameters(scope, attrs, ctrDeps,
                                                               factoryParams))!!
                    } else {
                        injectCtr.call()!!
                    }
                } catch (e: DI.Exception) {
                    throw e
                } catch (e: Exception) {
                    throw DI.Exception("Failed to invoke constructor: $origin", e)
                }
            }

            /* Inject fields. */
            if (injectFields != null) {
                for (f in injectFields) {
                    try {
                        f.field.set(result, f.dep.node!!.Create(scope, f.dep.GetAttributes(attrs)))
                    } catch (e: Exception) {
                        throw DI.Exception("Failed to set injectable field: ${f.field.name}", e)
                    }
                }
            }

            return result
        }

        inner class FactoryImpl(val scope: DI.Scope?): DI.Factory<Any> {
            override fun Create(vararg params: Any?): Any
            {
                return this@Node.factoryDep!!.node!!.Create(scope, null, params)!!
            }

            override fun CreateScoped(scope: DI.Scope, vararg params: Any?): Any
            {
                return this@Node.factoryDep!!.node!!.Create(scope, null, params)!!
            }
        }
    }

    private inner class GraphImpl: DI.Graph {

        @Suppress("UNCHECKED_CAST")
        override fun <T: Any> GetFactory(cls: KClass<T>): DI.Factory<T>
        {
            val key = TypeKey(cls, null, TypeKey.Type.FACTORY)
            val node = nodes[key] ?: throw DI.Exception("Unresolved type: ${cls.qualifiedName}")
            return node.Create(null, null, null) as DI.Factory<T>
        }
    }

    private fun ThrowNotAnnotatedModule(moduleCls: KClass<*>): Nothing
    {
        throw DI.Exception("Module class is not annotated with @Module: " + moduleCls.qualifiedName)
    }

    /** Get qualifier annotations from the specified annotations array.  */
    private fun GetQualifiers(annotations: List<Annotation>): List<Annotation>
    {
        val qualifiers = ArrayList<Annotation>()
        for (ann in annotations) {
            ann.annotationClass.findAnnotation<Qualifier>()?.also {
                qualifiers.add(ann)
            }
        }
        return qualifiers
    }

    private fun GetAttributes(annotations: List<Annotation>): DI.Attributes?
    {
        val list = ArrayList<Annotation>()
        for (ann in annotations) {
            if (ann.annotationClass.findAnnotation<Attribute>() != null) {
                list.add(ann)
            }
        }
        return if (list.isEmpty()) null else DI.Attributes(list)
    }

    /** Check if parameter is factory parameter based on its annotations list. */
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
        if (paramType.jvmErasure != DI.Factory::class) {
            return null
        }
        val proj = paramType.arguments[0]
        val type = proj.type ?: throw DI.Exception("Star projection not allowed for factory type")
        if (proj.variance != KVariance.INVARIANT) {
            throw DI.Exception("Only invariant type projection allowed for factory type")
        }
        return type.classifier as KClass<*>
    }

    private fun GetInjectableField(field: KMutableProperty1<Any, Any?>): InjectableField
    {
        val type = field.returnType.jvmErasure
        val qualifiers = GetQualifiers(field.annotations)
        val key: TypeKey
        key = when {
            type == DI.Factory::class -> {
                if (qualifiers.isNotEmpty()) {
                    throw DI.Exception(
                        "Qualifiers not allowed for factory field: " +
                        "${field.parameters[0].type}::${field.name}")
                }
                val factoryType = GetFactoryParamClass(field.returnType)!!
                TypeKey(factoryType, null, TypeKey.Type.FACTORY)
            }
            type.isSubclassOf(DI.Scope::class) -> {
                if (qualifiers.isNotEmpty()) {
                    throw DI.Exception(
                        "Qualifiers not allowed for scope field: " +
                        "${field.parameters[0].type}::${field.name}")
                }
                TypeKey(type, null, TypeKey.Type.SCOPE)
            }
            type == DI.Attributes::class -> {
                TypeKey(type, null, TypeKey.Type.ATTRIBUTES)
            }
            type == DI.Graph::class -> {
                TypeKey(type, null, TypeKey.Type.GRAPH)
            }
            else -> {
                TypeKey(type, qualifiers)
            }
        }
        field.isAccessible = true
        return InjectableField(field, DepRef(key, DepRef.Type.FIELD,
                                             GetAttributes(field.annotations)))
    }

    private fun DetectCircularDeps(startNode: Node, stack: Deque<Node>)
    {
        /* Check if the node is already in the stack. Factory node breaks the link. */
        val cycleFound = run {
            val it = stack.descendingIterator()
            var factoryFound = false
            while (it.hasNext()) {
                val node = it.next()
                if (node === startNode) {
                    if (factoryFound) {
                        /* Cycle found but link is broken by factory. */
                        return
                    }
                    return@run true
                }
                if (node.key.type == TypeKey.Type.FACTORY) {
                    factoryFound = true
                }
            }
            false
        }
        if (cycleFound) {
            val sb = StringBuilder()
            var node: Node
            do {
                node = stack.removeLast()
                sb.append(" <- ${node.key} [${node.origin}]")
            } while (node !== startNode)
            throw DI.Exception("Circular dependency detected: <loop>$sb")
        }
        /* Traverse node dependencies. */
        stack.addLast(startNode)
        val deps = startNode.GetDependencies()
        if (deps != null) {
            for (dep in deps) {
                if (dep !== FACTORY_PARAM) {
                    if (dep.refType != DepRef.Type.ADDITIONAL_REF &&
                        startNode.key.type != TypeKey.Type.FACTORY &&
                        dep.node!!.HasFactoryParams()) {

                        throw DI.Exception(
                            "Direct injection not allowed for factory-produced class: " +
                            "${dep.node!!.origin} required from ${startNode.origin}")
                    }
                    DetectCircularDeps(dep.node!!, stack)
                }
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

        for (method in moduleCls.declaredMemberFunctions) {
            if (method.findAnnotation<Provides>() != null) {
                nodes.add(CreateProviderNode(module, method, method.annotations))
            }
        }

        for (prop in moduleCls.declaredMemberProperties) {
            if (prop.findAnnotation<Provides>() != null) {
                nodes.add(CreateProviderNode(module, prop.getter, prop.annotations))
            }
        }

        return nodes
    }

    private fun GetSingletonType(annotations: List<Annotation>): SingletonType
    {
        val a = annotations.firstOrNull { it is Singleton } ?: return SingletonType.NONE
        return if ((a as Singleton).perScope) SingletonType.SCOPED else SingletonType.GLOBAL
    }

    /** Create node for provider method in the specified module.  */
    private fun CreateProviderNode(module: Any, method: KFunction<*>,
                                   annotations: List<Annotation>): Node
    {
        val type = method.returnType.jvmErasure
        val qualifiers = GetQualifiers(annotations)
        val key = TypeKey(type, qualifiers)
        val origin = "Module ${method.parameters[0].type}::${method.name}"
        val nodeParams = NodeParams(key, origin, GetSingletonType(annotations))

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
        /* Non-constructor function has receiver as first parameter which should not be processed
         * here.
         */
        val hasReceiver =
            callable.parameters.isNotEmpty() && callable.parameters[0].kind != KParameter.Kind.VALUE
        val size = if (hasReceiver) {
            callable.parameters.size - 1
        } else {
            callable.parameters.size
        }
        return Array(size) {
            i ->
            val paramIdx = if (hasReceiver) i + 1 else i
            val param = callable.parameters[paramIdx]
            val paramAnn = param.annotations
            val paramType = param.type
            if (IsFactoryParam(paramAnn)) {
                return@Array FACTORY_PARAM
            }
            val factoryType = GetFactoryParamClass(paramType)
            val qualifiers = GetQualifiers(paramAnn)

            when {
                factoryType != null -> {
                    if (!qualifiers.isEmpty()) {
                        throw DI.Exception(
                                "Qualifiers not allowed for factory parameter: " + callable.name)
                    }
                    return@Array DepRef(TypeKey(factoryType, null, TypeKey.Type.FACTORY))

                }
                paramType.jvmErasure.isSubclassOf(DI.Scope::class) -> {
                    return@Array DepRef(TypeKey(paramType.jvmErasure, null, TypeKey.Type.SCOPE))
                }
                paramType.jvmErasure == DI.Attributes::class -> {
                    return@Array DepRef(TypeKey(paramType.jvmErasure, null, TypeKey.Type.ATTRIBUTES))
                }
                paramType.jvmErasure == DI.Graph::class -> {
                    return@Array DepRef(TypeKey(paramType.jvmErasure, null, TypeKey.Type.GRAPH))
                }
                else -> {
                    /* Detect proxy provider parameter. */
                    val isProxy = providerType != null &&
                            qualifiers.isEmpty() &&
                            paramType.jvmErasure == providerType
                    return@Array DepRef(
                            TypeKey(paramType.jvmErasure, qualifiers,
                                    if (isProxy) TypeKey.Type.PROXY else TypeKey.Type.REGULAR),
                            if (providerType != null)
                                DepRef.Type.PROVIDER_ARG else DepRef.Type.CONSTRUCTOR_ARG,
                            GetAttributes(param.annotations))
                }
            }
        }
    }

    /** Return all module classes declared for root component.  */
    private fun GetComponentModules(): Set<KClass<*>>
    {
        val compAnn = rootClass.findAnnotation<Component>()
            ?: throw DI.Exception("Root component class is not annotated with @Component: " +
                                     rootClass.qualifiedName)
        val result = HashSet<KClass<*>>()
        for (moduleCls in compAnn.modules) {
            AddModuleWithIncludes(moduleCls, result)
        }
        return result
    }

    /** Add all declared module classes while recursively resolving includes. */
    private fun AddModuleWithIncludes(moduleCls: KClass<*>, modules: MutableSet<KClass<*>>)
    {
        if (!modules.add(moduleCls)) {
            return
        }
        val moduleAnn = moduleCls.findAnnotation<Module>()
            ?: ThrowNotAnnotatedModule(moduleCls)
        for (includeModuleCls in moduleAnn.include) {
            AddModuleWithIncludes(includeModuleCls, modules)
        }
    }

    /** Check if specified class is valid module instance class.
     * @return Module class to register this module with. May be the moduleCls or its base class
     * listed in component modules list. Null if the specified module class is not valid.
     */
    private fun CheckModuleInstance(moduleCls: KClass<*>,
                                    componentModules: Collection<KClass<*>>): KClass<*>?
    {
        val moduleAnn = moduleCls.findAnnotation<Module>() ?: ThrowNotAnnotatedModule(moduleCls)
        for (cls in componentModules) {
            if (cls.isSuperclassOf(moduleCls)) {
                if (cls != moduleCls && moduleAnn.include.isNotEmpty()) {
                    throw DI.Exception("Include not allowed in inherited module instance: $moduleCls")
                }
                return cls
            }
        }
        return null
    }

    /** Check if module instance of specific class (which may be its subclass) is present in the
     * provided list.
     */
    private fun CheckModuleSpecified(declaredModuleCls: KClass<*>, modules: Collection<Any>): Boolean
    {
        for (module in modules) {
            if (declaredModuleCls.isSuperclassOf(module::class)) {
                return true
            }
        }
        return false
    }

    private fun GatherProviders(): List<Node>
    {
        /* Sort out providers. Handle specified modules instances, then create all the rest
         * specified in the component declaration, then override some providers by the specified
         * override modules.
         */
        val modules = HashMap<KClass<*>, Any>()
        val providers = HashMap<TypeKey, Node>()
        val componentModules = GetComponentModules()

        for (module in this.modules) {
            val moduleCls = module::class
            val declaredModuleCls = CheckModuleInstance(moduleCls, componentModules)
                ?: throw DI.Exception(
                    "Specified module instance not declared in component modules: " +
                    moduleCls.qualifiedName)
            val prevModule = modules.put(declaredModuleCls, module)
            if (prevModule != null) {
                throw DI.Exception("Module instance provided twice: $declaredModuleCls, " +
                                   "provided $moduleCls, previous $prevModule")
            }
        }

        /* Create instances for all declared modules which instances were not specified. */
        for (moduleCls in componentModules) {
            if (CheckModuleSpecified(moduleCls, modules.values)) {
                continue
            }
            if (moduleCls.isInner) {
                throw DI.Exception(
                    "Inner class not allowed, either make it static or provide module instance: " +
                    moduleCls.qualifiedName)
            }
            if (moduleCls.isAbstract) {
                throw DI.Exception(
                    "Cannot instantiate abstract module class: ${moduleCls.qualifiedName}")
            }

            val ctr = moduleCls.constructors.singleOrNull { it.parameters.all(KParameter::isOptional) }
                ?: throw DI.Exception("Module default constructor not found: ${moduleCls.qualifiedName}")

            if (ctr.javaConstructor?.canAccess(null) == false) {
                ctr.isAccessible = true
            }
            val module: Any
            try {
                module = ctr.callBy(emptyMap())
            } catch (e: Exception) {
                throw DI.Exception("Module constructor failed for ${moduleCls.qualifiedName}", e)
            }

            modules[moduleCls] = module
        }

        val nodesAdder = {
            node: Node, allowOverride: Boolean ->

            val prev = providers.put(node.key, node)
            if (!allowOverride && prev != null) {
                throw DI.Exception(
                    "Duplicated provider:${node.key} at ${node.origin}, " +
                    "previously defined at ${prev.origin}")
            }
        }

        modules.values.forEach { module ->
            CreateModuleNodes(module).forEach { node -> nodesAdder.invoke(node, false) }
        }

        overrideModules.forEach { module ->
            CreateModuleNodes(module).forEach { node -> nodesAdder.invoke(node, true) }
        }

        return ArrayList(providers.values)
    }

    /** Create node for the specified injectable class.  */
    private fun CreateClassNode(key: TypeKey, makeSingleton: Boolean): Node
    {
        if (key.type != TypeKey.Type.REGULAR && key.type != TypeKey.Type.PROXY) {
            throw Error("Unexpected node type")
        }
        val origin = "Class " + key.cls.qualifiedName
        val singletonType = if (makeSingleton) {
            SingletonType.GLOBAL
        } else {
            GetSingletonType(key.cls.annotations)
        }
        val nodeParams = NodeParams(key, origin, singletonType)

        if (key.cls.isInner) {
            throw DI.Exception("Inner class not allowed, either make it static or define provider: " +
                              key.cls.qualifiedName)
        }

        /* Find injectable constructor if any. */
        for (ctr in key.cls.constructors) {
            if (ctr.findAnnotation<Inject>() == null) {
                continue
            }
            if (nodeParams.injectCtr != null) {
                throw DI.Exception(
                    "More than one injectable constructor in class ${key.cls.qualifiedName}")
            }
            if (ctr.javaConstructor?.canAccess(null) == false) {
                ctr.isAccessible = true
            }
            nodeParams.injectCtr = ctr
            nodeParams.ctrDeps = NodeDepRefsFromParameters(ctr, null)
        }

        /* Use default constructor if no injectable one found. */
        if (nodeParams.injectCtr == null) {
            val ctr = key.cls.constructors.singleOrNull { it.parameters.isEmpty() }
                ?: throw DI.Exception("Class default constructor not found: ${key.cls.qualifiedName}")
            if (ctr.javaConstructor?.canAccess(null) == false) {
                ctr.isAccessible = true
            }
            nodeParams.injectCtr = ctr
        }

        /* Find any injectable fields. */
        val fields = ArrayList<InjectableField>()
        for (field in key.cls.memberProperties) {
            if (field.findAnnotation<Inject>() == null) {
                continue
            }
            if (field !is KMutableProperty1) {
                throw DI.Exception("Mutable property expected: $field")
            }
            @Suppress("UNCHECKED_CAST")
            fields.add(GetInjectableField(field as KMutableProperty1<Any, Any?>))
        }

        if (fields.size != 0) {
            nodeParams.injectFields = fields.toTypedArray()
        }

        key.cls.findAnnotation<AdditionalRefs>()?.also {
            ann ->
            val refs = ArrayList<DepRef>()
            for (ref in ann.refs) {
                refs.add(DepRef(TypeKey(ref, null), DepRef.Type.ADDITIONAL_REF))
            }
            nodeParams.additionalRefs = refs.toTypedArray()
        }

        return Node(nodeParams)
    }

    private fun CreateFactoryNode(key: TypeKey): Node
    {
        val params = NodeParams(key, "Class " + key.cls.qualifiedName, SingletonType.NONE)
        params.factoryDep = DepRef(TypeKey(key.cls, key.qualifiers, TypeKey.Type.REGULAR))
        val node = Node(params)
        node.singletonInstance = node.FactoryImpl(null)
        return node
    }

    private fun CreateScopeNode(key: TypeKey): Node
    {
        return Node(NodeParams(key, "Scope class " + key.cls.qualifiedName, SingletonType.NONE))
    }

    private fun CreateAttributesNode(key: TypeKey): Node
    {
        return Node(NodeParams(key, "Attributes", SingletonType.NONE))
    }

    private fun CreateGraphNode(key: TypeKey): Node
    {
        val node = Node(NodeParams(key, "Graph", SingletonType.NONE))
        node.singletonInstance = graphImpl
        return node
    }
}
