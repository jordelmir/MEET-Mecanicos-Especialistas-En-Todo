package com.elysium369.meet.core.geo.runtime

/** Finite fallback policy: an unavailable provider never leaves a silent blank map. */
data class MapStyleLoadPolicy(
    val urls: List<String>,
    val attempt: Int = 0,
    val phase: Phase = if (urls.isEmpty()) Phase.UNAVAILABLE else Phase.LOADING,
) {
    enum class Phase { LOADING, READY, UNAVAILABLE }
    val currentUrl: String? get() = urls.getOrNull(attempt)
    fun loaded(): MapStyleLoadPolicy = copy(phase = Phase.READY)
    fun failed(): MapStyleLoadPolicy = if (attempt < urls.lastIndex) {
        copy(attempt = attempt + 1, phase = Phase.LOADING)
    } else copy(phase = Phase.UNAVAILABLE)
    fun retry(): MapStyleLoadPolicy = copy(attempt = 0, phase = if (urls.isEmpty()) Phase.UNAVAILABLE else Phase.LOADING)

    companion object {
        fun candidates(primary: String, fallback: String): MapStyleLoadPolicy = MapStyleLoadPolicy(
            listOf(primary, fallback).map(String::trim).filter(String::isNotEmpty).distinct(),
        )
    }
}
