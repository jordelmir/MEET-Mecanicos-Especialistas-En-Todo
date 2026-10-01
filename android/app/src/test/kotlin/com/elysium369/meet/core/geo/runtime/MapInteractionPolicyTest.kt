package com.elysium369.meet.core.geo.runtime

import org.junit.Assert.*
import org.junit.Test

class MapInteractionPolicyTest {
    @Test fun `incoming projections wait through touch and native inertia`() {
        val policy = MapInteractionPolicy()
        policy.rendered("old")
        policy.touchStarted()
        policy.cameraGestureStarted()
        assertFalse(policy.shouldRender("new"))
        policy.touchEnded()
        assertFalse(policy.shouldRender("new"))
        policy.cameraIdle()
        assertTrue(policy.shouldRender("new"))
        policy.rendered("new")
        assertFalse(policy.shouldRender("new"))
        assertTrue(policy.userOwnsCamera)
    }
    @Test fun `tap and cancellation never leave rendering locked`() {
        val policy = MapInteractionPolicy()
        policy.touchStarted(); policy.touchEnded()
        assertTrue(policy.shouldRender("new"))
        assertFalse(policy.userOwnsCamera)
        policy.touchStarted(); policy.cameraGestureStarted(); policy.cancelTouch()
        assertTrue(policy.shouldRender("new"))
    }
    @Test fun `recenter does not permanently override later gestures`() {
        val policy = MapInteractionPolicy()
        policy.touchStarted(); policy.touchEnded(); policy.recenter()
        assertFalse(policy.userOwnsCamera)
        policy.cameraGestureStarted(); policy.cameraIdle()
        assertTrue(policy.userOwnsCamera)
    }
    @Test fun `style reload forces one reconciliation even with identical data`() {
        val policy = MapInteractionPolicy()
        policy.rendered("same"); assertFalse(policy.shouldRender("same"))
        policy.invalidate(); assertTrue(policy.shouldRender("same"))
    }
}
