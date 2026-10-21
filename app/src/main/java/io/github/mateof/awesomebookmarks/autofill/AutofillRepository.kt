// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.autofill

import android.os.SystemClock
import io.github.mateof.awesomebookmarks.network.ApiCall
import io.github.mateof.awesomebookmarks.network.BookmarksApi
import io.github.mateof.awesomebookmarks.network.SessionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AutofillState {
    data class Ready(
        val runtime: AutofillRuntime,
        val runner: String,
        val captureScript: String?,
    ) : AutofillState
    data object NotAllowed : AutofillState
    data object Unsupported : AutofillState
    data class Failed(val message: String) : AutofillState
}

@Singleton
class AutofillRepository @Inject constructor(
    private val api: BookmarksApi,
    private val session: SessionManager,
) {
    private val mutex = Mutex()

    @Volatile
    private var cache: Pair<Long, AutofillState>? = null

    fun cachedRuntime(): AutofillRuntime? = (cache?.second as? AutofillState.Ready)?.runtime

    suspend fun refresh(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): AutofillState = mutex.withLock {
        cache?.let { (at, state) ->
            if (SystemClock.elapsedRealtime() - at < maxAgeMs) return@withLock state
        }
        val state = runCatching { load() }.getOrElse { AutofillState.Failed(it.message.orEmpty()) }
        cache = if (state is AutofillState.Failed) null else SystemClock.elapsedRealtime() to state
        state
    }

    private suspend fun load(): AutofillState = session.withSession { baseUrl, token ->
        val response = api.autofillRuntime(baseUrl, token)
        val state = when {
            response.httpCode == 403 -> AutofillState.NotAllowed
            response.httpCode == 404 -> AutofillState.Unsupported
            response.isSuccess -> {
                val runtime = parseAutofillRuntime(response.body)
                val runner = api.autofillRunner(baseUrl)
                if (runtime == null || runner == null) {
                    AutofillState.Failed("Unexpected autofill answer")
                } else {
                    AutofillState.Ready(runtime, runner, api.autofillCaptureScript(baseUrl))
                }
            }

            else -> null
        }
        ApiCall(response.httpCode, state, response.errorMessage())
    }

    suspend fun capture(bookmarkId: String?, url: String, fields: JsonArray): CaptureOutcome {
        val outcome = runCatching {
            session.withSession { baseUrl, token ->
                val body = buildJsonObject {
                    bookmarkId?.let { put("bookmarkId", it) }
                    put("url", url)
                    put("fields", fields)
                }
                val response = api.captureForm(baseUrl, body.toString(), token)
                val outcome = when {
                    response.httpCode == 403 -> CaptureOutcome.NotAllowed
                    response.isSuccess -> parseCaptureResult(response.body)
                    else -> null
                }
                ApiCall(response.httpCode, outcome, response.errorMessage())
            }
        }.getOrElse { CaptureOutcome.Failed(it.message.orEmpty()) }
        cache = null
        return outcome
    }

    private companion object {
        const val DEFAULT_MAX_AGE_MS = 60_000L
    }
}
