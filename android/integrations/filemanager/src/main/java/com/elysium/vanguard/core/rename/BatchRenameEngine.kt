package com.elysium.vanguard.core.rename

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PHASE 1.8 — Pure batch-rename engine.
 *
 * Stateless transformer that takes a [Pattern] and a list of files and produces
 * a [Plan] (the intended final names + conflict resolutions). The caller is
 * responsible for executing the rename — we never touch the filesystem from
 * here, so this is fully unit-testable without Android.
 *
 * Supported placeholders in [Pattern.template]:
 *   - `{counter}`     — 1-based index, zero-padded per [Pattern.counterPadding]
 *   - `{name}`        — original filename without extension
 *   - `{ext}`         — original extension (without the dot), upper-cased if
 *                       [Pattern.uppercaseExt] is true
 *   - `{date}`        — file's lastModified formatted with [Pattern.dateFormat]
 *   - `{parent}`      — immediate parent folder name
 *   - `{size}`        — human-readable size (KB/MB/…)
 *
 * Example: pattern "IMG_{counter}_{date}" with counter padding 3 →
 *   IMG_001_2024-03-15.jpg
 *   IMG_002_2024-03-15.jpg
 */
class BatchRenameEngine {

    data class Pattern(
        val template: String,
        val counterPadding: Int = 3,
        val startAt: Int = 1,
        val dateFormat: String = "yyyy-MM-dd",
        val uppercaseExt: Boolean = false,
        val onConflict: ConflictResolution = ConflictResolution.SKIP
    )

    enum class ConflictResolution {
        /** Skip the file that would collide; remaining files continue. */
        SKIP,

        /** Append " (1)", " (2)", … until the name is unique. */
        APPEND_SUFFIX,

        /** Stop the whole batch at the first conflict. */
        ABORT
    }

    data class PlannedRename(
        val original: File,
        val renamed: File,
        val parent: File
    )

    data class Plan(
        val renames: List<PlannedRename>,
        val skipped: List<File>,
        val aborted: Boolean
    ) {
        val isEmpty: Boolean get() = renames.isEmpty() && skipped.isEmpty()
        val totalOperations: Int get() = renames.size
    }

    /**
     * Compute the rename plan. The [files] list determines the counter order.
     * Caller must already have sorted the list if order matters.
     */
    fun plan(files: List<File>, pattern: Pattern, parent: File? = null): Plan {
        if (pattern.template.isBlank()) {
            return Plan(renames = emptyList(), skipped = files, aborted = false)
        }

        val renames = mutableListOf<PlannedRename>()
        val skipped = mutableListOf<File>()
        val seenTargets = mutableSetOf<String>()
        val dateFormat = SimpleDateFormat(pattern.dateFormat, Locale.US)

        var counter = pattern.startAt
        var aborted = false

        for (file in files) {
            val resolvedParent = parent ?: file.parentFile ?: continue
            val originalName = file.name
            val dotIdx = originalName.lastIndexOf('.')
            val stem = if (dotIdx > 0) originalName.substring(0, dotIdx) else originalName
            val ext = if (dotIdx > 0) originalName.substring(dotIdx + 1) else ""
            val parentName = resolvedParent.name

            val counterStr = counter.toString().padStart(pattern.counterPadding, '0').let {
                if (it.length < counter.toString().length) counter.toString() else it
            }
            val finalExt = if (pattern.uppercaseExt && ext.isNotEmpty()) ext.uppercase(Locale.US) else ext
            val dateStr = try { dateFormat.format(Date(file.lastModified())) } catch (_: Exception) { "" }
            val sizeStr = formatSize(file.length())

            val newStem = pattern.template
                .replace("{counter}", counterStr)
                .replace("{name}", stem)
                .replace("{date}", dateStr)
                .replace("{parent}", parentName)
                .replace("{size}", sizeStr)

            val finalName = if (finalExt.isNotEmpty()) "$newStem.$finalExt" else newStem
            val target = File(resolvedParent, finalName)

            // Conflict detection: same name as an existing file we are NOT renaming
            // or as another target in this batch.
            val isSelfRename = target.absolutePath == file.absolutePath
            val alreadyTaken = !isSelfRename &&
                (target.exists() || seenTargets.contains(target.absolutePath))

            if (alreadyTaken) {
                when (pattern.onConflict) {
                    ConflictResolution.SKIP -> {
                        skipped += file
                    }
                    ConflictResolution.APPEND_SUFFIX -> {
                        val resolved = resolveSuffix(target, seenTargets)
                        if (resolved != null) {
                            seenTargets += resolved.absolutePath
                            renames += PlannedRename(file, resolved, resolvedParent)
                        } else {
                            skipped += file
                        }
                    }
                    ConflictResolution.ABORT -> {
                        aborted = true
                        break
                    }
                }
            } else {
                seenTargets += target.absolutePath
                renames += PlannedRename(file, target, resolvedParent)
            }

            counter++
        }

        return Plan(renames = renames, skipped = skipped, aborted = aborted)
    }

    /**
     * Execute a [Plan] against the filesystem. Returns the number of successful
     * renames. Files in [Plan.skipped] are not touched. On I/O error the file is
     * added to [Plan.skipped] and the batch continues.
     */
    fun execute(plan: Plan): Int {
        var ok = 0
        for (rename in plan.renames) {
            try {
                if (rename.original.absolutePath == rename.renamed.absolutePath) {
                    // Identity rename — nothing to do, but count as success.
                    ok++
                    continue
                }
                if (rename.renamed.exists()) {
                    // Lost the race — skip silently.
                    continue
                }
                if (rename.original.renameTo(rename.renamed)) {
                    ok++
                }
            } catch (_: Exception) {
                // best effort
            }
        }
        return ok
    }

    private fun resolveSuffix(initial: File, taken: Set<String>): File? {
        val parent = initial.parentFile ?: return null
        val name = initial.name
        val dotIdx = name.lastIndexOf('.')
        val stem = if (dotIdx > 0) name.substring(0, dotIdx) else name
        val ext = if (dotIdx > 0) name.substring(dotIdx) else ""

        for (i in 1..999) {
            val candidate = File(parent, "$stem ($i)$ext")
            if (!candidate.exists() && !taken.contains(candidate.absolutePath)) {
                return candidate
            }
        }
        return null
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var idx = 0
        while (size >= 1024 && idx < units.lastIndex) {
            size /= 1024
            idx++
        }
        return if (idx == 0) "${bytes}${units[0]}" else String.format(Locale.US, "%.1f%s", size, units[idx])
    }
}
