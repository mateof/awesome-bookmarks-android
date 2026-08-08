package io.github.mateof.awesomebookmarks.ui

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mateof.awesomebookmarks.data.AppSettings
import io.github.mateof.awesomebookmarks.data.SettingsRepository
import io.github.mateof.awesomebookmarks.data.normalizeBaseUrl
import io.github.mateof.awesomebookmarks.network.SessionManager
import io.github.mateof.awesomebookmarks.network.SessionResult
import io.github.mateof.awesomebookmarks.network.SignInProblem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Null until DataStore answers, so the lock screen does not flash. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val currentSettings: AppSettings get() = settings.value ?: AppSettings()

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * When the app was last sent to the background, as elapsed realtime.
     *
     * The lock grace period is measured from here and not from the moment of
     * unlocking: otherwise a minute of normal use is enough to make every trip
     * to the browser come back to a biometric prompt.
     */
    var backgroundedAt: Long = 0L

    /**
     * The WebView's navigation history, kept across activity recreation.
     * Held here rather than in saved instance state because it can exceed the
     * Binder transaction limit on a long browsing session.
     */
    val webViewState: Bundle = Bundle()

    init {
        connect()
    }

    fun connect() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            _uiState.value = when (val result = sessionManager.prepare()) {
                is SessionResult.Ready -> MainUiState.Connected(result.baseUrl)
                SessionResult.NotConfigured -> MainUiState.NeedsSignIn(currentSettings)
                is SessionResult.Unreachable -> MainUiState.Unreachable(result.triedUrls)
                is SessionResult.SignInRequired -> result.toSignInState()
            }
        }
    }

    /**
     * The WebView landed on the SPA's login route. Replay the stored sign in
     * and reload, so an expired session is invisible.
     */
    fun onSessionExpired() {
        val baseUrl = sessionManager.activeBaseUrl ?: return
        viewModelScope.launch {
            if (sessionManager.renew(baseUrl)) {
                _events.send(MainEvent.Load(baseUrl))
            } else {
                _uiState.value = MainUiState.NeedsSignIn(
                    prefill = currentSettings,
                    problem = SignInProblem.INVALID_CREDENTIALS,
                )
            }
        }
    }

    fun submitSignIn(
        primaryUrl: String,
        fallbackUrl: String,
        identifier: String,
        password: String,
        totp: String?,
    ) {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            val normalizedPrimary = normalizeBaseUrl(primaryUrl)
            val normalizedFallback = normalizeBaseUrl(fallbackUrl)
            settingsRepository.setServer(normalizedPrimary, normalizedFallback)

            var result = sessionManager.signIn(normalizedPrimary, identifier, password, totp)
            if (result is SessionResult.Unreachable && normalizedFallback.isNotBlank()) {
                result = sessionManager.signIn(normalizedFallback, identifier, password, totp)
            }

            _uiState.value = when (result) {
                is SessionResult.Ready -> MainUiState.Connected(result.baseUrl)
                is SessionResult.Unreachable -> MainUiState.NeedsSignIn(
                    prefill = currentSettings.copy(
                        primaryUrl = normalizedPrimary,
                        fallbackUrl = normalizedFallback,
                    ),
                    problem = null,
                    unreachable = true,
                )

                is SessionResult.SignInRequired -> result.toSignInState()
                SessionResult.NotConfigured -> MainUiState.NeedsSignIn(currentSettings)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            sessionManager.signOut()
            backgroundedAt = 0L
            webViewState.clear()
            _uiState.value = MainUiState.NeedsSignIn(AppSettings())
        }
    }

    fun reload() {
        viewModelScope.launch { _events.send(MainEvent.Reload) }
    }

    fun navigateTo(path: String) {
        val baseUrl = sessionManager.activeBaseUrl ?: return
        viewModelScope.launch { _events.send(MainEvent.Load(baseUrl + path)) }
    }

    private fun SessionResult.SignInRequired.toSignInState() = MainUiState.NeedsSignIn(
        prefill = currentSettings.copy(primaryUrl = baseUrl.ifBlank { currentSettings.primaryUrl }),
        problem = problem.takeIf { it != SignInProblem.NO_CREDENTIALS_STORED },
        serverMessage = serverMessage,
    )
}

sealed interface MainUiState {
    data object Loading : MainUiState

    data class NeedsSignIn(
        val prefill: AppSettings,
        val problem: SignInProblem? = null,
        val serverMessage: String = "",
        val unreachable: Boolean = false,
    ) : MainUiState {
        val needsTotp: Boolean get() = problem == SignInProblem.TWO_FACTOR_REQUIRED
    }

    data class Connected(val baseUrl: String) : MainUiState

    data class Unreachable(val tried: List<String>) : MainUiState
}

sealed interface MainEvent {
    data class Load(val url: String) : MainEvent
    data object Reload : MainEvent
}
