// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.ui.settings

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.mateof.awesomebookmarks.BuildConfig
import io.github.mateof.awesomebookmarks.R
import io.github.mateof.awesomebookmarks.ui.lock.AppLock
import io.github.mateof.awesomebookmarks.ui.theme.AwesomeBookmarksTheme
import io.github.mateof.awesomebookmarks.update.UpdateCheckWorker
import io.github.mateof.awesomebookmarks.update.UpdateDialog
import io.github.mateof.awesomebookmarks.update.UpdateViewModel
import io.github.mateof.awesomebookmarks.util.WebViewInfo

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Update checks work regardless; only the notification is lost, so
            // the setting stays on either way.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fromUpdateNotification = intent.getBooleanExtra(EXTRA_SHOW_UPDATE, false)
        if (fromUpdateNotification) updateViewModel.check(force = true)

        setContent {
            AwesomeBookmarksTheme {
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()
                val updateState by updateViewModel.state.collectAsStateWithLifecycle()
                val tokenFeedback by viewModel.tokenFeedback.collectAsStateWithLifecycle()

                LaunchedEffect(signedOut) { if (signedOut) finish() }

                if (fromUpdateNotification && updateState.available != null && !updateState.dismissed) {
                    UpdateDialog(
                        state = updateState,
                        onDownload = updateViewModel::download,
                        onInstall = ::installUpdate,
                        onSkip = updateViewModel::skipCurrent,
                        onDismiss = updateViewModel::dismiss,
                    )
                }

                Surface(color = MaterialTheme.colorScheme.background) {
                    val current = settings
                    if (current == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        SettingsScreen(
                            settings = current,
                            appVersion = BuildConfig.VERSION_NAME,
                            webViewInfo = remember { WebViewInfo.of(this@SettingsActivity) },
                            biometricsAvailable = remember { AppLock.isAvailable(this@SettingsActivity) },
                            updateState = updateState,
                            tokenFeedback = tokenFeedback,
                            callbacks = SettingsCallbacks(
                                onBack = { finish() },
                                onServerChanged = viewModel::setServer,
                                onSignOut = viewModel::signOut,
                                onAppLockChanged = viewModel::setAppLock,
                                onRememberFolderChanged = viewModel::setRememberFolder,
                                onAlwaysTagsChanged = viewModel::setAlwaysTags,
                                onOneTapChanged = viewModel::setOneTapSave,
                                onQuickButtonChanged = viewModel::setQuickButton,
                                onKeepScreenOnChanged = viewModel::setKeepScreenOn,
                                onTextZoomChanged = viewModel::setTextZoom,
                                onExternalLinksChanged = viewModel::setOpenExternalLinks,
                                onMixedContentChanged = viewModel::setAllowMixedContent,
                                onSaveToken = viewModel::saveApiToken,
                                onClearToken = viewModel::clearApiToken,
                                onUpdateChecksChanged = ::onUpdateChecksChanged,
                                onCheckUpdates = { updateViewModel.check(force = true) },
                                onDownloadUpdate = updateViewModel::download,
                                onInstallUpdate = ::installUpdate,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun onUpdateChecksChanged(enabled: Boolean) {
        viewModel.setUpdateChecks(enabled)
        if (enabled) {
            UpdateCheckWorker.schedule(applicationContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            UpdateCheckWorker.cancel(applicationContext)
        }
    }

    private fun installUpdate() {
        val apk = updateViewModel.state.value.downloaded ?: return
        if (!updateViewModel.canInstall()) {
            Toast.makeText(this, getString(R.string.update_allow_unknown_sources), Toast.LENGTH_LONG).show()
            runCatching { startActivity(updateViewModel.unknownSourcesIntent()) }
            return
        }
        runCatching { startActivity(updateViewModel.installIntent(apk)) }
            .onFailure { Toast.makeText(this, getString(R.string.update_install_failed), Toast.LENGTH_LONG).show() }
    }

    companion object {
        /** Set by the update notification so the release notes open on top. */
        const val EXTRA_SHOW_UPDATE = "io.github.mateof.awesomebookmarks.extra.SHOW_UPDATE"
    }
}
