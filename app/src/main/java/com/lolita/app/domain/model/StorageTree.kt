package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.local.entity.StorageNode
import java.util.Locale

/** Shared by edits, batch moves and backup validation; never infers a location from a name. */
object StorageTree {
    fun normalizeName(name: String): String = name.trim().lowercase(Locale.ROOT)

    fun validate(nodes: List<StorageNode>, locationIds: Set<Long>) {
        require(nodes.all { it.id > 0 } && nodes.map { it.id }.distinct().size == nodes.size) { "收纳位置标识无效或重复" }
        val byId = nodes.associateBy { it.id }
        val siblingNames = mutableSetOf<Triple<Long, Long, String>>()
        nodes.forEach { node ->
            require(node.locationId in locationIds) { "所属房间已不存在，请重新选择" }
            require(node.name.isNotBlank() && node.name.length <= 120) { "收纳名称需为 1 至 120 个字" }
            require(node.normalizedName == normalizeName(node.name) && node.siblingScopeId == (node.parentNodeId ?: 0L)) { "收纳位置名称或父位置信息不一致" }
            require(siblingNames.add(Triple(node.locationId, node.siblingScopeId, normalizeName(node.name)))) { "同一父位置下已有同名收纳位置" }
            var current = node
            val visited = mutableSetOf<Long>()
            var depth = 0
            while (true) {
                require(visited.add(current.id)) { "收纳位置不能循环或移动到自身后代" }
                require(++depth <= 2) { "最多支持房间、柜架、层格三级位置" }
                val parentId = current.parentNodeId ?: break
                val parent = byId[parentId] ?: throw IllegalArgumentException("父收纳位置已不存在")
                require(parent.locationId == node.locationId) { "父收纳位置不属于所选房间" }
                current = parent
            }
        }
    }

    fun descendantIds(nodeId: Long, nodes: List<StorageNode>): Set<Long> {
        if (nodes.none { it.id == nodeId }) return emptySet()
        val children = nodes.groupBy { it.parentNodeId }
        val result = linkedSetOf<Long>()
        fun visit(id: Long) {
            if (!result.add(id)) return
            children[id].orEmpty().forEach { visit(it.id) }
        }
        visit(nodeId)
        return result
    }

    fun pathFor(locationId: Long?, nodeId: Long?, locations: List<Location>, nodes: List<StorageNode>): String {
        if (locationId == null) return if (nodeId == null) "未设置位置" else "位置待核对"
        val room = locations.find { it.id == locationId } ?: return "房间已失效"
        if (nodeId == null) return room.name
        val byId = nodes.associateBy { it.id }
        val names = mutableListOf<String>()
        val visited = mutableSetOf<Long>()
        var id: Long? = nodeId
        while (id != null) {
            if (!visited.add(id) || names.size >= 2) return "${room.name} → 收纳位置待核对"
            val node = byId[id] ?: return "${room.name} → 收纳位置已失效"
            if (node.locationId != locationId) return "${room.name} → 收纳位置待核对"
            names.add(node.name)
            id = node.parentNodeId
        }
        return (listOf(room.name) + names.asReversed()).joinToString(" → ")
    }
}
