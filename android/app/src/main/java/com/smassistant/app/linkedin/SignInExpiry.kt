package com.smassistant.app.linkedin

import android.content.Context
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * How close the LinkedIn sign-in is to running out. LinkedIn member tokens last about 60 days and
 * the app can't refresh them, so scheduled posts would start failing without a warning.
 */
sealed interface SignInExpiry {
    val expiresAt: Long

    data class Fine(override val expiresAt: Long) : SignInExpiry
    data class Soon(override val expiresAt: Long, val daysLeft: Int) : SignInExpiry
    data class Expired(override val expiresAt: Long) : SignInExpiry

    /** What to tell the user, or null while there's nothing to say. */
    fun message(zone: ZoneId = ZoneId.systemDefault()): String? = when (this) {
        is Fine -> null
        is Soon -> "Your LinkedIn sign-in expires " +
            (if (daysLeft <= 1) "within a day" else "in $daysLeft days") +
            " (${formatDate(expiresAt, zone)}). Sign in again in LinkedIn API settings to keep posting."
        is Expired -> "Your LinkedIn sign-in expired on ${formatDate(expiresAt, zone)}. Sign in again in LinkedIn API settings to post."
    }

    companion object {
        /** Warn this many days before the sign-in runs out. */
        const val WARN_DAYS = 7

        /** Null when there's no sign-in to judge, e.g. a pasted token whose expiry isn't known. */
        fun of(session: LinkedInSession?, now: Long): SignInExpiry? {
            val at = session?.expiresAt?.takeIf { it > 0 } ?: return null
            val left = at - now
            return when {
                left <= 0 -> Expired(at)
                left <= TimeUnit.DAYS.toMillis(WARN_DAYS.toLong()) ->
                    Soon(at, ((left + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)).toInt())
                else -> Fine(at)
            }
        }

        private fun formatDate(epochMillis: Long, zone: ZoneId) =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    }
}

/** The saved sign-in, but only while the saved LinkedIn key is still the token it came with. */
fun activeSession(authStore: LinkedInAuthStore, key: String): LinkedInSession? {
    val session = authStore.session() ?: return null
    return session.takeIf { key.isNotBlank() && LinkedInOAuth.hash(key) == it.tokenHash }
}

/** The app's current LinkedIn sign-in expiry, read fresh on every call. */
fun appSignInExpiry(context: Context, now: () -> Long = System::currentTimeMillis): () -> SignInExpiry? {
    val authStore = LinkedInAuthStore(context)
    val settings = ApiSettingsStore(context)
    return {
        // Only read (and decrypt) the key when there's a sign-in to check it against.
        authStore.session()?.let { SignInExpiry.of(activeSession(authStore, settings.load(ApiService.LINKEDIN).key), now()) }
    }
}
