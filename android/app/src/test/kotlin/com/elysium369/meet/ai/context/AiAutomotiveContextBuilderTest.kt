package com.elysium369.meet.ai.context

import com.elysium369.meet.ai.domain.AiContext
import com.elysium369.meet.ai.domain.UserRole
import com.elysium369.meet.ai.domain.VehicleContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAutomotiveContextBuilderTest {
    @Test
    fun `remote prompt never contains the complete VIN`() {
        val vin = "KMHCG45C51U123456"
        val prompt = AiAutomotiveContextBuilder.buildContextPrompt(
            AiContext(
                vehicle = VehicleContext(
                    make = "Hyundai",
                    model = "Accent",
                    year = 2005,
                    engine = "1.6",
                    vin = vin
                ),
                obd = null,
                dtcs = emptyList(),
                livePids = emptyList(),
                manualAvailability = null,
                appModule = "diagnostics",
                locale = "es-CR",
                userRole = UserRole.MECHANIC
            )
        )

        assertFalse(prompt.contains(vin))
        assertTrue(prompt.contains("VIN presente: sí"))
        assertTrue(prompt.contains("no se envía"))
    }
}
