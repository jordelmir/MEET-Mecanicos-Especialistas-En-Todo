package com.elysium369.meet.ecu

import com.elysium369.meet.ecu.domain.ProgrammingLifecyclePolicy
import com.elysium369.meet.ecu.domain.ProgrammingState
import com.elysium369.meet.ecu.domain.ProgrammingTransitionDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammingSessionTest {

    @Test
    fun `happy path advances monotonically through all operational phases`() {
        var state = ProgrammingState.CREATED

        val sequence = listOf(
            ProgrammingState.IDENTIFYING,
            ProgrammingState.PREFLIGHT,
            ProgrammingState.BACKUP_REQUIRED,
            ProgrammingState.BACKING_UP,
            ProgrammingState.BACKUP_VERIFYING,
            ProgrammingState.ARTIFACT_VALIDATING,
            ProgrammingState.READY_FOR_AUTHORIZATION,
            ProgrammingState.AUTHORIZED,
            ProgrammingState.PREPARING_ECU,
            ProgrammingState.ERASE_STARTED,
            ProgrammingState.TRANSFERRING,
            ProgrammingState.TRANSFER_EXIT,
            ProgrammingState.VERIFYING_FLASH,
            ProgrammingState.RESETTING,
            ProgrammingState.RECONNECTING,
            ProgrammingState.READBACK_VERIFYING,
            ProgrammingState.POST_DIAGNOSTIC,
            ProgrammingState.COMPLETED,
        )

        sequence.forEach { next ->
            val decision = ProgrammingLifecyclePolicy.decide(state, next)
            assertTrue("Transition from $state to $next must be allowed", decision is ProgrammingTransitionDecision.Allowed)
            state = (decision as ProgrammingTransitionDecision.Allowed).nextState
        }

        assertEquals(ProgrammingState.COMPLETED, state)
        assertTrue(state.isTerminal)
    }

    @Test
    fun `cannot jump backwards or skip mandatory preflight and backup steps`() {
        val directToErase = ProgrammingLifecyclePolicy.decide(ProgrammingState.CREATED, ProgrammingState.ERASE_STARTED)
        assertTrue(directToErase is ProgrammingTransitionDecision.Denied)

        val skipBackup = ProgrammingLifecyclePolicy.decide(ProgrammingState.PREFLIGHT, ProgrammingState.AUTHORIZED)
        assertTrue(skipBackup is ProgrammingTransitionDecision.Denied)
    }

    @Test
    fun `cannot safely abort once irreversible erase or transfer phase has begun`() {
        val abortFromPreflight = ProgrammingLifecyclePolicy.decide(ProgrammingState.PREFLIGHT, ProgrammingState.ABORT_REQUESTED)
        assertTrue("Abort in reversible preflight phase must be allowed", abortFromPreflight is ProgrammingTransitionDecision.Allowed)

        val abortDuringErase = ProgrammingLifecyclePolicy.decide(ProgrammingState.ERASE_STARTED, ProgrammingState.ABORT_REQUESTED)
        assertTrue("Abort during ERASE_STARTED must be denied", abortDuringErase is ProgrammingTransitionDecision.Denied)

        val abortDuringTransfer = ProgrammingLifecyclePolicy.decide(ProgrammingState.TRANSFERRING, ProgrammingState.ABORT_REQUESTED)
        assertTrue("Abort during TRANSFERRING must be denied", abortDuringTransfer is ProgrammingTransitionDecision.Denied)
    }

    @Test
    fun `unrecoverable failure transitions to RECOVERY_REQUIRED from any state`() {
        val recoveryFromTransfer = ProgrammingLifecyclePolicy.decide(ProgrammingState.TRANSFERRING, ProgrammingState.RECOVERY_REQUIRED)
        assertTrue(recoveryFromTransfer is ProgrammingTransitionDecision.Allowed)

        val recoveryFromReset = ProgrammingLifecyclePolicy.decide(ProgrammingState.RESETTING, ProgrammingState.RECOVERY_REQUIRED)
        assertTrue(recoveryFromReset is ProgrammingTransitionDecision.Allowed)
    }

    @Test
    fun `terminal state rejects all further transitions`() {
        val fromCompleted = ProgrammingLifecyclePolicy.decide(ProgrammingState.COMPLETED, ProgrammingState.IDENTIFYING)
        assertTrue(fromCompleted is ProgrammingTransitionDecision.Denied)

        val fromSafeAborted = ProgrammingLifecyclePolicy.decide(ProgrammingState.SAFE_ABORTED, ProgrammingState.PREFLIGHT)
        assertTrue(fromSafeAborted is ProgrammingTransitionDecision.Denied)
    }
}
