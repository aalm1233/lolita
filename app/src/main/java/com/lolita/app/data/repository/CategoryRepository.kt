package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.CategoryDao
import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.LolitaDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CategoryRepository(
    private val categoryDao: CategoryDao,
    private val itemDao: ItemDao,
    private val database: LolitaDatabase,
    private val initializer: (suspend () -> Unit)? = null
) {
    private val presetMutex=Mutex()
    private var initializedInMemory=false

    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun insertCategory(category: Category): Long {
        initializer?.invoke()
        return categoryDao.insertCategory(category)
    }

    suspend fun affectedItemCount(categoryId: Long): Int = itemDao.countItemsByCategory(categoryId)

    suspend fun updateCategory(category: Category, confirmSemanticChange: Boolean = false) {
        initializer?.invoke()
        database.withTransaction {
        val original = categoryDao.getCategoryById(category.id) ?: error("类型不存在")
        val changesMeaning = original.categorySemantic != category.categorySemantic || original.defaultWearRole != category.defaultWearRole
        require(!changesMeaning || confirmSemanticChange || itemDao.countItemsByCategory(category.id) == 0) { "请先确认此类型全部物品的品类变更" }
        categoryDao.updateCategory(category)
        }
    }

    suspend fun deleteCategory(category: Category) {
        initializer?.invoke()
        database.withTransaction {
            val itemCount = itemDao.countItemsByCategory(category.id)
            if (itemCount > 0) throw IllegalStateException("该类型下有 $itemCount 条记录，无法删除")
            categoryDao.deleteCategory(category)
        }
    }

    suspend fun getCategoryById(id: Long): Category? {
        return categoryDao.getCategoryById(id)
    }

    suspend fun getCategoryByName(name: String): Category? {
        return categoryDao.getCategoryByName(name)
    }

    suspend fun ensurePresetCategories() {
        initializer?.let { it();return }
        // Three-argument callers retain a per-repository guard for isolated in-memory usage.
        presetMutex.withLock {
            if(!initializedInMemory) {
                database.withTransaction { insertMissingWardrobeCategories(categoryDao) }
                initializedInMemory=true
            }
        }
    }
}
