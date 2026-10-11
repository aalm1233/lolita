package com.lolita.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Committed with restored data; external reminder cleanup can resume after process death. */
@Entity(tableName = "restore_journal")
data class RestoreJournal(@PrimaryKey val id: Int = 1, val previousPaymentsJson: String)
