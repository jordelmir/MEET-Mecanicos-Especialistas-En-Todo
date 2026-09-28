package com.elysium.nexus.core.profile

/**
 * A self-contained, JVM-testeable description of a
 * shareable profile artifact.
 *
 * `MASTER_ORDER.md` §15 calls for a profile to be
 * exportable as a JSON document; Phase 1.17 wires
 * the share intent so the user can send the artifact
 * to other apps (e-mail, drive, Bluetooth, …). The
 * artifact is described here as pure data so the
 * builder and the round-trip can be unit-tested
 * without an Android `Context`.
 *
 * The artifact is intentionally separate from the
 * Android [`Intent`][android.content.Intent]: the
 * same artifact can be used for *any* transport
 * (share sheet, copy to clipboard, write to a
 * file, send over the Elysium Link, etc.). The
 * Android adapter
 * [com.elysium.nexus.core.profile.AndroidProfileShareLauncher]
 * turns the artifact into an `Intent`.
 *
 * ## Why a `data class` and not a richer type
 *
 * The artifact is three things: a filename, a MIME
 * type, a payload. Anything more (compression,
 * signing, encryption) is layered on top of this
 * shape; the §15 signing milestone is a JSON field,
 * not an artifact field.
 */
data class ProfileShare(
    val filename: String,
    val mimeType: String,
    val content: String
) {
    init {
        require(filename.isNotBlank()) {
            "ProfileShare.filename must not be blank."
        }
        require(mimeType.isNotBlank()) {
            "ProfileShare.mimeType must not be blank."
        }
        // The content can be empty if the profile has
        // no controls; the JSON serialiser still
        // emits a valid empty-controls document.
    }
}
