package com.elysium369.meet.safety

import com.elysium369.meet.safety.domain.DataCoverage
import com.elysium369.meet.safety.domain.DataCoveragePolicy
import org.junit.Assert.*
import org.junit.Test

class SafetyDataCoverageTest {

    @Test
    fun `0 reports gives VERY_LOW`() {
        val coverage = DataCoveragePolicy.classify(reportCount = 0, sourceMixScore = 0, timeSpanDays = 30L)
        assertEquals(DataCoverage.VERY_LOW, coverage)
    }

    @Test
    fun `1 report 0 source mix gives VERY_LOW`() {
        val coverage = DataCoveragePolicy.classify(reportCount = 1, sourceMixScore = 0, timeSpanDays = 30L)
        assertEquals(DataCoverage.VERY_LOW, coverage)
    }

    @Test
    fun `5 reports 2 source mix gives MEDIUM`() {
        val coverage = DataCoveragePolicy.classify(reportCount = 5, sourceMixScore = 2, timeSpanDays = 30L)
        assertEquals(DataCoverage.MEDIUM, coverage)
    }

    @Test
    fun `20 reports 4 source mix gives HIGH`() {
        val coverage = DataCoveragePolicy.classify(reportCount = 20, sourceMixScore = 4, timeSpanDays = 30L)
        assertEquals(DataCoverage.HIGH, coverage)
    }
}
