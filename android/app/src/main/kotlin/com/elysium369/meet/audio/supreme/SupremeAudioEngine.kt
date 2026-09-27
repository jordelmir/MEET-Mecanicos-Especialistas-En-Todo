package com.elysium369.meet.audio.supreme

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper

/** Adapted from SupremeBass-Neon v1.2.0 (436ffd3): global effect with truthful fallback. */
class SupremeAudioEngine(context: Context, private val report: (String, Boolean) -> Unit) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var enhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var running = false
    private var percent = 100
    private var playbackHash: Int? = null
    private val reconnect = Runnable { if (running) connect() }
    private fun schedule() { handler.removeCallbacks(reconnect); handler.postDelayed(reconnect, 300) }
    private val playback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            val next = configs?.sumOf { it.hashCode() } ?: 0
            if (running && playbackHash != next) { playbackHash = next; schedule() }
        }
    }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { if (running) schedule() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { if (running) schedule() }
    }
    fun start(value: Int) {
        percent = SupremeBoostPolicy.clamp(value)
        if (!running) {
            running = true
            runCatching { manager.registerAudioPlaybackCallback(playback, handler) }
            runCatching { manager.registerAudioDeviceCallback(devices, handler) }
            connect()
        } else apply()
    }
    private fun releaseEffects() {
        runCatching { enhancer?.release() }; enhancer = null
        runCatching { equalizer?.release() }; equalizer = null
    }
    private fun connect() {
        releaseEffects()
        // Session zero is device-dependent; attaching does not prove another app is affected.
        enhancer = runCatching { LoudnessEnhancer(0) }.getOrNull()
        if (enhancer?.hasControl() != true) {
            runCatching { enhancer?.release() }; enhancer = null
            equalizer = runCatching { Equalizer(0, 0) }.getOrNull()
        }
        enhancer?.setControlStatusListener { _, _ -> if (running) apply() }
        equalizer?.setControlStatusListener { _, _ -> if (running) apply() }
        apply()
    }
    private fun apply() {
        val gain = SupremeBoostPolicy.millibels(percent)
        val applied = runCatching {
            enhancer?.let {
                if (!it.hasControl()) return@runCatching false
                it.setTargetGain(gain); it.enabled = true
                report("Efecto de volumen conectado · depende del reproductor", true)
                return@runCatching true
            }
            equalizer?.let {
                if (!it.hasControl()) return@runCatching false
                val range = it.bandLevelRange
                val limited = gain.coerceIn(range[0].toInt(), range[1].toInt())
                for (band in 0 until it.numberOfBands.toInt()) it.setBandLevel(band.toShort(), limited.toShort())
                it.enabled = true
                report(if (limited < gain) "Ecualizador conectado · ganancia limitada por Android" else "Ecualizador conectado · depende del reproductor", true)
                return@runCatching true
            }
            false
        }.getOrDefault(false)
        if (!applied) report("Android no permite aplicar el efecto global en este dispositivo", false)
    }
    fun stop() {
        running = false
        handler.removeCallbacks(reconnect)
        runCatching { manager.unregisterAudioPlaybackCallback(playback) }
        runCatching { manager.unregisterAudioDeviceCallback(devices) }
        releaseEffects()
    }
}
