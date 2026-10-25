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
import androidx.compose.runtime.rememberUpdatedState
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
 */
@Composable
fun LinkedInSignInDialog(url: String, onNavigate: (String) -> Boolean, onCancel: () -> Unit) {
    val navigate by rememberUpdatedState(onNavigate)
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
                AndroidView(
                    factory = { context -> signInWebView(context, url) { navigate(it) } },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("linkedInWebView"),
                )
            }
        }
    }
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
