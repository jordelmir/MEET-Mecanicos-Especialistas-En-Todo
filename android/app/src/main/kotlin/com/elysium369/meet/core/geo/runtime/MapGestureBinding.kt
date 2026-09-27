package com.elysium369.meet.core.geo.runtime

import android.annotation.SuppressLint
import android.view.MotionEvent
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

@SuppressLint("ClickableViewAccessibility")
fun bindSmoothMapGestures(view: MapView, map: MapLibreMap, policy: MapInteractionPolicy, onInteractionChanged: () -> Unit) {
    view.setOnTouchListener { target, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                policy.touchStarted()
                target.parent?.requestDisallowInterceptTouchEvent(true)
                onInteractionChanged()
            }
            MotionEvent.ACTION_MOVE -> target.parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP -> {
                policy.touchEnded()
                target.parent?.requestDisallowInterceptTouchEvent(false)
                target.performClick()
                onInteractionChanged()
            }
            MotionEvent.ACTION_CANCEL -> {
                policy.cancelTouch()
                target.parent?.requestDisallowInterceptTouchEvent(false)
                onInteractionChanged()
            }
        }
        false
    }
    map.addOnCameraMoveStartedListener { reason ->
        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
            policy.cameraGestureStarted()
            onInteractionChanged()
        }
    }
    map.addOnCameraIdleListener { policy.cameraIdle(); onInteractionChanged() }
}
