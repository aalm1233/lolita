package com.lolita.app.domain.usecase

import java.math.BigDecimal
import java.math.RoundingMode

/** Keep legacy Double values in storage, perform money arithmetic in integer cents. */
object OrderAccounting {
    const val MAX_AMOUNT = 90_000_000_000_000.0
    fun hasCentPrecision(value: Double): Boolean = value.isFinite() && BigDecimal.valueOf(value).stripTrailingZeros().scale() <= 2
    fun cents(value: Double): Long {
        require(value.isFinite()) { "金额必须为有限数值" }
        return BigDecimal.valueOf(value).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }
    fun validateAmount(value: Double, positive: Boolean = false) {
        require(value.isFinite() && (if (positive) value > 0 else value >= 0)) { "金额无效" }
        require(value < MAX_AMOUNT) { "金额超出支持范围" }
        require(hasCentPrecision(value)) { "金额最多保留两位小数" }
        cents(value)
    }
    fun sum(values: Iterable<Double>): Double = values.fold(0L) { a, b -> Math.addExact(a, cents(b)) } / 100.0
    fun remaining(total: Double, paid: Double): Double = (cents(total) - cents(paid)).coerceAtLeast(0) / 100.0
    fun settled(total: Double, paid: Double): Boolean = cents(paid) >= cents(total)
}
