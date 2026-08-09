package io.github.mateof.awesomebookmarks.save

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mateof.awesomebookmarks.data.SettingsRepository
import io.github.mateof.awesomebookmarks.network.SessionException
import io.github.mateof.awesomebookmarks.network.SessionProblem
import io.github.mateof.awesomebookmarks.network.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SaveBookmarkViewModel @Inject constructor(
    private val saveRepository: SaveRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SaveUiState())
    val state: StateFlow<SaveUiState> = _state.asStateFlow()

    /**
     * @return true when the caller should save straight away without showing
     *         the sheet, because one tap save is on and everything is known.
     */
    suspend fun prefill(sharedText: String?, explicitUrl: String?): Boolean {
        val settings = settingsRepository.current()
        val url = explicitUrl ?: extractUrl(sharedText)
        val title = extractTitle(sharedText, url)

        _state.update {
            it.copy(
                url = url.orEmpty(),
                title = title,
                folderId = settings.defaultFolderId.ifBlank { null },
                folderName = settings.defaultFolderName,
                urlLooksWrong = url == null,
            )
        }
        loadFolders()
        loadTags()
        return settings.oneTapSave && url != null
    }

    fun onUrlChanged(url: String) = _state.update {
        it.copy(url = url, urlLooksWrong = false, error = null)
    }

    fun onTitleChanged(title: String) = _state.update { it.copy(title = title) }

    fun onFolderSelected(node: FolderNode?) = _state.update {
        it.copy(folderId = node?.id, folderName = node?.name.orEmpty(), folderPickerOpen = false)
    }

    fun setFolderPickerOpen(open: Boolean) = _state.update { it.copy(folderPickerOpen = open) }

    fun onTagInputChanged(input: String) = _state.update { it.copy(tagInput = input) }

    /** Accepts a suggestion or whatever is typed, so unknown tags can be created. */
    fun commitTag(name: String = _state.value.tagInput) {
        val clean = name.trim().trim(',')
        if (clean.isEmpty()) return
        _state.update {
            if (it.tags.any { existing -> existing.equals(clean, ignoreCase = true) }) {
                it.copy(tagInput = "")
            } else {
                it.copy(tags = it.tags + clean, tagInput = "")
            }
        }
    }

    fun removeTag(name: String) = _state.update { it.copy(tags = it.tags - name) }

    fun save() {
        val current = _state.value
        if (current.isSaving) return
        if (current.url.isBlank()) {
            _state.update { it.copy(urlLooksWrong = true) }
            return
        }

        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            saveRepository.save(
                url = current.url.trim(),
                title = current.title.trim().ifBlank { null },
                folderId = current.folderId,
                folderName = current.folderName,
                tags = current.tags,
            ).onSuccess {
                _state.update { it.copy(isSaving = false, saved = true) }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(isSaving = false, error = throwable.toSaveError(), errorDetail = throwable.message.orEmpty())
                }
            }
        }
    }

    fun loadFolders() {
        _state.update { it.copy(foldersLoading = true, foldersError = null) }
        viewModelScope.launch {
            saveRepository.folders()
                .onSuccess { folders ->
                    _state.update { it.copy(folders = folders, foldersLoading = false) }
                }
                .onFailure { throwable ->
                    // Reported in the picker rather than as a global error: not
                    // being able to list folders does not stop you saving to the
                    // root, and claiming there are none would be a lie.
                    _state.update {
                        it.copy(
                            foldersLoading = false,
                            foldersError = throwable.message.orEmpty()
                                .ifBlank { "Could not load folders" },
                        )
                    }
                }
        }
    }

    fun openNewFolderDialog(parent: FolderNode?) = _state.update {
        it.copy(newFolderDialogOpen = true, newFolderParent = parent, newFolderError = null)
    }

    fun dismissNewFolderDialog() = _state.update {
        it.copy(newFolderDialogOpen = false, newFolderParent = null, newFolderError = null)
    }

    fun createFolder(name: String) {
        val current = _state.value
        if (current.isCreatingFolder || name.isBlank()) return

        _state.update { it.copy(isCreatingFolder = true, newFolderError = null) }
        viewModelScope.launch {
            saveRepository.createFolder(name, current.newFolderParent?.id)
                .onSuccess { created ->
                    // Select it straight away: wanting the folder is why you
                    // opened this dialog.
                    _state.update {
                        it.copy(
                            isCreatingFolder = false,
                            newFolderDialogOpen = false,
                            newFolderParent = null,
                            folderId = created.id,
                            folderName = created.name,
                            folderPickerOpen = false,
                        )
                    }
                    loadFolders()
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isCreatingFolder = false,
                            newFolderError = throwable.message.orEmpty()
                                .ifBlank { "Could not create the folder" },
                        )
                    }
                }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            saveRepository.tags().onSuccess { tags -> _state.update { it.copy(knownTags = tags) } }
        }
    }

    private fun Throwable.toSaveError(): SaveError = when {
        this is SessionException && problem == SessionProblem.NOT_CONFIGURED -> SaveError.NOT_CONFIGURED
        this is SessionException && problem == SessionProblem.UNREACHABLE -> SaveError.UNREACHABLE
        this is SessionException && problem == SessionProblem.TOKEN_REJECTED -> SaveError.TOKEN_REJECTED
        this is SessionException -> SaveError.SIGN_IN_REQUIRED
        else -> SaveError.FAILED
    }
}

data class SaveUiState(
    val url: String = "",
    val title: String = "",
    val folderId: String? = null,
    val folderName: String = "",
    val folders: List<FolderNode> = emptyList(),
    val foldersLoading: Boolean = true,
    /** Why the folder list could not be read. Null when it could. */
    val foldersError: String? = null,
    val folderPickerOpen: Boolean = false,
    val newFolderDialogOpen: Boolean = false,
    /** Null means the new folder goes at the root. */
    val newFolderParent: FolderNode? = null,
    val isCreatingFolder: Boolean = false,
    val newFolderError: String? = null,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val knownTags: List<Tag> = emptyList(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val urlLooksWrong: Boolean = false,
    val error: SaveError? = null,
    val errorDetail: String = "",
) {
    /** Known tags matching what is being typed, minus the ones already added. */
    val tagSuggestions: List<Tag>
        get() {
            val query = tagInput.trim()
            if (query.isEmpty()) return emptyList()
            return knownTags
                .filter { tag -> tag.name.contains(query, ignoreCase = true) }
                .filterNot { tag -> tags.any { it.equals(tag.name, ignoreCase = true) } }
                .take(MAX_SUGGESTIONS)
        }

    private companion object {
        const val MAX_SUGGESTIONS = 6
    }
}

enum class SaveError { NOT_CONFIGURED, UNREACHABLE, SIGN_IN_REQUIRED, TOKEN_REJECTED, FAILED }
