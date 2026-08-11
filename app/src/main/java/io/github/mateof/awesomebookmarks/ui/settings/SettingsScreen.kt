// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mateof.awesomebookmarks.R
import io.github.mateof.awesomebookmarks.data.AppSettings
import io.github.mateof.awesomebookmarks.network.TokenResult
import io.github.mateof.awesomebookmarks.update.UpdateSection
import io.github.mateof.awesomebookmarks.update.UpdateUiState
import io.github.mateof.awesomebookmarks.util.WebViewInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    appVersion: String,
    webViewInfo: WebViewInfo,
    biometricsAvailable: Boolean,
    updateState: UpdateUiState,
    tokenFeedback: TokenResult?,
    callbacks: SettingsCallbacks,
) {
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item { SectionHeader(stringResource(R.string.settings_section_server)) }
            item { ServerSection(settings, callbacks) }
            item {
                OutlinedButton(
                    onClick = { showSignOutDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.settings_sign_out)) }
            }
            item {
                Caption(stringResource(R.string.settings_session_explainer))
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_token)) }
            item { ApiTokenSection(settings, tokenFeedback, callbacks) }
            item { HorizontalDivider() }

            item { SectionHeader(stringResource(R.string.settings_section_security)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_app_lock),
                    subtitle = if (biometricsAvailable) {
                        stringResource(R.string.settings_app_lock_summary)
                    } else {
                        stringResource(R.string.settings_app_lock_unavailable)
                    },
                    checked = settings.appLockEnabled && biometricsAvailable,
                    enabled = biometricsAvailable,
                    onCheckedChange = callbacks.onAppLockChanged,
                )
                if (settings.appLockEnabled && biometricsAvailable) {
                    LockGraceRow(settings.appLockGraceMinutes, callbacks.onAppLockGraceChanged)
                }
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_saving)) }
            item {
                Caption(
                    stringResource(
                        R.string.settings_default_folder,
                        settings.defaultFolderName.ifBlank { stringResource(R.string.save_folder_root) },
                    ),
                )
                SwitchRow(
                    title = stringResource(R.string.settings_remember_folder),
                    subtitle = stringResource(R.string.settings_remember_folder_summary),
                    checked = settings.rememberLastFolder,
                    onCheckedChange = callbacks.onRememberFolderChanged,
                )
            }
            item { AlwaysTagsField(settings, callbacks) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_one_tap),
                    subtitle = stringResource(R.string.settings_one_tap_summary),
                    checked = settings.oneTapSave,
                    onCheckedChange = callbacks.onOneTapChanged,
                )
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_display)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_quick_button),
                    subtitle = stringResource(R.string.settings_quick_button_summary),
                    checked = settings.showQuickButton,
                    onCheckedChange = callbacks.onQuickButtonChanged,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_keep_screen_on),
                    subtitle = stringResource(R.string.settings_keep_screen_on_summary),
                    checked = settings.keepScreenOn,
                    onCheckedChange = callbacks.onKeepScreenOnChanged,
                )
            }
            item {
                TextZoomRow(settings.textZoom, callbacks.onTextZoomChanged)
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_advanced)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_external_links),
                    subtitle = stringResource(R.string.settings_external_links_summary),
                    checked = settings.openExternalLinksInBrowser,
                    onCheckedChange = callbacks.onExternalLinksChanged,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_mixed_content),
                    subtitle = stringResource(R.string.settings_mixed_content_summary),
                    checked = settings.allowMixedContent,
                    onCheckedChange = callbacks.onMixedContentChanged,
                )
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_updates)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_update_checks),
                    subtitle = stringResource(R.string.settings_update_checks_summary),
                    checked = settings.updateChecksEnabled,
                    onCheckedChange = callbacks.onUpdateChecksChanged,
                )
                UpdateSection(
                    state = updateState,
                    onCheck = callbacks.onCheckUpdates,
                    onDownload = callbacks.onDownloadUpdate,
                    onInstall = callbacks.onInstallUpdate,
                )
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                Caption(stringResource(R.string.settings_app_version, appVersion))
                Caption(
                    if (settings.serverVersion.isBlank()) {
                        stringResource(R.string.settings_server_version_unknown)
                    } else {
                        stringResource(R.string.settings_server_version, settings.serverVersion)
                    },
                )
                Caption(
                    stringResource(
                        R.string.settings_webview_version,
                        webViewInfo.versionName ?: stringResource(R.string.settings_webview_unknown),
                    ),
                )
                if (webViewInfo.isOutdated) Caption(stringResource(R.string.settings_webview_outdated_hint))
                Caption(stringResource(R.string.settings_disclaimer))
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.settings_sign_out)) },
            text = { Text(stringResource(R.string.settings_sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    callbacks.onSignOut()
                }) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ServerSection(settings: AppSettings, callbacks: SettingsCallbacks) {
    var primary by remember(settings.primaryUrl) { mutableStateOf(settings.primaryUrl) }
    var fallback by remember(settings.fallbackUrl) { mutableStateOf(settings.fallbackUrl) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = primary,
            onValueChange = { primary = it },
            label = { Text(stringResource(R.string.signin_server)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fallback,
            onValueChange = { fallback = it },
            label = { Text(stringResource(R.string.signin_fallback)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { callbacks.onServerChanged(primary, fallback) },
            enabled = primary != settings.primaryUrl || fallback != settings.fallbackUrl,
        ) { Text(stringResource(R.string.settings_save_server)) }
        if (settings.lastGoodUrl.isNotBlank()) {
            Caption(stringResource(R.string.settings_active_url, settings.lastGoodUrl))
        }
    }
}

@Composable
private fun ApiTokenSection(
    settings: AppSettings,
    feedback: TokenResult?,
    callbacks: SettingsCallbacks,
) {
    var token by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_token_when),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (settings.apiTokenConfigured) {
            Text(
                text = stringResource(R.string.settings_token_present),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(onClick = callbacks.onClearToken) {
                Text(stringResource(R.string.settings_token_clear))
            }
        }

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.settings_token_field)) },
            supportingText = { Text(stringResource(R.string.settings_token_explainer)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                callbacks.onSaveToken(token)
                token = ""
            },
            enabled = token.isNotBlank(),
        ) { Text(stringResource(R.string.settings_token_save)) }

        feedback?.let {
            val (message, isError) = when (it) {
                TokenResult.Saved -> stringResource(R.string.settings_token_saved) to false
                TokenResult.Invalid -> stringResource(R.string.settings_token_invalid) to true
                TokenResult.Unreachable -> stringResource(R.string.settings_token_unreachable) to true
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AlwaysTagsField(settings: AppSettings, callbacks: SettingsCallbacks) {
    var tags by remember(settings.alwaysTags) { mutableStateOf(settings.alwaysTags) }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it },
            label = { Text(stringResource(R.string.settings_always_tags)) },
            supportingText = { Text(stringResource(R.string.settings_always_tags_summary)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { callbacks.onAlwaysTagsChanged(tags) },
            enabled = tags != settings.alwaysTags,
        ) { Text(stringResource(R.string.settings_save)) }
    }
}

/** How long the app may sit in the background before asking again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockGraceRow(minutes: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.settings_lock_grace),
            style = MaterialTheme.typography.bodyLarge,
        )
        Box {
            TextButton(onClick = { expanded = true }) { Text(lockGraceLabel(minutes)) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AppSettings.GRACE_CHOICES.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(lockGraceLabel(choice)) },
                        onClick = {
                            expanded = false
                            onChange(choice)
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.settings_lock_grace_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun lockGraceLabel(minutes: Int): String = stringResource(
    when (minutes) {
        0 -> R.string.lock_grace_immediately
        1 -> R.string.lock_grace_1
        5 -> R.string.lock_grace_5
        15 -> R.string.lock_grace_15
        30 -> R.string.lock_grace_30
        60 -> R.string.lock_grace_60
        else -> R.string.lock_grace_only_on_start
    },
)

@Composable
private fun TextZoomRow(zoom: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.settings_text_zoom, zoom),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = zoom.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 70f..150f,
            steps = 7,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Grouped so the screen signature stays readable as options grow. */
data class SettingsCallbacks(
    val onBack: () -> Unit,
    val onServerChanged: (String, String) -> Unit,
    val onSignOut: () -> Unit,
    val onAppLockChanged: (Boolean) -> Unit,
    val onAppLockGraceChanged: (Int) -> Unit,
    val onRememberFolderChanged: (Boolean) -> Unit,
    val onAlwaysTagsChanged: (String) -> Unit,
    val onOneTapChanged: (Boolean) -> Unit,
    val onQuickButtonChanged: (Boolean) -> Unit,
    val onKeepScreenOnChanged: (Boolean) -> Unit,
    val onTextZoomChanged: (Int) -> Unit,
    val onExternalLinksChanged: (Boolean) -> Unit,
    val onMixedContentChanged: (Boolean) -> Unit,
    val onSaveToken: (String) -> Unit,
    val onClearToken: () -> Unit,
    val onUpdateChecksChanged: (Boolean) -> Unit,
    val onCheckUpdates: () -> Unit,
    val onDownloadUpdate: () -> Unit,
    val onInstallUpdate: () -> Unit,
)
