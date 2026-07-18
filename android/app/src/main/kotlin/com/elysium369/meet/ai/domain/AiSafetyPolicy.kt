package com.elysium369.meet.ai.domain

data class AiSafetyPolicy(
    val blockDestructiveCommands: Boolean = true,
    val redactSensitiveInformation: Boolean = true,
    val restrictSensitiveModels: Boolean = false
)
