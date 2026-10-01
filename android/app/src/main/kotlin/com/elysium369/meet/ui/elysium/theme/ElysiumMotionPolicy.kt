package com.elysium369.meet.ui.elysium.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Decorative motion runs only while visible and allowed by device and user preferences. */
@Composable
fun rememberElysiumMotionEnabled(): Boolean {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val prefs = remember(context) {
        context.getSharedPreferences("elysium_visual_prefs", Context.MODE_PRIVATE)
    }
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var savingPower by remember(power) { mutableStateOf(power?.isPowerSaveMode == true) }
    var systemMotion by remember(context) {
        mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f)
    }
    var userMotion by remember(prefs) { mutableStateOf(prefs.getBoolean("animated_icon_enabled", true)) }

    DisposableEffect(context, lifecycle, prefs, power) {
        fun refreshDevicePolicy() {
            savingPower = power?.isPowerSaveMode == true
            systemMotion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
            userMotion = prefs.getBoolean("animated_icon_enabled", true)
        }
        val observer = LifecycleEventObserver { _, _ ->
            started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (started) refreshDevicePolicy()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { refreshDevicePolicy() }
        }
        val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refreshDevicePolicy() }
        }
        val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "animated_icon_enabled") userMotion = prefs.getBoolean("animated_icon_enabled", true)
        }
        lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, settingsObserver,
        )
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        refreshDevicePolicy()
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(settingsObserver)
            prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
    }
    return started && !savingPower && systemMotion && userMotion
}
