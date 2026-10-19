// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.autofill

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI

const val AUTOFILL_BRIDGE = "__abAutofill"

data class RunnableAutomation(
    val bookmarkId: String,
    val url: String,
    val trigger: String,
    val timeoutMs: Int,
    val steps: JsonArray,
    val script: String,
)

data class AutofillRuntime(
    val variables: Map<String, String>,
    val automations: List<RunnableAutomation>,
)

data class AutofillReport(val ok: Boolean, val done: Int, val total: Int, val error: String?)

fun parseAutofillRuntime(body: JsonElement?): AutofillRuntime? {
    val obj = body as? JsonObject ?: return null
    val variables = (obj["variables"] as? JsonObject).orEmpty().mapNotNull { (key, value) ->
        (value as? JsonPrimitive)?.takeIf { it.isString }?.let { key to it.content }
    }.toMap()
    val automations = (obj["automations"] as? JsonArray).orEmpty().mapNotNull { element ->
        val a = element as? JsonObject ?: return@mapNotNull null
        RunnableAutomation(
            bookmarkId = a.text("bookmarkId") ?: return@mapNotNull null,
            url = a.text("url") ?: return@mapNotNull null,
            trigger = a.text("trigger") ?: "open",
            timeoutMs = a["timeoutMs"]?.jsonPrimitive?.intOrNull ?: 10_000,
            steps = a["steps"] as? JsonArray ?: JsonArray(emptyList()),
            script = a.text("script").orEmpty(),
        )
    }
    return AutofillRuntime(variables, automations)
}

fun parseAutofillReport(raw: String, parse: (String) -> JsonElement): AutofillReport? {
    val obj = runCatching { parse(raw) }.getOrNull() as? JsonObject ?: return null
    return AutofillReport(
        ok = obj["ok"]?.jsonPrimitive?.content == "true",
        done = obj["done"]?.jsonPrimitive?.intOrNull ?: 0,
        total = obj["total"]?.jsonPrimitive?.intOrNull ?: 0,
        error = obj.text("error"),
    )
}

fun canonicalUrl(url: String): String? {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase() ?: return null
    val port = uri.port.takeUnless { it == -1 || it == defaultPort(scheme) }
    val path = uri.rawPath.orEmpty().ifEmpty { "/" }
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    return "$scheme://$host${port?.let { ":$it" }.orEmpty()}$path$query"
}

fun originOf(url: String): String? {
    val canonical = canonicalUrl(url) ?: return null
    val uri = URI(canonical)
    val port = uri.port.takeUnless { it == -1 }
    return "${uri.scheme}://${uri.host}${port?.let { ":$it" }.orEmpty()}"
}

fun matchesScope(bookmarkUrl: String, pageUrl: String): Boolean {
    val scope = canonicalUrl(bookmarkUrl)?.let(::URI) ?: return false
    val page = canonicalUrl(pageUrl)?.let(::URI) ?: return false
    if (originOf(bookmarkUrl) != originOf(pageUrl)) return false
    val base = if (scope.rawPath.endsWith("/")) scope.rawPath else "${scope.rawPath}/"
    return page.rawPath == scope.rawPath || page.rawPath.startsWith(base)
}

fun automationFor(runtime: AutofillRuntime, url: String): RunnableAutomation? {
    val page = canonicalUrl(url) ?: return null
    runtime.automations
        .firstOrNull { it.trigger == "open" && canonicalUrl(it.url) == page }
        ?.let { return it }
    return runtime.automations
        .filter { it.trigger == "always" && matchesScope(it.url, url) }
        .maxByOrNull { it.url.length }
}

fun stepsInvocation(
    runner: String,
    automation: RunnableAutomation,
    variables: Map<String, String>,
): String {
    val payload = buildJsonObject {
        put("steps", automation.steps)
        put("vars", variablesJson(variables))
        put("timeoutMs", automation.timeoutMs)
    }
    return """
        (function () {
        $runner
        var post = function (r) {
          try { $AUTOFILL_BRIDGE.postMessage(JSON.stringify(r)); } catch (e) {}
        };
        globalThis.__abRunAutomationSteps($payload).then(post, function (e) {
          post({ ok: false, done: 0, total: 0, error: String(e) });
        });
        })();
    """.trimIndent()
}

fun scriptInvocation(script: String, variables: Map<String, String>): String =
    "(async (ab) => {\n$script\n})(${buildJsonObject { put("vars", variablesJson(variables)) }});"

private fun variablesJson(variables: Map<String, String>) =
    JsonObject(variables.mapValues { JsonPrimitive(it.value) })

private fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80

private fun JsonObject.text(key: String): String? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
