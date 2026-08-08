package io.github.mateof.awesomebookmarks.util

import android.content.Context
import androidx.webkit.WebViewCompat

/**
 * Android System WebView is Chromium, so the SPA is compatible by construction,
 * but an outdated or disabled WebView package still renders a broken interface.
 * Checking the version up front turns "the app is blank" into an actionable
 * message.
 */
data class WebViewInfo(
    val packageName: String?,
    val versionName: String?,
) {
    val majorVersion: Int? = versionName
        ?.substringBefore('.')
        ?.toIntOrNull()

    val isMissing: Boolean get() = packageName == null

    val isOutdated: Boolean get() = majorVersion != null && majorVersion < MIN_RECOMMENDED_MAJOR

    companion object {
        /**
         * Chromium 108 (Dec 2022) is the floor the SPA's toolchain targets.
         * Below that the pages load but layout and clipboard behaviour
         * degrade in ways that look like app bugs.
         */
        const val MIN_RECOMMENDED_MAJOR = 108

        fun of(context: Context): WebViewInfo {
            val pkg = WebViewCompat.getCurrentWebViewPackage(context)
            return WebViewInfo(packageName = pkg?.packageName, versionName = pkg?.versionName)
        }
    }
}
