package com.lolita.app.domain.usecase

import kotlin.math.abs

data class SpendingBucket(
    val name: String,
    val amount: Double,
    val filterValue: String?
)

/** Read-only projections of actual payments; source amounts remain unchanged. */
object SpendingBreakdown {
    fun singleLabel(
        rows: List<Pair<String, Double>>,
        unassignedLabel: String
    ): List<SpendingBucket> {
        val totals = mutableMapOf<BucketKey, Long>()
        rows.forEach { (label, amount) ->
            val key = if (label.isBlank()) {
                BucketKey(unassignedLabel, null)
            } else {
                // Keep the stored label intact so the filtered list can find it.
                BucketKey(label, label)
            }
            totals.add(key, OrderAccounting.cents(amount))
        }
        return totals.toBuckets()
    }

    fun seasons(rows: List<Pair<String, Double>>): List<SpendingBucket> {
        val totals = mutableMapOf<BucketKey, Long>()
        rows.forEach { (combination, amount) ->
            val cents = OrderAccounting.cents(amount)
            val labels = combination.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
            if (labels.isEmpty()) {
                totals.add(BucketKey("未设置季节", null), cents)
            } else {
                val share = cents / labels.size
                val remainder = cents % labels.size
                labels.forEachIndexed { index, label ->
                    val extra = if (index.toLong() < abs(remainder)) {
                        remainder.compareTo(0L).toLong()
                    } else {
                        0L
                    }
                    totals.add(BucketKey(label, label), Math.addExact(share, extra))
                }
            }
        }
        return totals.toBuckets()
    }

    private data class BucketKey(val name: String, val filterValue: String?)

    private fun MutableMap<BucketKey, Long>.add(key: BucketKey, cents: Long) {
        this[key] = Math.addExact(this[key] ?: 0L, cents)
    }

    private fun Map<BucketKey, Long>.toBuckets(): List<SpendingBucket> = entries
        .filter { it.value != 0L }
        .sortedWith(
            compareByDescending<Map.Entry<BucketKey, Long>> { it.value }
                .thenBy { it.key.name }
                .thenBy { it.key.filterValue ?: "" }
        )
        .map { (key, cents) -> SpendingBucket(key.name, cents / 100.0, key.filterValue) }
}
