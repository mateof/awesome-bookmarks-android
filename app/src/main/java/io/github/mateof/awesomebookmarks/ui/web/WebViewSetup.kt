// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.ui.web

import android.annotation.SuppressLint
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.github.mateof.awesomebookmarks.BuildConfig
import io.github.mateof.awesomebookmarks.data.AppSettings

/**
 * Turns a stock WebView into something the AwesomeBookmarks SPA is happy in.
 *
 * The app marker is appended after the default string rather than replacing it,
 * so the server still sees a normal mobile browser (nothing in the SPA sniffs
 * the User-Agent, but a truncated one breaks feature detection in libraries)
 * while our requests stay identifiable in the access log.
 */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.applyBookmarksConfiguration(appSettings: AppSettings) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        @Suppress("DEPRECATION")
        databaseEnabled = true
        loadWithOverviewMode = true
        useWideViewPort = true
        builtInZoomControls = false
        displayZoomControls = false
        mediaPlaybackRequiresUserGesture = false
        allowFileAccess = false
        allowContentAccess = true
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        cacheMode = WebSettings.LOAD_DEFAULT
        textZoom = appSettings.textZoom
        mixedContentMode = if (appSettings.allowMixedContent) {
            WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        } else {
            WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        userAgentString = "$userAgentString AwesomeBookmarks/${BuildConfig.VERSION_NAME}"
    }

    // The SPA ships its own light and dark themes with a system option. Letting
    // the WebView darken on top of them produces washed out colours.
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
    }

    // Passkeys. A WebView refuses WebAuthn unless it is asked for explicitly,
    // which is why passwordless sign in silently does nothing in most wrapper
    // apps. FOR_APP scopes credentials to this app rather than sharing the
    // browser's, which is what we want for a single site client.
    //
    // The server side has its own requirements: WEBAUTHN_RP_ID and
    // WEBAUTHN_ORIGIN configured, HTTPS, and a real hostname, because WebAuthn
    // forbids IP addresses as relying party ids. Over http on a LAN address
    // there is nothing to enable.
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
        WebSettingsCompat.setWebAuthenticationSupport(
            settings,
            WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP,
        )
    }

    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER

    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@applyBookmarksConfiguration, true)
    }
}

