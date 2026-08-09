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
    suspend fun me(baseUrl: String, token: String? = null): ApiResponse =
        get(baseUrl, "/api/v1/me", token)

    /**
     * The product version the server is running. Added in server 0.20.2; older
     * servers answer 404, which the caller reads as "unknown" rather than an
     * error.
     */
    suspend fun serverVersion(baseUrl: String, token: String? = null): String? {
        val response = get(baseUrl, "/api/v1/version", token)
        if (!response.isSuccess) return null
        return (response.body as? JsonObject)?.string("version")
    }

    suspend fun folders(baseUrl: String, token: String? = null): List<Folder> {
        val response = get(baseUrl, "/api/v1/folders", token)
        if (!response.isSuccess) throw ApiException(response.errorMessage())
        return parseFolders(response.body)
    }

    /** Creates a folder and returns it. `parentId` null means the root. */
    suspend fun createFolder(
        baseUrl: String,
        name: String,
        parentId: String?,
        token: String? = null,
    ): Folder {
        val body = buildJsonObject {
            put("name", name)
            put("parentId", parentId?.let { JsonPrimitiveOf(it) } ?: JsonNull)
        }
        val response = post(baseUrl, "/api/v1/folders", body.toString(), token)
        if (!response.isSuccess) throw ApiException(response.errorMessage())
        return parseFolder(response.body)
            ?: throw ApiException("The server did not return the new folder")
    }

    suspend fun tags(baseUrl: String, token: String? = null): List<Tag> {
        val response = get(baseUrl, "/api/v1/tags", token)
        if (!response.isSuccess) throw ApiException(response.errorMessage())
        return parseTags(response.body)
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
        token: String? = null,
    ): ApiResponse {
        val body = buildJsonObject {
            put("url", url)
            title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
            put("folderId", folderId?.let { JsonPrimitiveOf(it) } ?: JsonNull)
            put("tags", buildJsonArray { tags.forEach { add(JsonPrimitiveOf(it)) } })
        }
        return post(baseUrl, "/api/ext/quick-add", body.toString(), token)
    }

    private suspend fun get(baseUrl: String, path: String, token: String? = null): ApiResponse =
        execute(Request.Builder().url(baseUrl + path).get().bearer(token).build())

    private suspend fun post(
        baseUrl: String,
        path: String,
        jsonBody: String,
        token: String? = null,
    ): ApiResponse = execute(
        Request.Builder()
            .url(baseUrl + path)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .bearer(token)
            .build(),
    )

    /**
     * A Bearer token authenticates on its own, without the session cookie the
     * jar would otherwise attach. Both can travel together; the server prefers
     * the header.
     */
    private fun Request.Builder.bearer(token: String?): Request.Builder =
        if (token.isNullOrBlank()) this else header("Authorization", "Bearer $token")

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

/**
 * The list endpoints answer with a bare JSON array of objects keyed `id`,
 * `parentId` and `name`. Kept as pure functions so the shape is covered by
 * tests: a silent parsing change here shows up as "you have no folders", which
 * looks like a server problem and is not.
 */
fun parseFolders(body: JsonElement?): List<Folder> =
    (body as? JsonArray).orEmpty().mapNotNull { parseFolder(it) }

fun parseFolder(body: JsonElement?): Folder? {
    val obj = body as? JsonObject ?: return null
    val id = obj.string("id")?.takeIf { it.isNotEmpty() } ?: return null
    return Folder(id = id, parentId = obj.string("parentId"), name = obj.string("name").orEmpty())
}

fun parseTags(body: JsonElement?): List<Tag> =
    (body as? JsonArray).orEmpty().mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val name = obj.string("name")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        Tag(id = obj.string("id").orEmpty(), name = name, color = obj.string("color"))
    }

data class Tag(val id: String, val name: String, val color: String?)

class ApiException(message: String) : Exception(message)

internal fun JsonObject.string(key: String): String? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

@Suppress("FunctionName")
private fun JsonPrimitiveOf(value: String) = kotlinx.serialization.json.JsonPrimitive(value)
