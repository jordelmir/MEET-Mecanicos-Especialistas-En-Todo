package com.elysium369.meet

import android.app.Application
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.hilt.work.HiltWorkerFactory
import com.elysium369.meet.core.maintenance.DatabaseMaintenanceWorker
import com.elysium369.meet.core.obd.DtcDatabaseLoader
import com.elysium369.meet.data.local.MeetDatabase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class MeetApplication : Application(), Configuration.Provider {

    @Inject lateinit var db: MeetDatabase
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val applicationScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
    )

    override fun onCreate() {
        super.onCreate()
        
        // Initialize custom theme colors
        com.elysium369.meet.ui.theme.MeetColors.initialize(this)
        
        // Load DTC JSON if empty
        val dtcLoader = DtcDatabaseLoader(this, db)
        dtcLoader.loadIfEmpty()
        dtcLoader.loadKnowledgeGraphIfEmpty()

        // ── Schedule automatic database maintenance (daily) ──
        scheduleDatabaseMaintenance()

        // Elite Cloud Dynamic Update Integration
        applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.elysium369.meet.core.sync.ElysiumCloudServices.syncDtcDefinitionsFromCloud(db.dtcDefinitionDao())
                com.elysium369.meet.core.sync.ElysiumCloudServices.syncCommunityCustomPIDs(db.customPidDao())
            } catch (e: Exception) {
                android.util.Log.e("MeetApplication", "Error during automatic cloud dynamic update", e)
            }
        }
    }

    /**
     * Schedules the DatabaseMaintenanceWorker to run once every 24 hours.
     *
     * Constraints:
     *   - Device must NOT be low on battery (avoid draining user's phone)
     *
     * Policy: KEEP — if a schedule already exists from a prior launch, keep it.
     * This makes the call idempotent and safe on every onCreate().
     */
    private fun scheduleDatabaseMaintenance() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val maintenanceRequest = PeriodicWorkRequestBuilder<DatabaseMaintenanceWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS) // Don't run immediately on first install
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DatabaseMaintenanceWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            maintenanceRequest
        )

        android.util.Log.i("MeetApplication", "📋 Database maintenance scheduled (daily, battery-safe)")
    }
}
