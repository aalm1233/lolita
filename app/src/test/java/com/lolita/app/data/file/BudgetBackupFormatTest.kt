package com.lolita.app.data.file

import com.google.gson.Gson
import com.lolita.app.data.local.entity.MonthlyBudget
import org.junit.Assert.*
import org.junit.Test

class BudgetBackupFormatTest {
    private fun data() = BackupData(brands = emptyList(), categories = emptyList(), coordinates = emptyList(),
        items = emptyList(), prices = emptyList(), payments = emptyList(), outfitLogs = emptyList(), outfitItemCrossRefs = emptyList(),
        backupDate = 123L, monthlyBudgets = listOf(MonthlyBudget("2026-10", 10001), MonthlyBudget("2026-11", 0)))
    @Test fun newBudgetsRoundTripAndVersionFiveWithoutBudgetsIsAccepted() {
        assertEquals(data(), BackupFormat.parse(Gson().toJson(data())))
        val json = Gson().toJsonTree(data()).asJsonObject.apply {
            addProperty("formatVersion", 5); addProperty("schemaVersion", 23); remove("monthlyBudgets")
        }
        assertTrue(BackupFormat.parse(json.toString()).monthlyBudgets.isEmpty())
    }
    @Test fun rejectsMissingModernBudgetsDuplicateMonthsAndInvalidValues() {
        val json = Gson().toJsonTree(data()).asJsonObject.apply { remove("monthlyBudgets") }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
        listOf(listOf(MonthlyBudget("2026-10", 1), MonthlyBudget("2026-10", 2)),
            listOf(MonthlyBudget("2026-13", 1)), listOf(MonthlyBudget("2026-10", -1))).forEach { budgets ->
            assertThrows(RuntimeException::class.java) { BackupFormat.parse(Gson().toJson(data().copy(monthlyBudgets = budgets))) }
        }
    }
}
