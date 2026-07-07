package com.example.stats

import kotlin.math.*

/**
 * Results of a single step in the Generalized ESD algorithm.
 */
data class EsdStepResult(
    val step: Int,
    val originalIndex: Int,
    val label: String,
    val value: Double,
    val side: String,
    val rObs: Double,
    val lambda: Double,
    val ratio: Double
)

/**
 * Summary results of the Generalized ESD outlier test.
 */
data class EsdResult(
    val n: Int,
    val alpha: Double,
    val maxOutliers: Int,
    val outlierCount: Int,
    val steps: List<EsdStepResult>,
    val outlierIndices: Set<Int>
)

object EsdTest {

    /**
     * Approximates the Cumulative Distribution Function (CDF) for the Standard Normal distribution.
     * Uses Abramowitz & Stegun approximation (formula 26.2.17) with accuracy ~7.5e-8.
     */
    fun normalCdf(z: Double): Double {
        if (z < 0.0) return 1.0 - normalCdf(-z)
        val p = 0.2316419
        val b1 = 0.319381530
        val b2 = -0.356563782
        val b3 = 1.781477937
        val b4 = -1.821255978
        val b5 = 1.330274429

        val t = 1.0 / (1.0 + p * z)
        val expTerm = exp(-z * z / 2.0) / sqrt(2.0 * PI)
        val poly = t * (b1 + t * (b2 + t * (b3 + t * (b4 + t * b5))))
        return 1.0 - expTerm * poly
    }

    /**
     * Exact closed-form Cumulative Distribution Function (CDF) of Student's t-distribution.
     * Accurate for small to moderate degrees of freedom. Uses Normal approximation for df > 100.
     */
    fun tCdf(t: Double, df: Int): Double {
        if (df <= 0) return 0.5
        if (t == 0.0) return 0.5
        if (t < 0.0) return 1.0 - tCdf(-t, df)

        if (df > 100) {
            // Standard Normal approximation for large df
            return normalCdf(t * (1.0 - 0.25 / df))
        }

        val theta = atan(t / sqrt(df.toDouble()))
        val sinTheta = sin(theta)
        val cosTheta = cos(theta)

        return if (df % 2 == 0) {
            var sum = 1.0
            var term = 1.0
            val limit = df / 2 - 1
            for (k in 1..limit) {
                term *= (2.0 * k - 1.0) / (2.0 * k) * cosTheta * cosTheta
                sum += term
            }
            0.5 + 0.5 * sinTheta * sum
        } else {
            var sum = 0.0
            var term = 1.0
            val limit = (df - 3) / 2
            for (k in 1..limit) {
                term *= (2.0 * k) / (2.0 * k + 1.0) * cosTheta * cosTheta
                sum += term
            }
            val firstPart = 0.5 + theta / PI
            val secondPart = cosTheta * sinTheta * (1.0 + sum) / PI
            firstPart + secondPart
        }
    }

    /**
     * Calculates the Percent Point Function (PPF / Inverse CDF) of Student's t-distribution
     * using monotonic binary search on the t-CDF function.
     */
    fun tPpf(p: Double, df: Int): Double {
        if (p <= 0.0) return -100.0
        if (p >= 1.0) return 100.0
        if (p == 0.5) return 0.0
        if (p < 0.5) return -tPpf(1.0 - p, df)

        var low = 0.0
        var high = 100.0
        if (df == 1 && p > 0.99) {
            high = 1000.0
        }

        for (i in 0..64) {
            val mid = (low + high) / 2.0
            val cdfVal = tCdf(mid, df)
            if (cdfVal < p) {
                low = mid
            } else {
                high = mid
            }
        }
        return (low + high) / 2.0
    }

