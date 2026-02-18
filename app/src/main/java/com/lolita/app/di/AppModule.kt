package com.lolita.app.di

import android.content.Context
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.*

object AppModule {
    private lateinit var database: LolitaDatabase
    @Volatile
    private lateinit var appContext: Context

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context
        database = LolitaDatabase.getDatabase(context)
    }

    // Database
    fun database() = database

    // Repositories (lazy singletons)
    private val _coordinateRepository by lazy {
        CoordinateRepository(database.coordinateDao(), database.itemDao(), database)
    }
    fun coordinateRepository() = _coordinateRepository

    private val _itemRepository by lazy { ItemRepository(database.itemDao(), _paymentRepository, _priceRepository, database) }
    fun itemRepository() = _itemRepository

    private val _brandRepository by lazy { BrandRepository(database.brandDao(), database.itemDao()) }
    fun brandRepository() = _brandRepository

    private val _categoryRepository by lazy { CategoryRepository(database.categoryDao(), database.itemDao()) }
    fun categoryRepository() = _categoryRepository

    private val _priceRepository by lazy {
        PriceRepository(database.priceDao(), database.paymentDao())
    }
    fun priceRepository() = _priceRepository

    private val _paymentRepository by lazy { PaymentRepository(database.paymentDao(), appContext) }
    fun paymentRepository() = _paymentRepository

    private val _outfitLogRepository by lazy { OutfitLogRepository(database.outfitLogDao(), database) }
    fun outfitLogRepository() = _outfitLogRepository

    private val _styleRepository by lazy { StyleRepository(database.styleDao(), database.itemDao(), database) }
    fun styleRepository() = _styleRepository

    private val _seasonRepository by lazy { SeasonRepository(database.seasonDao(), database.itemDao(), database) }
    fun seasonRepository() = _seasonRepository

    private val _backupManager by lazy { BackupManager(appContext, database) }
    fun backupManager() = _backupManager

    private val _appPreferences by lazy { AppPreferences(appContext) }
    fun appPreferences() = _appPreferences

    private val _recommendationRepository by lazy {
        RecommendationRepository(database.itemDao(), database.outfitLogDao(), database.coordinateDao())
    }
    fun recommendationRepository() = _recommendationRepository

    fun context() = appContext
}
