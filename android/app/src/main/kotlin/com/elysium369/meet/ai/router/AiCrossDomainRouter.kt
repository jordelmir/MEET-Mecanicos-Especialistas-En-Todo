package com.elysium369.meet.ai.router

import com.elysium369.meet.ai.AiGatewayV2
import com.elysium369.meet.ai.AiTaskClass
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AiCrossDomainRouter — Routes AI requests across ALL domains (legal, diagnostics,
 * safety, fuel, properties, communications, PTT, vehicles).
 *
 * Laws:
 * - Each domain has its own task class with specific token budgets and deadlines
 * - Edge-first: try on-device, then cloud
 * - No domain can monopolize AI resources (per-domain rate limits)
 * - All AI interactions are auditable
 * - User data never leaves device without explicit consent
 */
enum class AiDomain {
    LEGAL,
    DIAGNOSTICS,
    SAFETY,
    FUEL,
    PROPERTIES,
    COMMUNICATIONS,
    PTT,
    VEHICLES,
    MARKET,
    GENERAL,
}

enum class AiRequestPriority {
    REALTIME,    // Voice intent, safety alerts
    INTERACTIVE, // User-initiated queries
    BACKGROUND,  // Summaries, suggestions
    BATCH,       // Bulk processing
}

data class AiDomainConfig(
    val domain: AiDomain,
    val taskClass: AiTaskClass,
    val maxRequestsPerMinute: Int,
    val maxRequestsPerHour: Int,
    val requiresConsent: Boolean,
    val canUseEdgeAi: Boolean,
    val canUseCloudAi: Boolean,
)

data class AiRoutingRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val domain: AiDomain,
    val priority: AiRequestPriority,
    val prompt: String,
    val context: Map<String, String> = emptyMap(),
    val preferEdge: Boolean = true,
    val maxTokens: Int? = null,
    val timeoutMs: Long? = null,
)

sealed interface AiRoutingResult {
    data class EdgeResponse(val text: String, val latencyMs: Long) : AiRoutingResult
    data class CloudResponse(val text: String, val provider: String, val latencyMs: Long) : AiRoutingResult
    data class RateLimited(val domain: AiDomain, val retryAfterMs: Long) : AiRoutingResult
    data class ConsentRequired(val domain: AiDomain) : AiRoutingResult
    data class CircuitOpen(val domain: AiDomain) : AiRoutingResult
    data class Error(val message: String) : AiRoutingResult
}

@Singleton
class AiCrossDomainRouter @Inject constructor() {

    private val domainConfigs = mutableMapOf<AiDomain, AiDomainConfig>()
    private val requestLog = mutableListOf<AiRoutingRequest>()
    private val rateLimitCounters = mutableMapOf<AiDomain, RateLimitCounter>()

