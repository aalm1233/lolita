package com.lolita.app.domain.usecase

import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.PaymentStage
import org.junit.Assert.assertEquals
import org.junit.Test

class StageAccountingTest {
    @Test
    fun unassignedActualPaysStagesInIdOrderWithoutCreatingAnotherDebt() {
        val stages = listOf(stage(20, 700.0), stage(10, 300.0))
        val actual = listOf(payment(500.0))

        val paid = StageAccounting.effectivePaid(stages, actual)

        assertEquals(mapOf(10L to 300.0, 20L to 200.0), paid)
        assertEquals(500.0, OrderAccounting.sum(stages.map { it.amount - paid.getValue(it.id) }), 0.0)
        assertEquals(null, actual.single().stageId)
        assertEquals(500.0, actual.single().amount, 0.0)
    }

    @Test
    fun explicitAssignmentsAreRespectedBeforeUnassignedPaymentsAreAllocated() {
        val stages = listOf(stage(10, 300.0), stage(20, 700.0))
        val actual = listOf(payment(100.0, 10), payment(200.0, 20), payment(400.0))

        assertEquals(mapOf(10L to 300.0, 20L to 400.0), StageAccounting.effectivePaid(stages, actual))
    }

    @Test
    fun unpaidPlansDoNotContributeToActualPayments() {
        val stages = listOf(stage(10, 300.0), stage(20, 700.0))
        val records = listOf(payment(300.0, paid = false), payment(700.0, 20, paid = false))

        assertEquals(mapOf(10L to 0.0, 20L to 0.0), StageAccounting.effectivePaid(stages, records))
    }

    @Test
    fun allocationCannotCrossOrders() {
        val stages = listOf(stage(10, 100.0), stage(20, 100.0, priceId = 2))
        val records = listOf(payment(150.0), payment(30.0, priceId = 2))

        assertEquals(mapOf(10L to 100.0, 20L to 30.0), StageAccounting.effectivePaid(stages, records))
    }

    @Test
    fun legacyAssignedOverpaymentIsCappedOnlyInTheProjection() {
        val stages = listOf(stage(10, 300.0), stage(20, 700.0))
        val records = listOf(payment(400.0, 10), payment(100.0))

        assertEquals(mapOf(10L to 300.0, 20L to 100.0), StageAccounting.effectivePaid(stages, records))
        assertEquals(500.0, OrderAccounting.sum(records.map { it.amount }), 0.0)
    }

    @Test
    fun decimalAmountsUseIntegerCentsAndSurplusCannotOverfillThePlan() {
        val stages = listOf(stage(10, 0.3), stage(20, 0.7))
        val records = listOf(payment(0.1), payment(0.2), payment(1.0))

        assertEquals(mapOf(10L to 0.3, 20L to 0.7), StageAccounting.effectivePaid(stages, records))
    }

    @Test
    fun anOversizedHistoricalPlanDoesNotDuplicateTheOrdersRemainingDebt() {
        val stages = listOf(stage(10,300.0),stage(20,1000.0))
        val actual = listOf(payment(100.0))
        assertEquals(mapOf(10L to 200.0,20L to 700.0),StageAccounting.remainingByStage(stages,actual,1000.0))
        assertEquals(1000.0,stages.last().amount,0.0)
        assertEquals(100.0,actual.single().amount,0.0)
    }

    private fun stage(id: Long, amount: Double, priceId: Long = 1L) =
        PaymentStage(id = id, priceId = priceId, name = "阶段$id", amount = amount)

    private fun payment(amount: Double, stageId: Long? = null, paid: Boolean = true, priceId: Long = 1L) =
        Payment(amount = amount, stageId = stageId, isPaid = paid, priceId = priceId,
            dueDate = 1L, paidDate = if (paid) 1L else null, createdAt = 1L)
}
