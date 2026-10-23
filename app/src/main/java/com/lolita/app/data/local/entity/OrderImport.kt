package com.lolita.app.data.local.entity

import androidx.room.*
import java.util.UUID

/** Business snapshots contain no image paths; every file reference is an explicit column. */
@Entity(tableName = "order_import_sessions")
data class OrderImportSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val version: Int = 1,
    val revision: Long = 0,
    val state: String = "ACTIVE",
    @ColumnInfo(name = "data_json") val dataJson: String,
    @ColumnInfo(name = "pending_operation_id") val pendingOperationId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "order_import_files",
    foreignKeys =
        [
            ForeignKey(
                entity = OrderImportSession::class,
                parentColumns = ["id"],
                childColumns = ["session_id"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("session_id")],
)
data class OrderImportFile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "session_id") val sessionId: String,
    val name: String,
    val sha256: String,
    val state: String,
    @ColumnInfo(name = "data_json") val dataJson: String = "[]",
    val problem: String? = null,
)

@Entity(
    tableName = "order_import_rows",
    foreignKeys =
        [
            ForeignKey(
                entity = OrderImportSession::class,
                parentColumns = ["id"],
                childColumns = ["session_id"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("session_id"), Index(value = ["session_id", "position"], unique = true)],
)
data class OrderImportRow(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val position: Int,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "data_json") val dataJson: String,
    @ColumnInfo(name = "image_paths") val imagePaths: List<String> = emptyList(),
)

@Entity(
    tableName = "order_import_commits",
    foreignKeys =
        [
            ForeignKey(
                entity = OrderImportSession::class,
                parentColumns = ["id"],
                childColumns = ["session_id"],
                onDelete = ForeignKey.SET_NULL,
            )
        ],
    indices = [Index("session_id")],
)
data class OrderImportCommit(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String?,
    val revision: Long,
    @ColumnInfo(name = "snapshot_hash") val snapshotHash: String,
    @ColumnInfo(name = "item_ids") val itemIds: List<String>,
    @ColumnInfo(name = "merged_count") val mergedCount: Int,
    @ColumnInfo(name = "linked_item_ids") val linkedItemIds: List<String> = emptyList(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "order_import_receipts",
    foreignKeys =
        [
            ForeignKey(
                entity = Item::class,
                parentColumns = ["id"],
                childColumns = ["item_id"],
                onDelete = ForeignKey.SET_NULL,
            ),
            ForeignKey(
                entity = OrderImportCommit::class,
                parentColumns = ["id"],
                childColumns = ["commit_id"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices =
        [
            Index("item_id"),
            Index("commit_id"),
            Index("order_id"),
            Index(value = ["source_key", "generation"], unique = true),
        ],
)
data class OrderImportReceipt(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "source_key") val sourceKey: String,
    val generation: Int = 1,
    @ColumnInfo(name = "order_id") val orderId: String,
    @ColumnInfo(name = "group_key") val groupKey: String,
    @ColumnInfo(name = "unit_ordinal") val unitOrdinal: Int,
    @ColumnInfo(name = "source_snapshot") val sourceSnapshot: String,
    @ColumnInfo(name = "commit_id") val commitId: String,
    @ColumnInfo(name = "item_id") val itemId: Long?,
    @ColumnInfo(name = "paid_cents") val paidCents: Long,
    @ColumnInfo(name = "shipping_cents") val shippingCents: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "item_source_records",
    foreignKeys =
        [
            ForeignKey(
                entity = Item::class,
                parentColumns = ["id"],
                childColumns = ["item_id"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("item_id")],
)
data class ItemSourceRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "item_id") val itemId: Long,
    val kind: String,
    @ColumnInfo(name = "public_url") val publicUrl: String?,
    @ColumnInfo(name = "snapshot_json") val snapshotJson: String,
    @ColumnInfo(name = "applied_fields") val appliedFields: List<String> = emptyList(),
    @ColumnInfo(name = "image_paths") val imagePaths: List<String> = emptyList(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
