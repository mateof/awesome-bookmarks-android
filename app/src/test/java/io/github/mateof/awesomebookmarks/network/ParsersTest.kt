// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payloads here are the real shape of `GET /api/v1/folders` and
 * `GET /api/v1/tags`: a bare array of camelCase objects. A change on either
 * side used to surface as "you have no folders", which reads like a server
 * problem rather than a parsing one, so it is worth pinning down.
 */
class ParsersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val foldersPayload = """
        [
          {"id":"11111111-1111-1111-1111-111111111111","parentId":null,"name":"Work",
           "description":null,"iconBlobPath":null,"imageBlobPath":null,"bgColor":null,
           "position":0,"tagIds":[],"createdAt":"2026-01-01","updatedAt":"2026-01-01"},
          {"id":"22222222-2222-2222-2222-222222222222",
           "parentId":"11111111-1111-1111-1111-111111111111","name":"Rust",
           "position":1,"tagIds":[]}
        ]
    """.trimIndent()

    @Test
    fun `reads the folder list the server actually sends`() {
        val folders = parseFolders(json.parseToJsonElement(foldersPayload))
        assertEquals(2, folders.size)
        assertEquals("Work", folders[0].name)
        assertNull(folders[0].parentId)
        assertEquals("11111111-1111-1111-1111-111111111111", folders[1].parentId)
    }

    @Test
    fun `an empty list is empty, not an error`() {
        assertTrue(parseFolders(json.parseToJsonElement("[]")).isEmpty())
    }

    @Test
    fun `anything that is not an array yields nothing rather than throwing`() {
        assertTrue(parseFolders(json.parseToJsonElement("""{"folders":[]}""")).isEmpty())
        assertTrue(parseFolders(null).isEmpty())
    }

    @Test
    fun `a row without an id is skipped instead of poisoning the list`() {
        val payload = """[{"parentId":null,"name":"Broken"},{"id":"abc","name":"Fine"}]"""
        val folders = parseFolders(json.parseToJsonElement(payload))
        assertEquals(listOf("Fine"), folders.map { it.name })
    }

    @Test
    fun `reads a single folder, which is what create answers with`() {
        val payload = """{"id":"abc","parentId":null,"name":"Reading"}"""
        val folder = parseFolder(json.parseToJsonElement(payload))
        assertEquals(Folder("abc", null, "Reading"), folder)
    }

    @Test
    fun `reads tags and drops nameless ones`() {
        val payload = """[{"id":"t1","name":"dev","color":"#3b82f6"},{"id":"t2","color":"#fff"}]"""
        val tags = parseTags(json.parseToJsonElement(payload))
        assertEquals(1, tags.size)
        assertEquals("dev", tags[0].name)
        assertEquals("#3b82f6", tags[0].color)
    }
}
