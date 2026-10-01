package com.elysium369.meet.truth

import androidx.compose.ui.geometry.Rect
import com.elysium369.meet.core.agent.ui.AgentTextFieldRole
import com.elysium369.meet.core.agent.ui.AgentUiControlId
import com.elysium369.meet.core.agent.ui.AgentUiControlKind
import com.elysium369.meet.core.agent.ui.AgentUiControlSnapshot
import com.elysium369.meet.core.agent.ui.AgentUiCoverageReleaseGate
import com.elysium369.meet.core.agent.ui.AgentUiSensitivity
import com.elysium369.meet.core.agent.ui.ViolationSeverity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentUiCoverageReleaseGateTest {

    private val releaseGate = AgentUiCoverageReleaseGate(minimumCoverageRatio = 0.90)

    @Test
    fun releaseGate_passes_whenAllControlsAreCompliantAndSecretsShielded() {
        val controls = listOf(
            AgentUiControlSnapshot(
                id = AgentUiControlId.NAV_HOME,
                label = "Inicio",
                route = "home",
                kind = AgentUiControlKind.TAB,
                bounds = Rect(0f, 0f, 100f, 50f),
            ),
            AgentUiControlSnapshot(
                id = AgentUiControlId.RIDE_PICKUP_FIELD,
                label = "Punto de partida",
                route = "ride_service",
                kind = AgentUiControlKind.TEXT_FIELD,
                role = AgentTextFieldRole.PICKUP_ADDRESS,
                bounds = Rect(0f, 50f, 300f, 100f),
            ),
            AgentUiControlSnapshot(
                id = AgentUiControlId("ride.boarding_pin"),
                label = "PIN de abordaje seguro",
                route = "ride_service",
                kind = AgentUiControlKind.TEXT_FIELD,
                role = AgentTextFieldRole.SECRET,
                sensitivity = AgentUiSensitivity.SECRET,
                bounds = Rect(0f, 100f, 200f, 150f),
            ),
        )

        val report = releaseGate.audit(controls)
        assertTrue("Report must pass when all controls comply", report.passed)
        assertTrue("Coverage ratio must be 1.0", report.coverageRatio >= 0.90)
        assertFalse("Must have zero critical violations", report.violations.any { it.severity == ViolationSeverity.CRITICAL })
    }

    @Test
    fun releaseGate_fails_whenSecretPinIsNotMarkedSecret() {
        val unshieldedPinControl = AgentUiControlSnapshot(
            id = AgentUiControlId("ride.unshielded_pin"),
            label = "PIN de abordaje",
            route = "ride_service",
            kind = AgentUiControlKind.TEXT_FIELD,
            role = AgentTextFieldRole.GENERIC,
            sensitivity = AgentUiSensitivity.NORMAL, // Critical security violation!
            bounds = Rect(0f, 100f, 200f, 150f),
        )

        val report = releaseGate.audit(listOf(unshieldedPinControl))
        assertFalse("Report must fail when PIN is not marked SECRET", report.passed)
        assertTrue("Must have critical violation for unshielded secret",
            report.violations.any { it.rule == "SECRET_SENSITIVITY_REQUIRED" && it.severity == ViolationSeverity.CRITICAL }
        )
    }

    @Test
    fun releaseGate_fails_whenControlHasBlankLabel() {
        val blankLabelControl = AgentUiControlSnapshot(
            id = AgentUiControlId("btn.unnamed"),
            label = "", // Blank label violation!
            route = "home",
            kind = AgentUiControlKind.BUTTON,
            bounds = Rect(0f, 0f, 100f, 50f),
        )

        val report = releaseGate.audit(listOf(blankLabelControl))
        assertFalse("Report must fail when control has blank label", report.passed)
        assertTrue("Must have critical violation for blank label",
            report.violations.any { it.rule == "NON_BLANK_LABEL" && it.severity == ViolationSeverity.CRITICAL }
        )
    }
}
