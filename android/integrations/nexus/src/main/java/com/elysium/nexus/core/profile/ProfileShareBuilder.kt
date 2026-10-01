package com.elysium.nexus.core.profile

import java.util.Locale

/**
 * Pure builder that turns a [Profile] into a
 * [ProfileShare] artifact.
 *
 * The builder is the JVM-testeable half of the
 * §15 export story. The Android half lives in
 * [AndroidProfileShareLauncher].
 *
 * The filename convention is
 * `elysium-profile-{id}-{slug}.json` where `slug`
 * is the profile's name lower-cased, ASCII-only,
 * with runs of non-alphanumeric characters
 * replaced by `-`. The slug is capped at 32
 * characters; an empty slug is replaced by
 * `untitled`. The id is the profile's database id.
 *
 * The MIME type is the standard
 * `application/vnd.elysium.profile+json` (a
 * vendor-specific subtype of JSON). The MIME type
 * is the one that a fully-typed Android share
 * sheet will surface as a "Profile" target.
 *
 * ## Why a `slug` for the filename
 *
 * The profile's `name` is user-typed, free-form,
 * and possibly non-ASCII. A slug is the simplest
 * way to keep the filename portable across
 * filesystems (no spaces, no special characters,
 * a sane length cap). The original name lives
 * inside the JSON document; the filename is
 * presentation only.
 *
 * ## Why we now sign here
 *
 * The §15 signature is a JSON field. The
 * builder is the natural seam: the share
 * payload is the JSON; the signature is
 * the HMAC-SHA256 of the JSON. The
 * builder takes the author's secret as
 * a parameter and embeds the signature
 * in the payload. An unsigned share is
 * still possible (the legacy
 * [build] overload) for the dev / test
 * paths; the production code calls the
 * signing overload.
 */
object ProfileShareBuilder {

    /** The MIME type emitted by [build]. */
    const val MIME_TYPE: String = "application/vnd.elysium.profile+json"

    /**
     * Build an **unsigned** [ProfileShare].
     * The function is total; the [Profile]'s
     * `init` block has already validated the
     * bounds, rotation, and opacity.
     *
     * The unsigned path is the legacy entry
     * point; new code should call [buildSigned]
     * (the production entry point).
     */
    fun build(profile: Profile): ProfileShare {
        val json = ProfileJson.toJson(profile)
        val filename = filenameFor(profile)
        return ProfileShare(
            filename = filename,
            mimeType = MIME_TYPE,
            content = json
        )
    }

    /**
     * Build a **signed** [ProfileShare]. The
     * signature is an HMAC-SHA256 of the JSON
     * payload, keyed by [secret]. The signature
     * is embedded in the JSON as a
     * [ProfileJson.SignatureField].
     *
     * The [secret] is the author's per-user
     * secret (the production key is in the
     * Android Keystore; the test path uses
     * a hand-rolled byte array). The secret
     * is never serialised; the JSON only
     * carries the *signature*, not the
     * secret.
     */
    fun buildSigned(profile: Profile, secret: ByteArray): ProfileShare {
        val baseJson = ProfileJson.toJson(profile)
        val signature = ProfileSignature.sign(profile, secret)
        val signedJson = embedSignature(baseJson, signature)
        val filename = filenameFor(profile)
        return ProfileShare(
            filename = filename,
            mimeType = MIME_TYPE,
            content = signedJson
        )
    }

    /**
     * Embed the [signature] into [baseJson] as
     * a `signature` field. The base JSON is the
     * canonical serialisation (the signature
     * is computed on the base, not on the
     * signed). The signature is added at the
     * **end** of the JSON object so the
     * canonical serialisation is unchanged.
     *
     * The function is hand-rolled because
     * `org.json.JSONObject.put` on an existing
     * object reorders keys, and the §15
     * `parseWithSignature` is order-insensitive
     * but the §15 round-trip test asserts the
     * *absence* of `signature` in the canonical
     * serialisation.
     */
    private fun embedSignature(baseJson: String, signature: String): String {
        require(!baseJson.contains("\"signature\"")) {
            "embedSignature: base JSON must not already contain a signature field."
        }
        // The base JSON is `{ "schemaVersion": 1, ... }`.
        // We insert the signature as the last
        // key (a single string append of
        // `, "signature": {...}` before the
        // closing `}`).
        val closingBrace = baseJson.lastIndexOf('}')
        require(closingBrace > 0) {
            "embedSignature: base JSON is malformed (no closing brace)."
        }
        val sigField = ",\"signature\":{\"algorithm\":\"HmacSHA256\",\"value\":\"$signature\"}"
        return baseJson.substring(0, closingBrace) + sigField + baseJson.substring(closingBrace)
    }

    /**
     * Compute the share filename for a profile.
     *
     * The filename is the public surface of the
     * artifact: visible in the user's "Recent
     * files" list, in the share sheet, and in the
     * "save to drive" target. The function is
     * exposed so callers (e.g. tests, importers)
     * can derive the same filename without going
     * through the full builder.
     */
    fun filenameFor(profile: Profile): String {
        val slug = slugOf(profile.name)
        return "elysium-profile-${profile.id}-$slug.json"
    }

    /**
     * Compute the slug of a profile name. The slug
     * is lower-case ASCII, runs of non-alphanumeric
     * characters become a single `-`, and the
     * result is capped at 32 characters. An empty
     * slug is replaced by `untitled`.
     *
     * Exposed for the test suite.
     */
    fun slugOf(name: String): String {
        val lowered = name.lowercase(Locale.ROOT)
        val builder = StringBuilder()
        var lastWasDash = false
        for (ch in lowered) {
            // We accept ASCII letters and digits
            // (the portable set). Everything else
            // (whitespace, punctuation, non-ASCII
            // letters) is a *separator*: it does not
            // appear in the slug; it triggers a dash
            // if the buffer is non-empty and we have
            // not just appended a dash. A separator
            // at the start of the name (or after a
            // run of separators) is dropped silently.
            val isAsciiAlnum = ch.isLetterOrDigit() && ch.code < 128
            if (isAsciiAlnum) {
                builder.append(ch)
                lastWasDash = false
            } else {
                if (builder.isNotEmpty() && !lastWasDash) {
                    builder.append('-')
                    lastWasDash = true
                }
            }
        }
        // Trim trailing dashes.
        while (builder.isNotEmpty() && builder.last() == '-') {
            builder.deleteCharAt(builder.length - 1)
        }
        val capped = if (builder.length > 32) builder.substring(0, 32) else builder.toString()
        return capped.ifEmpty { "untitled" }
    }
}
