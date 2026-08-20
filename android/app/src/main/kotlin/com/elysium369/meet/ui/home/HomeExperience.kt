package com.elysium369.meet.ui.home

/**
 * MEET Dual Home Experience Architecture (DHEA)
 * Defines the two official coexistence home experiences.
 */
enum class HomeExperience {
    CLASSIC,
    ADAPTIVE;

    val displayName: String
        get() = when (this) {
            CLASSIC -> "Vanguard Classic"
            ADAPTIVE -> "Vanguard Command"
        }

    val description: String
        get() = when (this) {
            CLASSIC -> "Todos tus módulos y herramientas siempre visibles."
            ADAPTIVE -> "MEET prioriza automáticamente lo que necesitas en tiempo real."
        }
}

sealed interface HomeExperienceUiState {
    data object Loading : HomeExperienceUiState
    data class Ready(
        val selected: HomeExperience,
        val isPreview: Boolean = false,
        val persistedExperience: HomeExperience = selected
    ) : HomeExperienceUiState
}
