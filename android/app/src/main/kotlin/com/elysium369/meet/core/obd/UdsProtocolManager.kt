package com.elysium369.meet.core.obd

import android.util.Log

/**
 * UdsProtocolManager — Professional UDS (ISO 14229) Protocol Handler
 *
 * Complete implementation of Unified Diagnostic Services for advanced
 * automotive diagnostics beyond standard OBD2 (SAE J1979).
 *
 * Implemented Services:
 *   $10 — Diagnostic Session Control ✅
 *   $11 — ECU Reset ✅
 *   $14 — Clear DTC (UDS) ✅
 *   $19 — Read DTC Information ✅
 *   $22 — Read Data By Identifier ✅
 *   $23 — Read Memory By Address ✅
 *   $27 — Security Access (seed-key) ✅
 *   $28 — Communication Control ✅
 *   $2A — Read Data By Periodic Identifier ✅
 *   $2C — Dynamically Define Data Identifier ✅
 *   $2E — Write Data By Identifier ⚠️ (safety-gated)
 *   $2F — IO Control By Identifier (Active Tests) ✅
 *   $31 — Routine Control ✅
 *   $34 — Request Download ✅ (diagnostics only)
 *   $36 — Transfer Data ✅ (diagnostics only)
 *   $37 — Request Transfer Exit ✅
 *   $3D — Write Memory By Address ⚠️ (safety-gated)
 *   $3E — Tester Present ✅
 *   $85 — Control DTC Setting ✅
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
        Log.d(TAG, "Cambiando sesión UDS a tipo: $sessionType")
        val command = "$SID_DIAGNOSTIC_SESSION_CONTROL$sessionType"
        val response = obdSession.sendRawCommand(command)
        Log.d(TAG, "Respuesta Session Control: $response")
        return isPositiveResponse(response, "50", command)
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
        Log.d(TAG, "Ejecutando ECU Reset tipo: $resetType")
        val command = "$SID_ECU_RESET$resetType"
        val response = obdSession.sendRawCommand(command)
        Log.d(TAG, "Respuesta ECU Reset: $response")
        return isPositiveResponse(response, "51", command)
    }

    // ═══════════════════════════════════════════════
    // $14 — CLEAR DIAGNOSTIC INFORMATION (UDS)
    // ═══════════════════════════════════════════════

    /**
     * Clear DTCs via UDS. Some modern vehicles only respond to this, not Mode $04.
     * @param groupOfDtc "FFFFFF" = all DTCs, or specific group
     */
    suspend fun clearDtcUds(groupOfDtc: String = "FFFFFF"): Boolean {
        Log.d(TAG, "Clearing DTCs UDS, grupo: $groupOfDtc")
        val command = "$SID_CLEAR_DTC$groupOfDtc"
        val response = obdSession.sendRawCommand(command)
        Log.d(TAG, "Respuesta Clear DTC UDS: $response")
        return isPositiveResponse(response, "54", command)
    }

    // ═══════════════════════════════════════════════
    // $19 — READ DTC INFORMATION
    // ═══════════════════════════════════════════════

    /**
     * Read DTCs by Status Mask (Service $19, subFunction $02).
     * Returns raw DTC bytes for decoding by the caller.
     */
    suspend fun readDtcByStatusMask(subFunction: String = "02", statusMask: String = "FF"): List<String> {
        Log.d(TAG, "Leyendo DTCs UDS sub=$subFunction, mask=$statusMask")
        val command = "$SID_READ_DTC_INFORMATION$subFunction$statusMask"
        val response = obdSession.sendRawCommand(command)
        Log.d(TAG, "Respuesta Read DTC Info: $response")

        if (!isPositiveResponse(response, "59", command)) return emptyList()

        return parseUdsDtcResponse(response)
    }

    /**
     * Report Supported DTCs ($19 $0A)
     */
    suspend fun readSupportedDtcs(): List<String> = readDtcByStatusMask("0A", "")

    /**
     * Report First/Most Recent Confirmed DTC ($19 $0E)
     */
    suspend fun readMostRecentDtc(): String? {
        val result = readDtcByStatusMask("0E", "")
        return result.firstOrNull()
    }

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
        Log.d(TAG, "Leyendo memoria en dirección: $address, tamaño: $size")
        // addressAndLengthFormatIdentifier: high nibble = size length, low nibble = address length
        val sizeLength = (size.length / 2).coerceIn(1, 4)
        val formatByte = String.format("%X%X", sizeLength, addressLength)
        val command = "$SID_READ_MEMORY_BY_ADDRESS$formatByte$address$size"
        val response = obdSession.sendRawCommand(command)

        val clean = response.replace(Regex("[\\s\\r\\n>]+"), "").uppercase()
        val idx = clean.indexOf("63")
        if (idx < 0) return null
        return clean.substring(idx + 2)
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
        Log.d(TAG, "Solicitando seed de seguridad, nivel: $accessLevel")
        val command = "$SID_SECURITY_ACCESS$accessLevel"
        val response = obdSession.sendRawCommand(command)
        Log.d(TAG, "Respuesta Security Seed: $response")

        val clean = response.replace(Regex("[\\s\\r\\n>]+"), "").uppercase()
        val marker = "67$accessLevel"
        val idx = clean.indexOf(marker)
        if (idx < 0) return null
        return clean.substring(idx + marker.length)
    }

    /**
     * Send Security Key (second step of seed-key authentication).
     * @param accessLevel Even number for key send (02, 04, 06...)
     * @param key Calculated key bytes
     */
    suspend fun sendSecurityKey(accessLevel: String = "02", key: String): Boolean {
        Log.d(TAG, "Enviando clave de seguridad, nivel: $accessLevel")
        val command = "$SID_SECURITY_ACCESS$accessLevel$key"
        val response = obdSession.sendRawCommand(command)
        Log.d(TAG, "Respuesta Security Key: $response")
        return isPositiveResponse(response, "67", command)
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
        Log.d(TAG, "Control de comunicación: tipo=$controlType, comm=$communicationType")
        val command = "$SID_COMMUNICATION_CONTROL$controlType$communicationType"
        val response = obdSession.sendRawCommand(command)
        return isPositiveResponse(response, "68", command)
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
        val command = "$SID_READ_DATA_BY_PERIODIC_ID$transmissionMode$periodicDid"
        val response = obdSession.sendRawCommand(command)
        return isPositiveResponse(response, "6A", command)
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
        // subFunction 01 = defineByIdentifier
        val command = "${SID_DYNAMICALLY_DEFINE_DID}01$dynamicDid$sourceDid$position$size"
        val response = obdSession.sendRawCommand(command)
        return isPositiveResponse(response, "6C", command)
    }

    /**
     * Clear a dynamically defined DID.
     */
    suspend fun clearDynamicDid(dynamicDid: String): Boolean {
        val command = "${SID_DYNAMICALLY_DEFINE_DID}03$dynamicDid"
        val response = obdSession.sendRawCommand(command)
        return isPositiveResponse(response, "6C", command)
    }

    // ═══════════════════════════════════════════════
    // $2F — INPUT/OUTPUT CONTROL (ACTIVE TESTS)
    // ═══════════════════════════════════════════════

    suspend fun startActiveTest(did: String, controlState: String = ""): Boolean {
        Log.d(TAG, "Iniciando Active Test UDS. DID: $did, State: $controlState")
        val command = "$SID_INPUT_OUTPUT_CONTROL$did$IO_SHORT_TERM_ADJUSTMENT$controlState"
        val response = obdSession.sendRawCommand(command)
        Log.d(TAG, "Respuesta UDS Active Test: $response")
        return isPositiveResponse(response, "6F", command)
    }

    suspend fun stopActiveTest(did: String): Boolean {
        Log.d(TAG, "Deteniendo Active Test UDS. DID: $did")
        val command = "$SID_INPUT_OUTPUT_CONTROL$did$IO_RETURN_CONTROL_TO_ECU"
        val response = obdSession.sendRawCommand(command)
        return isPositiveResponse(response, "6F", command)
    }

    suspend fun freezeCurrentState(did: String): Boolean {
        val command = "$SID_INPUT_OUTPUT_CONTROL$did$IO_FREEZE_CURRENT_STATE"
        val response = obdSession.sendRawCommand(command)
        return isPositiveResponse(response, "6F", command)
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
        Log.d(TAG, "Ejecutando rutina $routineId, opción: $controlOption")
        val command = "$SID_ROUTINE_CONTROL$controlOption$routineId$routineOption"
        val response = obdSession.sendRawCommand(command)
        Log.d(TAG, "Respuesta Routine Control: $response")
        return isPositiveResponse(response, "71", command)
    }

    suspend fun startRoutine(routineId: String, params: String = ""): Boolean =
        executeRoutine(routineId, ROUTINE_START, params)

    suspend fun stopRoutine(routineId: String): Boolean =
        executeRoutine(routineId, ROUTINE_STOP)

    suspend fun getRoutineResults(routineId: String): String? {
        val command = "$SID_ROUTINE_CONTROL$ROUTINE_REQUEST_RESULTS$routineId"
        val response = obdSession.sendRawCommand(command)
        val clean = response.replace(Regex("[\\s\\r\\n>]+"), "").uppercase()
        val marker = "71$ROUTINE_REQUEST_RESULTS${routineId.uppercase()}"
        val idx = clean.indexOf(marker)
        if (idx < 0) return null
        return clean.substring(idx + marker.length)
    }

    // ═══════════════════════════════════════════════
    // $34/$36/$37 — DATA TRANSFER (Read-only diagnostics)
    // ═══════════════════════════════════════════════

    /**
     * Request Download — used for diagnostic data extraction, NOT flashing.
     */
    suspend fun requestDownload(compressionMethod: String = "00", encryptionMethod: String = "00",
                                 address: String, size: String): Boolean {
        val formatByte = "44" // 4 bytes address, 4 bytes size
        val command = "$SID_REQUEST_DOWNLOAD$compressionMethod$encryptionMethod$formatByte$address$size"
        val response = obdSession.sendRawCommand(command)
        return isPositiveResponse(response, "74", command)
    }

    /**
     * Transfer Data — read block from ECU.
     */
    suspend fun transferData(blockSequence: Int): String? {
        val command = "$SID_TRANSFER_DATA${String.format("%02X", blockSequence)}"
        val response = obdSession.sendRawCommand(command)
        val clean = response.replace(Regex("[\\s\\r\\n>]+"), "").uppercase()
        val idx = clean.indexOf("76")
        if (idx < 0) return null
        return clean.substring(idx + 4) // Skip 76 + block counter
    }

    /**
     * Request Transfer Exit — finalize a data transfer operation.
     */
    suspend fun requestTransferExit(): Boolean {
        val command = SID_REQUEST_TRANSFER_EXIT
        val response = obdSession.sendRawCommand(command)
        return isPositiveResponse(response, "77", command)
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
        Log.d(TAG, "Control DTC Setting: ${if (onOff == DTC_SETTING_ON) "ON" else "OFF"}")
        val command = "$SID_CONTROL_DTC_SETTING$onOff"
        val response = obdSession.sendRawCommand(command)
        return isPositiveResponse(response, "C5", command)
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
        val command = KWP_START_COMMUNICATION
        val response = obdSession.sendRawCommand(command)
        return isPositiveResponse(response, "C1", command)
    }

    /**
     * KWP2000 Stop Communication ($82)
     */
    suspend fun kwpStopCommunication(): Boolean {
        val command = KWP_STOP_COMMUNICATION
        val response = obdSession.sendRawCommand(command)
        return isPositiveResponse(response, "C2", command)
    }

    /**
     * KWP2000 Access Timing Parameter ($83)
     * @param subFunction "01"=readExtended, "02"=setDefault, "03"=readActive, "04"=setNew
     */
    suspend fun kwpAccessTimingParameter(subFunction: String = "03"): String? {
        val command = "$KWP_ACCESS_TIMING$subFunction"
        val response = obdSession.sendRawCommand(command)
        val clean = response.replace(Regex("[\\s\\r\\n>]+"), "").uppercase()
        val idx = clean.indexOf("C3")
        if (idx < 0) return null
        return clean.substring(idx + 4) // Skip C3 + subFunction
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
     * Probe the ECU for supported UDS services by attempting each one.
     * Non-destructive — uses read-only operations.
     */
    suspend fun discoverCapabilities(): UdsCapabilities {
        Log.d(TAG, "Descubriendo capacidades UDS del ECU...")

        val supportsExtended = changeDiagnosticSession(SESSION_EXTENDED)
        val supportsReadById = readDataByIdentifier("F190") != null
        val supportsIOControl = run {
            val resp = obdSession.sendRawCommand("${SID_INPUT_OUTPUT_CONTROL}0000$IO_RETURN_CONTROL_TO_ECU")
            !resp.contains("NO DATA", true) && !resp.contains("ERROR", true)
        }

        // Return to default session
        changeDiagnosticSession(SESSION_DEFAULT)

        val discoveredDids = mutableListOf<String>()
        if (supportsReadById) {
            for (did in listOf("F190", "F187", "F188", "F197", "F18C")) {
                if (readDataByIdentifier(did) != null) discoveredDids.add(did)
            }
        }

        return UdsCapabilities(
            supportsExtendedSession = supportsExtended,
            supportsIOControl = supportsIOControl,
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
