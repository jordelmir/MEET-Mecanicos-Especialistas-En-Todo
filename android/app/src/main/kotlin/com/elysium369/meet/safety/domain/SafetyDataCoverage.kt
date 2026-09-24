package com.elysium369.meet.safety.domain

enum class DataCoverage {
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH,
}

object DataCoveragePolicy {
    fun classify(reportCount: Long, sourceMixScore: Long, timeSpanDays: Long): DataCoverage = when {
        reportCount < 2 || sourceMixScore == 0L -> DataCoverage.VERY_LOW
        reportCount < 5 || sourceMixScore < 2L -> DataCoverage.LOW
        reportCount < 15 || sourceMixScore < 3L -> DataCoverage.MEDIUM
        else -> DataCoverage.HIGH
    }

    fun classify(reportCount: Int, sourceMixScore: Int, timeSpanDays: Long): DataCoverage =
        classify(reportCount.toLong(), sourceMixScore.toLong(), timeSpanDays)
}
