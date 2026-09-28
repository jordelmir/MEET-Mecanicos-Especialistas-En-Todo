package com.elysium.nexus.databases.ir

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a learned IR command.
 *
 * Each row represents one IR signal captured
 * by the IR Learner and saved by the user.
 * The entity stores the protocol, address,
 * command, carrier frequency, and the raw
 * waveform pattern for replay.
 *
 * The [label] is a human-readable name
 * assigned by the user (e.g. "TV Power",
 * "AC Cool 24C"). The [templateId] links
 * the command to a device template in the
 * catalog.
 */
@Entity(tableName = "learned_ir_command")
data class LearnedIrCommandEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Human-readable name, e.g. "TV Power". */
    val label: String,

    /** Device template id from the catalog. */
    val templateId: String,

    /** Protocol name (NEC, Samsung, SonySirc, Rc5, etc.). */
    val protocolName: String,

    /** Device address (protocol-specific). */
    val address: Int,

    /** Button command code. */
    val command: Int,

    /** Carrier frequency in Hz. */
    val carrierHz: Int,

    /** Raw waveform pattern as comma-separated ints. */
    val rawPattern: String,

    /** Confidence score at capture time (0..1). */
    val confidence: Float,

    /** Wall-clock millis when captured. */
    val capturedAtMs: Long,

    /** Optional extras as JSON-like key=value pairs. */
    val extras: String = ""
)
