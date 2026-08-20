package com.elysium369.meet.vehiclelife

import com.elysium369.meet.vehiclelife.battery.BatteryEnergyManager
import com.elysium369.meet.vehiclelife.battery.PowerCondition
import org.junit.Assert.*
import org.junit.Test

class BatteryEnergyManagerTest {

    @Test
    fun testRestingVoltageEvaluations() {
        val good = BatteryEnergyManager.evaluate12vIceBattery(12.6f, isEngineRunning = false)
        assertEquals(PowerCondition.EXCELLENT, good.condition)

        val discharged = BatteryEnergyManager.evaluate12vIceBattery(12.2f, isEngineRunning = false)
        assertEquals(PowerCondition.DISCHARGED, discharged.condition)

        val critical = BatteryEnergyManager.evaluate12vIceBattery(11.8f, isEngineRunning = false)
        assertEquals(PowerCondition.CRITICAL_FAILURE, critical.condition)
    }

    @Test
    fun testChargingVoltageEvaluations() {
        val chargingGood = BatteryEnergyManager.evaluate12vIceBattery(14.2f, isEngineRunning = true)
        assertEquals(PowerCondition.EXCELLENT, chargingGood.condition)
        assertTrue(chargingGood.isChargingNormal == true)

        val weakAlternator = BatteryEnergyManager.evaluate12vIceBattery(12.8f, isEngineRunning = true)
        assertEquals(PowerCondition.WEAK_CHARGE, weakAlternator.condition)
        assertTrue(weakAlternator.isChargingNormal == false)
    }
}
