package me.yummydroid.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class VideoSubscriptionMutationRunner(
    private val scope: CoroutineScope,
) {
    private val jobs = mutableSetOf<Job>()

    fun clear() {
        jobs.toList().forEach(Job::cancel)
        jobs.clear()
    }

    fun launch(block: suspend () -> Unit) {
        val job = scope.launch { block() }
        jobs += job
        job.invokeOnCompletion { jobs -= job }
    }
}
