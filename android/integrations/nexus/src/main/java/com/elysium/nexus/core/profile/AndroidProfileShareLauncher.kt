package com.elysium.nexus.core.profile

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * The Android adapter for the §15 profile export
 * story.
 *
 * `ProfileShareBuilder` produces a [ProfileShare]
 * artifact (pure data, JVM-testeable). This class
 * turns the artifact into an Android
 * [`Intent`][Intent] that the share sheet can
 * surface to other apps (e-mail, drive, Bluetooth,
 * clipboard, …).
 *
 * The artifact is written to the app's *cache*
 * directory (not the *files* directory): the
 * artifact is ephemeral, the cache is wiped by the
 * OS on storage pressure, and we do not want
 * profile documents to leak into the user's long-
 * term storage when the user does not explicitly
 * save them.
 *
 * The `content://` URI is provided by
 * [FileProvider] (configured in the manifest with
 * `res/xml/file_paths.xml`). The grant is
 * `FLAG_GRANT_READ_URI_PERMISSION`: the receiving
 * app can read the file for the lifetime of its
 * stack, but cannot write to it.
 *
 * ## Why FileProvider and not a raw `file://` URI
 *
 * Raw `file://` URIs throw
 * `FileUriExposedException` from API 24+ (the
 * "scoped storage" enforcement). FileProvider is
 * the only supported way to share a file with
 * another app.
 *
 * ## Why a class and not a top-level function
 *
 * The launcher has exactly one dependency
 * ([Context]) and zero state. A class makes it
 * easy to swap in a fake launcher in tests and
 * makes the call site (`onShareProfile = {
 * launcher.launch(...) }`) read like a sentence.
 */
class AndroidProfileShareLauncher(
    private val context: Context
) {
    private val tag = "ElysiumNexus.Share"

    /**
     * Write [share] to the cache and return the
     * `ACTION_SEND` Intent for the share sheet.
     *
     * The function does *not* call `startActivity`;
     * the caller decides when to surface the
     * chooser. The typical call site is:
     *
     * ```kotlin
     * val intent = launcher.launch(share)
     * activity.startActivity(Intent.createChooser(intent, "Share profile"))
     * ```
     *
     * On any I/O failure the function returns
     * `null`; the caller is expected to log and
     * surface a user-visible error (a snackbar, a
     * toast, …) and to *not* crash the activity.
     * The §38 release-blocker discipline applies:
     * the share path must never take down the
     * activity.
     */
    fun launch(share: ProfileShare, chooserTitle: String = "Share profile"): Intent? {
        return try {
            val cacheDir = File(context.cacheDir, "shares").apply { mkdirs() }
            val outFile = File(cacheDir, share.filename)
            outFile.writeText(share.content, Charsets.UTF_8)
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, outFile)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = share.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, share.filename)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Log.i(
                tag,
                "Prepared share for ${share.filename} (${share.content.length} bytes) at $uri"
            )
            Intent.createChooser(send, chooserTitle)
        } catch (e: Throwable) {
            Log.w(tag, "Profile share failed for ${share.filename}", e)
            null
        }
    }
}
