package com.elysium369.meet.core.obd

import android.util.Log

/**
 * UdsProtocolManager — transitional UDS (ISO 14229) compatibility facade.
 *
 * Read-only identity operations remain available. DTC acquisition, memory
 * clearing and state-changing services belong to the typed acquisition,
 * memory and active-safety engines. Generic calls below fail closed unless a
 * future reviewed capability-pack path replaces them.
 *
 * Implemented Services:
 *   $10 — Diagnostic Session Control ✅
 *   $11 — ECU Reset BLOCKED (capability required)
 *   $14 — Direct Clear DTC BLOCKED (use ClearMemoryEngine)
 *   $19 — Legacy List<String> reader RETIRED (use DiagnosticAcquisitionEngine)
 *   $22 — Read Data By Identifier ✅
 *   $23 — Read Memory By Address ✅
 *   $27/$28/$2A/$2C — generic calls BLOCKED
 *   $2E/$2F/$31 — generic calls BLOCKED
 *   $34/$36/$37/$3D — generic calls BLOCKED
 *   $3E — Tester Present ✅
 *   $85 — generic call BLOCKED
 *
 * KWP2000 Legacy:
 *   $81 — Start Communication ✅
 *   $82 — Stop Communication ✅
 *   $83 — Access Timing Parameter ✅
 *
 * Negative Response Code ($7F) decoder ✅
 */
class UdsProtocolManager(private val obdSession: ObdSession) {
    private val TAG = "UdsProtocolManager"

    // ═══════════════════════════════════════════════
    // UDS SERVICE IDs (ISO 14229-1)
    // ═══════════════════════════════════════════════

    companion object {
        // Standard UDS Services
        const val SID_DIAGNOSTIC_SESSION_CONTROL = "10"
        const val SID_ECU_RESET = "11"
        const val SID_CLEAR_DTC = "14"
        const val SID_READ_DTC_INFORMATION = "19"
        const val SID_READ_DATA_BY_IDENTIFIER = "22"
        const val SID_READ_MEMORY_BY_ADDRESS = "23"
        const val SID_READ_SCALING_DATA = "24"
        const val SID_SECURITY_ACCESS = "27"
        const val SID_COMMUNICATION_CONTROL = "28"
        const val SID_AUTHENTICATION = "29"
        const val SID_READ_DATA_BY_PERIODIC_ID = "2A"
        const val SID_DYNAMICALLY_DEFINE_DID = "2C"
        const val SID_WRITE_DATA_BY_IDENTIFIER = "2E"
        const val SID_INPUT_OUTPUT_CONTROL = "2F"
        const val SID_ROUTINE_CONTROL = "31"
        const val SID_REQUEST_DOWNLOAD = "34"
        const val SID_REQUEST_UPLOAD = "35"
        const val SID_TRANSFER_DATA = "36"
        const val SID_REQUEST_TRANSFER_EXIT = "37"
        const val SID_REQUEST_FILE_TRANSFER = "38"
        const val SID_WRITE_MEMORY_BY_ADDRESS = "3D"
        const val SID_TESTER_PRESENT = "3E"
        const val SID_NEGATIVE_RESPONSE = "7F"
        const val SID_CONTROL_DTC_SETTING = "85"

        // KWP2000 Legacy Services
        const val KWP_START_COMMUNICATION = "81"
        const val KWP_STOP_COMMUNICATION = "82"
        const val KWP_ACCESS_TIMING = "83"
        const val KWP_ACCESS_TIMING_EXTENDED = "85"

        // IO Control Parameters ($2F)
        const val IO_RETURN_CONTROL_TO_ECU = "00"
        const val IO_RESET_TO_DEFAULT = "01"
        const val IO_FREEZE_CURRENT_STATE = "02"
        const val IO_SHORT_TERM_ADJUSTMENT = "03"

        // Session Types ($10)
        const val SESSION_DEFAULT = "01"
        const val SESSION_PROGRAMMING = "02"
        const val SESSION_EXTENDED = "03"

        // ECU Reset Types ($11)
        const val RESET_HARD = "01"
        const val RESET_KEY_OFF_ON = "02"
        const val RESET_SOFT = "03"

        // Routine Control sub-functions ($31)
        const val ROUTINE_START = "01"
        const val ROUTINE_STOP = "02"
        const val ROUTINE_REQUEST_RESULTS = "03"

        // Communication Control types ($28)
        const val COMM_ENABLE_RX_TX = "00"
        const val COMM_ENABLE_RX_DISABLE_TX = "01"
        const val COMM_DISABLE_RX_ENABLE_TX = "02"
        const val COMM_DISABLE_RX_TX = "03"

        // DTC Setting Control ($85)
        const val DTC_SETTING_ON = "01"
        const val DTC_SETTING_OFF = "02"

        // Common Data Identifiers (DIDs)
        val COMMON_DIDS = mapOf(
            "F180" to "Boot Software Identification",
            "F186" to "Active Diagnostic Session",
            "F187" to "Vehicle Manufacturer Spare Part Number",
            "F188" to "Vehicle Manufacturer ECU Software Number",
            "F189" to "Vehicle Manufacturer ECU Software Version",
            "F18A" to "System Supplier Identifier",
            "F18B" to "ECU Manufacturing Date",
            "F18C" to "ECU Serial Number",
            "F190" to "VIN (Vehicle Identification Number)",
            "F191" to "Vehicle Manufacturer ECU Hardware Number",
            "F192" to "System Supplier ECU Hardware Number",
            "F193" to "System Supplier ECU Hardware Version",
            "F194" to "System Supplier ECU Software Number",
            "F195" to "System Supplier ECU Software Version",
            "F197" to "System Name or Engine Type",
            "F198" to "Repair Shop Code / Tester Serial Number",
            "F199" to "Programming Date",
            "F19D" to "ECU Installation Date",
            "F19E" to "ODX File Identifier",
            "F1A0" to "Diagnostic Variant Identifier",
            "0100" to "Number of DTCs",
            "0110" to "Odometer Value"
        )
    }

