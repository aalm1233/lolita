package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lolita.app.data.local.entity.Brand
import kotlinx.coroutines.flow.Flow

@Dao
interface BrandDao {
    @Query("SELECT * FROM brands ORDER BY name ASC")
    fun getAllBrands(): Flow<List<Brand>>

    @Query("SELECT * FROM brands WHERE id = :id")
    suspend fun getBrandById(id: Long): Brand?

    @Query("SELECT * FROM brands WHERE is_preset = 1 ORDER BY name ASC")
    fun getPresetBrands(): Flow<List<Brand>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBrand(brand: Brand): Long

    @Update
    suspend fun updateBrand(brand: Brand)

    @Delete
    suspend fun deleteBrand(brand: Brand)

    @Query("SELECT * FROM brands WHERE name = :name LIMIT 1")
    suspend fun getBrandByName(name: String): Brand?
}