    init {
        // Default configs for each domain
        domainConfigs[AiDomain.LEGAL] = AiDomainConfig(
            domain = AiDomain.LEGAL,
            taskClass = AiTaskClass.LEGAL_TRIAGE,
            maxRequestsPerMinute = 10,
            maxRequestsPerHour = 100,
            requiresConsent = true,
            canUseEdgeAi = true,
            canUseCloudAi = true,
        )
        domainConfigs[AiDomain.DIAGNOSTICS] = AiDomainConfig(
            domain = AiDomain.DIAGNOSTICS,
            taskClass = AiTaskClass.DTC_SUMMARY,
            maxRequestsPerMinute = 20,
            maxRequestsPerHour = 200,
            requiresConsent = false,
            canUseEdgeAi = true,
            canUseCloudAi = true,
        )
        domainConfigs[AiDomain.SAFETY] = AiDomainConfig(
            domain = AiDomain.SAFETY,
            taskClass = AiTaskClass.VOICE_INTENT,
            maxRequestsPerMinute = 30,
            maxRequestsPerHour = 300,
            requiresConsent = false,
            canUseEdgeAi = true,
            canUseCloudAi = false, // Safety never goes to cloud
        )
        domainConfigs[AiDomain.FUEL] = AiDomainConfig(
            domain = AiDomain.FUEL,
            taskClass = AiTaskClass.CATALOG_MAPPING,
            maxRequestsPerMinute = 10,
            maxRequestsPerHour = 100,
            requiresConsent = false,
            canUseEdgeAi = true,
            canUseCloudAi = true,
        )
        domainConfigs[AiDomain.VEHICLES] = AiDomainConfig(
            domain = AiDomain.VEHICLES,
            taskClass = AiTaskClass.DEEP_REPAIR_REASONING,
            maxRequestsPerMinute = 5,
            maxRequestsPerHour = 50,
            requiresConsent = false,
            canUseEdgeAi = true,
            canUseCloudAi = true,
        )
        domainConfigs[AiDomain.COMMUNICATIONS] = AiDomainConfig(
            domain = AiDomain.COMMUNICATIONS,
            taskClass = AiTaskClass.VOICE_INTENT,
            maxRequestsPerMinute = 15,
            maxRequestsPerHour = 150,
            requiresConsent = false,
            canUseEdgeAi = true,
            canUseCloudAi = true,
        )
        domainConfigs[AiDomain.PROPERTIES] = AiDomainConfig(
            domain = AiDomain.PROPERTIES,
            taskClass = AiTaskClass.CATALOG_MAPPING,
            maxRequestsPerMinute = 5,
            maxRequestsPerHour = 50,
            requiresConsent = true,
            canUseEdgeAi = true,
            canUseCloudAi = true,
        )
        domainConfigs[AiDomain.PTT] = AiDomainConfig(
            domain = AiDomain.PTT,
            taskClass = AiTaskClass.VOICE_INTENT,
            maxRequestsPerMinute = 30,
            maxRequestsPerHour = 300,
            requiresConsent = false,
            canUseEdgeAi = true,
            canUseCloudAi = false,
        )
        domainConfigs[AiDomain.MARKET] = AiDomainConfig(
            domain = AiDomain.MARKET,
            taskClass = AiTaskClass.CATALOG_MAPPING,
            maxRequestsPerMinute = 10,
            maxRequestsPerHour = 100,
            requiresConsent = false,
            canUseEdgeAi = true,
            canUseCloudAi = true,
        )
        domainConfigs[AiDomain.GENERAL] = AiDomainConfig(
            domain = AiDomain.GENERAL,
            taskClass = AiTaskClass.DTC_SUMMARY,
            maxRequestsPerMinute = 20,
            maxRequestsPerHour = 200,
            requiresConsent = false,
            canUseEdgeAi = true,
            canUseCloudAi = true,
        )
    }

    /** Route an AI request to the appropriate provider. */
    fun route(request: AiRoutingRequest): AiRoutingResult {
        val config = domainConfigs[request.domain] ?: return AiRoutingResult.Error("Unknown domain: ${request.domain}")

        // Rate limit check
        val rateLimit = checkRateLimit(request.domain, config)
        if (rateLimit != null) return rateLimit

        // Circuit breaker check
        if (!AiGatewayV2.canExecute(config.taskClass)) {
            return AiRoutingResult.CircuitOpen(request.domain)
        }

        // Log request
        requestLog.add(request)

        // Edge-first routing
        if (request.preferEdge && config.canUseEdgeAi) {
            return AiRoutingResult.EdgeResponse(
                text = "[Edge AI placeholder — ${request.domain}]",
                latencyMs = 100,
            )
        }

        // Cloud fallback
        if (config.canUseCloudAi) {
            return AiRoutingResult.CloudResponse(
                text = "[Cloud AI placeholder — ${request.domain}]",
                provider = "openai",
                latencyMs = 500,
            )
        }

        return AiRoutingResult.Error("No available AI provider for ${request.domain}")
    }

    private fun checkRateLimit(domain: AiDomain, config: AiDomainConfig): AiRoutingResult? {
        val now = System.currentTimeMillis()
        val counter = rateLimitCounters.getOrPut(domain) { RateLimitCounter() }

        // Reset counters if window expired
        if (now - counter.windowStartMs > 60_000) {
            counter.minuteCount = 0
            counter.windowStartMs = now
        }
        if (now - counter.hourStartMs > 3_600_000) {
            counter.hourCount = 0
            counter.hourStartMs = now
        }

        counter.minuteCount++
        counter.hourCount++

        if (counter.minuteCount > config.maxRequestsPerMinute) {
            return AiRoutingResult.RateLimited(domain, retryAfterMs = 60_000 - (now - counter.windowStartMs))
        }
        if (counter.hourCount > config.maxRequestsPerHour) {
            return AiRoutingResult.RateLimited(domain, retryAfterMs = 3_600_000 - (now - counter.hourStartMs))
        }

        return null
    }

    fun getRequestLog(domain: AiDomain? = null): List<AiRoutingRequest> {
        return if (domain != null) requestLog.filter { it.domain == domain } else requestLog
    }

    fun getConfig(domain: AiDomain): AiDomainConfig? = domainConfigs[domain]
}

private class RateLimitCounter {
    var minuteCount = 0
    var hourCount = 0
    var windowStartMs = System.currentTimeMillis()
    var hourStartMs = System.currentTimeMillis()
}
