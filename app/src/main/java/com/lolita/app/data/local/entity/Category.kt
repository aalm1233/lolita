package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.domain.model.TryOnRole

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class Category(
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "is_preset", defaultValue = "0")
    val isPreset: Boolean = false,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "category_group", defaultValue = "CLOTHING")
    val group: CategoryGroup = CategoryGroup.CLOTHING,

    @ColumnInfo(name = "category_semantic", defaultValue = "UNCONFIRMED")
    val categorySemantic: CategorySemantic = CategorySemantic.UNCONFIRMED,

    @ColumnInfo(name = "default_wear_role", defaultValue = "NULL")
    val defaultWearRole: TryOnRole? = null
)
