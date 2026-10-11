package com.lolita.app.data.local.entity

enum class ItemStatus {
    OWNED,
    WISHED,
    PENDING_BALANCE,
    PENDING_PAYMENT,
    PENDING_REVIEW
}

enum class ItemPriority {
    HIGH,
    MEDIUM,
    LOW
}

enum class PriceType {
    FULL,
    DEPOSIT_BALANCE,
    MULTI_STAGE
}
