package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.StyleDao
import com.lolita.app.data.local.entity.Style
import kotlinx.coroutines.flow.Flow

class StyleRepository(private val styleDao: StyleDao, private val itemDao: ItemDao? = null) {
    fun getAllStyles(): Flow<List<Style>> = styleDao.getAllStyles()

    suspend fun insertStyle(style: Style): Long = styleDao.insertStyle(style)

    suspend fun updateStyle(style: Style, oldName: String? = null) {
        styleDao.updateStyle(style)
        if (oldName != null && oldName != style.name) {
            itemDao?.updateItemsStyle(oldName, style.name)
        }
    }

    suspend fun deleteStyle(style: Style) {
        itemDao?.clearItemsStyle(style.name)
        styleDao.deleteStyle(style)
    }

    suspend fun getStyleByName(name: String): Style? = styleDao.getStyleByName(name)
}
