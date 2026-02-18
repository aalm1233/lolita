package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.StyleDao
import com.lolita.app.data.local.entity.Style
import com.lolita.app.data.local.LolitaDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class StyleRepository(
    private val styleDao: StyleDao,
    private val itemDao: ItemDao,
    private val database: LolitaDatabase
) {
    fun getAllStyles(): Flow<List<Style>> = styleDao.getAllStyles()

    suspend fun insertStyle(style: Style): Long = styleDao.insertStyle(style)

    suspend fun updateStyle(style: Style, oldName: String? = null) {
        database.withTransaction {
            styleDao.updateStyle(style)
            if (oldName != null && oldName != style.name) {
                itemDao.updateItemsStyle(oldName, style.name)
            }
        }
    }

    suspend fun deleteStyle(style: Style) {
        database.withTransaction {
            itemDao.clearItemsStyle(style.name)
            styleDao.deleteStyle(style)
        }
    }

    suspend fun getStyleByName(name: String): Style? = styleDao.getStyleByName(name)
}
