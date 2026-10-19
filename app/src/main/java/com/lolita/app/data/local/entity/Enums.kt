package com.lolita.app.data.local.entity

enum class ItemStatus {
    OWNED,
    WISHED,
    PENDING_BALANCE,
    PENDING_PAYMENT,
    PENDING_REVIEW;

    /** Physical storage is available only after ownership has been established. */
    val supportsStorage: Boolean get() = this == OWNED
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
