package com.elysium369.meet.ui.home

/**
 * Elysium Dual Home Experience Architecture (DHEA)
 * Defines the two official coexistence home experiences.
 */
enum class HomeExperience {
    CLASSIC,
    ADAPTIVE;

    val displayName: String
        get() = when (this) {
            CLASSIC -> "Elysium Vanguard AI OS Classic"
            ADAPTIVE -> "Elysium Vanguard AI OS Command"
        }

    val description: String
        get() = when (this) {
            CLASSIC -> "Todos tus módulos y herramientas siempre visibles."
            ADAPTIVE -> "Elysium prioriza automáticamente lo que necesitas en tiempo real."
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
