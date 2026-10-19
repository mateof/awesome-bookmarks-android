// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.autofill

import android.os.SystemClock
import io.github.mateof.awesomebookmarks.network.ApiCall
import io.github.mateof.awesomebookmarks.network.BookmarksApi
import io.github.mateof.awesomebookmarks.network.SessionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AutofillState {
    data class Ready(val runtime: AutofillRuntime, val runner: String) : AutofillState
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
                    AutofillState.Ready(runtime, runner)
                }
            }

            else -> null
        }
        ApiCall(response.httpCode, state, response.errorMessage())
    }

    private companion object {
        const val DEFAULT_MAX_AGE_MS = 60_000L
    }
}
