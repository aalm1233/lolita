package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lolita.app.data.local.entity.RestoreJournal

@Dao
interface RestoreJournalDao {
    @Query("SELECT * FROM restore_journal WHERE id = 1")
    suspend fun get(): RestoreJournal?
    @Upsert
    suspend fun save(journal: RestoreJournal)
    @Query("DELETE FROM restore_journal")
    suspend fun clear()
}
