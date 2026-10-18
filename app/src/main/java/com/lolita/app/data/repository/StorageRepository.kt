package com.lolita.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.local.entity.StorageNode
import com.lolita.app.data.local.entity.ItemDraftData
import com.lolita.app.domain.model.StorageTree
import com.lolita.app.domain.model.StorageTemplate
import com.lolita.app.domain.model.StorageTemplates
import kotlinx.coroutines.flow.Flow

data class StorageMoveImpact(val nodeCount: Int, val itemCount: Int)

/** Every graph change and its affected item references share the same Room transaction. */
class StorageRepository(private val database: LolitaDatabase, private val context: Context? = null) {
    private val nodes get() = database.storageNodeDao()
    fun observeNodes(): Flow<List<StorageNode>> = nodes.observeAll()
    suspend fun getNodes(): List<StorageNode> = nodes.getAll()
    fun pathFor(locationId: Long?, nodeId: Long?, locations: List<Location>, nodes: List<StorageNode>) =
        StorageTree.pathFor(locationId, nodeId, locations, nodes)

    private suspend fun validate(graph: List<StorageNode>) = StorageTree.validate(graph,
        database.locationDao().getAllLocationsList().map { it.id }.toSet())
    private fun normalized(node: StorageNode) = node.copy(name = node.name.trim(),
        normalizedName = StorageTree.normalizeName(node.name), siblingScopeId = node.parentNodeId ?: 0L)

    suspend fun createNode(locationId: Long, parentNodeId: Long?, name: String,
        description: String = "", imageUrl: String? = null): Long = database.withTransaction {
        val current = nodes.getAll()
        val candidate = normalized(StorageNode(locationId = locationId, parentNodeId = parentNodeId,
            name = name, description = description.trim(), imageUrl = imageUrl))
        validate(current + candidate.copy(id = Long.MAX_VALUE))
        nodes.insert(candidate)
    }

    suspend fun renameNode(id: Long, name: String) = database.withTransaction {
        val node = nodes.getById(id) ?: throw IllegalArgumentException("收纳位置已删除")
        updateNode(node.copy(name = name))
    }

    suspend fun updateNode(node: StorageNode) {
        var oldImage: String? = null
        database.withTransaction {
            val latest = nodes.getById(node.id) ?: throw IllegalArgumentException("收纳位置已删除")
            require(latest.locationId == node.locationId && latest.parentNodeId == node.parentNodeId) { "收纳位置已移动，请重新打开编辑" }
            val updated = normalized(node.copy(createdAt = latest.createdAt, updatedAt = System.currentTimeMillis()))
            validate(nodes.getAll().map { if (it.id == updated.id) updated else it })
            nodes.update(updated)
            oldImage = latest.imageUrl?.takeIf { it != updated.imageUrl }
        }
        oldImage?.let { ImageFileHelper.cleanupUnreferencedImages(listOf(it)) }
    }

    suspend fun previewMoveImpact(nodeId: Long): StorageMoveImpact = database.withTransaction {
        val ids = StorageTree.descendantIds(nodeId, nodes.getAll())
        require(ids.isNotEmpty()) { "收纳位置已删除" }
        StorageMoveImpact(ids.size, database.itemDao().countItemsInStorageNodes(ids.toList()))
    }

    suspend fun moveNode(nodeId: Long, locationId: Long, parentNodeId: Long?) = database.withTransaction {
        val graph = nodes.getAll()
        val ids = StorageTree.descendantIds(nodeId, graph)
        require(ids.isNotEmpty()) { "收纳位置已删除" }
        require(parentNodeId !in ids) { "不能移动到自身或自己的下级位置" }
        val moved = graph.map { node -> when {
            node.id == nodeId -> normalized(node.copy(locationId = locationId, parentNodeId = parentNodeId))
            node.id in ids -> node.copy(locationId = locationId)
            else -> node
        } }
        validate(moved)
        moved.filter { it.id in ids }.forEach { nodes.update(it) }
        database.itemDao().moveStorageSubtreeItems(ids.toList(), locationId)
        updateDraftStorage { data ->
            if (data.storageNodeId in ids) data.copy(locationId = locationId) else data
        }
    }

