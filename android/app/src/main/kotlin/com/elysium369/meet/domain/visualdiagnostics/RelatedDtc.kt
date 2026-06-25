package com.elysium369.meet.domain.visualdiagnostics

data class RelatedDtc(
    val code: String,
    val title: String,
    val probabilityWeight: Double,
    val severity: String
)

