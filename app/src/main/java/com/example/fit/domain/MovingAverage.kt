package com.example.fit.domain

const val DEFAULT_AVERAGE_N = 10
const val MIN_AVERAGE_N = 2
const val MAX_AVERAGE_N = 20

/**
 * Trailing mean over the last [n] values: element i is the mean of the values
 * from i-n+1 to i. While fewer than [n] values exist it is the mean of what there is.
 */
fun movingAverage(values: List<Double>, n: Int): List<Double> {
    require(n >= 1) { "n must be at least 1" }
    return values.indices.map { i -> values.subList(maxOf(0, i - n + 1), i + 1).average() }
}
