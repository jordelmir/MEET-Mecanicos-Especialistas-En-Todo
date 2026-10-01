package com.elysium.nexus.fabric.infrared.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.io.SequenceInputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.zip.GZIPInputStream

private const val TAG = "ElysiumNexus.DbManager"
private const val DB_DIR_NAME = "ir-catalog"
private const val BUILDS_DIR_NAME = "builds"
private const val DB_FILE_NAME = "ir_catalog.db"
private const val MANIFEST_FILE_NAME = "manifest.json"
private const val CURRENT_POINTER_NAME = "current"
private const val CURRENT_POINTER_TMP = "current.tmp"
private const val TEMP_FILE_NAME = "ir_catalog.db.tmp"

/**
 * V06-PTG-01 §2: Catalog installer — the manifest is the SINGLE authority.
 *
 * Layout (order §2):
 * ```
 * noBackupFilesDir/ir-catalog/
 *   builds/<catalogBuildId>/
 *     ir_catalog.db        ← verified database
 *     manifest.json        ← copy of the packaged manifest (the build's identity)
 *   current                ← pointer file: active catalogBuildId
 * ```
 *
 * Install guarantees:
 * - extraction → temp file → fsync → SHA-256 == manifest.databaseSha256
 * - SQLite quick_check + foreign_key_check + schema v5 table gate
 * - verified copy promoted to builds/<buildId>/, then pointer swapped atomically
 * - the previous build is NEVER deleted before the new one is verified:
 *   a kill mid-install leaves the old catalog fully valid (rollback by restart)
 * - no duplicated hash constant in code (manifest is the authority)
 */
