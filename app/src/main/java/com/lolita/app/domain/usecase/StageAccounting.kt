package com.lolita.app.domain.usecase

import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.PaymentStage

/** Projects actual payments onto a plan without changing or duplicating the ledger. */
object StageAccounting {
    /** An inconsistent historical plan cannot schedule more debt than the order owes. */
    fun remainingByStage(stages: List<PaymentStage>, payments: List<Payment>, totalPrice: Double): Map<Long, Double> {
        val paid = effectivePaid(stages, payments)
        var remaining = (OrderAccounting.cents(totalPrice) - sumCents(payments.filter { it.isPaid })).coerceAtLeast(0)
        return stages.sortedBy { it.id }.associate { stage ->
            val plannedRemaining = (OrderAccounting.cents(stage.amount) - OrderAccounting.cents(paid[stage.id] ?: 0.0)).coerceAtLeast(0)
            val amount = minOf(plannedRemaining, remaining)
            remaining -= amount
            stage.id to amount / 100.0
        }
    }

    fun effectivePaid(stages: List<PaymentStage>, payments: List<Payment>): Map<Long, Double> {
        val actualByOrder = payments.filter { it.isPaid }.groupBy { it.priceId }
        val result = linkedMapOf<Long, Double>()
        stages.groupBy { it.priceId }.forEach { (priceId, orderStages) ->
            val actual = actualByOrder[priceId].orEmpty()
            val assigned = actual.filter { it.stageId != null }.groupBy { it.stageId }
                .mapValues { (_, records) -> sumCents(records) }
            var unassigned = sumCents(actual.filter { it.stageId == null })
            orderStages.sortedBy { it.id }.forEach { stage ->
                val planned = OrderAccounting.cents(stage.amount)
                val directlyPaid = minOf(planned, assigned[stage.id] ?: 0L)
                val allocated = minOf(planned - directlyPaid, unassigned)
                unassigned -= allocated
                result[stage.id] = Math.addExact(directlyPaid, allocated) / 100.0
            }
        }
        return result
    }

    private fun sumCents(payments: List<Payment>): Long = payments.fold(0L) { sum, payment ->
        Math.addExact(sum, OrderAccounting.cents(payment.amount))
    }
}
