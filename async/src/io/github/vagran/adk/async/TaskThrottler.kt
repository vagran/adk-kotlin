package io.github.vagran.adk.async

import java.util.concurrent.atomic.AtomicInteger

/** Can be called in arbitrary threads in parallel. May be called multiple times after null is
 * returned, null should be returned all times after the first time it is returned.
 * @return Next task for execution. Should already be submitted for execution. Null if no more
 *      tasks.
 */
typealias TaskThrottlerFabric = () -> Deferred<*>?

/**
 * Runs multiple tasks in parallel, limiting maximal number of concurrent tasks.
 * @param parallelHint True to indicate that tasks are running on different threads, false for
 *      single thread. This allows additional optimizations.
 */
class TaskThrottler(private val maxParallel: Int,
                    parallelHint: Boolean = true,
                    private val fabric: TaskThrottlerFabric) {

    /** Run tasks. Should be called once.
     *
     * @return Task which is completed when all tasks are completed. If some task fails the
     * concurrently running tasks are completed, and then the result fails.
     */
    fun Run(): Deferred<Void?>
    {
        ScheduleNext()
        return result
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val result: Deferred<Void?> = Deferred.Create()
    /** Current error if any. */
    @Volatile private var error: Throwable? = null
    /** Number of tasks to needed to be produced. Negative number to initiate termination,
     * number of tasks still running with negative sign and minus one.
     */
    private val tasksWanted = AtomicInteger(maxParallel)
    /** Maximal recursion depth for scheduling. */
    private val maxScheduleDepth = if (parallelHint) maxParallel + 1 else 1
    /** Number of pending schedule rounds, used to prevent deep recursion. */
    private val schedulePending = AtomicInteger(0)

    init {
        if (maxParallel < 1) {
            throw IllegalArgumentException("maxParallel < 1: $maxParallel")
        }
    }

    private fun ScheduleNext()
    {
        if (schedulePending.get() >= maxScheduleDepth) {
            return
        }
        while (GetTaskQuota()) {
            var error: Throwable? = null
            val nextTask =
                try {
                    fabric()
                } catch (e: Throwable) {
                    error = e
                    null
                }
            if (nextTask == null) {
                OnTaskComplete(true, error)
                continue
            }
            schedulePending.incrementAndGet()
            nextTask.Subscribe {_, e -> OnTaskComplete(false, e)}
            schedulePending.decrementAndGet()
        }
    }

    /** Atomically check if new task needed to be spawn (quota is acquired in such case).
     * @return True to spawn new task.
     */
    private fun GetTaskQuota(): Boolean
    {
        var isWanted: Boolean
        var n = tasksWanted.get()
        while (true) {
            var newValue: Int
            if (n > 0) {
                newValue = n - 1
                isWanted = true
            } else {
                /* Either no more tasks needed or already terminating(-ed). */
                return false
            }

            val expected = n
            n = tasksWanted.compareAndExchange(expected, newValue)
            if (n == expected) {
                break
            }
        }
        return isWanted
    }

    /** Called when task is complete. Returns quota held. Also detects if scheduler is terminated
     * and calls appropriate handler.
     * @param terminate Signals that termination is triggered (e.g. by returning null by fabric).
     *      Non-null error always triggers termination so this argument is ignored in such case.
     * @param error Completion error if any.
     */
    private fun OnTaskComplete(terminate: Boolean, error: Throwable?)
    {
        if (error != null) {
            synchronized(this) {
                if (this.error == null) {
                    this.error = error
                } else {
                    this.error!!.addSuppressed(error)
                }
            }
        }

        var isTerminated: Boolean
        var wantNext: Boolean
        var n = tasksWanted.get()
        while (true) {
            val newValue: Int
            wantNext = false
            if (n >= 0) {
                newValue =
                    if (terminate || error != null) {
                        /* The calling task is terminated so no minus one. */
                        n - maxParallel
                    } else {
                        wantNext = true
                        n + 1
                    }
            } else {
                newValue = n + 1
            }

            isTerminated = newValue == -1

            val expected = n
            n = tasksWanted.compareAndExchange(expected, newValue)
            if (n == expected) {
                break
            }
        }

        if (wantNext) {
            ScheduleNext()
        }

        if (isTerminated) {
            /* Last task completed, finish termination. */
            OnTerminated()
        }
    }

    private fun OnTerminated()
    {
        /* Error can be accessed without locking since the synchronization code ensures this method
         * is called when all tasks are completed and nobody can change the error.
         */
        if (error != null) {
            result.SetError(error!!)
        } else {
            result.SetResult(null)
        }
    }
}
