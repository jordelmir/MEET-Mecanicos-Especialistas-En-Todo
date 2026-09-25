package com.elysium369.meet.core.agent.anchor

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Attaches a semantic anchor to any Compose element.
 *
 * This calculates coordinates purely for presentation and living guidance (EVAIR pointing
 * and highlighting buttons to teach users). It NEVER executes synthetic tap injections.
 */
fun Modifier.agentAnchor(
    anchorId: AgentAnchorId,
    registry: AgentAnchorRegistry = AgentAnchorRegistry.default,
): Modifier = composed {
    DisposableEffect(anchorId) {
        onDispose {
            registry.unregisterAnchor(anchorId)
        }
    }

    onGloballyPositioned { layoutCoordinates ->
        if (layoutCoordinates.isAttached) {
            val bounds = layoutCoordinates.boundsInRoot()
            registry.registerAnchor(anchorId, bounds, isVisible = bounds.width > 0f && bounds.height > 0f)
        } else {
            registry.unregisterAnchor(anchorId)
        }
    }
}
