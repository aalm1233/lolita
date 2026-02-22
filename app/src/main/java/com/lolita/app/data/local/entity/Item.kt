package com.lolita.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = Coordinate::class,
            parentColumns = ["id"],
            childColumns = ["coordinate_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Brand::class,
            parentColumns = ["id"],
            childColumns = ["brand_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Location::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["name"]),
        Index(value = ["coordinate_id"]),
        Index(value = ["brand_id"]),
        Index(value = ["category_id"]),
        Index(value = ["status"]),
        Index(value = ["priority"]),
        Index(value = ["location_id"])
    ]
)
@Immutable
data class Item(
    @ColumnInfo(name = "brand_id")
    val brandId: Long,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "description")
    val description: String,

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "priority", defaultValue = "MEDIUM")
    val priority: ItemPriority = ItemPriority.MEDIUM,

    @ColumnInfo(name = "status")
    val status: ItemStatus,

    @ColumnInfo(name = "coordinate_id")
    val coordinateId: Long? = null,

    @ColumnInfo(name = "color")
    val color: String? = null,

    @ColumnInfo(name = "season")
    val season: String? = null,

    @ColumnInfo(name = "style")
    val style: String? = null,

    @ColumnInfo(name = "size")
    val size: String? = null,

    @ColumnInfo(name = "size_chart_image_url")
    val sizeChartImageUrl: String? = null,

    @ColumnInfo(name = "location_id")
    val locationId: Long? = null,

    @ColumnInfo(name = "source")
    val source: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
