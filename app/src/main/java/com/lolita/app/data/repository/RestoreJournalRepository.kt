package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.RestoreJournalDao

class RestoreJournalRepository(private val dao: RestoreJournalDao) {
    suspend fun get() = dao.get()
}
