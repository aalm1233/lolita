package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.ItemTryOnMaterialDao

class ItemTryOnMaterialRepository(private val dao: ItemTryOnMaterialDao) {
    val materials = dao.observeAll()
    fun observe(itemId: Long) = dao.observe(itemId)
    suspend fun get(itemId: Long) = dao.getForItem(itemId)
}
