// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mateof.awesomebookmarks.R
import io.github.mateof.awesomebookmarks.data.AppSettings
import io.github.mateof.awesomebookmarks.network.SignInProblem

@Composable
fun SignInScreen(
    prefill: AppSettings,
    problem: SignInProblem?,
    serverMessage: String,
    unreachable: Boolean,
    needsTotp: Boolean,
    isSubmitting: Boolean,
    onSubmit: (primaryUrl: String, fallbackUrl: String, identifier: String, password: String, totp: String?) -> Unit,
) {
    var primaryUrl by remember(prefill.primaryUrl) { mutableStateOf(prefill.primaryUrl) }
    var fallbackUrl by remember(prefill.fallbackUrl) { mutableStateOf(prefill.fallbackUrl) }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.signin_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.signin_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (unreachable || problem != null) {
            ProblemCard(problem = problem, unreachable = unreachable, serverMessage = serverMessage)
        }

        OutlinedTextField(
            value = primaryUrl,
            onValueChange = { primaryUrl = it },
            label = { Text(stringResource(R.string.signin_server)) },
            placeholder = { Text("http://192.168.1.50:3001") },
            supportingText = { Text(stringResource(R.string.signin_server_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = fallbackUrl,
            onValueChange = { fallbackUrl = it },
            label = { Text(stringResource(R.string.signin_fallback)) },
            placeholder = { Text("https://bookmarks.my-tailnet.ts.net") },
            supportingText = { Text(stringResource(R.string.signin_fallback_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it },
            label = { Text(stringResource(R.string.signin_identifier)) },
            supportingText = { Text(stringResource(R.string.signin_identifier_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.signin_password)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (needsTotp) ImeAction.Next else ImeAction.Done,
            ),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.action_hide_password else R.string.action_show_password,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (needsTotp) {
            Text(
                text = stringResource(R.string.signin_totp_background_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = totp,
                onValueChange = { totp = it.filter(Char::isDigit).take(6) },
                label = { Text(stringResource(R.string.signin_totp)) },
                supportingText = { Text(stringResource(R.string.signin_totp_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onSubmit(primaryUrl, fallbackUrl, identifier, password, totp.takeIf { needsTotp }) },
            enabled = !isSubmitting && primaryUrl.isNotBlank() &&
                identifier.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.signin_action))
            }
        }

        Text(
            text = stringResource(R.string.signin_storage_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProblemCard(problem: SignInProblem?, unreachable: Boolean, serverMessage: String) {
    val message = when {
        unreachable -> stringResource(R.string.signin_error_unreachable)
        problem == SignInProblem.INVALID_CREDENTIALS -> stringResource(R.string.signin_error_credentials)
        problem == SignInProblem.TWO_FACTOR_REQUIRED -> stringResource(R.string.signin_error_totp)
        problem == SignInProblem.SERVER_ERROR -> stringResource(R.string.signin_error_server)
        else -> return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (serverMessage.isNotBlank()) {
                Text(
                    text = serverMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
fun StatusScreen(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}
