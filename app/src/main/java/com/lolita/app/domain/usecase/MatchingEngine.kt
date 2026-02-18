package com.lolita.app.domain.usecase

import com.lolita.app.data.local.entity.Item
import kotlin.math.sqrt

data class ItemVector(
    val itemId: Long,
    val vector: DoubleArray
)

data class MatchScore(
    val item: Item,
    val score: Double
)

class MatchingEngine {

    companion object {
        val STYLES = listOf("甜系", "古典", "哥特", "田园", "中华", "其他")
        val COLORS = listOf(
            "白色", "黑色", "粉色", "红色", "蓝色", "紫色",
            "绿色", "黄色", "棕色", "米色", "灰色", "酒红",
            "藏蓝", "薄荷", "奶茶", "多色"
        )
        val SEASONS = listOf("春", "夏", "秋", "冬", "四季")
    }

    fun encode(item: Item): ItemVector {
        val styleVec = STYLES.map { if (item.style == it) 1.0 else 0.0 }
        val colorVec = COLORS.map { if (item.color?.contains(it) == true) 1.0 else 0.0 }
        val seasonVec = SEASONS.map { s ->
            if (item.season?.split(",")?.any { it.trim() == s } == true) 1.0 else 0.0
        }
        return ItemVector(item.id, (styleVec + colorVec + seasonVec).toDoubleArray())
    }

    fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
        if (a.size != b.size) return 0.0
        val dot = a.zip(b).sumOf { (x, y) -> x * y }
        val normA = sqrt(a.sumOf { it * it })
        val normB = sqrt(b.sumOf { it * it })
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (normA * normB)
    }

    fun recommend(
        target: Item,
        candidates: List<Item>,
        coOccurringItemIds: Set<Long>,
        historyBoost: Double = 1.3,
        topN: Int = 5
    ): List<MatchScore> {
        val targetVec = encode(target)
        return candidates.map { candidate ->
            val candidateVec = encode(candidate)
            var score = cosineSimilarity(targetVec.vector, candidateVec.vector)
            if (candidate.id in coOccurringItemIds) {
                score *= historyBoost
            }
            MatchScore(candidate, score)
        }
        .filter { it.score > 0.0 }
        .sortedByDescending { it.score }
        .take(topN)
    }
}
