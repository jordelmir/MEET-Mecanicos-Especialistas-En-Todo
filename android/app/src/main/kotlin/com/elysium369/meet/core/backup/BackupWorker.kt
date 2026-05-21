package com.elysium369.meet.core.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        android.util.Log.i("BackupWorker", "Starting automated background database backup to Google Drive...")
        
        val backupManager = GoogleDriveBackupManager(applicationContext)
        val result = backupManager.performBackup()
        
        return if (result.isSuccess) {
            android.util.Log.i("BackupWorker", "Automated background backup completed successfully. File ID: ${result.getOrNull()}")
            Result.success()
        } else {
            val error = result.exceptionOrNull()
            android.util.Log.e("BackupWorker", "Automated background backup failed: ${error?.message}", error)
            Result.retry()
        }
    }
}
