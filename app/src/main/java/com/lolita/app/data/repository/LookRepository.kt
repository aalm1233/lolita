package com.lolita.app.data.repository

import androidx.room.withTransaction
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Coordinate
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus

class LookRepository(private val database: LolitaDatabase) {
    /** Save exactly the reviewed selection, rejecting stale ownership instead of moving new assignments. */
    suspend fun save(name: String, selected: List<Item>): Long = database.withTransaction {
        require(name.isNotBlank()) { "请填写套装名称" }
        require(selected.isNotEmpty() && selected.map { it.id }.distinct().size == selected.size) { "请选择服饰" }
        val current = selected.map { expected ->
            val item = database.itemDao().getItemById(expected.id) ?: error("服饰已删除，请重新生成搭配")
            require(item.status == ItemStatus.OWNED) { "服饰状态已变化，请重新生成搭配" }
            require(item.coordinateId == expected.coordinateId) { "服饰所属套装已变化，请重新生成搭配" }
            item
        }
        val id = database.coordinateDao().insertCoordinate(Coordinate(name = name.trim(), description = ""))
        current.forEachIndexed { index, item ->
            database.itemDao().updateItem(item.copy(coordinateId = id, coordinateOrder = index, updatedAt = System.currentTimeMillis()))
        }
        id
    }
}
