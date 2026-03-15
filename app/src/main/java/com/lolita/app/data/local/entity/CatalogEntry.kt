package com.lolita.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalog_entries",
    foreignKeys = [
        ForeignKey(
            entity = Brand::class,
            parentColumns = ["id"],
            childColumns = ["brand_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["linked_item_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["name"]),
        Index(value = ["brand_id"]),
        Index(value = ["category_id"]),
        Index(value = ["linked_item_id"]),
        Index(value = ["updated_at"])
    ]
)
@Immutable
data class CatalogEntry(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "brand_id")
    val brandId: Long? = null,

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    @ColumnInfo(name = "series_name")
    val seriesName: String? = null,

    @ColumnInfo(name = "reference_url")
    val referenceUrl: String? = null,

    @ColumnInfo(name = "image_urls", defaultValue = "[]")
    val imageUrls: List<String> = emptyList(),

    @ColumnInfo(name = "colors", defaultValue = "[]")
    val colors: List<String> = emptyList(),

    @ColumnInfo(name = "style")
    val style: String? = null,

    @ColumnInfo(name = "season")
    val season: String? = null,

    @ColumnInfo(name = "size")
    val size: String? = null,

    @ColumnInfo(name = "source")
    val source: String? = null,

    @ColumnInfo(name = "description", defaultValue = "")
    val description: String = "",

    @ColumnInfo(name = "linked_item_id")
    val linkedItemId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
