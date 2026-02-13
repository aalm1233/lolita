package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.StyleDao
import com.lolita.app.data.local.entity.Style
import kotlinx.coroutines.flow.Flow

class StyleRepository(private val styleDao: StyleDao) {
    fun getAllStyles(): Flow<List<Style>> = styleDao.getAllStyles()

    suspend fun insertStyle(style: Style): Long = styleDao.insertStyle(style)

    suspend fun updateStyle(style: Style) = styleDao.updateStyle(style)

    suspend fun deleteStyle(style: Style) {
        styleDao.deleteStyle(style)
    }

    suspend fun getStyleByName(name: String): Style? = styleDao.getStyleByName(name)
}
