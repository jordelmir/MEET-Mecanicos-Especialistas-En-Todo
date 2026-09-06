package com.elysium369.meet.mobility.domain.realtime

sealed interface VersionDecision {
    data object Apply : VersionDecision
    data object Ignore : VersionDecision
    data object Resync : VersionDecision
}

class AggregateVersionGate {
    fun evaluate(
        current: Long,
        incoming: Long,
    ): VersionDecision =
        when {
            incoming <= current ->
                VersionDecision.Ignore

            incoming == current + 1L ->
                VersionDecision.Apply

            else ->
                VersionDecision.Resync
        }
}
