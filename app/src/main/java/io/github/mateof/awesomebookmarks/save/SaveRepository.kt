// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.save

import android.util.Patterns
import io.github.mateof.awesomebookmarks.data.SettingsRepository
import io.github.mateof.awesomebookmarks.network.ApiCall
import io.github.mateof.awesomebookmarks.network.BookmarksApi
import io.github.mateof.awesomebookmarks.network.Folder
import io.github.mateof.awesomebookmarks.network.SessionManager
import io.github.mateof.awesomebookmarks.network.Tag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the share sheet needs. Goes through
 * [SessionManager.withSession] so a save works even when the app has not been
 * opened for days and the server has dropped its decryption key.
 */
@Singleton
class SaveRepository @Inject constructor(
    private val api: BookmarksApi,
    private val sessionManager: SessionManager,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun folders(): Result<List<FolderNode>> = runCatching {
        val flat = sessionManager.withSession { baseUrl, token ->
            ApiCall(httpCode = 200, value = api.folders(baseUrl, token))
        }
        buildTree(flat)
    }

    /** Creates a folder, at the root when [parentId] is null. */
    suspend fun createFolder(name: String, parentId: String?): Result<Folder> = runCatching {
        require(name.isNotBlank()) { "A folder needs a name" }
        sessionManager.withSession { baseUrl, token ->
            ApiCall(httpCode = 200, value = api.createFolder(baseUrl, name.trim(), parentId, token))
        }
    }

    suspend fun tags(): Result<List<Tag>> = runCatching {
        sessionManager.withSession { baseUrl, token ->
            ApiCall(httpCode = 200, value = api.tags(baseUrl, token))
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun save(
        url: String,
        title: String?,
        folderId: String?,
        folderName: String,
        tags: List<String>,
    ): Result<Unit> = runCatching {
        require(isProbablyUrl(url)) { "Not a URL: $url" }

        val settings = settingsRepository.current()
        val allTags = (tags + settings.alwaysTagList)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

        sessionManager.withSession { baseUrl, token ->
            val response = api.quickAdd(baseUrl, url, title, folderId, allTags, token)
            ApiCall(
                httpCode = response.httpCode,
                value = if (response.isSuccess) Unit else null,
                errorMessage = response.errorMessage(),
            )
        }

        if (settings.rememberLastFolder) {
            settingsRepository.setDefaultFolder(folderId.orEmpty(), folderName)
        }
    }

    /**
     * The API returns folders flat with a `parentId`; the picker wants them in
     * pre-order with a depth, so the tree is built here once.
     *
     * Pre-order matters: it lets the picker decide what is visible in a single
     * pass, hiding everything deeper than a collapsed row until the depth drops
     * back, without walking ancestor chains per row.
     */
    private fun buildTree(flat: List<Folder>): List<FolderNode> {
        val childrenOf = flat.groupBy { it.parentId }
        val result = mutableListOf<FolderNode>()

        fun walk(parentId: String?, depth: Int, path: List<String>) {
            childrenOf[parentId]
                ?.sortedBy { it.name.lowercase() }
                ?.forEach { folder ->
                    result += FolderNode(
                        id = folder.id,
                        name = folder.name,
                        depth = depth,
                        parentId = folder.parentId,
                        hasChildren = !childrenOf[folder.id].isNullOrEmpty(),
                        path = path.joinToString(" / "),
                    )
                    // Depth is capped only by the data; a cycle would loop, but
                    // the server rejects moves that would create one.
                    walk(folder.id, depth + 1, path + folder.name)
                }
        }

        walk(null, 0, emptyList())
        return result
    }

    private fun isProbablyUrl(candidate: String): Boolean =
        Patterns.WEB_URL.matcher(candidate.trim()).matches()
}

data class FolderNode(
    val id: String,
    val name: String,
    val depth: Int,
    val parentId: String?,
    val hasChildren: Boolean,
    /** Ancestors joined for display, empty at the root. */
    val path: String,
)

/**
 * Browsers share a page as "Title https://url", or sometimes just the URL.
 * Pulling the first URL out of whatever arrived is what makes the share sheet
 * work from every app instead of only from Chrome.
 */
fun extractUrl(shared: String?): String? {
    if (shared.isNullOrBlank()) return null
    val matcher = Patterns.WEB_URL.matcher(shared)
    return if (matcher.find()) shared.substring(matcher.start(), matcher.end()) else null
}

/** Whatever is left once the URL is removed, which is usually the page title. */
fun extractTitle(shared: String?, url: String?): String {
    if (shared.isNullOrBlank()) return ""
    if (url == null) return shared.trim()
    return shared.replace(url, "").trim().trim('-', '|', '–').trim()
}
