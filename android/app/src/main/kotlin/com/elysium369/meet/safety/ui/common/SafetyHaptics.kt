package com.elysium369.meet.safety.ui.common

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Haptic feedback patterns for safety-critical actions.
 * Delegates to View.performHapticFeedback with graceful fallback on older APIs.
 */
object SafetyHaptics {

    /** Report successfully queued in outbox. Heavy confirmation. */
    fun reportSubmitted(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** Evidence file attached and hashed. Light tick. */
    fun evidenceAttached(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Wizard step completed successfully. */
    fun stepCompleted(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** SOS / emergency signal activated. Double heavy feedback. */
    fun sosActivated(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        view.postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }, 150L)
    }

    /** Validation or submission error. Rejection pattern. */
    fun error(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** Generic selection tick (filter chip, toggle, etc). */
    fun selectionTick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}
