package com.elysium369.meet.core.agent.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * ══════════════════════════════════════════════════════════════════════
 *  M O D I F I E R . A G E N T   A C T I O N
 *  ──────────────────────────────────────────────────────────────
 *  Registra cualquier elemento interactivo (botón, tarjeta, icono, tab)
 *  en el plano de control semántico de EVAIR.
 *  Permite que el usuario diga "Selecciona <nombre>" o "Sección <nombre>"
 *  para que el agente lo localice, se teletransporte a él y lo active
 *  utilizando el callback REAL.
 * ══════════════════════════════════════════════════════════════════════
 */
fun Modifier.agentAction(
    id: AgentUiControlId,
    label: String,
    aliases: Set<String> = emptySet(),
    route: String = "",
    section: String? = null,
    kind: AgentUiControlKind = AgentUiControlKind.BUTTON,
    enabled: Boolean = true,
    sensitivity: AgentUiSensitivity = AgentUiSensitivity.NORMAL,
    registry: AgentUiRegistry = AgentUiRegistry.default,
    onActivate: () -> Unit,
): Modifier = composed {
    val latestActivate by rememberUpdatedState(onActivate)
    val activation = remember { { latestActivate() } }

    DisposableEffect(id, label, enabled, aliases, kind, route, section, sensitivity) {
        registry.register(
            RegisteredAgentUiControl(
                snapshot = AgentUiControlSnapshot(
                    id = id,
                    label = label,
                    aliases = aliases,
                    kind = kind,
                    route = route,
                    sectionLabel = section,
                    bounds = Rect.Zero,
                    enabled = enabled,
                    visible = false,
                    sensitivity = sensitivity,
                    generation = 0L,
                ),
                activate = activation,
                readText = null,
                writeText = null,
                commitText = null,
            )
        )
        onDispose {
            registry.unregister(id)
        }
    }

    onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            val bounds = coordinates.boundsInRoot()
            registry.updateBounds(
                id = id,
                bounds = bounds,
                visible = enabled && bounds.width > 0f && bounds.height > 0f,
            )
        } else {
            registry.unregister(id)
        }
    }
}

/**
 * Registra un campo de entrada de texto para dictado inteligente guiado por EVAIR y Laya.
 */
fun Modifier.agentTextInput(
    id: AgentUiControlId,
    label: String,
    role: AgentTextFieldRole = AgentTextFieldRole.GENERIC,
    route: String = "",
    readValue: () -> String,
    writeValue: (String) -> Unit,
    commit: (() -> Unit)? = null,
    sensitivity: AgentUiSensitivity = AgentUiSensitivity.NORMAL,
    enabled: Boolean = true,
    registry: AgentUiRegistry = AgentUiRegistry.default,
): Modifier = composed {
    val latestRead by rememberUpdatedState(readValue)
    val focusRequester = remember { FocusRequester() }
    val latestWrite by rememberUpdatedState(writeValue)
    val latestCommit by rememberUpdatedState(commit)

    DisposableEffect(id, label, enabled, role, route, sensitivity, latestCommit != null) {
        registry.register(
            RegisteredAgentUiControl(
                snapshot = AgentUiControlSnapshot(
                    id = id,
                    label = label,
                    aliases = emptySet(),
                    kind = if (role == AgentTextFieldRole.SEARCH) AgentUiControlKind.SEARCH_FIELD else AgentUiControlKind.TEXT_FIELD,
                    route = route,
                    sectionLabel = null,
                    bounds = Rect.Zero,
                    enabled = enabled,
                    visible = false,
                    sensitivity = sensitivity,
                    role = role,
                    generation = 0L,
                ),
                activate = { focusRequester.requestFocus(); Unit },
                readText = { latestRead() },
                writeText = { latestWrite(it) },
                commitText = if (latestCommit != null) { { latestCommit?.invoke() } } else null,
            )
        )
        onDispose {
            registry.unregister(id)
        }
    }

    Modifier.focusRequester(focusRequester).onFocusChanged { registry.updateFocus(id, it.isFocused) }.onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            val bounds = coordinates.boundsInRoot()
            registry.updateBounds(
                id = id,
                bounds = bounds,
                visible = enabled && bounds.width > 0f && bounds.height > 0f,
            )
        } else {
            registry.unregister(id)
        }
    }
}

/**
 * Marca explícitamente un control como excluido del plano semántico de EVAIR
 * (para controles decorativos o secundarios, garantizando 100% de cobertura auditada).
 */
fun Modifier.agentExcluded(reason: String): Modifier = this