class IrCatalogDatabaseManager private constructor(
    private val applicationContext: Context
) : Closeable {

    private val mutex = Mutex()
    private var repositoryInstance: IrCatalogRepository? = null

    val targetDirectory: File
        get() = File(applicationContext.noBackupFilesDir, DB_DIR_NAME).apply { if (!exists()) mkdirs() }

    private val buildsDirectory: File
        get() = File(targetDirectory, BUILDS_DIR_NAME).apply { if (!exists()) mkdirs() }

    private val currentPointerFile: File
        get() = File(targetDirectory, CURRENT_POINTER_NAME)

    /** Active build directory (null before first install). */
    private fun buildDirectory(buildId: String): File = File(buildsDirectory, buildId)

    fun currentBuildId(): String? = runCatching {
        currentPointerFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * The database file the repository must open: the current build's file.
     * Falls back to the legacy root file only until the first install runs
     * (pre-PTG-01 installs are adopted into the build layout on next launch).
     */
    val databaseFile: File
        get() = currentBuildId()?.let { File(buildDirectory(it), DB_FILE_NAME) }
            ?: File(targetDirectory, DB_FILE_NAME)

    /**
     * §7 Installation result — propagates success/failure to caller.
     */
    sealed interface InstallResult {
        data object AlreadyInstalled : InstallResult
        data class Installed(val catalogBuildId: String, val dbHash: String) : InstallResult
        data class Failed(val reason: String, val cause: Exception? = null) : InstallResult
    }

    suspend fun getRepository(): IrCatalogRepository = mutex.withLock {
        repositoryInstance?.let { return it }

        val installResult = ensureDatabaseInstalled()
        if (installResult is InstallResult.Failed) {
            Log.e(TAG, "Database installation failed: ${installResult.reason}")
        }

        val repo = IrCatalogRepository.getInstance(applicationContext)
        repositoryInstance = repo
        repo
    }

    /**
     * §7 Public path used by [IrCatalogRepository] to guarantee the on-disk
     * catalog always matches the packaged asset before any query runs.
     * Does NOT take the mutex (callers own their own serialization).
     */
    fun ensureDatabaseInstalled(): InstallResult = ensureDatabaseInstalledInternal()

    /**
     * Metadata of the active build (from the build's own manifest.json copy).
     * Null when no verified build exists yet. Callers stamp profile sessions
     * with this identity (§18 / §31).
     */
    fun currentCatalogMetadata(): CatalogManifest.CatalogMetadata? {
        val buildId = currentBuildId() ?: return null
        val manifestFile = File(buildDirectory(buildId), MANIFEST_FILE_NAME)
        if (!manifestFile.exists()) return null
        return runCatching {
            val parsed = CatalogManifest.parse(manifestFile.readText())
            (parsed as? CatalogManifest.ParseResult.Success)?.metadata
        }.getOrNull()
    }

    /** Declared databaseSha256 of the active build (manifest authority). */
    fun catalogDatabaseHash(): String? = currentCatalogMetadata()?.databaseSha256

    private fun ensureDatabaseInstalledInternal(): InstallResult {
        // 1) Manifest is the authority: without a parseable, complete manifest
        //    we refuse to install anything (fail-closed).
        val manifestMetadata = readAssetManifestMetadata()
        if (manifestMetadata == null) {
            return InstallResult.Failed(
                "Packaged manifest is missing, unparseable or incomplete: refusing to install " +
                    "a catalog with unknown identity"
            )
        }
        val buildId = manifestMetadata.catalogBuildId

        // 2) Active build already verified → nothing to do.
        val active = currentBuildId()
        if (active != null) {
            val activeDb = File(buildDirectory(active), DB_FILE_NAME)
            val activeOk = active == buildId &&
                activeDb.exists() && activeDb.length() > 0L &&
                computeSha256(activeDb) == manifestMetadata.databaseSha256 &&
                verifyDatabaseIntegrity(activeDb)
            if (activeOk) {
                Log.d(TAG, "Active catalog build $active verified (${activeDb.length()} bytes)")
                return InstallResult.AlreadyInstalled
            }
            if (active != buildId) {
                Log.i(TAG, "Active build $active differs from packaged build $buildId — promoting packaged build")
            }
        }

        // 3) Legacy adoption (pre-PTG-01 installs): a root-level ir_catalog.db
        //    that exactly matches the current manifest is adopted into builds/.
        val legacyDb = File(targetDirectory, DB_FILE_NAME)
        if (legacyDb.exists() && legacyDb.length() > 0L) {
            if (computeSha256(legacyDb) == manifestMetadata.databaseSha256 && verifyDatabaseIntegrity(legacyDb)) {
                Log.i(TAG, "Legacy catalog matches packaged manifest — adopting into build $buildId")
                return adoptLegacyBuild(legacyDb, manifestMetadata)
            }
            Log.i(TAG, "Legacy catalog differs from manifest (or failed integrity) — reinstalling from asset")
        }

        // 4) Fresh install: temp → verify → promote → atomic pointer swap.
        val tempFile = File(targetDirectory, TEMP_FILE_NAME)
        try {
            val parts = (0..2).map { index ->
                applicationContext.assets.open("ir/$DB_FILE_NAME.gz.part-${index.toString().padStart(2, '0')}")
            }
            GZIPInputStream(SequenceInputStream(Collections.enumeration(parts)), 64 * 1024).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }

            if (tempFile.length() <= 0L) {
                tempFile.delete()
                return InstallResult.Failed("Installed database is empty after copy")
            }

            val computedHash = computeSha256(tempFile)
            if (computedHash != manifestMetadata.databaseSha256) {
                tempFile.delete()
                return InstallResult.Failed(
                    "Database asset does not match its own manifest: manifest=${manifestMetadata.databaseSha256}, actual=$computedHash"
                )
            }

            if (!CatalogManifest.isSchemaVersionAccepted(manifestMetadata.schemaVersion)) {
                tempFile.delete()
                return InstallResult.Failed(
                    "Catalog schema v${manifestMetadata.schemaVersion} rejected: minimum is v${CatalogManifest.MIN_SCHEMA_VERSION}"
                )
            }

            if (!verifyDatabaseIntegrity(tempFile)) {
                tempFile.delete()
                return InstallResult.Failed("Post-install integrity check failed on extracted asset")
            }

            // Promote into builds/<buildId>/ (same filesystem: atomic rename).
            val buildDir = buildDirectory(buildId)
            buildDir.mkdirs()
            val promotedDb = File(buildDir, DB_FILE_NAME)
            if (promotedDb.exists()) promotedDb.delete()
            val renamed = tempFile.renameTo(promotedDb)
            if (!renamed) {
                tempFile.delete()
                return InstallResult.Failed("Promotion rename failed: temp=${tempFile.absolutePath} → ${promotedDb.absolutePath}")
            }

            // The build's own manifest copy (fsynced, so a build dir is self-describing).
            val buildManifest = File(buildDir, MANIFEST_FILE_NAME)
            buildManifest.outputStream().use { output ->
                output.write(readAssetManifestText().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            fsyncDirectory(buildDir)
            fsyncDirectory(buildsDirectory)

            // Atomic pointer swap: current.tmp → current. Previous pointer is
            // captured BEFORE the swap for rollback-keeping.
            val previousBuildId = currentBuildId()
            val tmpPointer = File(targetDirectory, CURRENT_POINTER_TMP)
            tmpPointer.outputStream().use { output ->
                output.write(buildId.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            var swapped = tmpPointer.renameTo(currentPointerFile)
            if (!swapped) {
                currentPointerFile.delete()
                swapped = tmpPointer.renameTo(currentPointerFile)
            }
            if (!swapped) {
                return InstallResult.Failed("Atomic pointer swap failed for build $buildId")
            }
            fsyncDirectory(targetDirectory)

            pruneBuilds(keep = setOfNotNull(buildId, previousBuildId))

            Log.i(TAG, "Installed catalog build $buildId (${promotedDb.length()} bytes, sha256=$computedHash)")
            return InstallResult.Installed(buildId, computedHash)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install ir_catalog.db asset: ${e.message}", e)
            return InstallResult.Failed("Installation exception: ${e.message}", e)
        }
    }

    private fun adoptLegacyBuild(legacyDb: File, metadata: CatalogManifest.CatalogMetadata): InstallResult {
        val buildDir = buildDirectory(metadata.catalogBuildId)
        return try {
            buildDir.mkdirs()
            val promotedDb = File(buildDir, DB_FILE_NAME)
            if (!promotedDb.exists()) {
                if (!legacyDb.renameTo(promotedDb)) {
                    legacyDb.copyTo(promotedDb, overwrite = false)
                }
            }
            val buildManifest = File(buildDir, MANIFEST_FILE_NAME)
            if (!buildManifest.exists()) {
                buildManifest.outputStream().use { output ->
                    output.write(readAssetManifestText().toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
            }
            val tmpPointer = File(targetDirectory, CURRENT_POINTER_TMP)
            tmpPointer.outputStream().use { output ->
                output.write(metadata.catalogBuildId.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            val previousBuildId = currentBuildId()
            var swapped = tmpPointer.renameTo(currentPointerFile)
            if (!swapped) {
                currentPointerFile.delete()
                swapped = tmpPointer.renameTo(currentPointerFile)
            }
            if (!swapped) return InstallResult.Failed("Atomic pointer swap failed (adoption) for build ${metadata.catalogBuildId}")
            fsyncDirectory(targetDirectory)
            pruneBuilds(keep = setOfNotNull(metadata.catalogBuildId, previousBuildId))
            InstallResult.AlreadyInstalled
        } catch (e: Exception) {
            InstallResult.Failed("Legacy adoption failed: ${e.message}", e)
        }
    }

    /** Keep exactly [keep] build directories; everything else in builds/ is removed. */
    private fun pruneBuilds(keep: Set<String>) {
        val kept = keep.filterNotNull()
        buildsDirectory.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name !in kept) {
                val deleted = dir.deleteRecursively()
                if (deleted) {
                    Log.i(TAG, "Pruned stale catalog build ${dir.name}")
                } else {
                    Log.w(TAG, "Failed to prune stale catalog build ${dir.name}")
                }
            }
        }
    }

    private fun readAssetManifestText(): String = runCatching {
        applicationContext.assets.open("ir/ir_catalog.manifest.json").bufferedReader().use { it.readText() }
    }.getOrNull() ?: ""

    private fun readAssetManifestMetadata(): CatalogManifest.CatalogMetadata? {
        val text = readAssetManifestText()
        if (text.isBlank()) return null
        return runCatching {
            (CatalogManifest.parse(text) as? CatalogManifest.ParseResult.Success)?.metadata
        }.getOrNull()
    }

    /**
     * §7 Verify database integrity using SQLite PRAGMA checks.
     */
    private fun verifyDatabaseIntegrity(dbFile: File): Boolean {
        return try {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            db.use {
                // §7 foreign_key_check
                val fkCursor = it.rawQuery("PRAGMA foreign_key_check", null)
                fkCursor.use { c ->
                    if (c.count > 0) {
                        Log.w(TAG, "foreign_key_check found violations")
                        return false
                    }
                }
                // §7 quick_check
                val qcCursor = it.rawQuery("PRAGMA quick_check", null)
                qcCursor.use { c ->
                    if (c.moveToFirst()) {
                        val result = c.getString(0)
                        if (result != "ok") {
                            Log.w(TAG, "quick_check returned: $result")
                            return false
                        }
                    }
                }
                // V06-P5 §14: Schema v5 table presence — a v4 database missing
                // the v5 entities must never be treated as current.
                val requiredV5Tables = listOf(
                    "protocol_definitions", "protocol_variants",
                    "device_families", "compatibility_assertions",
                    "physical_test_evidence", "catalog_rejections"
                )
                for (table in requiredV5Tables) {
                    val cursor = it.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                        arrayOf(table)
                    )
                    val present = cursor.count > 0
                    cursor.close()
                    if (!present) {
                        Log.w(TAG, "Catalog schema v5 gate: missing table $table")
                        return false
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Integrity check failed: ${e.message}")
            false
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** fsync a directory so renames/creates inside survive a crash. */
    private fun fsyncDirectory(dir: File) {
        try {
            RandomAccessFile(dir, "r").use { it.fd.sync() }
        } catch (e: Exception) {
            Log.w(TAG, "Directory fsync failed for ${dir.absolutePath}: ${e.message}")
        }
    }

    override fun close() {
        repositoryInstance = null
    }

    companion object {
        @Volatile
        private var instance: IrCatalogDatabaseManager? = null

        fun getInstance(context: Context): IrCatalogDatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: IrCatalogDatabaseManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