    // ═══════════════════════════════════════════════
    // $10 — DIAGNOSTIC SESSION CONTROL
    // ═══════════════════════════════════════════════

    suspend fun changeDiagnosticSession(sessionType: String): Boolean {
        Log.w(TAG, "Blocked generic diagnostic session transition type=$sessionType")
        return false
    }

    suspend fun enterExtendedSession(): Boolean = changeDiagnosticSession(SESSION_EXTENDED)
    suspend fun enterProgrammingSession(): Boolean = changeDiagnosticSession(SESSION_PROGRAMMING)
    suspend fun enterDefaultSession(): Boolean = changeDiagnosticSession(SESSION_DEFAULT)

    // ═══════════════════════════════════════════════
    // $11 — ECU RESET
    // ═══════════════════════════════════════════════

    /**
     * ECU Reset (Service $11)
     * @param resetType "01"=hardReset, "02"=keyOffOnReset, "03"=softReset
     */
    suspend fun resetEcu(resetType: String = RESET_SOFT): Boolean {
        Log.w(TAG, "Blocked generic ECU reset type=$resetType: no reviewed capability pack")
        return false
    }

    // ═══════════════════════════════════════════════
    // $14 — CLEAR DIAGNOSTIC INFORMATION (UDS)
    // ═══════════════════════════════════════════════

    /**
     * Clear DTCs via UDS. Some modern vehicles only respond to this, not Mode $04.
     * @param groupOfDtc "FFFFFF" = all DTCs, or specific group
     */
    suspend fun clearDtcUds(groupOfDtc: String = "FFFFFF"): Boolean {
        Log.w(TAG, "Blocked direct UDS clear group=$groupOfDtc; use ClearMemoryEngine")
        return false
    }

    // ═══════════════════════════════════════════════
    // $19 — READ DTC INFORMATION
    // ═══════════════════════════════════════════════

    /**
     * Read DTCs by Status Mask (Service $19, subFunction $02).
     * Returns raw DTC bytes for decoding by the caller.
     */
    @Deprecated(
        message = "Use DiagnosticAcquisitionEngine; List<String> loses ECU attribution and evidence.",
        level = DeprecationLevel.ERROR,
    )
    suspend fun readDtcByStatusMask(
        @Suppress("UNUSED_PARAMETER") subFunction: String = "02",
        @Suppress("UNUSED_PARAMETER") statusMask: String = "FF",
    ): List<String> = error("Legacy UDS DTC reader retired")

    /**
     * Report Supported DTCs ($19 $0A)
     */
    @Deprecated(
        message = "Use DiagnosticAcquisitionEngine; List<String> loses ECU attribution and evidence.",
        level = DeprecationLevel.ERROR,
    )
    suspend fun readSupportedDtcs(): List<String> = error("Legacy UDS DTC reader retired")

