package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.SeasonDao
import com.lolita.app.data.local.entity.Season
import kotlinx.coroutines.flow.Flow

class SeasonRepository(private val seasonDao: SeasonDao, private val itemDao: ItemDao? = null) {
    fun getAllSeasons(): Flow<List<Season>> = seasonDao.getAllSeasons()

    suspend fun insertSeason(season: Season): Long = seasonDao.insertSeason(season)

    suspend fun updateSeason(season: Season, oldName: String? = null) {
        seasonDao.updateSeason(season)
        if (oldName != null && oldName != season.name) {
            itemDao?.updateItemsSeason(oldName, season.name)
        }
    }

    suspend fun deleteSeason(season: Season) {
        itemDao?.clearItemsSeason(season.name)
        seasonDao.deleteSeason(season)
    }

    suspend fun getSeasonByName(name: String): Season? = seasonDao.getSeasonByName(name)
}
