package com.smassistant.app.scheduler

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.smassistant.app.linkedin.SignInExpiry
import com.smassistant.app.linkedin.appSignInExpiry

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

        val notifier = RunNotifier(applicationContext)
        runInForeground(notifier, scheduler)
        val outcome = appAutoPostRun(applicationContext)(scheduler)
        if (outcome.retry && runAttemptCount < MAX_RETRIES) return Result.retry()

        val saved = store.update(id) {
            it.advanced(outcome.title.takeIf { outcome.record.ok }).copy(lastRun = outcome.record)
        }
        notifier.show(saved ?: scheduler, outcome.record)
        // An expired sign-in is already the run's failure message; warn ahead of time too.
        val expiry = appSignInExpiry(applicationContext)()
        if (expiry is SignInExpiry.Soon) expiry.message()?.let(notifier::showSignInWarning)
        if (!manual) SchedulerEngine(applicationContext, store).planNext(id)
        return Result.success()
    }

    /**
     * Image and video runs can take several minutes, close to the 10-minute limit a background job
     * gets, so they ask to run as a foreground service, which has no such limit. Android 12+ refuses
     * that while the app is in the background unless it's exempt; the run then carries on as
     * ordinary background work (and WorkManager runs it again if the system stops it).
     */
    private suspend fun runInForeground(notifier: RunNotifier, scheduler: AutoPostScheduler) {
        if (scheduler.postType.promptType.media == null) return
        val notification = notifier.progress(scheduler)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_ID, notification)
        }
        try {
            setForeground(info)
        } catch (e: IllegalStateException) {
            // Not allowed from the background right now (ForegroundServiceStartNotAllowedException).
        }
    }

    companion object {
        private const val PROGRESS_ID = 0x50_52
        const val KEY_ID = "scheduler_id"
        const val KEY_MANUAL = "manual"
        const val MAX_RETRIES = 2
    }
}
