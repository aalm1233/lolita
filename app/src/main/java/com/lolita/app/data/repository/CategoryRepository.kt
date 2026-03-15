package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.CatalogEntryDao
import com.lolita.app.data.local.dao.CategoryDao
import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.entity.Category
import kotlinx.coroutines.flow.Flow

class CategoryRepository(
    private val categoryDao: CategoryDao,
    private val itemDao: ItemDao,
    private val catalogEntryDao: CatalogEntryDao
) {
    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun insertCategory(category: Category): Long = categoryDao.insertCategory(category)

    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)

    suspend fun deleteCategory(category: Category) {
        val itemCount = itemDao.countItemsByCategory(category.id)
        val catalogCount = catalogEntryDao.countEntriesByCategory(category.id)
        val totalCount = itemCount + catalogCount
        if (totalCount > 0) throw IllegalStateException("该类型下有 $totalCount 条记录，无法删除")
        categoryDao.deleteCategory(category)
    }

    suspend fun getCategoryById(id: Long): Category? {
        return categoryDao.getCategoryById(id)
    }

    suspend fun getCategoryByName(name: String): Category? {
        return categoryDao.getCategoryByName(name)
    }

    suspend fun ensurePresetCategories() {
        val clothingNames = listOf("JSK", "OP", "SK")
        val accessoryNames = listOf(
            "KC", "斗篷", "披肩", "发带", "Bonnet",
            "其他头饰", "袜子", "手套", "其他配饰"
        )

        clothingNames.forEach { name ->
            if (getCategoryByName(name) == null) {
                categoryDao.insertCategory(
                    Category(
                        name = name,
                        isPreset = true,
                        group = com.lolita.app.data.local.entity.CategoryGroup.CLOTHING
                    )
                )
            }
        }
        accessoryNames.forEach { name ->
            if (getCategoryByName(name) == null) {
                categoryDao.insertCategory(
                    Category(
                        name = name,
                        isPreset = true,
                        group = com.lolita.app.data.local.entity.CategoryGroup.ACCESSORY
                    )
                )
            }
        }
    }
}
