package com.elysium.nexus.fabric.routing

import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.evidence.ControlEvidenceStore
import com.elysium.nexus.fabric.evidence.EventResult

/**
 * §7 Dynamic Route Intelligence.
 *
 * The [ActionRouteScorer] replaces rigid priority
 * ordering with a dynamic scoring function. Each
 * route is scored 0.0 (worst) to 1.0 (best) based
 * on real-time signals:
 *
 * 1. Protocol capability match
 * 2. Adapter availability
 * 3. Measured latency (from evidence store)
 * 4. Recent failure rate (from evidence store)
 * 5. State confirmation ability
 * 6. Pairing / trust state
 * 7. Security classification
 * 8. User preference
 *
 * The score is the weighted sum; higher is better.
 * The [ActionDispatcher] picks the highest-scoring
 * available route.
 */
class ActionRouteScorer(
    private val evidenceStore: ControlEvidenceStore
) {

    /**
     * Score a single [TransportRoute] for the given
     * [action] on the given [target] device.
     *
     * @return a score in [0.0, 1.0]. Higher is better.
     */
    fun score(
        action: UniversalAction,
        target: DeviceTwin,
        route: TransportRoute,
        nowNs: Long = System.nanoTime()
    ): Double {
        var score = 0.0

        // 1. Capability match (0.0 or 0.20)
        val requiredCapability = action.requiredCapability()
        if (requiredCapability in route.binding.capabilities) {
            score += 0.20
        } else {
            return 0.0
        }

        // 2. Adapter availability (0.0 or 0.15)
        if (route.isAvailable) {
            score += 0.15
        } else {
            return 0.0
        }

        // 3. Latency (0.0 to 0.20) — lower is better
        val latencyScore = when {
            route.latencyEstimateMs <= 5L -> 0.20
            route.latencyEstimateMs <= 20L -> 0.15
            route.latencyEstimateMs <= 50L -> 0.10
            route.latencyEstimateMs <= 100L -> 0.05
            else -> 0.02
        }
        score += latencyScore

        // 4. Historical success rate (0.0 to 0.20)
        val protocolEvents = evidenceStore.query(protocol = route.protocol)
        if (protocolEvents.isNotEmpty()) {
            val successes = protocolEvents.count { it.result == EventResult.Success }
            val successRate = successes.toDouble() / protocolEvents.size
            score += successRate * 0.20
        } else {
            // No history — assume neutral 0.10
            score += 0.10
        }

        // 5. Recent failure penalty (0.0 to -0.10)
        val recentWindowNs = nowNs - 60_000_000_000L // last 60s
        val recentFailures = protocolEvents.count {
            it.timestampNs >= recentWindowNs && it.result != EventResult.Success
        }
        if (recentFailures >= 5) {
            score -= 0.10
        } else if (recentFailures >= 3) {
            score -= 0.05
        }

        // 6. State confirmation ability (0.0 or 0.10)
        val canConfirm = route.protocol in CONFIRMATION_CAPABLE_PROTOCOLS
        if (canConfirm) {
            score += 0.10
        }

        // 7. Trust state (0.0 to 0.10)
        val trustScore = when (target.trust) {
            com.elysium.nexus.fabric.canonical.TrustState.ManufacturerCertified -> 0.10
            com.elysium.nexus.fabric.canonical.TrustState.Attested -> 0.08
            com.elysium.nexus.fabric.canonical.TrustState.SelfDeclared -> 0.05
            com.elysium.nexus.fabric.canonical.TrustState.Untrusted -> 0.02
        }
        score += trustScore

        // 8. Protocol priority tiebreaker (0.0 to 0.05)
        val priorityBonus = (100 - RouteNegotiator.protocolPriority(route.protocol)).toDouble() / 1000.0
        score += priorityBonus.coerceIn(0.0, 0.05)

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Score and rank all [routes] for the given
     * [action] on [target]. Returns routes sorted
     * by score descending (best first).
     */
    fun rank(
        action: UniversalAction,
        target: DeviceTwin,
        routes: List<TransportRoute>,
        nowNs: Long = System.nanoTime()
    ): List<ScoredRoute> = routes
        .map { ScoredRoute(it, score(action, target, it, nowNs)) }
        .sortedByDescending { it.score }

    companion object {
        /**
         * Protocols that can confirm state after
         * a command (read-back or subscription).
         */
        val CONFIRMATION_CAPABLE_PROTOCOLS: Set<Protocol> = setOf(
            Protocol.Matter,
            Protocol.Thread,
            Protocol.Zigbee,
            Protocol.ZWave,
            Protocol.ZWaveLongRange,
            Protocol.Ble,
            Protocol.HidOverBle,
            Protocol.WiFi,
            Protocol.Ethernet,
            Protocol.Mqtt,
            Protocol.Onvif,
            Protocol.VendorRest,
            Protocol.VendorWebSocket,
            Protocol.ElysiumLink,
            Protocol.HdmiCec
        )
    }
}

/**
 * A [TransportRoute] with its computed score.
 */
data class ScoredRoute(
    val route: TransportRoute,
    val score: Double
) {
    init {
        require(score in 0.0..1.0) {
            "Score must be in [0.0, 1.0] (got $score)."
        }
    }
}
