package com.elysium369.meet.domain.visualdiagnostics

data class ComponentSpec(
    val label: String,
    val expectedValue: String,
    val notes: String = ""
)

