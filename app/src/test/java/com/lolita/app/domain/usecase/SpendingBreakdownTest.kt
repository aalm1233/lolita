package com.lolita.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpendingBreakdownTest {
    @Test
    fun severalSeasonsDivideOddCentsWithoutDuplicatingThePayment() {
        val rows = listOf("春,夏,秋" to 1.01)

        val buckets = SpendingBreakdown.seasons(rows)

        assertEquals(
            listOf(
                SpendingBucket("夏", 0.34, "夏"),
                SpendingBucket("春", 0.34, "春"),
                SpendingBucket("秋", 0.33, "秋")
            ),
            buckets
        )
        assertConserved(rows, buckets)
    }

    @Test
    fun seasonLabelsAreTrimmedDeduplicatedAndAssignedInStableOrder() {
        val rows = listOf(" B, A, B, , A " to 0.01)

        val buckets = SpendingBreakdown.seasons(rows)

        assertEquals(listOf(SpendingBucket("A", 0.01, "A")), buckets)
        assertEquals(buckets, SpendingBreakdown.seasons(listOf("A,B" to 0.01)))
        assertConserved(rows, buckets)
    }

    @Test
    fun severalRawSeasonCombinationsAggregateIntoTheSameBuckets() {
        val rows = listOf("A,B" to 1.01, "B,A" to 2.0, "A" to 0.03, "B,C" to 0.05)

        val buckets = SpendingBreakdown.seasons(rows)

        assertEquals(
            listOf(
                SpendingBucket("A", 1.54, "A"),
                SpendingBucket("B", 1.53, "B"),
                SpendingBucket("C", 0.02, "C")
            ),
            buckets
        )
        assertConserved(rows, buckets)
    }

    @Test
    fun missingSeasonsRemainVisibleAndHaveNoNavigationFilter() {
        val rows = listOf("" to 0.1, " , , " to 0.2, "A" to 0.05)

        val buckets = SpendingBreakdown.seasons(rows)

        assertEquals(
            listOf(SpendingBucket("未设置季节", 0.3, null), SpendingBucket("A", 0.05, "A")),
            buckets
        )
        assertConserved(rows, buckets)
    }

    @Test
    fun missingSingleLabelsAggregateWithoutTrimmingStoredLabels() {
        val rows = listOf("" to 0.1, " \t\n" to 0.2, " A " to 0.25, "A" to 0.2)

        val buckets = SpendingBreakdown.singleLabel(rows, "未设置品牌")

        assertEquals(
            listOf(
                SpendingBucket("未设置品牌", 0.3, null),
                SpendingBucket(" A ", 0.25, " A "),
                SpendingBucket("A", 0.2, "A")
            ),
            buckets
        )
        assertConserved(rows, buckets)
    }

    @Test
    fun decimalAdditionUsesIntegerCentsAndEqualAmountsSortByName() {
        val rows = listOf("B" to 0.3, "A" to 0.1, "A" to 0.2)

        val buckets = SpendingBreakdown.singleLabel(rows, "未设置分类")

        assertEquals(
            listOf(SpendingBucket("A", 0.3, "A"), SpendingBucket("B", 0.3, "B")),
            buckets
        )
        assertConserved(rows, buckets)
    }

    @Test
    fun zeroAndCancelledAmountsDoNotCreateConsumptionBuckets() {
        val rows = listOf("A" to 0.0, "B" to 0.1, "B" to -0.1, "C" to -0.02)

        val buckets = SpendingBreakdown.singleLabel(rows, "未设置风格")

        assertEquals(listOf(SpendingBucket("C", -0.02, "C")), buckets)
        assertConserved(rows, buckets)
        assertEquals(emptyList<SpendingBucket>(), SpendingBreakdown.seasons(listOf("A,B" to 0.0)))
    }

    @Test
    fun negativeHistoricalPaymentsKeepTheirSignedTotalWhenSplit() {
        val rows = listOf("B,A" to -0.03)

        val buckets = SpendingBreakdown.seasons(rows)

        assertEquals(
            listOf(SpendingBucket("B", -0.01, "B"), SpendingBucket("A", -0.02, "A")),
            buckets
        )
        assertConserved(rows, buckets)
        assertEquals(listOf("B,A" to -0.03), rows)
    }

    @Test
    fun allGroupsAreReturnedForTheUiToCreateItsOwnTopTen() {
        val rows = (1..12).map { "Group%02d".format(it) to it.toDouble() }

        val single = SpendingBreakdown.singleLabel(rows, "未设置品牌")
        val seasons = SpendingBreakdown.seasons(rows)

        assertEquals(12, single.size)
        assertEquals(12, seasons.size)
        assertEquals("Group12", single.first().name)
        assertFalse(single.any { it.name == "其他" })
        assertConserved(rows, single)
        assertConserved(rows, seasons)
    }

    @Test
    fun aRealLabelEqualToTheFallbackKeepsItsOriginalNavigationFilter() {
        val buckets = SpendingBreakdown.singleLabel(
            listOf("" to 0.1, "未设置品牌" to 0.2),
            "未设置品牌"
        )

        assertEquals(
            listOf(
                SpendingBucket("未设置品牌", 0.2, "未设置品牌"),
                SpendingBucket("未设置品牌", 0.1, null)
            ),
            buckets
        )
    }

    private fun assertConserved(rows: List<Pair<String, Double>>, buckets: List<SpendingBucket>) {
        assertEquals(
            OrderAccounting.sum(rows.map { it.second }),
            OrderAccounting.sum(buckets.map { it.amount }),
            0.0
        )
    }
}
