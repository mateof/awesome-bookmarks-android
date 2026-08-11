// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.data

/**
 * Everything the user can configure, in one immutable snapshot.
 *
 * The account credentials are deliberately absent: they live encrypted in
 * [SecretStore] and never travel through DataStore in plain text.
 */
data class AppSettings(
    /** Preferred base URL, e.g. `http://192.168.1.50:3001`. Never ends with `/`. */
    val primaryUrl: String = "",
    /** Optional second base URL, tried when [primaryUrl] does not answer. */
    val fallbackUrl: String = "",
    /** Base URL that answered last time, so the next cold start goes straight there. */
    val lastGoodUrl: String = "",

    val appLockEnabled: Boolean = true,
    /**
     * How long the app may sit in the background before the lock re-arms.
     * Zero locks on every return; [AppSettings.GRACE_ONLY_ON_START] locks only
     * when the process starts, which is the loosest setting on offer.
     */
    val appLockGraceMinutes: Int = 30,
    val keepScreenOn: Boolean = false,
    val showQuickButton: Boolean = true,
    /**
     * Where the floating button sits, as a fraction of the free space on each
     * axis. Fractions rather than pixels so it lands in the same relative spot
     * after a rotation or on a different screen. 1,1 is the bottom end corner.
     */
    val quickButtonX: Float = 1f,
    val quickButtonY: Float = 1f,
    val openExternalLinksInBrowser: Boolean = true,
    val allowMixedContent: Boolean = false,
    val textZoom: Int = 100,

    val updateChecksEnabled: Boolean = true,
    val lastUpdateCheckAt: Long = 0L,
    val skippedUpdateVersion: String = "",

    /**
     * Folder the share sheet preselects. Empty means the root. Updated after
     * every save when [rememberLastFolder] is on, because in practice you file
     * a run of links into the same place.
     */
    val defaultFolderId: String = "",
    val defaultFolderName: String = "",
    val rememberLastFolder: Boolean = true,

    /** Tags added to every saved link without typing them, comma separated. */
    val alwaysTags: String = "",

    /** Save immediately on share, with no sheet, using the remembered folder. */
    val oneTapSave: Boolean = false,

    /**
     * Whether an API token is stored. The token itself lives in [SecretStore];
     * this mirror exists so the UI can react to it through the same flow as
     * everything else.
     */
    val apiTokenConfigured: Boolean = false,

    /** Last version reported by the server, shown in Settings. */
    val serverVersion: String = "",
) {
    val isConfigured: Boolean get() = primaryUrl.isNotBlank()

    /** Grace period in milliseconds, or null when the lock should never re-arm. */
    val appLockGraceMillis: Long?
        get() = if (appLockGraceMinutes == GRACE_ONLY_ON_START) {
            null
        } else {
            appLockGraceMinutes.coerceAtLeast(0) * 60_000L
        }

    companion object {
        const val GRACE_ONLY_ON_START = -1
        val GRACE_CHOICES = listOf(0, 1, 5, 15, 30, 60, GRACE_ONLY_ON_START)
    }

    /** Candidate base URLs in the order they should be probed. */
    val candidateUrls: List<String>
        get() = listOf(lastGoodUrl, primaryUrl, fallbackUrl)
            .filter { it.isNotBlank() }
            .distinct()

    val alwaysTagList: List<String>
        get() = alwaysTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
