package com.smassistant.app.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Runs one scheduler in the background (SC-I4): the whole [AutoPostRun], then records the result,
 * moves to the next topic, tells the user (SC-7) and, for a scheduled run, plans the next one.
 * A run that failed on a network error or rate limit is retried up to [MAX_RETRIES] times first.
 */
class AutoPostWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        val store = SchedulerStore(applicationContext)
        val scheduler = store.get(id) ?: return Result.success()
        if (!manual && !scheduler.enabled) return Result.success()

        val outcome = appAutoPostRun(applicationContext)(scheduler)
        if (outcome.retry && runAttemptCount < MAX_RETRIES) return Result.retry()

        val saved = store.update(id) {
            it.advanced(outcome.title.takeIf { outcome.record.ok }).copy(lastRun = outcome.record)
        }
        RunNotifier(applicationContext).show(saved ?: scheduler, outcome.record)
        if (!manual) SchedulerEngine(applicationContext, store).planNext(id)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "scheduler_id"
        const val KEY_MANUAL = "manual"
        const val MAX_RETRIES = 2
    }
}
