package io.github.mateof.awesomebookmarks.save

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.mateof.awesomebookmarks.R
import io.github.mateof.awesomebookmarks.ui.theme.AwesomeBookmarksTheme

/**
 * The share target, and the reason this app exists rather than a browser
 * bookmark to the web UI.
 *
 * Anything can share a page here: the URL is pulled out of whatever text
 * arrived, the folder defaults to the last one used, tags autocomplete against
 * the server and unknown ones are created on save.
 */
@AndroidEntryPoint
class SaveBookmarkActivity : ComponentActivity() {

    private val viewModel: SaveBookmarkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.dataString
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)

        setContent {
            AwesomeBookmarksTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    val saveImmediately = viewModel.prefill(
                        sharedText = listOfNotNull(subject, shared).joinToString(" "),
                        explicitUrl = null,
                    )
                    if (saveImmediately) viewModel.save()
                }

                LaunchedEffect(state.saved) {
                    if (state.saved) {
                        Toast.makeText(
                            this@SaveBookmarkActivity,
                            getString(R.string.save_done),
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    }
                }

                Surface(color = MaterialTheme.colorScheme.surface) {
                    SaveSheet(
                        state = state,
                        onUrlChanged = viewModel::onUrlChanged,
                        onTitleChanged = viewModel::onTitleChanged,
                        onOpenFolders = { viewModel.setFolderPickerOpen(true) },
                        onTagInputChanged = viewModel::onTagInputChanged,
                        onCommitTag = viewModel::commitTag,
                        onRemoveTag = viewModel::removeTag,
                        onSave = viewModel::save,
                        onCancel = ::finish,
                    )
                }

                if (state.folderPickerOpen) {
                    FolderPickerSheet(
                        folders = state.folders,
                        selectedId = state.folderId,
                        onSelect = viewModel::onFolderSelected,
                        onDismiss = { viewModel.setFolderPickerOpen(false) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveSheet(
    state: SaveUiState,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onOpenFolders: () -> Unit,
    onTagInputChanged: (String) -> Unit,
    onCommitTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.save_title), style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = state.url,
            onValueChange = onUrlChanged,
            label = { Text(stringResource(R.string.save_url)) },
            isError = state.urlLooksWrong,
            supportingText = if (state.urlLooksWrong) {
                { Text(stringResource(R.string.save_url_invalid)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChanged,
            label = { Text(stringResource(R.string.save_title_field)) },
            supportingText = { Text(stringResource(R.string.save_title_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        AssistChip(
            onClick = onOpenFolders,
            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
            label = {
                Text(state.folderName.ifBlank { stringResource(R.string.save_folder_root) })
            },
        )

        TagField(
            state = state,
            onTagInputChanged = onTagInputChanged,
            onCommitTag = onCommitTag,
            onRemoveTag = onRemoveTag,
        )

        state.error?.let { error ->
            val message = when (error) {
                SaveError.NOT_CONFIGURED -> stringResource(R.string.save_error_not_configured)
                SaveError.UNREACHABLE -> stringResource(R.string.save_error_unreachable)
                SaveError.SIGN_IN_REQUIRED -> stringResource(R.string.save_error_sign_in)
                SaveError.FAILED -> state.errorDetail.ifBlank { stringResource(R.string.save_error_generic) }
            }
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = onSave, enabled = !state.isSaving) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.heightIn(max = 18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.save_action))
                }
            }
        }
    }
}

@Composable
private fun TagField(
    state: SaveUiState,
    onTagInputChanged: (String) -> Unit,
    onCommitTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = state.tagInput,
            onValueChange = { typed ->
                // A comma is how people separate tags without thinking about it.
                if (typed.endsWith(",")) onCommitTag(typed) else onTagInputChanged(typed)
            },
            label = { Text(stringResource(R.string.save_tags)) },
            supportingText = { Text(stringResource(R.string.save_tags_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommitTag(state.tagInput) }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.tagSuggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.tagSuggestions.forEach { tag ->
                    AssistChip(onClick = { onCommitTag(tag.name) }, label = { Text(tag.name) })
                }
            }
        }

        if (state.tags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.tags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveTag(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.save_tag_remove),
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerSheet(
    folders: List<FolderNode>,
    selectedId: String?,
    onSelect: (FolderNode?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            item {
                FolderRow(
                    name = stringResource(R.string.save_folder_root),
                    depth = 0,
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                )
            }
            items(folders.size) { index ->
                val node = folders[index]
                FolderRow(
                    name = node.name,
                    depth = node.depth + 1,
                    selected = node.id == selectedId,
                    onClick = { onSelect(node) },
                )
            }
            if (folders.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.save_folders_empty))
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(name: String, depth: Int, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 16).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
