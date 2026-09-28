package com.elysium.nexus.core.profile

/**
 * The result of a [ProfileImporter.import] call.
 *
 * The importer is total: every input produces a
 * result. The result is either [Success] (a
 * fully-validated [Profile]) or [Failure]
 * (a reason the import was rejected).
 *
 * The shape mirrors the standard "parse, don't
 * validate" pattern: the [Success] is a real
 * [Profile] (the data class's `init` block has
 * already validated the bounds, the rotation,
 * the opacity). The [Failure] is a typed
 * envelope; the caller surfaces the reason to
 * the user.
 */
sealed class ProfileImportResult {
    /** The import succeeded; the [profile] is ready to persist. */
    data class Success(val profile: Profile) : ProfileImportResult()

    /**
     * The import failed; the [reason] is a
     * human-readable description of the failure
     * (and is also safe to log). The [cause] is
     * the underlying exception, if any; the field
     * is non-null when the failure is an
     * `IllegalArgumentException` from a parser
     * (e.g. `ProfileJson.fromJson`).
     */
    data class Failure(
        val reason: String,
        val cause: Throwable? = null
    ) : ProfileImportResult()
}

/**
 * The §15 profile import story.
 *
 * The importer is the symmetric counterpart to
 * [ProfileShareBuilder]. The builder turns a
 * [Profile] into a [ProfileShare] (a JSON
 * document); the importer turns a JSON document
 * into a [Profile]. The two halves are
 * mirror-images: the builder is total, the
 * importer is also total, the round-trip is
 * `Profile → JSON → Profile'` with
 * `Profile == Profile'` (modulo a fresh
 * `createdAt` / `updatedAt`).
 *
 * The importer does **not** assign a new `id`;
 * the repository owns the id strategy. The
 * importer returns the JSON's `id` as the
 * profile's `id` (the caller may overwrite
 * with `repository.nextId()` to avoid
 * collisions). The §15 "import preserves
 * identity" milestone is a follow-up; for now,
 * the caller decides.
 *
 * The importer stamps `createdAt` and
 * `updatedAt` with the import time. The
 * rationale: the JSON's timestamps are the
 * source's local clock; the receiver's
 * "this profile was created at" should be
 * the local clock of the moment the
 * receiver added the profile.
 *
 * ## Signature verification
 *
 * A signed JSON carries a `signature` field
 * (per `ProfileJson.SignatureField`). When
 * the importer sees the field, it verifies
 * the signature against the parsed
 * [Profile] using the supplied [secret]. A
 * signature mismatch is a [Failure] — the
 * profile is rejected. The §15 "import
 * verifies signature" milestone is now
 * complete: signed profiles are accepted
 * only when the signature matches; unsigned
 * profiles are accepted (back-compat with
 * Phase 1.17).
 *
 * ## Why a result envelope and not a `try` / `catch`
 *
 * The [ProfileJson.fromJson] function throws on
 * schema mismatch, on unrecognised control
 * types, on unrecognised bindings. Wrapping the
 * call in a `try` / `catch` and re-throwing as
 * a [ProfileImportResult.Failure] is the
 * standard "translate exceptions to typed
 * errors" pattern. The agent-memory rule
 * (`Result<Throwable>` vs typed error
 * envelopes) is exactly the same: a typed
 * envelope is a stronger contract than a
 * `Result<Throwable>`.
 */
object ProfileImporter {

    /**
     * Import a [Profile] from a JSON document.
     * The function does **not** verify a
     * signature; use [importSigned] for the
     * signature-verifying path. The unsigned
     * path is back-compat with the Phase 1.17
     * share format.
     *
     * The function is total: every input
     * produces a [ProfileImportResult]. The
     * [now] parameter is the local clock at the
     * moment of import; the default is
     * `System.currentTimeMillis()`.
     */
    fun import(json: String, now: Long = System.currentTimeMillis()): ProfileImportResult {
        val parsed: ProfileJson.ParsedProfile
        try {
            parsed = ProfileJson.parseWithSignature(json)
        } catch (e: IllegalArgumentException) {
            return ProfileImportResult.Failure(
                reason = e.message ?: "Invalid profile JSON",
                cause = e
            )
        } catch (e: Throwable) {
            return ProfileImportResult.Failure(
                reason = "Malformed profile JSON: ${e.message ?: e::class.simpleName}",
                cause = e
            )
        }
        // The profile is validated by
        // [ProfileJson.parseProfileFields] +
        // [Profile.init] (name, author,
        // version, timestamps).
        return try {
            val imported = parsed.profile.copy(
                createdAt = now,
                updatedAt = now
            )
            ProfileImportResult.Success(imported)
        } catch (e: IllegalArgumentException) {
            ProfileImportResult.Failure(
                reason = e.message ?: "Imported profile failed validation",
                cause = e
            )
        }
    }

    /**
     * Import a [Profile] from a **signed** JSON
     * document. The signature is verified
     * against the parsed [Profile] using
     * [secret]. A signature mismatch is a
     * [ProfileImportResult.Failure] with the
     * reason "signature mismatch".
     *
     * The function is total: every input
     * produces a result. An unsigned JSON
     * returns a [ProfileImportResult.Failure]
     * with the reason "profile is not signed";
     * a signed JSON with a wrong signature
     * returns [ProfileImportResult.Failure]
     * with the reason "signature mismatch".
     */
    fun importSigned(
        json: String,
        secret: ByteArray,
        now: Long = System.currentTimeMillis()
    ): ProfileImportResult {
        val parsed: ProfileJson.ParsedProfile
        try {
            parsed = ProfileJson.parseWithSignature(json)
        } catch (e: IllegalArgumentException) {
            return ProfileImportResult.Failure(
                reason = e.message ?: "Invalid profile JSON",
                cause = e
            )
        } catch (e: Throwable) {
            return ProfileImportResult.Failure(
                reason = "Malformed profile JSON: ${e.message ?: e::class.simpleName}",
                cause = e
            )
        }
        val signature = parsed.signature
            ?: return ProfileImportResult.Failure(
                reason = "Profile is not signed; use import() for unsigned profiles."
            )
        if (!ProfileSignature.verify(parsed.profile, signature.value, secret)) {
            return ProfileImportResult.Failure(
                reason = "Signature mismatch: the profile was tampered with " +
                    "or the secret is wrong."
            )
        }
        return try {
            val imported = parsed.profile.copy(
                createdAt = now,
                updatedAt = now
            )
            ProfileImportResult.Success(imported)
        } catch (e: IllegalArgumentException) {
            ProfileImportResult.Failure(
                reason = e.message ?: "Imported profile failed validation",
                cause = e
            )
        }
    }
}
