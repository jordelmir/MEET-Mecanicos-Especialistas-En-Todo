package com.elysium369.meet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeExperienceViewModel @Inject constructor(
    private val repository: HomeExperienceRepository
) : ViewModel() {

    private val _previewExperience = MutableStateFlow<HomeExperience?>(null)

    val uiState: StateFlow<HomeExperienceUiState> = combine(
        repository.selectedExperience,
        _previewExperience
    ) { persisted, preview ->
        if (preview != null) {
            HomeExperienceUiState.Ready(
                selected = preview,
                isPreview = true,
                persistedExperience = persisted
            )
        } else {
            HomeExperienceUiState.Ready(
                selected = persisted,
                isPreview = false,
                persistedExperience = persisted
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeExperienceUiState.Ready(
            selected = repository.getExperience(),
            isPreview = false,
            persistedExperience = repository.getExperience()
        )
    )

    fun switchExperience(experience: HomeExperience) {
        _previewExperience.value = null
        repository.setExperience(experience)
    }

    fun toggleExperience() {
        val current = when (val state = uiState.value) {
            is HomeExperienceUiState.Ready -> state.selected
            else -> repository.getExperience()
        }
        val next = if (current == HomeExperience.CLASSIC) HomeExperience.ADAPTIVE else HomeExperience.CLASSIC
        switchExperience(next)
    }

    fun startPreview(experience: HomeExperience) {
        _previewExperience.value = experience
    }

    fun commitPreview() {
        val preview = _previewExperience.value
        if (preview != null) {
            repository.setExperience(preview)
            _previewExperience.value = null
        }
    }

    fun cancelPreview() {
        _previewExperience.value = null
    }
}
