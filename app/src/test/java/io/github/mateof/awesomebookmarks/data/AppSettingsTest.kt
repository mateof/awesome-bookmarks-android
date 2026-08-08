package io.github.mateof.awesomebookmarks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizeBaseUrlTest {

    @Test
    fun `adds http when the scheme is missing`() {
        assertEquals("http://192.168.1.50:3001", normalizeBaseUrl("192.168.1.50:3001"))
    }

    @Test
    fun `keeps an explicit https scheme`() {
        assertEquals("https://bookmarks.example.com", normalizeBaseUrl("https://bookmarks.example.com"))
    }

    @Test
    fun `strips trailing slashes and surrounding spaces`() {
        assertEquals("http://books.lan:3001", normalizeBaseUrl("  http://books.lan:3001/  "))
    }
}

class AppSettingsTest {

    @Test
    fun `is not configured without a primary url`() {
        assertFalse(AppSettings().isConfigured)
        assertTrue(AppSettings(primaryUrl = "http://books.lan:3001").isConfigured)
    }

    @Test
    fun `probes the last good url first so cold starts skip a dead candidate`() {
        val settings = AppSettings(
            primaryUrl = "http://books.lan:3001",
            fallbackUrl = "https://books.example.ts.net",
            lastGoodUrl = "https://books.example.ts.net",
        )
        assertEquals(
            listOf("https://books.example.ts.net", "http://books.lan:3001"),
            settings.candidateUrls,
        )
    }

    @Test
    fun `parses the always-tags list, ignoring blanks and spacing`() {
        assertEquals(
            listOf("mobile", "to read"),
            AppSettings(alwaysTags = " mobile , , to read ").alwaysTagList,
        )
        assertTrue(AppSettings().alwaysTagList.isEmpty())
    }
}
