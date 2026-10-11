package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.LegacyCatalogArchiveDao

class LegacyCatalogArchiveRepository(private val dao: LegacyCatalogArchiveDao) {
    suspend fun getAll() = dao.getAll()
}
