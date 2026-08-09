package io.github.mateof.awesomebookmarks.save

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
                        state = state,
                        onSelect = viewModel::onFolderSelected,
                        onCreateIn = viewModel::openNewFolderDialog,
                        onToggleExpanded = viewModel::toggleFolderExpanded,
                        onQueryChanged = viewModel::onFolderQueryChanged,
                        onRetry = viewModel::loadFolders,
                        onDismiss = { viewModel.setFolderPickerOpen(false) },
                    )
                }

                if (state.newFolderDialogOpen) {
                    NewFolderDialog(
                        parentName = state.newFolderParent?.name,
                        isCreating = state.isCreatingFolder,
                        error = state.newFolderError,
                        onCreate = viewModel::createFolder,
                        onDismiss = viewModel::dismissNewFolderDialog,
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
                SaveError.TOKEN_REJECTED -> stringResource(R.string.save_error_token_rejected)
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
    state: SaveUiState,
    onSelect: (FolderNode?) -> Unit,
    onCreateIn: (FolderNode?) -> Unit,
    onToggleExpanded: (String) -> Unit,
    onQueryChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.folderQuery,
                onValueChange = onQueryChanged,
                label = { Text(stringResource(R.string.save_folder_search)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.folderQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.save_folder_search_clear),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val visible = state.visibleFolders

            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                // The root is a destination, not a match, so searching hides it.
                if (!state.isSearchingFolders) {
                    item {
                        FolderRow(
                            name = stringResource(R.string.save_folder_root),
                            path = "",
                            depth = 0,
                            selected = state.folderId == null,
                            expandable = false,
                            expanded = false,
                            onClick = { onSelect(null) },
                            onToggle = {},
                            onCreateChild = { onCreateIn(null) },
                        )
                    }
                }

                items(visible.size) { index ->
                    val node = visible[index]
                    FolderRow(
                        name = node.name,
                        // The path disambiguates matches once names repeat,
                        // which is the whole reason to search rather than browse.
                        path = if (state.isSearchingFolders) node.path else "",
                        depth = if (state.isSearchingFolders) 0 else node.depth + 1,
                        selected = node.id == state.folderId,
                        expandable = node.hasChildren && !state.isSearchingFolders,
                        expanded = node.id in state.expandedFolders,
                        onClick = { onSelect(node) },
                        onToggle = { onToggleExpanded(node.id) },
                        onCreateChild = { onCreateIn(node) },
                    )
                }

                when {
                    state.foldersLoading -> item {
                        Box(
                            Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }

                    state.foldersError != null -> item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.save_folders_failed),
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = state.foldersError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                        }
                    }

                    state.isSearchingFolders && visible.isEmpty() -> item {
                        Box(
                            Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(R.string.save_folder_no_matches)) }
                    }

                    state.folders.isEmpty() -> item {
                        Box(
                            Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(R.string.save_folders_empty)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewFolderDialog(
    parentName: String?,
    isCreating: Boolean,
    error: String?,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text(stringResource(R.string.save_folder_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (parentName == null) {
                        stringResource(R.string.save_folder_new_at_root)
                    } else {
                        stringResource(R.string.save_folder_new_inside, parentName)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.save_folder_new_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCreate(name) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = !isCreating && name.isNotBlank()) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.heightIn(max = 18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.save_folder_create))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun FolderRow(
    name: String,
    path: String,
    depth: Int,
    selected: Boolean,
    expandable: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onCreateChild: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width((depth * 16).dp))

        // A fixed slot either way, so names stay aligned whether or not the
        // row has children.
        if (expandable) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = stringResource(
                        if (expanded) R.string.save_folder_collapse else R.string.save_folder_expand,
                    ),
                )
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (path.isNotBlank()) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = onCreateChild) {
            Icon(
                imageVector = Icons.Default.CreateNewFolder,
                contentDescription = stringResource(R.string.save_folder_new_child_of, name),
            )
        }
    }
}
