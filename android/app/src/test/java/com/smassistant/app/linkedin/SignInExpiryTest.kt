package com.smassistant.app.linkedin

import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInExpiryTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_800_000_000_000L // 15 Jan 2027, 08:00 UTC

    private fun session(expiresAt: Long) = LinkedInSession("Ada", expiresAt, "hash")

    @Test
    fun `no sign-in or no expiry means nothing to judge`() {
        assertNull(SignInExpiry.of(null, now))
        assertNull(SignInExpiry.of(session(0L), now))
    }

    @Test
    fun `more than a week left is fine and says nothing`() {
        val expiry = SignInExpiry.of(session(now + 30 * day), now)
        assertTrue(expiry is SignInExpiry.Fine)
        assertNull(expiry!!.message(ZoneOffset.UTC))
    }

    @Test
    fun `a week or less left warns with the days rounded up`() {
        assertEquals(SignInExpiry.Soon(now + 7 * day, 7), SignInExpiry.of(session(now + 7 * day), now))
        assertEquals(SignInExpiry.Soon(now + 2 * day + 1, 3), SignInExpiry.of(session(now + 2 * day + 1), now))
        val soon = SignInExpiry.of(session(now + 3 * day), now)!!
        assertEquals(
            "Your LinkedIn sign-in expires in 3 days (18 Jan 2027). Sign in again in LinkedIn API settings to keep posting.",
            soon.message(ZoneOffset.UTC),
        )
        assertTrue(SignInExpiry.of(session(now + 1000), now)!!.message(ZoneOffset.UTC)!!.contains("within a day"))
    }

    @Test
    fun `past the expiry is expired`() {
        val expiry = SignInExpiry.of(session(now), now)
        assertEquals(SignInExpiry.Expired(now), expiry)
        assertEquals(
            "Your LinkedIn sign-in expired on 15 Jan 2027. Sign in again in LinkedIn API settings to post.",
            expiry!!.message(ZoneOffset.UTC),
        )
    }
}
