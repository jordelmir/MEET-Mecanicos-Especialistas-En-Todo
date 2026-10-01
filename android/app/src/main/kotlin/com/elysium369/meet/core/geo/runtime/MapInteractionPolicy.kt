package com.elysium369.meet.core.geo.runtime

/** Keeps native gestures and inertia independent of incoming projections. */
class MapInteractionPolicy {
    private var touching = false
    private var cameraGesture = false
    var userOwnsCamera = false
        private set
    private var renderedKey: Any? = null
    val interacting: Boolean get() = touching || cameraGesture
    fun touchStarted() { touching = true }
    fun touchEnded() { touching = false }
    fun cameraGestureStarted() { cameraGesture = true; userOwnsCamera = true }
    fun cameraIdle() { cameraGesture = false }
    fun cancelTouch() { touching = false; cameraGesture = false }
    fun recenter() { userOwnsCamera = false }
    fun shouldRender(key: Any): Boolean = !interacting && renderedKey != key
    fun rendered(key: Any) { renderedKey = key }
    fun invalidate() { renderedKey = null }
}
