package com.elysium369.meet.domain.visualdiagnostics

data class ComponentTest(
    val title: String,
    val procedure: String,
    val expectedResult: String,
    val tool: String
)

