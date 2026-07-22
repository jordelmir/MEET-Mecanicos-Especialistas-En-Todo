package com.elysium369.meet.visual3d.ui

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousOrbitCameraManipulatorTest {
    @Test
    fun `horizontal viewport drag completes one full orbit`() {
        val camera = ContinuousOrbitState()
        camera.setViewport(1_000, 600)
        val initial = camera.anglesForTest()

        camera.grabBegin(0, 300)
        camera.grabUpdate(1_000, 300)

        assertAngleEquals(initial.first, camera.anglesForTest().first)
        assertEquals(initial.second, camera.anglesForTest().second, 0.0001f)
    }

    @Test
    fun `vertical viewport drag completes one full orbit`() {
        val camera = ContinuousOrbitState()
        camera.setViewport(1_000, 600)
        val initial = camera.anglesForTest()

        camera.grabBegin(500, 0)
        camera.grabUpdate(500, 600)

        assertEquals(initial.first, camera.anglesForTest().first, 0.0001f)
        assertAngleEquals(initial.second, camera.anglesForTest().second)
    }

    @Test
    fun `camera transform remains finite while crossing both poles`() {
        val camera = ContinuousOrbitState(initialPitchRadians = 0f)
        camera.setViewport(1_000, 1_000)
        camera.grabBegin(500, 500)

        listOf(250, 500, 750, 1_000, 1_250).forEach { y ->
            camera.grabUpdate(500, y)
            assertTrue(camera.pose().allFinite())
        }
    }

    @Test
    fun `pinch zoom is proportional and clamped`() {
        val camera = ContinuousOrbitState(
            initialRadius = 4f,
            minimumRadius = 2f,
            maximumRadius = 8f
        )

        camera.zoom(previousSeparation = 100f, currentSeparation = 200f)
        assertEquals(2f, camera.radiusForTest(), 0.0001f)
        camera.zoom(previousSeparation = 200f, currentSeparation = 10f)
        assertEquals(8f, camera.radiusForTest(), 0.0001f)
    }

    @Test
    fun `inspection zoom can enter the vehicle assembly`() {
        val camera = ContinuousOrbitState()

        camera.zoom(previousSeparation = 100f, currentSeparation = 2_000f)

        assertTrue(camera.radiusForTest() < 0.5f)
        assertTrue(camera.pose().allFinite())
    }

    private fun assertAngleEquals(expected: Float, actual: Float) {
        val fullTurn = (PI * 2.0).toFloat()
        val difference = ((actual - expected + PI.toFloat()) % fullTurn + fullTurn) % fullTurn - PI.toFloat()
        assertEquals(0f, difference, 0.0001f)
    }
}
