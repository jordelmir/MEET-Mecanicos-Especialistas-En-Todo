package com.elysium369.meet.core.agent.ui

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E V A I R   U I   C O N T R O L   I D
 *  ──────────────────────────────────────────────────────────────
 *  Identificador único, semántico y fuertemente tipado para cualquier
 *  elemento interactivo registrado en el árbol de UI de Compose.
 * ══════════════════════════════════════════════════════════════════════
 */
@JvmInline
value class AgentUiControlId(val value: String) {

    override fun toString(): String = value

    companion object {
        // Navegación Global
        val NAV_HOME = AgentUiControlId("nav.home")
        val NAV_SCANNER = AgentUiControlId("nav.scanner")
        val NAV_DTCS = AgentUiControlId("nav.dtcs")
        val NAV_RIDES = AgentUiControlId("nav.rides")
        val NAV_GARAGE = AgentUiControlId("nav.garage")
        val NAV_PRO = AgentUiControlId("nav.pro")
        val NAV_SERVICES = AgentUiControlId("nav.services")
        val NAV_COMMERCE = AgentUiControlId("nav.commerce")
        val NAV_SAFETY = AgentUiControlId("nav.safety")
        val NAV_TRUST = AgentUiControlId("nav.trust")
        val NAV_STORE = AgentUiControlId("nav.store")
        val NAV_SETTINGS = AgentUiControlId("nav.settings")

        // Acciones Globales
        val ACTION_SAVE = AgentUiControlId("action.save")
        val ACTION_CANCEL = AgentUiControlId("action.cancel")
        val ACTION_CONFIRM = AgentUiControlId("action.confirm")
        val ACTION_BACK = AgentUiControlId("action.back")
        val ACTION_SEARCH = AgentUiControlId("action.search")

        // Scanner / Diagnóstico
        val SCANNER_CONNECT = AgentUiControlId("scanner.connect")
        val SCANNER_READ_CODES = AgentUiControlId("scanner.read_codes")
        val SCANNER_CLEAR_CODES = AgentUiControlId("scanner.clear_codes")
        val SCANNER_MODE06 = AgentUiControlId("scanner.mode06")
        val SCANNER_EMISSIONS = AgentUiControlId("scanner.emissions")

        // Viajes / Rides
        val RIDE_PICKUP_FIELD = AgentUiControlId("ride.pickup_field")
        val RIDE_DESTINATION_FIELD = AgentUiControlId("ride.destination_field")
        val RIDE_REQUEST_BUTTON = AgentUiControlId("ride.request_button")
        val RIDE_CANCEL_BUTTON = AgentUiControlId("ride.cancel_button")
        val RIDE_CONFIRM_BUTTON = AgentUiControlId("ride.confirm_button")
        val RIDE_DRIVER_ACCEPT = AgentUiControlId("ride.driver_accept")

        // Servicios / Marketplace
        val SERVICE_REQUEST_BUTTON = AgentUiControlId("service.request_button")
        val SERVICE_TAKE_DIRECT = AgentUiControlId("service.take_direct")
        val SERVICE_COUNTER_OFFER = AgentUiControlId("service.counter_offer")
        val SERVICE_TOPUP_WALLET = AgentUiControlId("service.topup_wallet")
        val SERVICE_WAZE_BUTTON = AgentUiControlId("service.waze_button")
    }
}
