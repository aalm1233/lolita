package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.SeasonDao
import com.lolita.app.data.local.entity.Season
import kotlinx.coroutines.flow.Flow

class SeasonRepository(private val seasonDao: SeasonDao) {
    fun getAllSeasons(): Flow<List<Season>> = seasonDao.getAllSeasons()

    suspend fun insertSeason(season: Season): Long = seasonDao.insertSeason(season)

    suspend fun updateSeason(season: Season) = seasonDao.updateSeason(season)

    suspend fun deleteSeason(season: Season) {
        seasonDao.deleteSeason(season)
    }

    suspend fun getSeasonByName(name: String): Season? = seasonDao.getSeasonByName(name)
}
