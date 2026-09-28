package com.elysium.nexus.fabric.tv.adb

/**
 * Android keycodes used to drive an Android TV /
 * Google TV / Fire TV over ADB `shell input keyevent`.
 */
object AndroidTvKeyCodes {
    const val POWER = 26
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val MUTE = 164
    const val HOME = 3
    const val BACK = 4
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val ENTER = 66
    const val MENU = 82
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_STOP = 86
    const val MEDIA_NEXT = 87
    const val MEDIA_PREVIOUS = 88
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val SETTINGS = 176
    const val SLEEP = 223

    /**
     * Map a canonical IR-style action name to a keyevent.
     * Returns null when the action has no ADB equivalent
     * (that call is honest: "not supported on this TV").
     */
    fun keyCodeForAction(action: String): Int? = when (action.uppercase()) {
        "POWER_TOGGLE", "POWER", "POWER_ON", "POWER_OFF" -> POWER
        "VOLUME_UP", "VOL_UP" -> VOLUME_UP
        "VOLUME_DOWN", "VOL_DOWN" -> VOLUME_DOWN
        "MUTE", "MUTE_TOGGLE" -> MUTE
        "HOME" -> HOME
        "BACK", "EXIT" -> BACK
        "DPAD_UP", "UP" -> DPAD_UP
        "DPAD_DOWN", "DOWN" -> DPAD_DOWN
        "DPAD_LEFT", "LEFT" -> DPAD_LEFT
        "DPAD_RIGHT", "RIGHT" -> DPAD_RIGHT
        "OK", "ENTER", "SELECT" -> DPAD_CENTER
        "MENU" -> MENU
        "PLAY_PAUSE", "MEDIA_PLAY_PAUSE" -> MEDIA_PLAY_PAUSE
        "STOP", "MEDIA_STOP" -> MEDIA_STOP
        "NEXT", "MEDIA_NEXT" -> MEDIA_NEXT
        "PREVIOUS", "MEDIA_PREVIOUS" -> MEDIA_PREVIOUS
        "CHANNEL_UP" -> CHANNEL_UP
        "CHANNEL_DOWN" -> CHANNEL_DOWN
        "SETTINGS" -> SETTINGS
        "SLEEP" -> SLEEP
        else -> null
    }

    /** Human-readable label for UI buttons (Spanish). */
    fun labelForKeyCode(keyCode: Int): String = when (keyCode) {
        POWER -> "Power"; VOLUME_UP -> "Vol +"; VOLUME_DOWN -> "Vol −"
        MUTE -> "Mute"; HOME -> "Inicio"; BACK -> "Atrás"
        DPAD_UP -> "▲"; DPAD_DOWN -> "▼"; DPAD_LEFT -> "◀"; DPAD_RIGHT -> "▶"
        DPAD_CENTER -> "OK"; MENU -> "Menú"; MEDIA_PLAY_PAUSE -> "Play/Pausa"
        MEDIA_STOP -> "Stop"; MEDIA_NEXT -> "Siguiente"; MEDIA_PREVIOUS -> "Anterior"
        CHANNEL_UP -> "CH +"; CHANNEL_DOWN -> "CH −"; SETTINGS -> "Ajustes"
        SLEEP -> "Sleep"; else -> "#$keyCode"
    }
}