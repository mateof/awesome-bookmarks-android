package io.github.mateof.awesomebookmarks.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The slice of the AwesomeBookmarks HTTP API this app talks to.
 *
 * Two surfaces are in play, deliberately:
 *
 * - The `/api/auth` routes are the SPA's own session endpoints. Calling them
 *   is what gives the WebView a logged in session, because the cookie jar is
 *   shared between OkHttp and the WebView.
 * - The `/api/v1` routes are the stable public API, and `/api/ext/quick-add`
 *   is the one-shot save the browser extension uses. Those are what the
 *   native screens use, so the app is not coupled to the SPA's internals.
 */
@Singleton
class BookmarksApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    /**
     * Short-timeout client for "is this host up" checks. Waiting the full read
     * timeout on each candidate address would make a cold start off the LAN
     * feel broken.
     */
    private val probeClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Unauthenticated liveness probe. `/api/auth/config` is public and only
     * exists on an AwesomeBookmarks server, so a 200 means both "reachable"
     * and "is the right kind of server".
     */
    suspend fun isServerReachable(baseUrl: String): Boolean = runCatching {
        val request = Request.Builder().url("$baseUrl/api/auth/config").get().build()
        execute(request, probeClient).httpCode == 200
    }.getOrDefault(false)

    /**
     * Signs in and, as a side effect, gives the WebView its session cookie.
     *
     * The server answers `{"twoFactorRequired": true}` instead of a user when
     * TOTP is on and no code was supplied, which is a 200, not an error.
     */
    suspend fun login(
        baseUrl: String,
        identifier: String,
        password: String,
        totp: String? = null,
    ): LoginResult {
        val body = buildJsonObject {
            put("identifier", identifier)
            put("password", password)
            totp?.takeIf { it.isNotBlank() }?.let { put("totp", it) }
        }
        val response = post(baseUrl, "/api/auth/login", body.toString())
        return when {
            response.httpCode == 200 &&
                response.body?.jsonObject?.get("twoFactorRequired")?.jsonPrimitive?.content == "true" ->
                LoginResult.TwoFactorRequired

            response.httpCode == 200 -> LoginResult.Success
            response.httpCode == 401 || response.httpCode == 400 -> LoginResult.InvalidCredentials
            else -> LoginResult.Failed(response.httpCode, response.errorMessage())
        }
    }

    suspend fun logout(baseUrl: String) = post(baseUrl, "/api/auth/logout", "{}")

    /**
     * Cheap "is the session still usable" probe. A `423` means the session is
     * valid but the server dropped the decryption key after an idle timeout,
     * which is fixed by logging in again, not by asking the user for anything.
     */
    suspend fun me(baseUrl: String): ApiResponse = get(baseUrl, "/api/v1/me")

    suspend fun folders(baseUrl: String): List<Folder> {
        val response = get(baseUrl, "/api/v1/folders")
        if (!response.isSuccess) throw ApiException(response.errorMessage())
        return (response.body as? JsonArray).orEmpty().map { element ->
            val obj = element.jsonObject
            Folder(
                id = obj.string("id").orEmpty(),
                parentId = obj.string("parentId"),
                name = obj.string("name").orEmpty(),
            )
        }.filter { it.id.isNotEmpty() }
    }

    suspend fun tags(baseUrl: String): List<Tag> {
        val response = get(baseUrl, "/api/v1/tags")
        if (!response.isSuccess) throw ApiException(response.errorMessage())
        return (response.body as? JsonArray).orEmpty().map { element ->
            val obj = element.jsonObject
            Tag(
                id = obj.string("id").orEmpty(),
                name = obj.string("name").orEmpty(),
                color = obj.string("color"),
            )
        }.filter { it.name.isNotEmpty() }
    }

    /**
     * Saves a link in one call.
     *
     * Uses `/api/ext/quick-add` rather than `POST /api/v1/bookmarks` because it
     * takes tag **names** and creates the missing ones server side. The v1
     * endpoint takes tag ids, which would mean a create-then-link dance on the
     * client for every new tag typed in the share sheet.
     */
    suspend fun quickAdd(
        baseUrl: String,
        url: String,
        title: String?,
        folderId: String?,
        tags: List<String>,
    ): ApiResponse {
        val body = buildJsonObject {
            put("url", url)
            title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
            put("folderId", folderId?.let { JsonPrimitiveOf(it) } ?: JsonNull)
            put("tags", buildJsonArray { tags.forEach { add(JsonPrimitiveOf(it)) } })
        }
        return post(baseUrl, "/api/ext/quick-add", body.toString())
    }

    private suspend fun get(baseUrl: String, path: String): ApiResponse =
        execute(Request.Builder().url(baseUrl + path).get().build())

    private suspend fun post(baseUrl: String, path: String, jsonBody: String): ApiResponse =
        execute(
            Request.Builder()
                .url(baseUrl + path)
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )

    private suspend fun execute(
        request: Request,
        httpClient: OkHttpClient = client,
    ): ApiResponse = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            ApiResponse(
                httpCode = response.code,
                body = runCatching { json.parseToJsonElement(raw) }.getOrNull(),
            )
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

data class ApiResponse(val httpCode: Int, val body: JsonElement?) {
    val isSuccess: Boolean get() = httpCode in 200..299

    /** Session gone, or never there. */
    val isUnauthorized: Boolean get() = httpCode == 401

    /**
     * The session is valid but the server cannot decrypt: it evicted the data
     * encryption key after an idle timeout. Signing in again re-derives it.
     */
    val isLocked: Boolean get() = httpCode == 423

    fun errorMessage(): String =
        (body as? JsonObject)?.let { it.string("message") ?: it.string("error") }
            ?: "HTTP $httpCode"
}

sealed interface LoginResult {
    data object Success : LoginResult
    data object TwoFactorRequired : LoginResult
    data object InvalidCredentials : LoginResult
    data class Failed(val httpCode: Int, val message: String) : LoginResult
}

data class Folder(val id: String, val parentId: String?, val name: String)

data class Tag(val id: String, val name: String, val color: String?)

class ApiException(message: String) : Exception(message)

private fun JsonObject.string(key: String): String? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

@Suppress("FunctionName")
private fun JsonPrimitiveOf(value: String) = kotlinx.serialization.json.JsonPrimitive(value)
