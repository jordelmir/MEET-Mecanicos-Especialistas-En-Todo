package com.elysium.nexus.databases.compatibility

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.elysium.nexus.core.compat.CompatibilityStatus

/**
 * The Room entity for the §33 compatibility database.
 *
 * Room requires:
 *  - a no-arg constructor (or a constructor that defaults
 *    every field);
 *  - a `@PrimaryKey` field;
 *  - every field is a primitive, a String, or a TypeConverter-
 *    supported type.
 *
 * The [CompatibilityResult] data class in `core/compat/` is
 * the *domain* shape: it has the validation rules, the
 * `VERIFIED_LAB`-rejects-failures invariant, and the §33
 * fields. The Room entity is the *storage* shape: it
 * mirrors the domain shape with a Room-friendly `id` primary
 * key (an auto-generated `Long`) and a single index on
 * `(deviceId, targetPlatform, date)` for the "latest record
 * for a device + target" query.
 *
 * ## Why a separate entity, not a Room-annotated `CompatibilityResult`
 *
 * The domain `CompatibilityResult` lives in `core/compat/`
 * which is pure-Kotlin and has no Android types. Annotating
 * the domain with `@Entity` would couple it to the
 * Android SDK and break the JVM test surface. The
 * conversion from domain to entity lives in
 * [CompatibilityRepository.add] / [CompatibilityRepository.all].
 *
 * ## Why the index
 *
 * §33's "latest" query is `SELECT * FROM compatibility
 * WHERE deviceId = ? AND targetPlatform = ? ORDER BY date
 * DESC LIMIT 1`. The `(deviceId, targetPlatform)` prefix
 * is the most selective; the `date` is the tie-breaker.
 * The composite index covers both the WHERE and the
 * ORDER BY in a single B-tree walk.
 */
@Entity(
    tableName = "compatibility",
    indices = [
        androidx.room.Index(
            value = ["deviceId", "targetPlatform", "date"],
            name = "idx_compatibility_device_target_date"
        )
    ]
)
data class CompatibilityEntity(
    /** Auto-generated primary key. */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** The §33 device identifier. */
    val deviceId: String,

    /** The §33 device model name. */
    val deviceModel: String,

    /** The Android version on the device. */
    val androidVersion: String,

    /** The OEM firmware version. */
    val oemFirmware: String,

    /** The transport used for the test. */
    val transport: String,

    /** The target platform. */
    val targetPlatform: String,

    /** The target's OS / firmware. */
    val targetOsFirmware: String,

    /** The game / application, if any. */
    val game: String?,

    /** Capabilities tested (semicolon-separated; parsed by the converter). */
    val capabilitiesTested: String,

    /** Capabilities passed. */
    val capabilitiesPassed: String,

    /** Capabilities failed. */
    val capabilitiesFailed: String,

    /** p50 latency in nanoseconds, if measured. */
    val latencyP50Ns: Long?,

    /** p95 latency in nanoseconds, if measured. */
    val latencyP95Ns: Long?,

    /** The tester. */
    val tester: String,

    /** The test date in ISO-8601 (YYYY-MM-DD). */
    val date: String,

    /** A pointer to evidence. */
    val evidence: String?,

    /** The test's confidence in `[0, 100]`. */
    val confidence: Int,

    /** The §33 headline status. */
    val status: CompatibilityStatus
)
