package com.lolita.app.data.local.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lolita.app.data.local.entity.*

class Converters {
    private val gson = Gson()

    // ItemStatus
    @TypeConverter
    fun fromItemStatus(value: ItemStatus): String = value.name

    @TypeConverter
    fun toItemStatus(value: String): ItemStatus = try {
        ItemStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        ItemStatus.OWNED
    }

    // ItemPriority
    @TypeConverter
    fun fromItemPriority(value: ItemPriority): String = value.name

    @TypeConverter
    fun toItemPriority(value: String): ItemPriority = try {
        ItemPriority.valueOf(value)
    } catch (e: IllegalArgumentException) {
        ItemPriority.MEDIUM
    }

    // PriceType
    @TypeConverter
    fun fromPriceType(value: PriceType): String = value.name

    @TypeConverter
    fun toPriceType(value: String): PriceType = try {
        PriceType.valueOf(value)
    } catch (e: IllegalArgumentException) {
        PriceType.FULL
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