    suspend fun deleteNode(nodeId: Long, clearContents: Boolean = false) {
        val images = database.withTransaction {
            val graph = nodes.getAll()
            val ids = StorageTree.descendantIds(nodeId, graph)
            require(ids.isNotEmpty()) { "收纳位置已删除" }
            val count = database.itemDao().countItemsInStorageNodes(ids.toList())
            require(clearContents || ids.size == 1 && count == 0) { "该位置包含 ${ids.size - 1} 个子位置、$count 条记录，请先移动内容或明确全部移至未设置位置" }
            if (clearContents) database.itemDao().clearStorageSubtreeItems(ids.toList())
            val rooms = database.locationDao().getAllLocationsList().map { it.id }.toSet()
            updateDraftStorage { data ->
                if (data.storageNodeId in ids) data.copy(storageNodeId = null,
                    locationId = data.locationId?.takeIf { it in rooms }) else data
            }
            val removed = graph.filter { it.id in ids }
            removed.sortedByDescending { it.parentNodeId != null }.forEach { nodes.delete(it) }
            removed.mapNotNull { it.imageUrl }
        }
        ImageFileHelper.cleanupUnreferencedImages(images)
    }

    suspend fun deleteRoom(location: Location, clearContents: Boolean = false) {
        val images = database.withTransaction {
            val latest = database.locationDao().getLocationByIdSync(location.id)
                ?: throw IllegalArgumentException("房间已删除")
            val graph = nodes.getAll().filter { it.locationId == location.id }
            val count = database.itemDao().countItemsByLocation(location.id)
            require(clearContents || graph.isEmpty() && count == 0) { "房间包含 ${graph.size} 个收纳位置、$count 条记录，请先移动内容或明确全部移至未设置位置" }
            if (clearContents) database.itemDao().clearRoomStorage(location.id)
            val ids = graph.map { it.id }.toSet()
            updateDraftStorage { data ->
                if (data.locationId == location.id || data.storageNodeId in ids)
                    data.copy(locationId = null, storageNodeId = null) else data
            }
            graph.sortedByDescending { it.parentNodeId != null }.forEach { nodes.delete(it) }
            database.locationDao().deleteLocation(latest)
            graph.mapNotNull { it.imageUrl } + listOfNotNull(latest.imageUrl)
        }
        ImageFileHelper.cleanupUnreferencedImages(images)
    }

    /** Called only inside the graph transaction; every other draft field and file stays owned. */
    private suspend fun updateDraftStorage(transform: (ItemDraftData) -> ItemDraftData) {
        database.itemDraftDao().getAll().forEach { draft ->
            val original = draft.data()
            val updated = transform(original)
            if (updated != original) database.itemDraftDao().save(draft.copy(dataJson = updated.encode()))
        }
    }

    suspend fun moveItems(itemIds: Set<Long>, locationId: Long?, nodeId: Long?) = database.withTransaction {
        require(nodeId == null || locationId != null) { "选择收纳位置前请先选择房间" }
        if (locationId != null) require(database.locationDao().getLocationByIdSync(locationId) != null) { "房间已删除" }
        if (nodeId != null) require(nodes.getById(nodeId)?.locationId == locationId) { "收纳位置已删除或不属于所选房间" }
        if (itemIds.isNotEmpty()) {
            require(database.itemDao().getItemsForStorageMove(itemIds.toList()).size == itemIds.size) { "部分物品已删除，请重新选择" }
            database.itemDao().updateItemsStorage(itemIds.toList(), locationId, nodeId)
        }
    }

    suspend fun getTemplates(): List<StorageTemplate> = StorageTemplates.read(requireNotNull(context) { "无法读取内置收纳素材" })

    suspend fun adoptTemplates(locationId: Long, templateIds: Set<String>) {
        require(templateIds.isNotEmpty()) { "请先选择收纳模板" }
        val appContext = requireNotNull(context) { "无法读取内置收纳素材" }
        val templates = getTemplates().filter { it.id in templateIds }
        require(templates.size == templateIds.size) { "收纳模板已失效" }
        val staged = mutableListOf<String>()
        try {
            database.withTransaction {
                templates.forEach { template ->
                    val path = StorageTemplates.copyVerifiedImage(appContext, template)
                    staged.add(path)
                    createNode(locationId, null, template.name, template.description, path)
                }
            }
        } catch (failure: Throwable) {
            ImageFileHelper.cleanupUnreferencedImages(staged)
            throw failure
        }
    }
}
