package com.lolita.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monthly_budgets")
data class MonthlyBudget(@PrimaryKey val month: String, val amountCents: Long)
