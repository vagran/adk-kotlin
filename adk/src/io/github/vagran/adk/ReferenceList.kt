/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk

import io.github.vagran.adk.Ref as IRef

typealias LockFunc<R> = (block: () -> R) -> R
/** Implements explicit references with referents tracking. */
class ReferenceList<T: Any> private constructor(val obj: T,
                                                private val destructor: (() -> Unit)?,
                                                private val lockFunc: LockFunc<*>?) {

    interface Ref<T: Any>: IRef<T> {
        val refList: ReferenceList<T>
    }

    companion object {
        fun <T: Any> Create(obj: T, referent: Any,
                            destructor: (() -> Unit)? = null,
                            lockFunc: LockFunc<*>? = null): Ref<T>
        {
            return ReferenceList(obj, destructor, lockFunc)
                .CreateInitialReference(referent)
        }
    }

    fun AddRef(referent: Any): Ref<T>
    {
        val ref = RefImpl(referent)
        Lock {
            if (refs.isEmpty()) {
                throw Error("Referencing destructed instance, referent: $referent")
            }
            refs.add(ref)
        }
        return ref
    }

    fun EnsureUnreferenced()
    {
        Lock {
            if (refs.isNotEmpty()) {
                val sb = StringBuilder()
                sb.append("Still referenced by:\n")
                for (ref in refs) {
                    sb.append(ref.toString())
                    sb.append('\n')
                }
                throw Error(sb.toString())
            }
        }
    }

    fun IsReferenced(): Boolean
    {
        return Lock { refs.isNotEmpty() }
    }

    fun GetReferences(): List<Any>
    {
        return Lock { refs.toList() }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private inner class RefImpl(val referent: Any):
        Ref<T> {
        override val refList: ReferenceList<T>
            get() = this@ReferenceList

        override val obj: T
        get()
        {
            if (released) {
                throw Error("Accessing instance via released reference")
            }
            return this@ReferenceList.obj
        }

        override fun AddRef(referent: Any): Ref<T>
        {
            if (released) {
                throw Error("Adding reference via released reference")
            }
            return this@ReferenceList.AddRef(referent)
        }

        override fun Release(): Boolean
        {
            if (released) {
                throw Error("Double release")
            }
            released = true
            return Release(this)
        }


        override fun WeakRef(): WeakRef<T>
        {
            if (released) {
                throw Error("Getting weak reference via released reference")
            }
            return WeakRefImpl()
        }

        override fun toString(): String = referent.toString()

        private var released = false
    }

    private inner class WeakRefImpl: WeakRef<T> {
        override fun Lock(referent: Any): IRef<T>?
        {
            return LockWeakRef(referent)
        }
    }

    private val refs = HashSet<RefImpl>()

    @Suppress("UNCHECKED_CAST")
    private fun <R> Lock(block: () -> R): R
    {
        return if (lockFunc != null) {
            (lockFunc as LockFunc<R>)(block)
        } else synchronized(refs) {
            block()
        }
    }

    private fun CreateInitialReference(referent: Any): Ref<T>
    {
        val ref = RefImpl(referent)
        refs.add(ref)
        return ref
    }

    private fun Release(ref: RefImpl): Boolean
    {
        var isLast = false
        Lock {
            if (!refs.remove(ref)) {
                throw Error("Reference not registered: $ref")
            }
            if (refs.isEmpty()) {
                isLast = true
                destructor?.invoke()
            }
        }
        return isLast
    }

    private fun LockWeakRef(referent: Any): RefImpl?
    {
        var ret: RefImpl? = null
        Lock {
            if (refs.isEmpty()) {
                return@Lock
            }
            ret = RefImpl(referent).also { refs.add(it) }
        }
        return ret
    }
}
