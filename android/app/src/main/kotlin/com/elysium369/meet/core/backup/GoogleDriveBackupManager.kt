package com.elysium369.meet.core.backup

import android.content.Context
import android.os.Build
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class GoogleDriveBackupManager(private val context: Context) {

    private val dbName = "meet_database"
    private val backupFileName = "meet_backup.zip"

    // Set up Google Sign In options requesting Drive AppData folder access (hidden from user)
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()

    private val googleSignInClient = GoogleSignIn.getClient(context, gso)

    /**
     * Get the active signed-in Google account, if any.
     */
    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Build the authenticated Google Drive Service instance.
     */
    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_APPDATA)
        ).apply {
            selectedAccount = account.account
        }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Elysium Vanguard").build()
    }

    /**
     * Archive active Room database files (db, wal, shm) into a single ZIP archive.
     */
    private suspend fun createDatabaseZip(tempZipFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(dbName)
        val walFile = context.getDatabasePath("$dbName-wal")
        val shmFile = context.getDatabasePath("$dbName-shm")

        if (!dbFile.exists()) {
            android.util.Log.e("BackupManager", "Database file does not exist, cannot backup")
            return@withContext false
        }

        val filesToZip = listOfNotNull(
            dbFile,
            if (walFile.exists()) walFile else null,
            if (shmFile.exists()) shmFile else null
        )

        try {
            ZipOutputStream(FileOutputStream(tempZipFile)).use { zos ->
                byteBuffer.use { buffer ->
                    for (file in filesToZip) {
                        FileInputStream(file).use { fis ->
                            zos.putNextEntry(ZipEntry(file.name))
                            var length: Int
                            val byteBufferArray = ByteArray(4096)
                            while (fis.read(byteBufferArray).also { length = it } >= 0) {
                                zos.write(byteBufferArray, 0, length)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("BackupManager", "Failed to create backup ZIP archive", e)
            false
        }
    }

    private val byteBuffer = object : AutoCloseable {
        override fun close() {}
    }

    /**
     * Upload the database backup ZIP file to Google Drive under the application's hidden AppData folder.
     */
    suspend fun performBackup(): Result<String> = withContext(Dispatchers.IO) {
        val account = getSignedInAccount()
            ?: return@withContext Result.failure(Exception("User is not signed in to Google"))

        val tempZip = java.io.File(context.cacheDir, "meet_backup_temp.zip")
        if (!createDatabaseZip(tempZip)) {
            return@withContext Result.failure(Exception("Failed to zip database files"))
        }

        try {
            val drive = getDriveService(account)

            // Search for existing backups in appDataFolder
            val queryResult = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$backupFileName'")
                .setFields("files(id)")
                .execute()

            val existingFiles = queryResult.files
            val fileMetadata = File().apply {
                name = backupFileName
                parents = Collections.singletonList("appDataFolder")
            }
            val mediaContent = FileContent("application/zip", tempZip)

            val uploadedFileId: String
            if (!existingFiles.isNullOrEmpty()) {
                // Update existing backup file
                val existingFileId = existingFiles[0].id
                val updatedFile = drive.files().update(existingFileId, null, mediaContent).execute()
                uploadedFileId = updatedFile.id
                android.util.Log.i("BackupManager", "Successfully updated existing backup with ID: $uploadedFileId")
            } else {
                // Create new backup file
                val createdFile = drive.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                uploadedFileId = createdFile.id
                android.util.Log.i("BackupManager", "Successfully created new backup with ID: $uploadedFileId")
            }

            // Save last backup timestamp locally
            context.getSharedPreferences("meet_backup_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_backup_time", System.currentTimeMillis())
                .apply()

            Result.success(uploadedFileId)
        } catch (e: Exception) {
            android.util.Log.e("BackupManager", "Google Drive backup failed", e)
            Result.failure(e)
        } finally {
            if (tempZip.exists()) {
                tempZip.delete()
            }
        }
    }

    /**
     * Checks if a backup file exists in the user's Google Drive appDataFolder and returns the modification time.
     */
    suspend fun checkRemoteBackup(): Result<Long?> = withContext(Dispatchers.IO) {
        val account = getSignedInAccount()
            ?: return@withContext Result.failure(Exception("User is not signed in to Google"))

        try {
            val drive = getDriveService(account)
            val queryResult = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$backupFileName'")
                .setFields("files(id, modifiedTime)")
                .execute()

            val existingFiles = queryResult.files
            if (!existingFiles.isNullOrEmpty()) {
                val time = existingFiles[0].modifiedTime.value
                Result.success(time)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            android.util.Log.e("BackupManager", "Failed to check remote backup on Google Drive", e)
            Result.failure(e)
        }
    }

    /**
     * Download the backup file from Drive and restore it locally, overwriting the Room database.
     */
    suspend fun performRestore(): Result<Boolean> = withContext(Dispatchers.IO) {
        val account = getSignedInAccount()
            ?: return@withContext Result.failure(Exception("User is not signed in to Google"))

        try {
            val drive = getDriveService(account)
            val queryResult = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$backupFileName'")
                .setFields("files(id)")
                .execute()

            val existingFiles = queryResult.files
            if (existingFiles.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No backup file found in Google Drive appDataFolder"))
            }

            val fileId = existingFiles[0].id
            val tempZip = java.io.File(context.cacheDir, "meet_restore_temp.zip")

            // Download backup
            FileOutputStream(tempZip).use { fos ->
                drive.files().get(fileId).executeMediaAndDownloadTo(fos)
            }

            // Restore from ZIP
            val success = unzipDatabase(tempZip)
            Result.success(success)
        } catch (e: Exception) {
            android.util.Log.e("BackupManager", "Google Drive restore failed", e)
            Result.failure(e)
        }
    }

    /**
     * Extracts Room database files from the downloaded ZIP and replaces the active database.
     */
    private fun unzipDatabase(zipFile: java.io.File): Boolean {
        val dbDir = context.getDatabasePath(dbName).parentFile ?: return false
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }

        // Close the database helper to avoid file locks
        try {
            // Trigger DB instance closing before replacing files.
            // (The calling ViewModel should close database access points or restart the app).
            context.deleteDatabase(dbName)
        } catch (e: Exception) {
            android.util.Log.w("BackupManager", "Failed to delete existing database files before unzip", e)
        }

        return try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val outputFile = java.io.File(dbDir, entry.name)
                    FileOutputStream(outputFile).use { fos ->
                        val buffer = ByteArray(4096)
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            android.util.Log.i("BackupManager", "Database files successfully restored from backup ZIP")
            true
        } catch (e: IOException) {
            android.util.Log.e("BackupManager", "Failed to extract database files from ZIP archive", e)
            false
        } finally {
            if (zipFile.exists()) {
                zipFile.delete()
            }
        }
    }
}
