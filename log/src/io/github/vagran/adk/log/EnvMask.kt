package io.github.vagran.adk.log

/** Mask defines which kind of environment parameters should be gathered when log message is
 * constructed.
 */
class EnvMask {
    enum class Resource {
        THREAD_NAME;

        val bit = 1 shl ordinal
    }

    var mask: Int = 0

    fun Set(resource: Resource)
    {
        mask = mask or resource.bit
    }

    fun IsSet(resource: Resource) = (mask and resource.bit) != 0

    fun Merge(other: EnvMask)
    {
        mask = mask or other.mask
    }
}
