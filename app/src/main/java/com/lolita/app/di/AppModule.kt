package com.lolita.app.di

import android.content.Context
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

    // Repositories
    fun coordinateRepository() = CoordinateRepository(
        database.coordinateDao(),
        database.itemDao()
    )

    fun itemRepository() = ItemRepository(database.itemDao())

    fun brandRepository() = BrandRepository(database.brandDao())

    fun categoryRepository() = CategoryRepository(database.categoryDao())

    fun priceRepository() = PriceRepository(database.priceDao())

    fun paymentRepository() = PaymentRepository(database.paymentDao())

    fun outfitLogRepository() = OutfitLogRepository(database.outfitLogDao())

    fun context() = appContext
}
