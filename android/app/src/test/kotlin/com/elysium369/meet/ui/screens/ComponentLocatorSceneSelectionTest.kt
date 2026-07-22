package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.SceneType
import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentLocatorSceneSelectionTest {
    @Test
    fun `brakes and steering use diagnostic engine components`() {
        val brake = component("front_brake_caliper", ComponentCategory.ENGINE)
        val steering = component("steering_rack", ComponentCategory.ENGINE)
        val unrelatedEngine = component("spark_plug", ComponentCategory.ENGINE)
        val suspensionPilot = component("front_control_arm", ComponentCategory.SUSPENSION)
        val literalBrake = component("document-16-brake-booster", ComponentCategory.BRAKES)
        val literalWheel = component("document-16-front-wheel", ComponentCategory.BRAKES)

        val selected = componentsForScene(
            scene = SceneType.BRAKES_STEERING,
            engineComponents = listOf(brake, steering, unrelatedEngine),
            suspensionComponents = listOf(suspensionPilot),
            proprietaryComponents = emptyList(),
            proprietaryComponentsBySystem = mapOf(
                "brakes" to listOf(literalBrake),
                "wheels" to listOf(literalWheel)
            )
        )

        assertEquals(listOf(literalBrake, literalWheel, brake, steering), selected)
    }

    private fun component(id: String, category: ComponentCategory) = ComponentInfo(
        id = id,
        name = id,
        category = category,
        description = id
    )
}
