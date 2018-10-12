package com.ast.adk.domain.httpserver

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** @param path Should be raw path, usually returned by URI.rawPath */
class HttpPath(path: String) {
    val components: List<String> get() = _components
    val hasRoot: Boolean

    private val _components = ArrayList<String>()

    init {
        var curIdx = 0
        var rootSeen = false
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
        hasRoot = rootSeen
    }
}
