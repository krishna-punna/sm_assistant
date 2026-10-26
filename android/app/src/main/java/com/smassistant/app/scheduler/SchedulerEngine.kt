package com.smassistant.app.scheduler

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Plans scheduler runs with WorkManager (SC-NFR1): one unique one-time work per scheduler, due at
 * its next run, re-planned after each run. One-time work rather than periodic work, because
 * WorkManager's periodic work can't say "Tuesdays and Thursdays at 09:00" and drifts with Doze.
 */
class SchedulerEngine(
    context: Context,
    private val store: SchedulerStore = SchedulerStore(context),
    private val workManager: WorkManager = WorkManager.getInstance(context),
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /**
     * Saves [scheduler] and plans its next run from now, replacing any planned one; a scheduler
     * that is off or can't run (no topics) gets none. Returns what was saved.
     */
    fun save(scheduler: AutoPostScheduler): AutoPostScheduler {
        val planned = planned(scheduler, now(), zone())
        store.save(planned)
        enqueue(planned, ExistingWorkPolicy.REPLACE)
        return planned
    }

    fun setEnabled(id: String, enabled: Boolean) {
        store.get(id)?.let { save(it.copy(enabled = enabled)) }
    }

    fun delete(id: String) {
        workManager.cancelUniqueWork(workName(id))
        workManager.cancelUniqueWork(runNowName(id))
        store.delete(id)
    }

    /** Run now (SC-10): runs [id] once, as soon as there's a network, without moving its next planned run. */
    fun runNow(id: String) {
        val request = OneTimeWorkRequestBuilder<AutoPostWorker>()
            .setInputData(workDataOf(AutoPostWorker.KEY_ID to id, AutoPostWorker.KEY_MANUAL to true))
            .setConstraints(NETWORK)
            .build()
        workManager.enqueueUniqueWork(runNowName(id), ExistingWorkPolicy.KEEP, request)
    }

    /**
     * After a scheduled run of [id]: plans the next one from now (SC-NFR2, so runs missed while
     * the phone was off aren't caught up one by one). Called from inside that run's worker, so the
     * new work is appended to the running one rather than replacing (and cancelling) it.
     */
    fun planNext(id: String) {
        val updated = store.update(id) { planned(it, now(), zone()) } ?: return
        enqueue(updated, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    /**
     * At app start: makes sure every scheduler that's on has its run planned. WorkManager keeps
     * work across restarts, so this normally finds it there and changes nothing.
     */
    fun ensurePlanned() {
        store.all().forEach { scheduler ->
            if (scheduler.nextRunAt == null && scheduler.enabled && scheduler.problem() == null) {
                save(scheduler)
            } else {
                enqueue(scheduler, ExistingWorkPolicy.KEEP)
            }
        }
    }

    private fun enqueue(scheduler: AutoPostScheduler, policy: ExistingWorkPolicy) {
        val at = scheduler.nextRunAt
        if (at == null) {
            workManager.cancelUniqueWork(workName(scheduler.id))
            return
        }
        val request = OneTimeWorkRequestBuilder<AutoPostWorker>()
            .setInputData(workDataOf(AutoPostWorker.KEY_ID to scheduler.id))
            .setInitialDelay((at - now()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(NETWORK)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_AFTER_MINUTES, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(workName(scheduler.id), policy, request)
    }

    companion object {
        const val RETRY_AFTER_MINUTES = 5L

        private val NETWORK = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun workName(id: String) = "scheduled-post-$id"
        fun runNowName(id: String) = "scheduled-post-now-$id"

        /** [scheduler] with its next run after [now], or none when it is off or can't run. */
        fun planned(scheduler: AutoPostScheduler, now: Long, zone: ZoneId): AutoPostScheduler {
            if (!scheduler.enabled || scheduler.problem() != null) return scheduler.copy(nextRunAt = null)
            val next = scheduler.schedule.nextRun(Instant.ofEpochMilli(now).atZone(zone))
            return scheduler.copy(nextRunAt = next.toInstant().toEpochMilli())
        }
    }
}
