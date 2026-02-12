package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.BrandDao
import com.lolita.app.data.local.entity.Brand
import kotlinx.coroutines.flow.Flow

class BrandRepository(
    private val brandDao: BrandDao
) {
    fun getAllBrands(): Flow<List<Brand>> = brandDao.getAllBrands()

    fun getPresetBrands(): Flow<List<Brand>> = brandDao.getPresetBrands()

    suspend fun insertBrand(brand: Brand): Long = brandDao.insertBrand(brand)

    suspend fun updateBrand(brand: Brand) = brandDao.updateBrand(brand)

    suspend fun deleteBrand(brand: Brand) {
        require(!brand.isPreset) { "预置品牌不可删除" }
        brandDao.deleteBrand(brand)
    }

    suspend fun getBrandByName(name: String): Brand? = brandDao.getBrandByName(name)

    suspend fun ensurePresetBrands() {
        val presetNames = listOf(
            "Baby, The Stars Shine Bright",
            "Angelic Pretty",
            "Metamorphose",
            "Mary Magdalene",
            "Innocent World",
            "Victorian Maiden",
            "JetJET",
            "汉洋元素",
            "魔魔"
        )

        presetNames.forEach { name ->
            if (brandDao.getBrandByName(name) == null) {
                brandDao.insertBrand(Brand(name = name, isPreset = true))
            }
        }
    }
}
