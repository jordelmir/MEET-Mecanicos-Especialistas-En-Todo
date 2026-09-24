package com.elysium369.meet.safety.data

class AggregateVersionGate {
    fun shouldApply(localVersion: Long, incomingVersion: Long): Boolean =
        incomingVersion > localVersion

    fun hasGap(localVersion: Long, incomingVersion: Long): Boolean =
        incomingVersion > localVersion + 1
}
