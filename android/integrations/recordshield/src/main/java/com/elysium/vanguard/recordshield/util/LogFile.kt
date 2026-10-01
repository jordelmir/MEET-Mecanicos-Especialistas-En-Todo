package com.elysium.vanguard.recordshield.util

import android.content.Context
import com.elysium.vanguard.recordshield.util.SafeLog as Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LogFile {
    private var file: File? = null
    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun init(context: Context) {
        file = null
    }

    fun log(tag: String, msg: String) {
        // Evidence details and file paths must not persist in diagnostic logs.
    }

    fun getLogFile(): File? = file
}
