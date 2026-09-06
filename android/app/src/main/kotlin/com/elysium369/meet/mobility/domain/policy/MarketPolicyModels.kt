package com.elysium369.meet.mobility.domain.policy

import com.elysium369.meet.mobility.domain.models.MarketId
import com.elysium369.meet.mobility.domain.pricing.Rate
import java.time.Instant

data class MarketPolicyConfig(
    val marketId: MarketId,
    val driverLocationTtlSeconds: Long = 30L,
    val maxSearchRadiusMeters: Double = 10000.0,
    val minSurgeMultiplier: Rate = Rate(1L, 1L), // 1.0x
    val maxSurgeMultiplier: Rate = Rate(3L, 1L), // 3.0x
    val defaultCancellationFreeMinutes: Long = 60L,
    val updatedAt: Instant,
) {
    init {
        require(driverLocationTtlSeconds > 0L) { "driverLocationTtlSeconds must be > 0" }
        require(maxSearchRadiusMeters > 0.0) { "maxSearchRadiusMeters must be > 0" }
        require(minSurgeMultiplier.numerator <= minSurgeMultiplier.denominator) {
            "minSurgeMultiplier cannot exceed 1.0x"
        }
        require(maxSurgeMultiplier.numerator >= maxSurgeMultiplier.denominator) {
            "maxSurgeMultiplier must be at least 1.0x"
        }
    }
}

data class SurgeCalculation(
    val activeDemandCount: Long,
    val availableSupplyCount: Long,
    val surgeMultiplier: Rate,
    val calculatedAt: Instant,
) {
    companion object {
        fun calculate(
            activeDemand: Long,
            availableSupply: Long,
            policy: MarketPolicyConfig,
            now: Instant = Instant.now()
        ): SurgeCalculation {
            require(activeDemand >= 0L) { "activeDemand cannot be negative" }
            require(availableSupply >= 0L) { "availableSupply cannot be negative" }

            // If supply is ample, multiplier is baseline (1/1)
            if (activeDemand == 0L || availableSupply >= activeDemand * 2) {
                return SurgeCalculation(
                    activeDemandCount = activeDemand,
                    availableSupplyCount = availableSupply,
                    surgeMultiplier = Rate(1L, 1L),
                    calculatedAt = now
                )
            }

            // If supply is 0 and there is demand, max surge
            if (availableSupply == 0L) {
                return SurgeCalculation(
                    activeDemandCount = activeDemand,
                    availableSupplyCount = availableSupply,
                    surgeMultiplier = policy.maxSurgeMultiplier,
                    calculatedAt = now
                )
            }

            // Ratio: demand / supply. For instance: 10 demand / 5 supply = 2.0x (2/1)
            val effectiveNum = activeDemand.coerceAtMost(availableSupply * policy.maxSurgeMultiplier.numerator)
            val multiplier = Rate(effectiveNum, availableSupply)

            return SurgeCalculation(
                activeDemandCount = activeDemand,
                availableSupplyCount = availableSupply,
                surgeMultiplier = multiplier,
                calculatedAt = now
            )
        }
    }
}
