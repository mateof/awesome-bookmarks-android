package io.github.mateof.awesomebookmarks.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mateof.awesomebookmarks.data.AppSettings
import io.github.mateof.awesomebookmarks.data.SettingsRepository
import io.github.mateof.awesomebookmarks.network.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

    fun setServer(primary: String, fallback: String) = edit { settingsRepository.setServer(primary, fallback) }
    fun setAppLock(enabled: Boolean) = edit { settingsRepository.setAppLockEnabled(enabled) }
    fun setRememberFolder(enabled: Boolean) = edit { settingsRepository.setRememberLastFolder(enabled) }
    fun setAlwaysTags(tags: String) = edit { settingsRepository.setAlwaysTags(tags) }
    fun setOneTapSave(enabled: Boolean) = edit { settingsRepository.setOneTapSave(enabled) }
    fun setQuickButton(enabled: Boolean) = edit { settingsRepository.setShowQuickButton(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = edit { settingsRepository.setKeepScreenOn(enabled) }
    fun setTextZoom(zoom: Int) = edit { settingsRepository.setTextZoom(zoom) }
    fun setOpenExternalLinks(enabled: Boolean) = edit { settingsRepository.setOpenExternalLinksInBrowser(enabled) }
    fun setAllowMixedContent(enabled: Boolean) = edit { settingsRepository.setAllowMixedContent(enabled) }
    fun setUpdateChecks(enabled: Boolean) = edit { settingsRepository.setUpdateChecksEnabled(enabled) }

    fun signOut() {
        viewModelScope.launch {
            sessionManager.signOut()
            _signedOut.value = true
        }
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
