package com.elysium369.meet

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.elysium369.meet.core.obd.DtcDatabaseLoader
import com.elysium369.meet.data.local.MeetDatabase
import javax.inject.Inject

@HiltAndroidApp
class MeetApplication : Application() {

    @Inject lateinit var db: MeetDatabase

    override fun onCreate() {
        super.onCreate()
        
        // Load DTC JSON if empty
        DtcDatabaseLoader(this, db).loadIfEmpty()
    }
}
