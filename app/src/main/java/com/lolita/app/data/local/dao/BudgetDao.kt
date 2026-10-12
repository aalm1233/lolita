package com.lolita.app.data.local.dao

import androidx.room.*
import com.lolita.app.data.local.entity.MonthlyBudget
import com.lolita.app.domain.usecase.BudgetEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM monthly_budgets WHERE month=:month")
    fun observe(month: String): Flow<MonthlyBudget?>
    @Query("SELECT * FROM monthly_budgets ORDER BY month")
    suspend fun getAll(): List<MonthlyBudget>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(budget: MonthlyBudget)
    @Query("DELETE FROM monthly_budgets WHERE month=:month")
    suspend fun delete(month: String)
    @Query("DELETE FROM monthly_budgets")
    suspend fun deleteAll()
    // All recorded cash payments count, including payments on archived orders. Missing dates stay unknown.
    @Query("""SELECT p.id,i.name AS itemName,p.amount,p.paid_date AS date,1 AS isPaid
        FROM payments p JOIN prices pr ON pr.id=p.price_id JOIN items i ON i.id=pr.item_id WHERE p.is_paid=1""")
    fun actualPayments(): Flow<List<BudgetEntry>>
    @Query(STAGE_BALANCES + "SELECT -id AS id,item_name AS itemName,remaining AS amount,due_date AS date,0 AS isPaid FROM balances WHERE remaining>0")
    fun commitments(): Flow<List<BudgetEntry>>
}
