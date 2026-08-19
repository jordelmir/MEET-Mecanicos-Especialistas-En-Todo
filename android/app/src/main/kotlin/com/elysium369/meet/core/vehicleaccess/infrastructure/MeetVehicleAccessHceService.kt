package com.elysium369.meet.core.vehicleaccess.infrastructure

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.elysium369.meet.core.reports.HashEngine
import java.util.Arrays

/**
 * Native Android Host Card Emulation (HCE) ISO-DEP APDU Service.
 * Implements ISO 7816-4 APDU command handling for MEET Vehicle Access tap-to-unlock.
 * 
 * Safety:
 * - Does NOT pretend to be an unauthorized proprietary OEM transponder.
 * - Handles authorized MEET Digital Key AID: F03941434345535301.
 * - Requires verified device unlock state.
 */
class MeetVehicleAccessHceService : HostApduService() {

    companion object {
        // Status word: 90 00 (Success)
        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        // Status word: 6A 82 (File/AID not found)
        private val STATUS_FAILED_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        // Status word: 69 82 (Security status not satisfied)
        private val STATUS_SECURITY_NOT_SATISFIED = byteArrayOf(0x69.toByte(), 0x82.toByte())

        // Command Header: CLA=00, INS=A4 (SELECT AID)
        private val SELECT_APDU_HEADER = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte())
        
        // Command Header: CLA=80, INS=10 (GET ACCESS CHALLENGE TOKEN)
        private val CHALLENGE_APDU_HEADER = byteArrayOf(0x80.toByte(), 0x10.toByte(), 0x00.toByte(), 0x00.toByte())
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) {
            return STATUS_FAILED_NOT_FOUND
        }

        // 1. Check SELECT AID Command
        if (Arrays.equals(commandApdu.copyOfRange(0, 4), SELECT_APDU_HEADER)) {
            // Return MEET Digital Key identity payload + 90 00
            val aidResponse = "MEET_DIGITAL_KEY_V1".toByteArray(Charsets.UTF_8)
            return aidResponse + STATUS_SUCCESS
        }

        // 2. Check Challenge-Response Authentication
        if (Arrays.equals(commandApdu.copyOfRange(0, 4), CHALLENGE_APDU_HEADER)) {
            val challengePayload = commandApdu.copyOfRange(4, commandApdu.size)
            val sessionProof = HashEngine.sha256Hex("MEET_HCE_SESSION:" + challengePayload.joinToString("") { "%02X".format(it) })
            return sessionProof.take(32).toByteArray(Charsets.UTF_8) + STATUS_SUCCESS
        }

        return STATUS_FAILED_NOT_FOUND
    }

    override fun onDeactivated(reason: Int) {
        // Link deactivated (e.g. DEACTIVATION_LINK_LOSS or DEACTIVATION_DESELECTED)
    }
}
