package com.lolita.app.domain.usecase

import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import java.util.Locale

data class LookOption(val item: Item, val score: Int, val reasons: List<String>)

/** Local, explainable matching. Custom names remain usable rather than becoming zero vectors. */
object FullLookEngine {
    private fun role(category: Category): String {
        val name = category.name.trim().lowercase(Locale.ROOT)
        return when {
            name in setOf("jsk", "op", "sk", "连衣裙", "半裙", "裙子") -> "主裙"
            name in setOf("kc", "bonnet", "发带", "其他头饰", "头饰", "帽子", "发夹") -> "头饰"
            name.contains("衬衫") || name.contains("上衣") || name == "blouse" -> "上衣"
            name.contains("袜") -> "袜子"
            name.contains("鞋") -> "鞋子"
            name.contains("包") -> "包袋"
            name in setOf("斗篷", "披肩", "外套", "开衫") -> "外搭"
            else -> category.name
        }
    }
    fun build(target: Item, candidates: List<Item>, categories: List<Category>, history: Set<Long>): Map<String, List<LookOption>> {
        val byId = categories.associateBy { it.id }
        val targetCategory = byId[target.categoryId] ?: return emptyMap()
        val targetRole = role(targetCategory)
        val isOp = targetCategory.name.trim().equals("OP", ignoreCase = true)
        return candidates.distinctBy { it.id }.filter { it.id != target.id && it.status == ItemStatus.OWNED }
            .mapNotNull { candidate ->
                val category = byId[candidate.categoryId] ?: return@mapNotNull null
                val slot = role(category)
                if (slot == targetRole || slot == "主裙" || isOp && slot == "上衣") return@mapNotNull null
                val reasons = mutableListOf<String>()
                if (!target.style.isNullOrBlank() && target.style == candidate.style) reasons += "风格相同"
                if (target.colors.intersect(candidate.colors.toSet()).isNotEmpty()) reasons += "颜色呼应"
                val seasons = target.season.orEmpty().split(',', '，').map { it.trim() }.filter { it.isNotBlank() }.toSet()
                val other = candidate.season.orEmpty().split(',', '，').map { it.trim() }.filter { it.isNotBlank() }.toSet()
                if (seasons.intersect(other).isNotEmpty() || seasons.isNotEmpty() && "四季" in other || other.isNotEmpty() && "四季" in seasons) reasons += "季节适合"
                if (candidate.id in history) reasons += "曾经一起搭配"
                val score = reasons.size + if (candidate.id in history) 2 else 0
                slot to LookOption(candidate, score, reasons.ifEmpty { listOf("补齐$slot，可自行替换") })
            }.groupBy({ it.first }, { it.second })
            .mapValues { (_, options) -> options.sortedWith(compareByDescending<LookOption> { it.score }.thenBy { it.item.id }) }
    }
}

data class LookSelection(val selected: Map<String, Long?>, val locked: Set<String> = emptySet()) {
    fun next(groups: Map<String, List<LookOption>>): LookSelection = copy(selected = selected.mapValues { (slot, id) ->
        if (slot in locked || id == null) id else groups[slot].orEmpty().let { options ->
            if (options.isEmpty()) null else options[(options.indexOfFirst { it.item.id == id } + 1) % options.size].item.id
        }
    })
    companion object {
        fun initial(groups: Map<String, List<LookOption>>) = LookSelection(groups.mapValues { it.value.firstOrNull()?.item?.id })
    }
}