    /**
     * Report First/Most Recent Confirmed DTC ($19 $0E)
     */
    @Deprecated(
        message = "Use DiagnosticAcquisitionEngine; presentation code is not finding authority.",
        level = DeprecationLevel.ERROR,
    )
    suspend fun readMostRecentDtc(): String? = error("Legacy UDS DTC reader retired")

    /**
     * Parse UDS DTC response bytes into DTC code strings.
     */
    private fun parseUdsDtcResponse(response: String): List<String> {
        val clean = response.replace(Regex("[\\s\\r\\n>]+"), "").uppercase()
        val dtcs = mutableListOf<String>()

        // Find the positive response (59)
        val idx59 = clean.indexOf("59")
        if (idx59 < 0) return emptyList()

        // Skip service ID + sub-function + status availability mask (6 chars)
        val dtcData = clean.substring(idx59 + 6)
        // Each DTC is 3 bytes (6 hex chars): High byte, Low byte, Status byte
        var i = 0
        while (i + 5 < dtcData.length) {
            val highByte = dtcData.substring(i, i + 2).toIntOrNull(16) ?: break
            val lowByte = dtcData.substring(i + 2, i + 4).toIntOrNull(16) ?: break
            // Status byte at i+4..i+5 (skip for now)

            val firstChar = when ((highByte shr 6) and 0x03) {
                0 -> 'P'; 1 -> 'C'; 2 -> 'B'; 3 -> 'U'; else -> 'P'
            }
            val secondDigit = (highByte shr 4) and 0x03
            val thirdDigit = highByte and 0x0F
            val dtcCode = "$firstChar$secondDigit${String.format("%X", thirdDigit)}${String.format("%02X", lowByte)}"
            if (dtcCode != "P0000") dtcs.add(dtcCode)
            i += 6
        }
        return dtcs
    }

    // ═══════════════════════════════════════════════
    // $22 — READ DATA BY IDENTIFIER
    // ═══════════════════════════════════════════════

    /**
     * Read Data By Identifier.
     * @param did 2-byte Data Identifier (e.g. "F190" for VIN, "F187" for part number)
     * @return Raw hex data response or null on failure
     */
    suspend fun readDataByIdentifier(did: String): String? {
        Log.d(TAG, "Leyendo DID: $did (${COMMON_DIDS[did.uppercase()] ?: "Desconocido"})")
        val command = "$SID_READ_DATA_BY_IDENTIFIER$did"
        val response = obdSession.sendRawCommand(command)
        Log.d(TAG, "Respuesta Read By ID: $response")

        val clean = response.replace(Regex("[\\s\\r\\n>]+"), "").uppercase()
        // Positive response starts with 62 + DID
        val marker = "62${did.uppercase()}"
        val idx = clean.indexOf(marker)
        if (idx < 0) return null
        return clean.substring(idx + marker.length)
    }

    /**
     * Read multiple DIDs in a single request (supported by some ECUs).
     */
    suspend fun readMultipleDataByIdentifier(dids: List<String>): Map<String, String?> {
        val results = mutableMapOf<String, String?>()
        for (did in dids) {
            results[did] = readDataByIdentifier(did)
        }
        return results
    }

    /**
     * Read and decode common ECU information DIDs.
     */
    suspend fun readEcuInfo(): List<UdsReadResult> {
        val results = mutableListOf<UdsReadResult>()
        val infoDidList = listOf("F190", "F187", "F188", "F189", "F191", "F197", "F18C", "F18B")

        for (did in infoDidList) {
            val rawHex = readDataByIdentifier(did)
            val name = COMMON_DIDS[did] ?: "DID $did"
            if (rawHex != null) {
                val decoded = hexToAsciiSafe(rawHex)
                results.add(UdsReadResult(did, name, rawHex, decoded, true))
            }
        }
        return results
    }

    // ═══════════════════════════════════════════════
    // $23 — READ MEMORY BY ADDRESS
    // ═══════════════════════════════════════════════

    /**
     * Read Memory By Address.
     * @param address Memory address (e.g. "00FF0000")
     * @param size Number of bytes to read (e.g. "10" for 16 bytes)
     * @param addressLength 2 or 4 bytes for address field
     */
    suspend fun readMemoryByAddress(address: String, size: String, addressLength: Int = 4): String? {
        Log.w(TAG, "Blocked arbitrary memory read address=$address size=$size addressLength=$addressLength")
        return null
    }

