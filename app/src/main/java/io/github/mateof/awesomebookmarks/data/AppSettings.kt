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
) {
    val isConfigured: Boolean get() = primaryUrl.isNotBlank()

    /** Candidate base URLs in the order they should be probed. */
    val candidateUrls: List<String>
        get() = listOf(lastGoodUrl, primaryUrl, fallbackUrl)
            .filter { it.isNotBlank() }
            .distinct()

    val alwaysTagList: List<String>
        get() = alwaysTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
