package com.elysium369.meet.core.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

enum class DtcBrowserLaunchResult {
    OPENED,
    NO_BROWSER,
    BLOCKED
}

/**
 * Opens the device's default HTTPS browser without coupling MEET to Chrome or
 * any other browser package.
 */
object DtcBrowserSearchLauncher {
    fun open(
        context: Context,
        request: DtcGoogleSearchRequest
    ): DtcBrowserLaunchResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(request.googleUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return try {
            context.startActivity(intent)
            DtcBrowserLaunchResult.OPENED
        } catch (_: ActivityNotFoundException) {
            DtcBrowserLaunchResult.NO_BROWSER
        } catch (_: SecurityException) {
            DtcBrowserLaunchResult.BLOCKED
        }
    }
}
