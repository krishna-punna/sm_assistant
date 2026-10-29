package com.smassistant.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.smassistant.app.linkedin.appSignInExpiry

/** The LinkedIn sign-in warning to show, re-read each time the screen comes back (e.g. after signing in again). */
@Composable
fun rememberSignInWarning(): String? {
    val appContext = LocalContext.current.applicationContext
    val expiry = remember { appSignInExpiry(appContext) }
    var warning by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        warning = expiry()?.message()
        onPauseOrDispose {}
    }
    return warning
}

/** Says the LinkedIn sign-in is about to run out (or has), with a shortcut to sign in again. */
@Composable
fun SignInWarningCard(message: String, onSignIn: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("signInWarning")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onSignIn) { Text("Sign in") }
        }
    }
}