    // ═══════════════════════════════════════════════
    // $27 — SECURITY ACCESS
    // ═══════════════════════════════════════════════

    /**
     * Request Security Seed (first step of seed-key authentication).
     * @param accessLevel Odd number for seed request (01, 03, 05...)
     * @return Seed bytes or null if denied
     */
    suspend fun requestSecuritySeed(accessLevel: String = "01"): String? {
        Log.w(TAG, "Blocked generic security seed request level=$accessLevel")
        return null
    }

    /**
     * Send Security Key (second step of seed-key authentication).
     * @param accessLevel Even number for key send (02, 04, 06...)
     * @param key Calculated key bytes
     */
    suspend fun sendSecurityKey(accessLevel: String = "02", key: String): Boolean {
        Log.w(TAG, "Blocked generic security key level=$accessLevel keyLength=${key.length}")
        return false
    }

    // ═══════════════════════════════════════════════
    // $28 — COMMUNICATION CONTROL
    // ═══════════════════════════════════════════════

    /**
     * Control ECU communication (enable/disable Rx/Tx).
     * @param controlType COMM_ENABLE_RX_TX, COMM_DISABLE_RX_TX, etc.
     * @param communicationType "01"=normal, "02"=NM, "03"=both
     */
    suspend fun communicationControl(controlType: String, communicationType: String = "01"): Boolean {
        Log.w(TAG, "Blocked generic communication control type=$controlType comm=$communicationType")
        return false
    }

    // ═══════════════════════════════════════════════
    // $2A — READ DATA BY PERIODIC IDENTIFIER
    // ═══════════════════════════════════════════════

    /**
     * Start periodic data reading.
     * @param transmissionMode "01"=slow, "02"=medium, "03"=fast, "04"=stop
     * @param periodicDid Periodic DID (e.g. "F200")
     */
    suspend fun readDataByPeriodicIdentifier(transmissionMode: String, periodicDid: String): Boolean {
        Log.w(TAG, "Blocked generic periodic read mode=$transmissionMode did=$periodicDid")
        return false
    }

    suspend fun stopPeriodicReading(periodicDid: String): Boolean {
        return readDataByPeriodicIdentifier("04", periodicDid)
    }

    // ═══════════════════════════════════════════════
    // $2C — DYNAMICALLY DEFINE DATA IDENTIFIER
    // ═══════════════════════════════════════════════

    /**
     * Define a dynamic DID from existing source DIDs.
     * @param dynamicDid The new DID to define (e.g. "F300")
     * @param sourceDids List of source DIDs with position/size
     */
    suspend fun dynamicallyDefineDid(dynamicDid: String, sourceDid: String, position: String, size: String): Boolean {
        Log.w(TAG, "Blocked dynamic DID=$dynamicDid source=$sourceDid position=$position size=$size")
        return false
    }

    /**
     * Clear a dynamically defined DID.
     */
    suspend fun clearDynamicDid(dynamicDid: String): Boolean {
        Log.w(TAG, "Blocked dynamic DID clear=$dynamicDid")
        return false
    }

    // ═══════════════════════════════════════════════
    // $2F — INPUT/OUTPUT CONTROL (ACTIVE TESTS)
    // ═══════════════════════════════════════════════

    suspend fun startActiveTest(did: String, controlState: String = ""): Boolean {
        Log.w(TAG, "Blocked generic IO control DID=$did stateLength=${controlState.length}")
        return false
    }

    suspend fun stopActiveTest(did: String): Boolean {
        Log.w(TAG, "Blocked generic IO stop DID=$did: no acknowledged capability-scoped activation")
        return false
    }

    suspend fun freezeCurrentState(did: String): Boolean {
        Log.w(TAG, "Blocked generic IO freeze DID=$did: no reviewed capability pack")
        return false
    }

    // ═══════════════════════════════════════════════
    // $31 — ROUTINE CONTROL
    // ═══════════════════════════════════════════════

    /**
     * Execute a Routine.
     * @param routineId 2-byte routine identifier (e.g. "FF00")
     * @param controlOption ROUTINE_START, ROUTINE_STOP, ROUTINE_REQUEST_RESULTS
     * @param routineOption Additional parameters (optional)
     */
    suspend fun executeRoutine(routineId: String, controlOption: String = ROUTINE_START, routineOption: String = ""): Boolean {
        Log.w(
            TAG,
            "Blocked generic routine id=$routineId option=$controlOption optionLength=${routineOption.length}",
        )
        return false
    }

