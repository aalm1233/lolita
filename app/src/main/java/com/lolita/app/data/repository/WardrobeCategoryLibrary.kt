package com.lolita.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.dao.CategoryDao
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.domain.model.HistoricalCategoryRecognition
import com.lolita.app.domain.model.WardrobeFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Backup and mutating entry points await this before the restore/operation lock. */
class WardrobeCategoryLibrary(context:Context,private val database:LolitaDatabase,
    preferencesName:String="wardrobe_category_library") {
    private val preferences by lazy { context.getSharedPreferences(preferencesName,Context.MODE_PRIVATE) }
    suspend fun awaitInitialization() = withContext(Dispatchers.IO) {
        processMutex.withLock {
            if(!preferences.getBoolean(INITIALIZED,false)) {
                database.withTransaction { insertMissingWardrobeCategories(database.categoryDao()) }
                // Never expose deletion/editing or begin a restore before the durable marker succeeds.
                check(preferences.edit().putBoolean(INITIALIZED,true).commit()) { "类型初始化记录保存失败，请重试" }
            }
            if(!preferences.getBoolean(HISTORICAL_SEMANTICS,false)) {
                database.withTransaction {
                    val dao=database.categoryDao()
                    val changed=dao.getAllCategoriesList().mapNotNull { old ->
                        HistoricalCategoryRecognition.recognize(old).takeIf {it!=old}
                    }
                    if(changed.isNotEmpty()) dao.updateCategories(changed)
                }
                check(preferences.edit().putBoolean(HISTORICAL_SEMANTICS,true).commit()) { "历史分类识别记录保存失败，请重试" }
            }
        }
    }
    companion object {
        private const val INITIALIZED="initialized_v1"
        private const val HISTORICAL_SEMANTICS="historical_semantics_v1"
        private val processMutex=Mutex()
    }
}

internal suspend fun insertMissingWardrobeCategories(dao:CategoryDao) {
    // Preserve existing IDs; explicit legacy names are recognized by the separate one-time repair.
    CategorySemantic.entries.filter {it!=CategorySemantic.UNCONFIRMED}.forEach { semantic ->
        if(dao.getCategoryByName(semantic.label)==null) dao.insertCategory(Category(name=semantic.label,isPreset=true,
            group=if(semantic.family==WardrobeFamily.CLOTHING) CategoryGroup.CLOTHING else CategoryGroup.ACCESSORY,
            categorySemantic=semantic))
    }
}
