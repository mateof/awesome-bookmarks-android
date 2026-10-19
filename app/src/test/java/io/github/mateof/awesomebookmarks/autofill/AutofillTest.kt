// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.autofill

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val payload = """
        {
          "variables": { "dni": "12345678Z", "nombre": "Hedy \"la\" Lamarr" },
          "automations": [
            {
              "bookmarkId": "11111111-1111-1111-1111-111111111111",
              "url": "https://sede.example.es/tramite/alta",
              "trigger": "open",
              "timeoutMs": 8000,
              "steps": [{ "action": "fill", "selector": "#nif", "value": "{{dni}}" }],
              "script": ""
            },
            {
              "bookmarkId": "22222222-2222-2222-2222-222222222222",
              "url": "https://banco.example.com/login",
              "trigger": "always",
              "timeoutMs": 10000,
              "steps": [],
              "script": "document.title = ab.vars.nombre;"
            }
          ]
        }
    """.trimIndent()

    private fun runtime() = parseAutofillRuntime(json.parseToJsonElement(payload))!!

    @Test
    fun `reads the runtime the server sends for the extension`() {
        val rt = runtime()
        assertEquals("12345678Z", rt.variables["dni"])
        assertEquals(2, rt.automations.size)
        assertEquals(8000, rt.automations[0].timeoutMs)
        assertEquals(1, rt.automations[0].steps.size)
    }

    @Test
    fun `an open automation matches its own address however the link spells it`() {
        val rt = runtime()
        assertNotNull(automationFor(rt, "https://SEDE.example.es:443/tramite/alta#paso-1"))
        assertNull(automationFor(rt, "https://sede.example.es/tramite/alta/otra"))
    }

    @Test
    fun `an always automation covers the pages under its path and nothing beside it`() {
        val rt = runtime()
        assertEquals(
            "22222222-2222-2222-2222-222222222222",
            automationFor(rt, "https://banco.example.com/login/paso2?x=1")?.bookmarkId,
        )
        assertNull(automationFor(rt, "https://banco.example.com/loginx"))
        assertNull(automationFor(rt, "http://banco.example.com/login"))
    }

    @Test
    fun `a different port is a different origin, so the DNI never reaches it`() {
        assertEquals("https://a.example:8443", originOf("https://a.example:8443/x"))
        assertFalse(originOf("https://a.example:8443/x") == originOf("https://a.example/x"))
        assertFalse(matchesScope("https://a.example/", "https://a.example:8443/"))
    }

    @Test
    fun `non web links never get an automation`() {
        assertNull(canonicalUrl("javascript:alert(1)"))
        assertNull(automationFor(runtime(), "intent://scan/#Intent;scheme=zxing;end"))
    }

    @Test
    fun `values are embedded as JSON, so quotes in them cannot break out of the call`() {
        val rt = runtime()
        val js = stepsInvocation("RUNNER", rt.automations[0], rt.variables)
        assertTrue(js.contains("RUNNER"))
        assertTrue(js.contains("\"nombre\":\"Hedy \\\"la\\\" Lamarr\""))
        assertTrue(js.contains("$AUTOFILL_BRIDGE.postMessage"))
        val script = scriptInvocation(rt.automations[1].script, rt.variables)
        assertTrue(script.startsWith("(async (ab) => {\ndocument.title = ab.vars.nombre;\n})("))
    }

    @Test
    fun `reads the report the page posts back`() {
        val report = parseAutofillReport("""{"ok":false,"done":1,"total":3,"error":"No element matches #x"}""") {
            json.parseToJsonElement(it)
        }!!
        assertFalse(report.ok)
        assertEquals(1, report.done)
        assertEquals("No element matches #x", report.error)
    }

    @Test
    fun `a malformed runtime is ignored instead of crashing the app`() {
        assertNull(parseAutofillRuntime(JsonArray(emptyList())))
        val partial = parseAutofillRuntime(json.parseToJsonElement("""{"automations":[{"url":"https://x"}]}"""))!!
        assertTrue(partial.automations.isEmpty())
    }
}
