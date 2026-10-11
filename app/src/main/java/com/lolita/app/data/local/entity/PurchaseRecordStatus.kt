package com.lolita.app.data.local.entity

/** Distinguish a complete payment ledger from historical purchase evidence. */
enum class PurchaseRecordStatus {
    TRACKED,
    HISTORICAL_OWNED,
    NEEDS_REVIEW
}
