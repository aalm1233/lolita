package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.SeasonDao
import com.lolita.app.data.local.entity.Season
import com.lolita.app.data.local.LolitaDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class SeasonRepository(
    private val seasonDao: SeasonDao,
    private val itemDao: ItemDao,
    private val database: LolitaDatabase
) {
    fun getAllSeasons(): Flow<List<Season>> = seasonDao.getAllSeasons()

    suspend fun insertSeason(season: Season): Long = seasonDao.insertSeason(season)

    suspend fun updateSeason(season: Season, oldName: String? = null) {
        database.withTransaction {
            seasonDao.updateSeason(season)
            if (oldName != null && oldName != season.name) {
                val items = itemDao.getItemsWithSeason(oldName)
                val updated = items.mapNotNull { item ->
                    val seasons = item.season?.split(",")?.map { it.trim() } ?: return@mapNotNull null
                    if (oldName in seasons) {
                        val newSeasons = seasons.map { if (it == oldName) season.name else it }
                        item.copy(season = newSeasons.joinToString(","))
                    } else null
                }
                if (updated.isNotEmpty()) itemDao.updateItems(updated)
            }
        }
    }

    suspend fun deleteSeason(season: Season) {
        database.withTransaction {
            val items = itemDao.getItemsWithSeason(season.name)
            val updated = items.mapNotNull { item ->
                val seasons = item.season?.split(",")?.map { it.trim() } ?: return@mapNotNull null
                if (season.name in seasons) {
                    val remaining = seasons.filter { it != season.name }
                    item.copy(season = remaining.joinToString(",").ifBlank { null })
                } else null
            }
            if (updated.isNotEmpty()) itemDao.updateItems(updated)
            seasonDao.deleteSeason(season)
        }
    }

    suspend fun getSeasonByName(name: String): Season? = seasonDao.getSeasonByName(name)
}
