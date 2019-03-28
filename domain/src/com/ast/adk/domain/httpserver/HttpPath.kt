package com.ast.adk.domain.httpserver

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class HttpPath {
    val components: List<String>
    val hasRoot: Boolean

    val length get() = components.size

    /** @param path Should be raw path, usually returned by URI.rawPath */
    constructor(path: String)
    {
        var curIdx = 0
        var rootSeen = false
        val _components = ArrayList<String>()
        while (true) {
            val nextIdx = path.indexOf('/', curIdx)
            if (nextIdx == 0) {
                rootSeen = true
            } else if (nextIdx < 0) {
                break
            }
            if (nextIdx > curIdx) {
                _components.add(URLDecoder.decode(path.substring(curIdx, nextIdx),
                                                  StandardCharsets.UTF_8))
            }
            curIdx = nextIdx + 1
        }
        if (curIdx < path.length) {
            _components.add(URLDecoder.decode(path.substring(curIdx),
                                              StandardCharsets.UTF_8))
        }
        components = _components
        hasRoot = rootSeen
    }

    fun Append(path: HttpPath): HttpPath
    {
        val newComponents = ArrayList<String>(components)
        newComponents.addAll(path.components)
        return HttpPath(newComponents, hasRoot)
    }

    fun Append(component: String): HttpPath
    {
        val newComponents = ArrayList<String>(components)
        newComponents.add(component)
        return HttpPath(newComponents, hasRoot)
    }

    override fun toString(): String
    {
        val buf = StringBuilder()
        if (hasRoot) {
            buf.append('/')
        }
        var isFirst = true
        for (comp in components) {
            if (!isFirst) {
                buf.append('/')
            } else {
                isFirst = false
            }
            buf.append(comp)
        }
        return buf.toString()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    constructor(components: List<String>, hasRoot: Boolean)
    {
        this.components = components
        this.hasRoot = hasRoot
    }
}
