package com.lolita.app.data.local.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lolita.app.data.local.entity.*
import android.util.Log

class Converters {
    private val gson = Gson()

    // ItemStatus
    @TypeConverter
    fun fromItemStatus(value: ItemStatus): String = value.name

    @TypeConverter
    fun toItemStatus(value: String): ItemStatus = try {
        ItemStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        Log.e("Converters", "Unknown ItemStatus: $value — data may be corrupted, preserving as WISHED to avoid silent status change")
        ItemStatus.WISHED
    }

    // ItemPriority
    @TypeConverter
    fun fromItemPriority(value: ItemPriority): String = value.name

    @TypeConverter
    fun toItemPriority(value: String): ItemPriority = try {
        ItemPriority.valueOf(value)
    } catch (e: IllegalArgumentException) {
        Log.w("Converters", "Unknown ItemPriority: $value, defaulting to MEDIUM")
        ItemPriority.MEDIUM
    }

    // PriceType
    @TypeConverter
    fun fromPriceType(value: PriceType): String = value.name

    @TypeConverter
    fun toPriceType(value: String): PriceType = try {
        PriceType.valueOf(value)
    } catch (e: IllegalArgumentException) {
        Log.w("Converters", "Unknown PriceType: $value, defaulting to FULL")
        PriceType.FULL
    }

    // CategoryGroup
    @TypeConverter
    fun fromCategoryGroup(value: CategoryGroup): String = value.name

    @TypeConverter
    fun toCategoryGroup(value: String): CategoryGroup = try {
        CategoryGroup.valueOf(value)
    } catch (e: IllegalArgumentException) {
        Log.w("Converters", "Unknown CategoryGroup: $value, defaulting to CLOTHING")
        CategoryGroup.CLOTHING
    }

    // String List
    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = try {
        gson.fromJson<List<String>>(value, object : TypeToken<List<String>>() {}.type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
