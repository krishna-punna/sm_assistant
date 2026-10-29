package com.smassistant.app.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.smassistant.app.MainActivity
import com.smassistant.app.R

/**
 * The notification after each scheduled run (SC-7): "Posted …" opening the post, or what went
 * wrong opening the app. Shown only when the user allowed notifications (Android 13+ asks).
 */
class RunNotifier(private val context: Context) {
    fun show(scheduler: AutoPostScheduler, record: RunRecord) {
        if (!allowed()) return
        createChannel()
        val open = record.postUrl?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
            ?: Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(
            context,
            scheduler.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(if (record.ok) "Posted to LinkedIn: ${scheduler.displayName}" else "Scheduled post failed: ${scheduler.displayName}")
            .setContentText(record.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(record.message))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        notify(scheduler.id.hashCode(), notification)
    }

    /** One notification (replaced, not stacked) saying the LinkedIn sign-in runs out soon. */
    fun showSignInWarning(message: String) {
        if (!allowed()) return
        createChannel()
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(context, SIGN_IN_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("LinkedIn sign-in expiring")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        notify(SIGN_IN_ID, notification)
    }

    private fun allowed() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notify(id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission taken away between the check and the call.
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Scheduled posts", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "How each scheduled LinkedIn post went" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "scheduled_posts"
        const val SIGN_IN_ID = 0x51_6E
    }
}
