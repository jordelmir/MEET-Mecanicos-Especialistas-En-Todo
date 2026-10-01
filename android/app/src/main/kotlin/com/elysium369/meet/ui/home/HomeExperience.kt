package com.elysium369.meet.ui.home

/**
 * Three persisted visual experiences sharing the same product and domain state.
 */
enum class HomeExperience {
    CLASSIC,
    VANGUARD,
    ADAPTIVE,
    MAIKEL_BLANCO;

    val displayName: String
        get() = when (this) {
            CLASSIC -> "Elysium Vanguard AI OS Classic"
            VANGUARD -> "Elysium Vanguard AI OS Actual"
            ADAPTIVE -> "Elysium Vanguard AI OS Command"
            MAIKEL_BLANCO -> "Maikel Blanco"
        }

    val description: String
        get() = when (this) {
            CLASSIC -> "Estética anterior, fondo oscuro y todos los módulos visibles."
            VANGUARD -> "Identidad actual con imágenes de fondo a pantalla completa."
            ADAPTIVE -> "Elysium prioriza automáticamente lo que necesitas en tiempo real."
            MAIKEL_BLANCO -> "Tema claro con background blanco puro en todas las pantallas."
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
