package org.slf4j.impl

import org.slf4j.IMarkerFactory
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.spi.MarkerFactoryBinder

/** NOP markers support. */
@Suppress("unused")
class StaticMarkerBinder private constructor(): MarkerFactoryBinder {
    companion object {
        @JvmStatic
        val singleton = StaticMarkerBinder()

        /** Stub for preventing proguard warnings, caused by SLF4J old API compatibility code. */
        @JvmField
        val SINGLETON: StaticMarkerBinder? = null
    }

    override fun getMarkerFactory() = markerFactory

    override fun getMarkerFactoryClassStr(): String = BasicMarkerFactory::class.java.name

    private val markerFactory: IMarkerFactory = BasicMarkerFactory()
}