    /**
     * Generalized ESD test for outliers (Rosner, NIST implementation).
     *
     * @param x The data values
     * @param labels Corresponding labels for each data point
     * @param maxOutliers Maximum number of outliers to test for
     * @param alpha Significance level (typically 0.05)
     */
    fun generalizedEsd(
        x: List<Double>,
        labels: List<String>,
        maxOutliers: Int,
        alpha: Double = 0.05
    ): EsdResult {
        val n = x.size
        
        // Edge cases or safety guards
        if (n < 3) {
            return EsdResult(n, alpha, maxOutliers, 0, emptyList(), emptySet())
        }
        val safeMaxOutliers = maxOutliers.coerceIn(1, n - 2)

        val remaining = x.toMutableList()
        val remainingIdx = (0 until n).toMutableList()

        val rList = mutableListOf<Double>()
        val lambdas = mutableListOf<Double>()
        val removedIndices = mutableListOf<Int>()
        val removedValues = mutableListOf<Double>()
        val removedSides = mutableListOf<String>()
        val removedLabels = mutableListOf<String>()

        for (i in 1..safeMaxOutliers) {
            val mean = remaining.average()
            val stdDev = std(remaining, mean)

            if (stdDev == 0.0) break

            var maxDev = -1.0
            var j = -1
            for (idx in remaining.indices) {
                val dev = abs(remaining[idx] - mean) / stdDev
                if (dev > maxDev) {
                    maxDev = dev
                    j = idx
                }
            }

            if (j == -1) break

            rList.add(maxDev)
            removedIndices.add(remainingIdx[j])
            removedValues.add(remaining[j])
            removedSides.add(if (remaining[j] > mean) "upper" else "lower")
            removedLabels.add(labels[remainingIdx[j]])

            val p = 1.0 - alpha / (2.0 * (n - i + 1))
            val df = n - i - 1
            
            val tCrit = tPpf(p, df)
            val numerator = (n - i) * tCrit
            val denominator = sqrt((n - i - 1 + tCrit * tCrit) * (n - i + 1))
            val lambdaI = if (denominator != 0.0) numerator / denominator else 0.0
            lambdas.add(lambdaI)

            remaining.removeAt(j)
            remainingIdx.removeAt(j)
        }

        var outlierCount = 0
        for (i in rList.indices) {
            if (rList[i] > lambdas[i]) {
                outlierCount = i + 1
            }
        }

        val steps = mutableListOf<EsdStepResult>()
        for (i in 0 until rList.size) {
            val ratio = if (lambdas[i] != 0.0) rList[i] / lambdas[i] else Double.POSITIVE_INFINITY
            steps.add(
                EsdStepResult(
                    step = i + 1,
                    originalIndex = removedIndices[i],
                    label = removedLabels[i],
                    value = removedValues[i],
                    side = removedSides[i],
                    rObs = rList[i],
                    lambda = lambdas[i],
                    ratio = ratio
                )
            )
        }

        val outlierIndices = removedIndices.take(outlierCount).toSet()

        return EsdResult(
            n = n,
            alpha = alpha,
            maxOutliers = safeMaxOutliers,
            outlierCount = outlierCount,
            steps = steps,
            outlierIndices = outlierIndices
        )
    }

    /**
     * One-step continuous outlier score for all points (one-step fence).
     */
    fun oneStepScores(x: List<Double>, alpha: Double = 0.05): List<Double> {
        val n = x.size
        if (n <= 2) return List(n) { 0.0 }

        val mean = x.average()
        val stdDev = std(x, mean)

        if (stdDev == 0.0) {
            return List(n) { 0.0 }
        }

        val rObs = x.map { abs(it - mean) / stdDev }

        // NIST lambda_1 formula
        val p = 1.0 - alpha / (2.0 * n)
        val tCrit = tPpf(p, n - 2)
        val numerator = (n - 1) * tCrit
        val denominator = sqrt((n - 2 + tCrit * tCrit) * (n + 1))
        val lambda1 = if (denominator != 0.0) numerator / denominator else 1.0

        return rObs.map { it / lambda1 }
    }

    /**
     * Computes sample standard deviation (ddof = 1).
     */
    private fun std(list: List<Double>, mean: Double): Double {
        if (list.size <= 1) return 0.0
        var sum = 0.0
        for (v in list) {
            sum += (v - mean) * (v - mean)
        }
        return sqrt(sum / (list.size - 1))
    }
}
