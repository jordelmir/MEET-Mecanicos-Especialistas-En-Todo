package com.elysium369.meet.core.dna

import kotlinx.serialization.Serializable
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * LightweightIsolationForest — A native Kotlin implementation of the unsupervised Anomaly Detection algorithm.
 *
 * Isolation Forest isolates anomalies by randomly partitioning feature spaces.
 * Anomalies require fewer splits to isolate, resulting in shorter path lengths in iTrees.
 */
@Serializable
class LightweightIsolationForest(
    private val numTrees: Int = 100,
    private val subSampleSize: Int = 256
) {
    private var trees: List<ITreeNode> = emptyList()
    private var trainedSampleSize: Int = 0

    // Euler-Mascheroni constant
    private val EULER_CONSTANT = 0.5772156649

    @Serializable
    sealed class ITreeNode {
        @Serializable
        data class InternalNode(
            val featureIndex: Int,
            val splitValue: Float,
            val left: ITreeNode,
            val right: ITreeNode
        ) : ITreeNode()

        @Serializable
        data class LeafNode(
            val size: Int
        ) : ITreeNode()
    }

    /**
     * Train the Isolation Forest on a dataset.
     * Each row in [dataset] represents a multidimensional sample (e.g. 7-dimensional sensor vector).
     */
    fun fit(dataset: List<FloatArray>) {
        if (dataset.isEmpty()) return
        val n = dataset.size
        trainedSampleSize = n.coerceAtMost(subSampleSize)
        
        val maxDepth = ln(trainedSampleSize.toDouble() - 1.0).let { 
            if (it.isNaN() || it.isInfinite()) 10 else (2.0 * it).toInt() 
        }

        val tempTrees = mutableListOf<ITreeNode>()
        for (i in 0 until numTrees) {
            val sample = drawSubSample(dataset, trainedSampleSize)
            tempTrees.add(buildTree(sample, 0, maxDepth))
        }
        trees = tempTrees
    }

    /**
     * Compute the anomaly score for a given observation.
     * Returns a value between 0.0 and 1.0. Scores above 0.65 are highly anomalous.
     */
    fun computeAnomalyScore(observation: FloatArray): Double {
        if (trees.isEmpty()) return 0.0
        val pathLengths = trees.map { pathLength(observation, it, 0) }
        val avgPathLength = pathLengths.average()
        
        val c = c(trainedSampleSize)
        if (c == 0.0) return 0.0
        
        return 2.0.pow(-avgPathLength / c)
    }

    private fun drawSubSample(dataset: List<FloatArray>, size: Int): List<FloatArray> {
        if (dataset.size <= size) return dataset
        val indices = dataset.indices.shuffled().take(size)
        return indices.map { dataset[it] }
    }

    private fun buildTree(sample: List<FloatArray>, currentDepth: Int, maxDepth: Int): ITreeNode {
        if (currentDepth >= maxDepth || sample.size <= 1) {
            return ITreeNode.LeafNode(sample.size)
        }

        val numFeatures = sample.first().size
        val featureIndices = (0 until numFeatures).shuffled()
        
        for (q in featureIndices) {
            val values = sample.map { it[q] }
            val min = values.minOrNull() ?: 0f
            val max = values.maxOrNull() ?: 0f
            
            if (min == max) continue // Cannot split on uniform feature

            val splitPoint = min + Random.nextFloat() * (max - min)
            
            val leftSample = sample.filter { it[q] < splitPoint }
            val rightSample = sample.filter { it[q] >= splitPoint }

            // Verify the split actually separated data
            if (leftSample.isNotEmpty() && rightSample.isNotEmpty()) {
                return ITreeNode.InternalNode(
                    featureIndex = q,
                    splitValue = splitPoint,
                    left = buildTree(leftSample, currentDepth + 1, maxDepth),
                    right = buildTree(rightSample, currentDepth + 1, maxDepth)
                )
            }
        }

        return ITreeNode.LeafNode(sample.size)
    }

    private fun pathLength(x: FloatArray, node: ITreeNode, currentDepth: Int): Double {
        return when (node) {
            is ITreeNode.LeafNode -> {
                currentDepth + c(node.size)
            }
            is ITreeNode.InternalNode -> {
                val value = x.getOrNull(node.featureIndex) ?: 0f
                if (value < node.splitValue) {
                    pathLength(x, node.left, currentDepth + 1)
                } else {
                    pathLength(x, node.right, currentDepth + 1)
                }
            }
        }
    }

    /**
     * Normalizing factor c(n) representing the average path length of an unsuccessful search in a BST.
     */
    private fun c(n: Int): Double {
        if (n <= 1) return 0.0
        if (n == 2) return 1.0
        return 2.0 * (ln(n - 1.0) + EULER_CONSTANT) - (2.0 * (n - 1.0) / n)
    }
}
