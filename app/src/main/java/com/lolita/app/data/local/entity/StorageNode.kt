package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

/** A cabinet or compartment inside an existing room. Room identity remains in Location. */
@Entity(tableName = "storage_nodes", foreignKeys = [
    ForeignKey(entity = Location::class, parentColumns = ["id"], childColumns = ["location_id"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = StorageNode::class, parentColumns = ["id"], childColumns = ["parent_node_id"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("location_id"), Index("parent_node_id"),
    Index(value = ["location_id", "sibling_scope_id", "normalized_name"], unique = true)])
data class StorageNode(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "location_id") val locationId: Long,
    @ColumnInfo(name = "parent_node_id") val parentNodeId: Long? = null,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String = name.trim().lowercase(Locale.ROOT),
    // SQLite NULLs do not collide in a unique index, so room-level siblings use scope 0.
    @ColumnInfo(name = "sibling_scope_id") val siblingScopeId: Long = parentNodeId ?: 0L,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
