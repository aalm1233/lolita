package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.BudgetDao
import com.lolita.app.data.local.entity.MonthlyBudget
import com.lolita.app.domain.usecase.BudgetAccounting
import kotlinx.coroutines.flow.combine
import java.time.YearMonth

class BudgetRepository(private val dao: BudgetDao) {
    fun observe(month: YearMonth) = combine(dao.observe(month.toString()), dao.actualPayments(), dao.commitments()) { budget, actual, pending ->
        budget to BudgetAccounting.summarize(month, budget?.amountCents, actual + pending)
    }
    suspend fun save(month: YearMonth, input: String) {
        val amount = BudgetAccounting.parseAmount(input)
        BudgetAccounting.validate(month.toString(), amount)
        dao.save(MonthlyBudget(month.toString(), amount))
    }
    suspend fun delete(month: YearMonth) = dao.delete(month.toString())
}
