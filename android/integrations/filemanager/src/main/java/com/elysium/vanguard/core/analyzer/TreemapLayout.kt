package com.elysium.vanguard.core.analyzer

import java.io.File

/**
 * PHASE 1.14 — Squarified treemap layout for the storage analyzer.
 *
 * Implementation of the squarified treemap algorithm (Bruls, Huijing, van
 * Wijk, 1999). Produces rectangles whose aspect ratios stay close to 1, which
 * is the key to a treemap that actually looks like boxes instead of long
 * thin strips.
 *
 * Why not D3-style slice-and-dice?
 *   - Slice-and-dice degenerates badly when one item dominates (typical for
 *     a media library with one big video). Squarified keeps rectangles
 *     legible even at 50:1 size ratios.
 */
class TreemapLayout @javax.inject.Inject constructor() {

    data class Node(
        val file: File,
        val sizeBytes: Long,
        val depth: Int = 0
    )

    data class Rect(
        val file: File,
        val sizeBytes: Long,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    /**
     * Compute the layout for [nodes] inside the rectangle (0,0)-(width,height).
     * Nodes are pre-sorted by size descending by [analyze].
     */
    fun layout(nodes: List<Node>, width: Float, height: Float): List<Rect> {
        if (nodes.isEmpty() || width <= 0f || height <= 0f) return emptyList()
        val total = nodes.sumOf { it.sizeBytes }.toFloat()
        if (total <= 0f) return emptyList()
        val normalised = nodes.map { it.copy(sizeBytes = ((it.sizeBytes.toDouble() / total) * 1_000_000).toLong()) }
        val out = mutableListOf<Rect>()
        squarify(normalised, ArrayDeque(normalised), Rect(File("/"), 0L, 0f, 0f, width, height), out)
        return out
    }

    private fun squarify(
        row: List<Node>,
        remaining: ArrayDeque<Node>,
        frame: Rect,
        out: MutableList<Rect>
    ) {
        if (remaining.isEmpty()) {
            placeRow(row, frame, out)
            return
        }
        val head = remaining.first()
        if (row.isEmpty()) {
            squarify(listOf(head), ArrayDeque(remaining.drop(1)), frame, out)
            return
        }
        val withHead = row + head
        if (improvesRatio(row, frame) || !improvesRatio(withHead, frame) && row.isNotEmpty()) {
            squarify(withHead, ArrayDeque(remaining.drop(1)), frame, out)
        } else {
            val newFrame = placeRow(row, frame, out)
            squarify(emptyList(), remaining, newFrame, out)
        }
    }

    private fun improvesRatio(row: List<Node>, frame: Rect): Boolean {
        if (row.isEmpty()) return true
        val sum = row.sumOf { it.sizeBytes }.toFloat()
        val minSize = row.minOf { it.sizeBytes }.toFloat()
        val maxSize = row.maxOf { it.sizeBytes }.toFloat()
        val shortSide = minOf(frame.width, frame.height)
        val ratio = (shortSide * shortSide * maxSize) / (sum * sum)
        return ratio <= 1.0f
    }

    private fun placeRow(row: List<Node>, frame: Rect, out: MutableList<Rect>): Rect {
        if (row.isEmpty()) return frame
        val sum = row.sumOf { it.sizeBytes }.toFloat()
        val horizontal = frame.width >= frame.height
        val rowExtent: Float
        val crossExtent: Float
        var cursor = if (horizontal) frame.x else frame.y
        val crossStart = if (horizontal) frame.y else frame.x
        val totalSize = if (horizontal) frame.width else frame.height

        rowExtent = if (horizontal) frame.height else frame.width
        crossExtent = (sum / totalSize) * if (horizontal) frame.width else frame.height

        for (node in row) {
            val len = (node.sizeBytes.toFloat() / sum) * (if (horizontal) frame.width else frame.height)
            if (horizontal) {
                out += Rect(node.file, node.sizeBytes, cursor, crossStart, len, rowExtent)
            } else {
                out += Rect(node.file, node.sizeBytes, crossStart, cursor, rowExtent, len)
            }
            cursor += len
        }
        return if (horizontal) {
            Rect(File("/"), 0L, frame.x, crossStart + rowExtent, frame.width, frame.height - rowExtent)
        } else {
            Rect(File("/"), 0L, crossStart + rowExtent, frame.y, frame.width - rowExtent, frame.height)
        }
    }

    /**
     * Walks [root], collecting top-N largest children (files + immediate dirs)
     * so the treemap never explodes when there are 50K files in /DCIM.
     */
    fun analyze(root: File, maxNodes: Int = 200): List<Node> {
        val children = try { root.listFiles() ?: return emptyList() } catch (_: SecurityException) { return emptyList() }
        val nodes = children
            .filter { it.length() > 0 || (it.isDirectory && countFiles(it) > 0) }
            .map { child ->
                val size = if (child.isDirectory) dirSize(child) else child.length()
                Node(child, size)
            }
            .sortedByDescending { it.sizeBytes }
            .take(maxNodes)
        return nodes
    }

    private fun dirSize(dir: File): Long {
        var total = 0L
        val children = try { dir.listFiles() ?: return 0L } catch (_: SecurityException) { return 0L }
        for (child in children) {
            if (java.nio.file.Files.isSymbolicLink(child.toPath())) continue
            total += if (child.isDirectory) dirSize(child) else child.length()
        }
        return total
    }

    private fun countFiles(dir: File): Int {
        var n = 0
        val children = try { dir.listFiles() ?: return 0 } catch (_: SecurityException) { return 0 }
        for (c in children) {
            if (java.nio.file.Files.isSymbolicLink(c.toPath())) continue
            n += if (c.isDirectory) countFiles(c) else 1
        }
        return n
    }
}
