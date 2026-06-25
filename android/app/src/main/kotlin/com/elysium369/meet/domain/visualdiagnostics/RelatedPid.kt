package com.elysium369.meet.domain.visualdiagnostics

data class RelatedPid(
    val pid: String,
    val label: String,
    val unit: String,
    val normalRange: String,
    val severityWhenOutOfRange: String
)

