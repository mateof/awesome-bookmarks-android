// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.util

import android.net.Uri

/**
 * True when [candidate] points at the same server as [baseUrl]. Anything else
 * is an outbound link and must not stay inside the app WebView.
 */
fun isSameOrigin(candidate: Uri, baseUrl: String): Boolean {
    val base = Uri.parse(baseUrl)
    return candidate.scheme.equals(base.scheme, ignoreCase = true) &&
        candidate.host.equals(base.host, ignoreCase = true) &&
        candidate.effectivePort() == base.effectivePort()
}

private fun Uri.effectivePort(): Int = when {
    port != -1 -> port
    scheme.equals("https", ignoreCase = true) -> 443
    else -> 80
}
