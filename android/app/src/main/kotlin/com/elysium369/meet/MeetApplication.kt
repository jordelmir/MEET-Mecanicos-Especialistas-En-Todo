package com.elysium369.meet

import android.app.Application
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.elysium369.meet.core.obd.DtcDatabaseLoader
import com.elysium369.meet.data.local.MeetDatabase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MeetApplication : Application(), Configuration.Provider {

    @Inject lateinit var db: MeetDatabase
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Load DTC JSON if empty
        DtcDatabaseLoader(this, db).loadIfEmpty()
    }
}
