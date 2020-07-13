package io.github.vagran.adk.domain

interface Activity {
    fun Start()
    fun Stop()

    companion object {
        /**
         * Start many activities. If any one is failed to start by throwing an exception, all
         * previously started activities are stopped and the exception is rethrown.
         */
        fun StartMany(activities: Collection<Activity>)
        {
            var numStarted = 0
            for (activity in activities) {
                try {
                    activity.Start()
                    numStarted++
                } catch (e: Throwable) {
                    var numStopped = 0
                    for (startedActivity in activities) {
                        if (numStopped >= numStarted) {
                            break
                        }
                        activity.Stop()
                        numStopped++
                    }
                    throw e
                }
            }
        }
    }
}
