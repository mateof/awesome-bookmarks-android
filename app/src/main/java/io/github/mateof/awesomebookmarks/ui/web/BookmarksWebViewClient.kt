// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.ui.web

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.mateof.awesomebookmarks.util.isSameOrigin

/**
 * Keeps navigation inside the app when it belongs to our server, hands
 * everything else to the system, and turns "the SPA bounced me to its login
 * page" into a silent sign in instead of a form to fill in again.
 *
 * The web app is a client-side router, so a session expiring mid-use does not
 * reload the page: it pushes `/login` onto the history. That is why the check
 * lives in [doUpdateVisitedHistory], which fires for `pushState` too, and not
 * only in the page load callbacks.
 */
class BookmarksWebViewClient(
    private val baseUrl: () -> String,
    private val onSessionExpired: () -> Unit,
    /** Returns true when the app took the URL over and the WebView must not load it. */
    private val onExternalUrl: (Uri) -> Boolean,
    private val onStateChanged: (WebPageState) -> Unit,
) : WebViewClient() {

    private var lastRenewAt = 0L
    private var consecutiveRenewals = 0

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        val base = baseUrl()
        if (base.isNotEmpty() && isSameOrigin(url, base)) return false
        return onExternalUrl(url)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        onStateChanged(WebPageState.Loading)
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (!isLoginRoute(url)) {
            consecutiveRenewals = 0
            onStateChanged(WebPageState.Ready)
        } else {
            handleLoginRoute()
        }
    }

    /** Fires for `pushState` and `replaceState`, which is how the SPA navigates. */
    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        if (isLoginRoute(url)) handleLoginRoute()
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        Log.w(TAG, "Main frame error ${error.errorCode} for ${request.url}")
        onStateChanged(WebPageState.Failed(error.description?.toString().orEmpty()))
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (!request.isForMainFrame) return
        // 401 and 423 are expected while the session is being renewed.
        if (errorResponse.statusCode == 401 || errorResponse.statusCode == 423) return
        onStateChanged(WebPageState.Failed("HTTP ${errorResponse.statusCode}"))
    }

    private fun isLoginRoute(url: String): Boolean =
        Uri.parse(url).path.orEmpty().trimEnd('/') == LOGIN_PATH

    private fun handleLoginRoute() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRenewAt < RENEW_DEBOUNCE_MS) return
        lastRenewAt = now

        consecutiveRenewals++
        if (consecutiveRenewals > MAX_RENEWALS) {
            onStateChanged(WebPageState.SessionRejected)
            return
        }
        onSessionExpired()
    }

    private companion object {
        const val TAG = "BookmarksWebViewClient"
        const val LOGIN_PATH = "/login"
        const val RENEW_DEBOUNCE_MS = 2_000L

        /** Stops a loop when the stored password is no longer accepted. */
        const val MAX_RENEWALS = 3
    }
}

sealed interface WebPageState {
    data object Loading : WebPageState
    data object Ready : WebPageState
    data class Failed(val reason: String) : WebPageState

    /** The stored credentials no longer work: the user must re-enter them. */
    data object SessionRejected : WebPageState
}
