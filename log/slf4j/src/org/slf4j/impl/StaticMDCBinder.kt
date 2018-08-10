package org.slf4j.impl

import org.slf4j.helpers.NOPMDCAdapter
import org.slf4j.spi.MDCAdapter

/** NOP MDC support. */
class StaticMDCBinder private constructor() {
    companion object {
        @JvmStatic
        val singleton = StaticMDCBinder()
    }

    fun getMDCA(): MDCAdapter = NOPMDCAdapter()

    fun getMDCAdapterClassStr() = NOPMDCAdapter::class.java.name
}
