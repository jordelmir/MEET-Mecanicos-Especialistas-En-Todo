package com.elysium369.meet.core.obd

import android.util.Log

/**
 * UdsProtocolManager
 * 
 * Gestor profesional del protocolo UDS (Unified Diagnostic Services - ISO 14229).
 * Responsable de la ejecución de rutinas avanzadas y control bi-direccional (Active Tests)
 * a través de comandos de bajo nivel.
 */
class UdsProtocolManager(private val obdSession: ObdSession) {
    private val TAG = "UdsProtocolManager"

    // UDS Service IDs
    companion object {
        const val SID_DIAGNOSTIC_SESSION_CONTROL = "10"
        const val SID_ECU_RESET = "11"
        const val SID_SECURITY_ACCESS = "27"
        const val SID_TESTER_PRESENT = "3E"
        const val SID_READ_DATA_BY_IDENTIFIER = "22"
        const val SID_INPUT_OUTPUT_CONTROL = "2F" // Active Tests
        const val SID_ROUTINE_CONTROL = "31" // Resets / Adaptations

        // IO Control Parameters (0x2F)
        const val IO_RETURN_CONTROL_TO_ECU = "00"
        const val IO_RESET_TO_DEFAULT = "01"
        const val IO_FREEZE_CURRENT_STATE = "02"
        const val IO_SHORT_TERM_ADJUSTMENT = "03"
    }

    /**
     * Inicia una prueba activa utilizando el servicio 0x2F (Input/Output Control By Identifier).
     * 
     * @param did Data Identifier (ej. "01 23" -> "0123")
     * @param controlState Parámetros de estado (ej. "01" para ON, "00" para OFF, "FF" para 100%)
     * @return true si el comando fue aceptado por la ECU (Respuesta 6F), false de lo contrario.
     */
    suspend fun startActiveTest(did: String, controlState: String = ""): Boolean {
        Log.d(TAG, "Iniciando Active Test UDS. DID: $did, State: $controlState")
        
        // El formato es: 2F [DID] [ControlOption] [ControlState]
        // ControlOption = 03 (ShortTermAdjustment) indica que estamos tomando el control temporalmente
        val command = "$SID_INPUT_OUTPUT_CONTROL$did$IO_SHORT_TERM_ADJUSTMENT$controlState"
        
        val response = obdSession.sendRawCommand(command)
        Log.d(TAG, "Respuesta UDS Active Test: $response")

        // Respuesta positiva UDS es [SID + 0x40], por lo tanto 2F -> 6F
        return isPositiveResponse(response, "6F")
    }

    /**
     * Detiene una prueba activa y devuelve el control a la ECU.
     */
    suspend fun stopActiveTest(did: String): Boolean {
        Log.d(TAG, "Deteniendo Active Test UDS. DID: $did")
        
        // ControlOption = 00 (ReturnControlToECU)
        val command = "$SID_INPUT_OUTPUT_CONTROL$did$IO_RETURN_CONTROL_TO_ECU"
        
        val response = obdSession.sendRawCommand(command)
        Log.d(TAG, "Respuesta UDS Stop Test: $response")

        return isPositiveResponse(response, "6F")
    }

    /**
     * Valida si la respuesta del adaptador ELM327 contiene el byte de respuesta positiva esperado.
     */
    private fun isPositiveResponse(response: String, expectedSid: String): Boolean {
        val cleanResponse = response.replace(Regex("[\\s\r\n>]+"), "").uppercase()
        // Ignorar "SEARCHING...", errores de bus, etc.
        if (cleanResponse.contains("ERROR") || cleanResponse.contains("NODATA")) return false
        
        // Verifica si la cadena limpia contiene el SID de respuesta esperado
        return cleanResponse.contains(expectedSid)
    }

    /**
     * Mantener la sesión activa para UDS. Muchos ECUs requieren el comando TesterPresent (3E)
     * enviado cada 2-3 segundos para no cerrar la sesión de diagnóstico.
     */
    suspend fun sendTesterPresent(): Boolean {
        val response = obdSession.sendRawCommand("$SID_TESTER_PRESENT 00")
        return isPositiveResponse(response, "7E")
    }
}
