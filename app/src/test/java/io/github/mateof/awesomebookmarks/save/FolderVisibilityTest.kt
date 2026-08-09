// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.save

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picker decides what to draw in one pre-order pass rather than by walking
 * ancestors per row. That is cheap but easy to get subtly wrong, and getting it
 * wrong means folders that quietly do not appear, which is exactly the class of
 * bug this screen already had once.
 *
 * Tree under test:
 *
 * ```
 * Work            depth 0
 *   Dev           depth 1
 *     Rust        depth 2
 *   Admin         depth 1
 * Personal        depth 0
 * ```
 */
class FolderVisibilityTest {

    private val work = FolderNode("work", "Work", 0, null, hasChildren = true, path = "")
    private val dev = FolderNode("dev", "Dev", 1, "work", hasChildren = true, path = "Work")
    private val rust = FolderNode("rust", "Rust", 2, "dev", hasChildren = false, path = "Work / Dev")
    private val admin = FolderNode("admin", "Admin", 1, "work", hasChildren = false, path = "Work")
    private val personal = FolderNode("personal", "Personal", 0, null, hasChildren = false, path = "")

    private val tree = listOf(work, dev, rust, admin, personal)

    private fun state(expanded: Set<String> = emptySet(), query: String = "") =
        SaveUiState(folders = tree, expandedFolders = expanded, folderQuery = query)

    @Test
    fun `everything starts collapsed, so only the roots show`() {
        assertEquals(listOf("Work", "Personal"), state().visibleFolders.map { it.name })
    }

    @Test
    fun `expanding a folder reveals its children but not its grandchildren`() {
        val visible = state(expanded = setOf("work")).visibleFolders.map { it.name }
        assertEquals(listOf("Work", "Dev", "Admin", "Personal"), visible)
    }

    @Test
    fun `expanding down the branch reveals the whole path`() {
        val visible = state(expanded = setOf("work", "dev")).visibleFolders.map { it.name }
        assertEquals(listOf("Work", "Dev", "Rust", "Admin", "Personal"), visible)
    }

    @Test
    fun `an expanded folder inside a collapsed parent stays hidden`() {
        // Dev is open, but Work is not, so nothing below Work may appear.
        val visible = state(expanded = setOf("dev")).visibleFolders.map { it.name }
        assertEquals(listOf("Work", "Personal"), visible)
    }

    @Test
    fun `searching ignores the tree and lists matches flat`() {
        val visible = state(query = "rust").visibleFolders
        assertEquals(listOf("Rust"), visible.map { it.name })
        // The path is what tells two folders with the same name apart.
        assertEquals("Work / Dev", visible.single().path)
    }

    @Test
    fun `searching matches the path too, so a parent name finds its children`() {
        val visible = state(query = "dev").visibleFolders.map { it.name }
        assertEquals(listOf("Dev", "Rust"), visible)
    }

    @Test
    fun `searching is case insensitive and can match nothing`() {
        assertEquals(listOf("Personal"), state(query = "PERSON").visibleFolders.map { it.name })
        assertTrue(state(query = "zzz").visibleFolders.isEmpty())
    }
}
