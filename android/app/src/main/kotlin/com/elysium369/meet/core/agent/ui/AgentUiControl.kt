package com.elysium369.meet.core.agent.ui

import androidx.compose.ui.geometry.Rect

/**
 * Categoría estructural del control interactivo en la interfaz.
 */
enum class AgentUiControlKind {
    BUTTON,
    ICON_BUTTON,
    NAVIGATION,
    TAB,
    CARD,
    SWITCH,
    CHECKBOX,
    RADIO,
    DROPDOWN,
    TEXT_FIELD,
    SEARCH_FIELD,
}

/**
 * Nivel de sensibilidad de datos del control.
 * Los campos SECRET tienen autofill por voz estrictamente DENEGADO.
 */
enum class AgentUiSensitivity {
    NORMAL,
    PERSONAL,
    FINANCIAL,
    SECRET,
}

/**
 * Rol semántico del campo de texto para dictado inteligente guiado por Laya.
 */
enum class AgentTextFieldRole {
    GENERIC,
    SEARCH,
    PICKUP_ADDRESS,
    DESTINATION_ADDRESS,
    MESSAGE,
    NAME,
    PHONE,
    EMAIL,
    NOTES,
    SECRET,
}

/**
 * Snapshot inmutable y seguro para el runtime de EVAIR y Laya.
 * NUNCA contiene lambdas ni callbacks de ejecución; solo metadatos y geometría.
 */
data class AgentUiControlSnapshot(
    val id: AgentUiControlId,
    val label: String,
    val aliases: Set<String> = emptySet(),
    val kind: AgentUiControlKind = AgentUiControlKind.BUTTON,
    val route: String = "",
    val sectionLabel: String? = null,
    val bounds: Rect = Rect.Zero,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val sensitivity: AgentUiSensitivity = AgentUiSensitivity.NORMAL,
    val role: AgentTextFieldRole? = null,
    val generation: Long = 0L,
    val focused: Boolean = false,
) {
    val centerX: Float get() = bounds.left + bounds.width / 2f
    val centerY: Float get() = bounds.top + bounds.height / 2f
    val isActionable: Boolean get() = visible && enabled && bounds.width > 0f && bounds.height > 0f
}

/**
 * Registro interno que asocia la identidad semántica y los bounds reales
 * con el callback de activación real o manipuladores de texto.
 * NUNCA se expone fuera del proceso UI.
 */
internal data class RegisteredAgentUiControl(
    val snapshot: AgentUiControlSnapshot,
    val activate: (() -> Unit)? = null,
    val readText: (() -> String)? = null,
    val writeText: ((String) -> Unit)? = null,
    val commitText: (() -> Unit)? = null,
)
