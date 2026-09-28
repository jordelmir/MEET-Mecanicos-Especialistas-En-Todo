package com.elysium.nexus.fabric.profile.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * §36 durable scene storage.
 *
 * A scene is stored as its full-fidelity JSON payload
 * (see SceneJsonCodec) plus the fields needed for
 * listing and tag queries.
 */
@Entity(
    tableName = "scenes",
    indices = [Index(value = ["updatedAtEpochMs"])]
)
data class SceneEntity(
    @PrimaryKey val sceneId: String,
    val name: String,
    /** Full-fidelity JSON payload (SceneJsonCodec.encode). */
    val payloadJson: String,
    /** Comma-separated tags (also embedded in payloadJson). */
    val tagsCsv: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)