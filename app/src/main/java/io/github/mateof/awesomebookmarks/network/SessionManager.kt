package io.github.mateof.awesomebookmarks.network

import android.util.Log
import io.github.mateof.awesomebookmarks.data.SecretStore
import io.github.mateof.awesomebookmarks.data.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers the two questions every entry point needs before it can do anything:
 * which server are we talking to, and are we signed in.
 *
 * ## Why the credentials are stored
 *
 * The session cookie lasts 30 days, but that is not the binding constraint.
 * The server derives your data encryption key from the password at login and
 * holds it in memory only, dropping it after roughly 30 idle minutes. After
 * that a perfectly valid cookie starts getting `423 Locked`.
 *
 * An API token would dodge that (its row carries a wrapped copy of the key),
 * but a token cannot give the WebView a session, and the WebView is the whole
 * app. So the password is kept, encrypted under a Keystore key, and replayed
 * whenever the server answers 401 or 423. One credential, one login screen,
 * and the share target keeps working after days of not opening the app.
 */
@Singleton
class SessionManager @Inject constructor(
    private val api: BookmarksApi,
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val cookieJar: WebViewCookieJar,
) {
    private val mutex = Mutex()

    @Volatile
    var activeBaseUrl: String? = null
        private set

    /** Resolves a reachable server and guarantees a usable session on it. */
    suspend fun prepare(): SessionResult = mutex.withLock {
        val current = settings.current()
        if (!current.isConfigured) return@withLock SessionResult.NotConfigured

        val baseUrl = current.candidateUrls.firstOrNull { api.isServerReachable(it) }
            ?: return@withLock SessionResult.Unreachable(current.candidateUrls)

        activeBaseUrl = baseUrl
        if (baseUrl != current.lastGoodUrl) settings.setLastGoodUrl(baseUrl)

        return@withLock ensureSignedIn(baseUrl)
    }

    /** First sign in, or re-entering credentials after they changed. */
    suspend fun signIn(
        rawBaseUrl: String,
        identifier: String,
        password: String,
        totp: String? = null,
    ): SessionResult = mutex.withLock {
        val baseUrl = rawBaseUrl.trimEnd('/')
        if (!api.isServerReachable(baseUrl)) return@withLock SessionResult.Unreachable(listOf(baseUrl))

        return@withLock when (val result = api.login(baseUrl, identifier, password, totp)) {
            LoginResult.Success -> {
                secrets.writeCredentials(identifier, password)
                activeBaseUrl = baseUrl
                settings.setLastGoodUrl(baseUrl)
                SessionResult.Ready(baseUrl)
            }

            LoginResult.TwoFactorRequired ->
                SessionResult.SignInRequired(baseUrl, SignInProblem.TWO_FACTOR_REQUIRED)

            LoginResult.InvalidCredentials ->
                SessionResult.SignInRequired(baseUrl, SignInProblem.INVALID_CREDENTIALS)

            is LoginResult.Failed ->
                SessionResult.SignInRequired(baseUrl, SignInProblem.SERVER_ERROR, result.message)
        }
    }

    /** Ends everything: server session, cookies and stored credentials. */
    suspend fun signOut() = mutex.withLock {
        activeBaseUrl?.let { runCatching { api.logout(it) } }
        cookieJar.clear()
        secrets.clear()
        settings.clearServer()
        activeBaseUrl = null
    }

    /**
     * Replays the stored login. Called when the WebView lands on the SPA's
     * `/login` route, and by [withSession] on a 401 or 423.
     */
    suspend fun renew(baseUrl: String): Boolean = mutex.withLock { renewLocked(baseUrl) }

    /**
     * Runs an API call, and if the server says the session is gone or locked,
     * signs in again once and retries. This is what keeps the share target
     * working when the app has not been opened in days.
     */
    suspend fun <T> withSession(block: suspend (baseUrl: String) -> ApiCall<T>): T {
        val baseUrl = when (val prepared = prepare()) {
            is SessionResult.Ready -> prepared.baseUrl
            SessionResult.NotConfigured -> throw SessionException(SessionProblem.NOT_CONFIGURED)
            is SessionResult.Unreachable -> throw SessionException(SessionProblem.UNREACHABLE)
            is SessionResult.SignInRequired -> throw SessionException(SessionProblem.SIGN_IN_REQUIRED)
        }

        val first = block(baseUrl)
        if (!first.needsFreshSession) return first.valueOrThrow()

        Log.i(TAG, "Session rejected (${first.httpCode}), signing in again")
        if (!renew(baseUrl)) throw SessionException(SessionProblem.SIGN_IN_REQUIRED)
        return block(baseUrl).valueOrThrow()
    }

    private suspend fun ensureSignedIn(baseUrl: String): SessionResult {
        val me = api.me(baseUrl)
        if (me.isSuccess) return SessionResult.Ready(baseUrl)
        if (!me.isUnauthorized && !me.isLocked) {
            Log.w(TAG, "Unexpected answer from $baseUrl: HTTP ${me.httpCode}")
            return SessionResult.Unreachable(listOf(baseUrl))
        }
        if (!secrets.hasCredentials()) {
            return SessionResult.SignInRequired(baseUrl, SignInProblem.NO_CREDENTIALS_STORED)
        }
        return if (renewLocked(baseUrl)) {
            SessionResult.Ready(baseUrl)
        } else {
            SessionResult.SignInRequired(baseUrl, SignInProblem.INVALID_CREDENTIALS)
        }
    }

    /** Caller must already hold [mutex]. */
    private suspend fun renewLocked(baseUrl: String): Boolean {
        val credentials = secrets.readCredentials() ?: return false
        val result = api.login(baseUrl, credentials.identifier, credentials.password)
        if (result != LoginResult.Success) {
            Log.w(TAG, "Silent sign in rejected: $result")
        }
        return result == LoginResult.Success
    }

    private companion object {
        const val TAG = "SessionManager"
    }
}

/** Wraps an API answer so [SessionManager.withSession] can decide about retrying. */
data class ApiCall<T>(
    val httpCode: Int,
    val value: T?,
    val errorMessage: String = "",
) {
    val needsFreshSession: Boolean get() = httpCode == 401 || httpCode == 423

    fun valueOrThrow(): T = value ?: throw ApiException(errorMessage.ifBlank { "HTTP $httpCode" })
}

sealed interface SessionResult {
    data class Ready(val baseUrl: String) : SessionResult
    data object NotConfigured : SessionResult
    data class Unreachable(val triedUrls: List<String>) : SessionResult
    data class SignInRequired(
        val baseUrl: String,
        val problem: SignInProblem,
        val serverMessage: String = "",
    ) : SessionResult
}

enum class SignInProblem {
    /** Normal on first run. */
    NO_CREDENTIALS_STORED,

    /** The stored password no longer matches, or the account changed. */
    INVALID_CREDENTIALS,

    /** The account has TOTP enabled and the sign in needs a code. */
    TWO_FACTOR_REQUIRED,

    SERVER_ERROR,
}

enum class SessionProblem { NOT_CONFIGURED, UNREACHABLE, SIGN_IN_REQUIRED }

class SessionException(val problem: SessionProblem) : Exception(problem.name)
