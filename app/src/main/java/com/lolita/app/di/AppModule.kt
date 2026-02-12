package com.lolita.app.di

import android.content.Context
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.repository.*

object AppModule {
    private lateinit var database: LolitaDatabase
    private lateinit var appContext: Context

    fun init(context: Context) {
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

    private val _itemRepository by lazy { ItemRepository(database.itemDao()) }
    fun itemRepository() = _itemRepository

    private val _brandRepository by lazy { BrandRepository(database.brandDao()) }
    fun brandRepository() = _brandRepository

    private val _categoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    fun categoryRepository() = _categoryRepository

    private val _priceRepository by lazy {
        PriceRepository(database.priceDao(), database.paymentDao())
    }
    fun priceRepository() = _priceRepository

    private val _paymentRepository by lazy { PaymentRepository(database.paymentDao(), appContext) }
    fun paymentRepository() = _paymentRepository

    private val _outfitLogRepository by lazy { OutfitLogRepository(database.outfitLogDao()) }
    fun outfitLogRepository() = _outfitLogRepository

    fun backupManager() = BackupManager(appContext, database)

    fun context() = appContext
}