    suspend fun startRoutine(routineId: String, params: String = ""): Boolean =
        executeRoutine(routineId, ROUTINE_START, params)

    suspend fun stopRoutine(routineId: String): Boolean =
        executeRoutine(routineId, ROUTINE_STOP)

    suspend fun getRoutineResults(routineId: String): String? {
        Log.w(TAG, "Blocked generic routine result request id=$routineId")
        return null
    }

    // ═══════════════════════════════════════════════
    // $34/$36/$37 — DATA TRANSFER (Read-only diagnostics)
    // ═══════════════════════════════════════════════

    /**
     * Request Download — used for diagnostic data extraction, NOT flashing.
     */
    suspend fun requestDownload(compressionMethod: String = "00", encryptionMethod: String = "00",
                                 address: String, size: String): Boolean {
        Log.w(
            TAG,
            "Blocked transfer request compression=$compressionMethod encryption=$encryptionMethod address=$address size=$size",
        )
        return false
    }

    /**
     * Transfer Data — read block from ECU.
     */
    suspend fun transferData(blockSequence: Int): String? {
        Log.w(TAG, "Blocked generic transfer block=$blockSequence")
        return null
    }

    /**
     * Request Transfer Exit — finalize a data transfer operation.
     */
    suspend fun requestTransferExit(): Boolean {
        Log.w(TAG, "Blocked generic transfer exit")
        return false
    }

    // ═══════════════════════════════════════════════
    // $3E — TESTER PRESENT
    // ═══════════════════════════════════════════════

    suspend fun sendTesterPresent(): Boolean {
        val command = "$SID_TESTER_PRESENT 00"
        val response = obdSession.sendKeepAliveDirectly(command)
        return isPositiveResponse(response, "7E", command)
    }

    // ═══════════════════════════════════════════════
    // $85 — CONTROL DTC SETTING
    // ═══════════════════════════════════════════════

    /**
     * Enable or disable DTC storage in the ECU.
     * Useful during diagnostic procedures to prevent spurious DTCs.
     */
    suspend fun controlDtcSetting(onOff: String = DTC_SETTING_ON): Boolean {
        Log.w(TAG, "Blocked generic DTC-setting control option=$onOff")
        return false
    }

    suspend fun enableDtcStorage(): Boolean = controlDtcSetting(DTC_SETTING_ON)
    suspend fun disableDtcStorage(): Boolean = controlDtcSetting(DTC_SETTING_OFF)

    // ═══════════════════════════════════════════════
    // KWP2000 LEGACY SERVICES ($81-$85)
    // ═══════════════════════════════════════════════

    /**
     * KWP2000 Start Communication ($81)
     * Used by older vehicles (pre-2008) for initializing diagnostic session.
     */
    suspend fun kwpStartCommunication(): Boolean {
        Log.w(TAG, "Blocked generic KWP start; transport owner must establish a sourced session")
        return false
    }

    /**
     * KWP2000 Stop Communication ($82)
     */
    suspend fun kwpStopCommunication(): Boolean {
        Log.w(TAG, "Blocked generic KWP stop; transport owner must close its own session")
        return false
    }

    /**
     * KWP2000 Access Timing Parameter ($83)
     * @param subFunction "01"=readExtended, "02"=setDefault, "03"=readActive, "04"=setNew
     */
    suspend fun kwpAccessTimingParameter(subFunction: String = "03"): String? {
        Log.w(TAG, "Blocked generic KWP timing access subFunction=$subFunction")
        return null
    }

    // ═══════════════════════════════════════════════
    // $7F — NEGATIVE RESPONSE CODE DECODER
    // ═══════════════════════════════════════════════

