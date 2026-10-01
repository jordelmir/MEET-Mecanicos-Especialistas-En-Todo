package com.elysium369.meet.ui.screens.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourierPackagePolicyTest {
    @Test fun motorcycleOnlyAcceptsSmallParcels() {
        assertTrue(CourierPackagePolicy.valid("MOTORCYCLE", 10.0, 45.0, 35.0, 35.0))
        assertFalse(CourierPackagePolicy.valid("MOTORCYCLE", 10.1, 45.0, 35.0, 35.0))
        assertFalse(CourierPackagePolicy.valid("MOTORCYCLE", 5.0, 46.0, 35.0, 35.0))
        assertFalse(CourierPackagePolicy.valid("MOTORCYCLE", null, 30.0, 20.0, 10.0))
    }

    @Test fun carStillUsesBoundedCourierCategory() {
        assertTrue(CourierPackagePolicy.valid("CAR", 20.0, 80.0, 60.0, 60.0))
        assertFalse(CourierPackagePolicy.valid("CAR", 21.0, 80.0, 60.0, 60.0))
        assertFalse(CourierPackagePolicy.valid("TRUCK", 5.0, 30.0, 20.0, 10.0))
    }
}
