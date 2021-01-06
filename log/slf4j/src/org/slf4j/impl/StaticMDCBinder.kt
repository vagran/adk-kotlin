/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package org.slf4j.impl

import org.slf4j.helpers.NOPMDCAdapter
import org.slf4j.spi.MDCAdapter

/** NOP MDC support. */
@Suppress("unused")
class StaticMDCBinder private constructor() {
    companion object {
        @JvmStatic
        val singleton = StaticMDCBinder()

        /** Stub for preventing proguard warnings, caused by SLF4J old API compatibility code. */
        @JvmField
        val SINGLETON: StaticMDCBinder? = null
    }

    fun getMDCA(): MDCAdapter = NOPMDCAdapter()

    fun getMDCAdapterClassStr() = NOPMDCAdapter::class.java.name
}
