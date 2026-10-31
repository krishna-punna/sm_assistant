package com.smassistant.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * LinkedIn's sign-in page in an in-app browser (Q-03). LinkedIn only redirects to http(s) URLs,
 * so the app can't be opened by the redirect; instead every navigation goes to [onNavigate],
 * which returns true for the redirect URL so it is caught here and never loaded.
 *
 * Google refuses sign-in inside an app's browser, so "Continue with Google" can't work here
 * (improvement #11): a note says so, louder once the page goes to Google, with [onUseToken]
 * to close the browser and paste an access token instead.
 */
@Composable
fun LinkedInSignInDialog(url: String, onNavigate: (String) -> Boolean, onCancel: () -> Unit, onUseToken: () -> Unit = onCancel) {
    val navigate by rememberUpdatedState(onNavigate)
    var wentToGoogle by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Text("Sign in with LinkedIn", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                GoogleSignInNote(wentToGoogle, onUseToken)
                AndroidView(
                    factory = { context ->
                        signInWebView(context, url) {
                            if (isGoogleSignIn(it)) wentToGoogle = true
                            navigate(it)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("linkedInWebView"),
                )
            }
        }
    }
}

/**
 * Above the sign-in page: LinkedIn accounts made with Google have no password to type here.
 * [blocked] is true once the page went to Google, which will refuse it, so the note turns red.
 */
@Composable
fun GoogleSignInNote(blocked: Boolean, onUseToken: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).testTag("googleSignInNote")) {
        Text(
            if (blocked) {
                "Google doesn't allow signing in inside apps, so Continue with Google won't work here."
            } else {
                "Use your LinkedIn email and password. Continue with Google doesn't work inside apps."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Joined LinkedIn with Google? Set a LinkedIn password first (Settings, Sign in & security, Change password, " +
                "or Forgot password on the login page), or paste an access token instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onUseToken, modifier = Modifier.testTag("useTokenInstead")) { Text("Paste a token instead") }
    }
}

/** True for Google's sign-in pages, which LinkedIn's "Continue with Google" opens. */
fun isGoogleSignIn(url: String): Boolean {
    val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return false
    return host == "accounts.google.com" || host.endsWith(".accounts.google.com")
}

/** Forgets the LinkedIn login kept by the sign-in browser, so the next sign-in can pick another account. */
fun clearLinkedInBrowserLogin() {
    CookieManager.getInstance().removeAllCookies(null)
}

// LinkedIn's login page needs JavaScript; the browser only ever loads linkedin.com pages.
@SuppressLint("SetJavaScriptEnabled")
private fun signInWebView(context: android.content.Context, url: String, onNavigate: (String) -> Boolean) = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.isForMainFrame && onNavigate(request.url.toString())

        // Some redirects (after a form POST) skip shouldOverrideUrlLoading; catch them as the page starts.
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (onNavigate(url)) view.stopLoading() else super.onPageStarted(view, url, favicon)
        }
    }
    loadUrl(url)
}
