package com.elysium.vanguard.core.util

import java.io.*
import java.util.zip.*

object CompressionEngine {

    interface ProgressListener {
        fun onProgress(percentage: Int, currentFile: String)
    }

    /**
     * Compress files and directories to a ZIP archive.
     * Supports recursive directory compression with real progress tracking.
     */
    fun compress(
        files: List<File>,
        outputFile: File,
        listener: ProgressListener? = null
    ): Result<File> {
        return try {
            val buffer = ByteArray(1024 * 8)

            // Calculate total size including directories
            val allFiles = mutableListOf<Pair<File, String>>() // file, relative path
            for (file in files) {
                if (file.isDirectory) {
                    collectFilesRecursive(file, file.name, allFiles)
                } else {
                    allFiles.add(file to file.name)
                }
            }

            val totalSize = allFiles.sumOf { it.first.length() }.coerceAtLeast(1L)
            var processedSize = 0L

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zos ->
                for ((file, relativePath) in allFiles) {
                    if (file.isDirectory) {
                        zos.putNextEntry(ZipEntry("$relativePath/"))
                        zos.closeEntry()
                        continue
                    }

                    listener?.onProgress(
                        ((processedSize * 100) / totalSize).toInt().coerceAtMost(99),
                        file.name
                    )

                    val entry = ZipEntry(relativePath)
                    zos.putNextEntry(entry)
                    file.inputStream().use { fis ->
                        var len: Int
                        while (fis.read(buffer).also { len = it } > 0) {
                            zos.write(buffer, 0, len)
                            processedSize += len
                        }
                    }
                    zos.closeEntry()
                }
            }
            listener?.onProgress(100, "Complete")
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decompress a ZIP archive with entry-count based progress.
     * Optionally provide a password — if the ZIP is password-protected
     * and standard Java ZIP doesn't support it, this will fail gracefully.
     */
    fun decompress(
        zipFile: File,
        outputDir: File,
        password: String? = null,
        listener: ProgressListener? = null
    ): Result<File> {
        return try {
            if (!outputDir.exists()) outputDir.mkdirs()

            // First pass: count entries for accurate progress
            val entryCount = countZipEntries(zipFile)
            var processedEntries = 0
            val buffer = ByteArray(1024 * 8)

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(outputDir, entry.name)

                    // Security: prevent zip slip
                    val canonicalOutput = outputDir.canonicalPath
                    val canonicalFile = newFile.canonicalPath
                    if (!canonicalFile.startsWith(canonicalOutput)) {
                        throw SecurityException("ZIP entry outside target directory: ${entry.name}")
                    }

                    listener?.onProgress(
                        if (entryCount > 0) ((processedEntries * 100) / entryCount).coerceAtMost(99) else 0,
                        entry.name.substringAfterLast("/").ifEmpty { entry.name }
                    )

                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    processedEntries++
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            listener?.onProgress(100, "Complete")
            Result.success(outputDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun collectFilesRecursive(dir: File, basePath: String, result: MutableList<Pair<File, String>>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            val childPath = "$basePath/${child.name}"
            if (child.isDirectory) {
                result.add(child to childPath)
                collectFilesRecursive(child, childPath, result)
            } else {
                result.add(child to childPath)
            }
        }
    }

    private fun countZipEntries(zipFile: File): Int {
        var count = 0
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                while (zis.nextEntry != null) {
                    count++
                    zis.closeEntry()
                }
            }
        } catch (e: Exception) { /* ignore */ }
        return count
    }
}
