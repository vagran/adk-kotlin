package com.ast.adk.async

import kotlin.math.min

/** Can be called in arbitrary thread.
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
                    private val fabric: TaskThrottlerFabric,
                    parallelHint: Boolean = true) {

    /** Run tasks. Should be called once.
     *
     * @return Task which is completed when all tasks are completed. If some task fails the
     * concurrently running tasks are completed, and then the result fails.
     */
    fun Run(): Deferred<Void?>
    {
        ScheduleNextTasks()
        return result
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val result: Deferred<Void?> = Deferred.Create()
    private var resultSet = false
    /** Current error if any. */
    private var error: Throwable? = null
    /** Current number of of parallel running tasks.  */
    private var curParallel = 0
    /** Fabric signalled about no more tasks left. */
    private var tasksExhausted = false
    /** Flag for preventing deep recursion when tasks are completed synchronously.  */
    private var schedulePending = 0
    /** Maximal recursion depth for scheduling. */
    private val maxScheduleDepth = if (parallelHint) min(maxParallel, 8) else 1

    init {
        if (maxParallel < 1) {
            throw IllegalArgumentException("maxParallel < 1: $maxParallel")
        }
    }

    private fun ScheduleNextTasks()
    {
        var isFirst = true
        while (true) {
            var nextTask: Deferred<*>? = null
            synchronized(this) {
                if (isFirst) {
                    if (schedulePending >= maxScheduleDepth) {
                        return
                    }
                    isFirst = false
                } else {
                    schedulePending--
                }

                if (curParallel == maxParallel) {
                    return
                }
                if (error == null && !tasksExhausted) {
                    try {
                        nextTask = fabric()
                        if (nextTask == null) {
                            tasksExhausted = true
                        } else {
                            schedulePending++
                            curParallel++
                        }
                    } catch (error: Exception) {
                        if (this.error == null) {
                            this.error = error
                        }
                    }
                }

                if (nextTask == null && curParallel == 0 && !resultSet) {
                    resultSet = true
                    if (error != null) {
                        result.SetError(error!!)
                    } else {
                        result.SetResult(null)
                    }
                    return
                }
            }
            if (nextTask != null) {
                nextTask!!.Subscribe { _, error -> OnTaskDone(error) }
            } else {
                return
            }
        }
    }

    private fun OnTaskDone(error: Throwable?) {
        synchronized(this) {
            curParallel--
            if (error != null) {
                if (this.error == null) {
                    this.error = error
                }
            }
        }
        ScheduleNextTasks()
    }
}
