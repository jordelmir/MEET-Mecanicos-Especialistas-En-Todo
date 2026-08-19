package com.elysium369.meet.core.obd

data class DiagnosticScanTarget(
    val requestAddress: String,
    val moduleName: String,
    val discoveryState: DiagnosticModuleDiscoveryState,
    val requiredForCompleteness: Boolean,
)

object DiagnosticScanPlanCompiler {
    /**
     * Confirmed topology responders are always first. Static addresses remain
     * discovery candidates only and can never reduce mathematical completeness.
     */
    fun compile(
        mode: DiagnosticScanMode,
        confirmedModules: List<NetworkModule>,
        discoveryCandidates: Map<String, String>,
    ): List<DiagnosticScanTarget> {
        val confirmed = linkedMapOf<String, DiagnosticScanTarget>()
        confirmedModules.asSequence()
            .filter(NetworkModule::isAlive)
            .forEach { module ->
                val requestAddress = DiagnosticModuleIdentity.canonical(
                    targetAddress = module.id,
                    responseAddress = module.responseId,
                    moduleName = module.name,
                )
                if (requestAddress.matches(Regex("[0-9A-F]{3,8}")) && requestAddress != "7DF") {
                    confirmed[requestAddress] = DiagnosticScanTarget(
                        requestAddress = requestAddress,
                        moduleName = module.name,
                        discoveryState = DiagnosticModuleDiscoveryState.CONFIRMED,
                        requiredForCompleteness = true,
                    )
                }
            }

        if (mode == DiagnosticScanMode.QUICK || mode == DiagnosticScanMode.CLEAR_VERIFY) return confirmed.values.toList()

        val plan = LinkedHashMap(confirmed)
        discoveryCandidates.forEach { (address, name) ->
            plan.putIfAbsent(
                address,
                DiagnosticScanTarget(
                    requestAddress = address,
                    moduleName = name,
                    discoveryState = DiagnosticModuleDiscoveryState.DISCOVERY_CANDIDATE,
                    requiredForCompleteness = false,
                ),
            )
        }
        return plan.values.toList()
    }
}
