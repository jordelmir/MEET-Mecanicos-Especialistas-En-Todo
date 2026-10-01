package com.elysium.vanguard.features.filemanager

sealed class FileManagerUiState {
    object Loading : FileManagerUiState()
    data class Success(val files: List<TitanFile>) : FileManagerUiState()
    object Empty : FileManagerUiState()
    object PermissionRequired : FileManagerUiState()
    data class Error(val message: String) : FileManagerUiState()
}
