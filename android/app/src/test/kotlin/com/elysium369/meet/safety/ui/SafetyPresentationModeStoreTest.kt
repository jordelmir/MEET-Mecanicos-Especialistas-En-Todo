package com.elysium369.meet.safety.ui

import org.junit.Assert.*
import org.junit.Test

class SafetyPresentationModeStoreTest {

    @Test
    fun defaultModeIsInstitutionalDeputiesSafety() {
        val store = SafetyPresentationModeStore(context = null)
        assertEquals(PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY, store.getMode())
        assertTrue(store.isInstitutionalMode())
    }

    @Test
    fun toggleModeSwitchesBetweenInstitutionalAndFullOs() {
        val store = SafetyPresentationModeStore(context = null)
        assertEquals(PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY, store.getMode())

        val switched = store.toggleMode()
        assertEquals(PresentationMode.FULL_PLATFORM_OPERATING_SYSTEM, switched)
        assertFalse(store.isInstitutionalMode())

        val switchedBack = store.toggleMode()
        assertEquals(PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY, switchedBack)
        assertTrue(store.isInstitutionalMode())
    }

    @Test
    fun setModeDirectlyUpdatesState() {
        val store = SafetyPresentationModeStore(context = null)
        store.setMode(PresentationMode.FULL_PLATFORM_OPERATING_SYSTEM)
        assertEquals(PresentationMode.FULL_PLATFORM_OPERATING_SYSTEM, store.getMode())

        store.setMode(PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY)
        assertEquals(PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY, store.getMode())
    }

    @Test
    fun isolatedDeputiesModeHidesAllAutomotiveAndMobilityModules() {
        val mode = PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY
        val allowed = SafetyInstitutionalPresentationMode.resolveAllowedModules(mode)

        assertEquals(1, allowed.size)
        assertTrue(allowed.contains(PlatformModuleDestination.SAFETY_CITIZEN_CORE))
        assertFalse(allowed.contains(PlatformModuleDestination.AUTOMOTIVE_OBD_DIAGNOSTICS))
        assertFalse(allowed.contains(PlatformModuleDestination.MOBILITY_DISPATCH))
        assertFalse(allowed.contains(PlatformModuleDestination.PARTS_MARKETPLACE))
        assertFalse(allowed.contains(PlatformModuleDestination.VEHICLE_HISTORY))
    }
}
