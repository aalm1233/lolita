package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.SourceDao
import com.lolita.app.data.local.entity.Source
import com.lolita.app.data.local.LolitaDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class SourceRepository(
    private val sourceDao: SourceDao,
    private val itemDao: ItemDao,
    private val database: LolitaDatabase
) {
    fun getAllSources(): Flow<List<Source>> = sourceDao.getAllSources()

    suspend fun insertSource(source: Source): Long = sourceDao.insertSource(source)

    suspend fun updateSource(source: Source, oldName: String? = null) {
        database.withTransaction {
            sourceDao.updateSource(source)
            if (oldName != null && oldName != source.name) {
                itemDao.updateItemsSource(oldName, source.name)
            }
        }
    }

    suspend fun deleteSource(source: Source) {
        database.withTransaction {
            itemDao.clearItemsSource(source.name)
            sourceDao.deleteSource(source)
        }
    }

    suspend fun getSourceByName(name: String): Source? = sourceDao.getSourceByName(name)

    suspend fun getAllSourcesList(): List<Source> = sourceDao.getAllSourcesList()
}
