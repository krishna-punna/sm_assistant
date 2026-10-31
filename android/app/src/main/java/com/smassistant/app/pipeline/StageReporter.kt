package com.smassistant.app.pipeline

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException

/**
 * Hears the stages of the generation it runs with, through the coroutine context, so deep generation
 * code can mark its steps without a callback in every signature (docs/design/pipeline-status.md).
 */
class StageReporter(val report: (StageId, StageStatus, String?) -> Unit) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<StageReporter>

    /** A reporter that keeps [initial] up to date and hands every change to [onChange]. */
    class Tracking(initial: Pipeline, private val onChange: (Pipeline) -> Unit) {
        @Volatile var pipeline: Pipeline = initial
            private set

        val reporter = StageReporter { id, status, note -> update { it.set(id, status, note) } }

        @Synchronized fun update(change: (Pipeline) -> Pipeline) {
            val next = change(pipeline)
            if (next != pipeline) {
                pipeline = next
                onChange(next)
            }
        }
    }
}

/** Reports [status] for stage [id] to the coroutine's [StageReporter]; does nothing without one. */
suspend fun reportStage(id: StageId, status: StageStatus, note: String? = null) {
    coroutineContext[StageReporter]?.report?.invoke(id, status, note)
}

/**
 * Runs [block] as stage [id]: running before, done after. When [failed] gives an error for the result
 * (generation code returns its failures rather than throwing them), or the block throws, the stage fails.
 * Cancellation leaves it to the caller.
 */
suspend fun <T> stage(id: StageId, failed: (T) -> String? = { null }, block: suspend () -> T): T {
    reportStage(id, StageStatus.RUNNING)
    val result = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        reportStage(id, StageStatus.FAILED, e.message)
        throw e
    }
    val error = failed(result)
    reportStage(id, if (error == null) StageStatus.DONE else StageStatus.FAILED, error)
    return result
}