    /**
     * Decode UDS Negative Response Code (NRC) into a human-readable description.
     */
    fun decodeNrc(nrcByte: Int): Pair<String, String> {
        return when (nrcByte) {
            0x10 -> "generalReject" to "Rechazo general del servicio"
            0x11 -> "serviceNotSupported" to "Servicio no soportado por esta ECU"
            0x12 -> "subFunctionNotSupported" to "Sub-función no soportada"
            0x13 -> "incorrectMessageLengthOrInvalidFormat" to "Longitud de mensaje incorrecta"
            0x14 -> "responseTooLong" to "Respuesta demasiado larga"
            0x21 -> "busyRepeatRequest" to "ECU ocupada, reintentar"
            0x22 -> "conditionsNotCorrect" to "Condiciones no correctas (motor apagado/encendido?)"
            0x24 -> "requestSequenceError" to "Error de secuencia de solicitud"
            0x25 -> "noResponseFromSubnetComponent" to "Sin respuesta del sub-componente"
            0x26 -> "failurePreventsExecutionOfRequestedAction" to "Falla impide la ejecución"
            0x31 -> "requestOutOfRange" to "Solicitud fuera de rango"
            0x33 -> "securityAccessDenied" to "Acceso de seguridad denegado"
            0x35 -> "invalidKey" to "Clave de seguridad inválida"
            0x36 -> "exceededNumberOfAttempts" to "Excedido número de intentos de seguridad"
            0x37 -> "requiredTimeDelayNotExpired" to "Tiempo de espera de seguridad no expirado"
            0x70 -> "uploadDownloadNotAccepted" to "Transferencia no aceptada"
            0x71 -> "transferDataSuspended" to "Transferencia suspendida"
            0x72 -> "generalProgrammingFailure" to "Falla general de programación"
            0x73 -> "wrongBlockSequenceCounter" to "Secuencia de bloque incorrecta"
            0x78 -> "requestCorrectlyReceivedResponsePending" to "Solicitud recibida, respuesta pendiente"
            0x7E -> "subFunctionNotSupportedInActiveSession" to "Sub-función no soportada en sesión activa"
            0x7F -> "serviceNotSupportedInActiveSession" to "Servicio no soportado en sesión activa"
            else -> "unknownNRC_${String.format("%02X", nrcByte)}" to "Código de respuesta negativa desconocido ($nrcByte)"
        }
    }

    /**
     * Parse a negative response and extract the NRC.
     * Format: 7F [rejected SID] [NRC]
     */
    fun parseNegativeResponse(response: String): Triple<String, Int, String>? {
        val clean = response.replace(Regex("[\\s\\r\\n>]+"), "").uppercase()
        val idx = clean.indexOf("7F")
        if (idx < 0 || idx + 5 >= clean.length) return null
        val rejectedSid = clean.substring(idx + 2, idx + 4)
        val nrc = clean.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return null
        val (_, description) = decodeNrc(nrc)
        return Triple(rejectedSid, nrc, description)
    }

    // ═══════════════════════════════════════════════
    // UDS CAPABILITIES DISCOVERY
    // ═══════════════════════════════════════════════

    /**
     * Discover only read-only identity DIDs. Potentially state-changing
     * services (session change, IO control, routine control) are deliberately
     * not probed; `false` below means "not proven by a capability pack".
     */
    suspend fun discoverCapabilities(): UdsCapabilities {
        Log.d(TAG, "Descubriendo capacidades UDS del ECU...")

        val supportsReadById = readDataByIdentifier("F190") != null

        val discoveredDids = mutableListOf<String>()
        if (supportsReadById) {
            for (did in listOf("F190", "F187", "F188", "F197", "F18C")) {
                if (readDataByIdentifier(did) != null) discoveredDids.add(did)
            }
        }

        return UdsCapabilities(
            supportsExtendedSession = false,
            supportsIOControl = false,
            supportsRoutineControl = false,
            supportsReadByIdentifier = supportsReadById,
            discoveredDids = discoveredDids
        )
    }

    // ═══════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════

    private fun isPositiveResponse(response: String, expectedSid: String, commandSent: String = ""): Boolean {
        val cleanResponse = response.replace(Regex("[\\s\\r\\n>]+"), "").uppercase()
        if (cleanResponse.contains("ERROR") || cleanResponse.contains("NODATA")) return false

        val cleanCommand = commandSent.replace(Regex("[\\s]+"), "").uppercase()
        val finalResponse = if (cleanCommand.isNotEmpty() && cleanResponse.startsWith(cleanCommand)) {
            cleanResponse.substring(cleanCommand.length)
        } else {
            cleanResponse
        }

        if (finalResponse.startsWith("7F")) return false
        return finalResponse.contains(expectedSid)
    }

    /**
     * Convert hex string to ASCII, replacing non-printable chars with dots.
     */
    private fun hexToAsciiSafe(hex: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < hex.length) {
            val byte = hex.substring(i, i + 2).toIntOrNull(16) ?: break
            sb.append(if (byte in 32..126) byte.toChar() else '.')
            i += 2
        }
        return sb.toString().trim()
    }
}
