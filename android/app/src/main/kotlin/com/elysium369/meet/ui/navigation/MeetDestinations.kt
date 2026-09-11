package com.elysium369.meet.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * MEET Centralized Destinations Contract.
 * Guarantees that both Vanguard Classic and Vanguard Command navigate to identical targets.
 */
object MeetDestinations {
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val DTCS = "dtc"
    const val GARAGE = "garage"
    const val SETTINGS = "settings"
    const val TERMINAL = "terminal"
    const val AI = "ai"
    const val HEALTH_SCORE = "health_score"
    const val MAINTENANCE = "maintenance"
    const val TRIP_LOG = "trips"
    const val ENGINE_3D = "component_locator"
    const val PERITO = "meet_perito"
    const val DNA = "meet_dna"
    const val VEHICLE_ACCESS = "vehicle_access"
    const val FINDINGS = "findings"
    const val HUD = "hud"
    const val DVIR = "dvir"
    const val COMPONENT_LOCATOR = "component_locator"
    const val PROTOCOL_LEARNING = "adaptation"
    const val ADAPTER_DIAGNOSTICS = "clone_test"
    const val LIVE_STREAM = "live_link"
    const val MESSAGES = "messages"
    const val MECHANIC_SERVICES = "repair_network"
    const val TOW_TRUCK = "tow_truck_service"
    const val PARTS_STORE = "marketplace"
    const val PRO_HUB = "pro_hub"
    const val TRUST_CENTER = "platform_trust_center"
    const val RIDE_HOME = "ride_service"
    /** Passenger and driver entry points converge on the wired Ride product. */
    const val RIDE_PASSENGER_REQUEST = RIDE_HOME
    const val RIDE_DRIVER_MODE = RIDE_HOME
    const val RIDE_DRIVER_REGISTRATION = "ride_driver_registration"
    const val RIDE_ACTIVE_TRACKING = "ride_active_tracking"
    const val RIDE_SCHEDULE = "ride_schedule"

    /** Unwired prototypes retained for strangler migration, never as Home entry points. */
    const val RIDE_PASSENGER_EXPERIMENT = "ride_passenger_request"
    const val RIDE_DRIVER_EXPERIMENT = "ride_driver_cockpit"
    const val CAMPAIGNS = "reports"
    const val BATTERY_HEALTH = "health_score"
    const val CONNECT = "connect"
    const val LEARNING_HUB = "learning_hub"
    const val ELYSIUM_LEARNING_OS = "elysium_learning_os"
    const val MISSION_DETAIL = "mission_detail"
    const val MULTIMETER_SIMULATION = "multimeter_simulation"
    const val CAPABILITY_PASSPORT = "capability_passport"
    const val LEGAL_VANGUARD = "legal_vanguard"
    const val PROPERTIES = "elysium_properties"
    const val FUEL_REWARDS = "fuel_rewards"
}

fun androidx.navigation.NavController.safeNavigate(route: String) {
    try {
        navigate(route)
    } catch (e: Exception) {
        android.util.Log.e("Navigation", "Failed to navigate to route: $route", e)
    }
}

/**
 * One navigation contract for every visible back affordance.
 *
 * A regular visit pops exactly one entry. A destination reached without a
 * usable parent (for example from a deep link) returns to Home instead of
 * becoming a dead end. Home itself is never duplicated.
 */
fun NavController.backOrHome(): Boolean {
    val currentRoute = currentDestination?.route
    val hasPrevious = previousBackStackEntry != null
    return when (
        MeetBackStackPolicy.action(
            currentRoute = currentRoute,
            hasPreviousEntry = hasPrevious,
        )
    ) {
        MeetBackStackPolicy.Action.POP_ONE -> {
            if (popBackStack()) true else navigateHomeFallback()
        }
        MeetBackStackPolicy.Action.NAVIGATE_HOME -> navigateHomeFallback()
        MeetBackStackPolicy.Action.STAY_HOME -> false
    }
}

/** Save and restore each top-level branch instead of recreating it on every tap. */
fun NavController.navigateTopLevel(route: String) {
    if (currentDestination?.route == route) return
    // Prefer returning to an existing top-level entry. This is reliable even
    // when the current screen was opened from a nested ride/profile flow.
    if (popBackStack(route, false)) return
    runCatching {
        navigate(route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }.onFailure { error ->
        android.util.Log.w("Navigation", "Top-level navigation fallback for $route", error)
        navigate(route) { launchSingleTop = true }
    }
}

private fun NavController.navigateHomeFallback(): Boolean {
    navigate(MeetDestinations.HOME) {
        popUpTo(graph.findStartDestination().id) { inclusive = false }
        launchSingleTop = true
        restoreState = true
    }
    return true
}

object MeetBackStackPolicy {
    enum class Action { POP_ONE, NAVIGATE_HOME, STAY_HOME }

    fun action(currentRoute: String?, hasPreviousEntry: Boolean): Action = when {
        currentRoute == MeetDestinations.HOME -> Action.STAY_HOME
        currentRoute == null -> Action.NAVIGATE_HOME
        hasPreviousEntry -> Action.POP_ONE
        else -> Action.NAVIGATE_HOME
    }
}
