package com.elysium369.meet.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcedureSafetyEngineTest {
    private val engine = ProcedureSafetyEngine()

    @Test
    fun `pretensioner resistance measurement is blocked`() {
        val decision = engine.evaluate(
            ProcedureSafetyRequest(
                action = SafetyActionType.MEASURE_RESISTANCE,
                target = SafetyTarget.PRETENSIONER,
                origin = ActionOrigin.TECHNICIAN
            )
        )

        assertEquals(SafetyDecisionStatus.BLOCKED, decision.status)
        assertEquals("SRS_DIRECT_TEST_FORBIDDEN", decision.ruleId)
    }

    @Test
    fun `welding ISOFIX anchor is blocked`() {
        val decision = engine.evaluate(
            ProcedureSafetyRequest(
                action = SafetyActionType.WELD,
                target = SafetyTarget.ISOFIX_ANCHOR,
                origin = ActionOrigin.TECHNICIAN
            )
        )

        assertEquals(SafetyDecisionStatus.BLOCKED, decision.status)
    }

    @Test
    fun `used seat requires complete SRS geometry and history evidence`() {
        val decision = engine.evaluate(
            ProcedureSafetyRequest(
                action = SafetyActionType.INSTALL_USED_COMPONENT,
                target = SafetyTarget.USED_SEAT,
                origin = ActionOrigin.TECHNICIAN,
                evidenceIds = setOf("oem_part_number_match", "geometry_match")
            )
        )

        assertEquals(SafetyDecisionStatus.REQUIRES_EVIDENCE, decision.status)
        assertTrue(decision.missingEvidence.contains("srs_connector_match"))
        assertTrue(decision.missingEvidence.contains("collision_history_checked"))
        assertTrue(decision.missingEvidence.contains("flood_history_checked"))
    }

    @Test
    fun `joining radio ACC and battery feed is blocked`() {
        val decision = engine.evaluate(
            ProcedureSafetyRequest(
                action = SafetyActionType.JOIN_POWER_FEEDS,
                target = SafetyTarget.RADIO_ACC_B_PLUS,
                origin = ActionOrigin.TECHNICIAN
            )
        )

        assertEquals(SafetyDecisionStatus.BLOCKED, decision.status)
        assertEquals("ACC_B_PLUS_JOIN_FORBIDDEN", decision.ruleId)
    }

    @Test
    fun `AI cannot authorize remote CAN write`() {
        val decision = engine.evaluate(
            ProcedureSafetyRequest(
                action = SafetyActionType.REMOTE_VEHICLE_WRITE,
                target = SafetyTarget.CAN_UDS,
                origin = ActionOrigin.AI_GENERATED
            )
        )

        assertEquals(SafetyDecisionStatus.BLOCKED, decision.status)
        assertEquals("AI_REMOTE_WRITE_FORBIDDEN", decision.ruleId)
    }

    @Test
    fun `direct CAN write requires authorization from active test engine`() {
        val decision = engine.evaluate(
            ProcedureSafetyRequest(
                action = SafetyActionType.REMOTE_VEHICLE_WRITE,
                target = SafetyTarget.CAN_UDS,
                origin = ActionOrigin.TECHNICIAN
            )
        )

        assertEquals(SafetyDecisionStatus.REQUIRES_EVIDENCE, decision.status)
        assertTrue(decision.missingEvidence.contains("active_test_authorization_engine_decision"))
    }

    @Test
    fun `caller supplied authorization label cannot bypass active test engine`() {
        val decision = engine.evaluate(
            ProcedureSafetyRequest(
                action = SafetyActionType.REMOTE_VEHICLE_WRITE,
                target = SafetyTarget.CAN_UDS,
                origin = ActionOrigin.TECHNICIAN,
                evidenceIds = setOf("active_test_authorization")
            )
        )

        assertEquals(SafetyDecisionStatus.REQUIRES_EVIDENCE, decision.status)
        assertEquals("ACTIVE_TEST_AUTHORIZATION_REQUIRED", decision.ruleId)
    }

    @Test
    fun `high voltage service requires OEM isolation and absence of voltage evidence`() {
        val decision = engine.evaluate(
            ProcedureSafetyRequest(
                action = SafetyActionType.HIGH_VOLTAGE_SERVICE,
                target = SafetyTarget.HIGH_VOLTAGE_SYSTEM,
                origin = ActionOrigin.TECHNICIAN,
                evidenceIds = setOf("ppe_confirmed")
            )
        )

        assertEquals(SafetyDecisionStatus.REQUIRES_EVIDENCE, decision.status)
        assertTrue(decision.missingEvidence.contains("oem_deenergization_completed"))
        assertTrue(decision.missingEvidence.contains("absence_of_voltage_confirmed"))
    }

    @Test
    fun `active test needs allowlist stable link voltage and confirmation`() {
        val authorization = ActiveTestAuthorizationEngine().authorize(
            ActiveTestRequest(
                commandId = "fuel_pump_prime",
                origin = ActionOrigin.TECHNICIAN,
                allowlistedCommandIds = setOf("fuel_pump_prime"),
                connectionStable = true,
                measuredSystemVoltage = 12.4,
                minimumValidatedVoltage = 12.0,
                explicitUserConfirmation = true
            )
        )

        assertEquals(SafetyDecisionStatus.ALLOWED, authorization.status)
    }

    @Test
    fun `AI origin is blocked even for an allowlisted active test`() {
        val authorization = ActiveTestAuthorizationEngine().authorize(
            ActiveTestRequest(
                commandId = "fuel_pump_prime",
                origin = ActionOrigin.AI_GENERATED,
                allowlistedCommandIds = setOf("fuel_pump_prime"),
                connectionStable = true,
                measuredSystemVoltage = 12.4,
                minimumValidatedVoltage = 12.0,
                explicitUserConfirmation = true
            )
        )

        assertEquals(SafetyDecisionStatus.BLOCKED, authorization.status)
        assertEquals("AI_ACTIVE_TEST_AUTHORIZATION_FORBIDDEN", authorization.ruleId)
    }

    @Test
    fun `active test rejects non finite measured voltage`() {
        val authorization = ActiveTestAuthorizationEngine().authorize(
            ActiveTestRequest(
                commandId = "fuel_pump_prime",
                origin = ActionOrigin.TECHNICIAN,
                allowlistedCommandIds = setOf("fuel_pump_prime"),
                connectionStable = true,
                measuredSystemVoltage = Double.NaN,
                minimumValidatedVoltage = 12.0,
                explicitUserConfirmation = true
            )
        )

        assertEquals(SafetyDecisionStatus.BLOCKED, authorization.status)
        assertEquals("ACTIVE_TEST_VOLTAGE_INVALID", authorization.ruleId)
    }
}
