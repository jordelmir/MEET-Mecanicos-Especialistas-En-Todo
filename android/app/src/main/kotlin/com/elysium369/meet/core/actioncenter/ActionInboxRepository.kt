package com.elysium369.meet.core.actioncenter

import com.elysium369.meet.ui.screens.home.adaptive.HomeAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ActionState {
    ACTIVE,
    SNOOZED,
    COMPLETED,
    SUPERSEDED
}

data class ActionInboxEntry(
    val action: HomeAction,
    val state: ActionState = ActionState.ACTIVE,
    val createdAtUtc: Long = System.currentTimeMillis(),
    val resolvedAtUtc: Long? = null,
    val snoozedUntilUtc: Long? = null
)

interface ActionInboxRepository {
    val activeActions: StateFlow<List<ActionInboxEntry>>
    suspend fun updateActions(actions: List<HomeAction>)
    suspend fun markCompleted(actionId: String)
    suspend fun snoozeAction(actionId: String, durationMs: Long)
}

@Singleton
class DefaultActionInboxRepository @Inject constructor() : ActionInboxRepository {
    private val _actions = MutableStateFlow<List<ActionInboxEntry>>(emptyList())
    override val activeActions: StateFlow<List<ActionInboxEntry>> = _actions.asStateFlow()

    override suspend fun updateActions(actions: List<HomeAction>) {
        val now = System.currentTimeMillis()
        val current = _actions.value.associateBy { it.action.id }
        val updated = actions.map { newAction ->
            val existing = current[newAction.id]
            if (existing != null) {
                if (existing.state == ActionState.SNOOZED && existing.snoozedUntilUtc != null && now >= existing.snoozedUntilUtc) {
                    // Snooze expired: reactivate action
                    existing.copy(action = newAction, state = ActionState.ACTIVE, snoozedUntilUtc = null)
                } else if (existing.state != ActionState.ACTIVE) {
                    existing.copy(action = newAction)
                } else {
                    existing.copy(action = newAction)
                }
            } else {
                ActionInboxEntry(action = newAction, state = ActionState.ACTIVE)
            }
        }
        _actions.value = updated
    }

    override suspend fun markCompleted(actionId: String) {
        _actions.value = _actions.value.map {
            if (it.action.id == actionId) it.copy(state = ActionState.COMPLETED, resolvedAtUtc = System.currentTimeMillis())
            else it
        }
    }

    override suspend fun snoozeAction(actionId: String, durationMs: Long) {
        val now = System.currentTimeMillis()
        val until = now + durationMs.coerceAtLeast(60_000L)
        _actions.value = _actions.value.map {
            if (it.action.id == actionId) it.copy(state = ActionState.SNOOZED, snoozedUntilUtc = until)
            else it
        }
    }
}
