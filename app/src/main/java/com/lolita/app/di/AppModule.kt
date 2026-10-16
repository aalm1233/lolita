package com.lolita.app.di

import android.content.Context
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.BuiltInLibrary
import com.lolita.app.data.file.ImageFileHelper
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
        appContext = context.applicationContext
        database = LolitaDatabase.getDatabase(context)
        ImageFileHelper.configure(appContext, database)
    }

    fun database() = database

    private val _imageTaskRepository by lazy { ImageTaskRepository(database.imageTaskDao()) }
    fun imageTaskRepository() = _imageTaskRepository

    private val _tryOnRepository by lazy { TryOnRepository(appContext,database) }
    fun tryOnRepository() = _tryOnRepository
    private val _tryOnSettings by lazy { com.lolita.app.data.tryon.TryOnSettings(appContext) }
    fun tryOnSettings() = _tryOnSettings
    private val _imageGenerationApi by lazy { com.lolita.app.data.remote.ImageGenerationApi() }
    private val _generationImages by lazy { com.lolita.app.data.tryon.TryOnImages(appContext) }
    private val _tryOnGenerator by lazy {
        com.lolita.app.data.tryon.TryOnGenerator(
            findItem = { database.itemDao().getItemById(it) },
            readCredentials = { _tryOnSettings.readCredentials() },
            prepareImages = _generationImages::prepare,
            generateImage = _imageGenerationApi::generate,
            saveResult = { bytes, selected, model ->
                _tryOnRepository.save("虚拟试衣", bytes, selected, model)
            }
        )
    }
    fun tryOnGenerator() = _tryOnGenerator

    private val _mannequinGenerator by lazy {
        com.lolita.app.data.mannequin.MannequinGenerator(
            findItem = { database.itemDao().getItemById(it) },
            findCategory = { database.categoryDao().getCategoryById(it) },
            readCredentials = { _tryOnSettings.readCredentials() },
            prepareImages = _generationImages::prepareMannequin,
            generateImage = _imageGenerationApi::generate,
            savePreview = { ImageFileHelper.saveGeneratedImage(appContext, it) },
            discardPreview = { ImageFileHelper.deleteImage(it) }
        )
    }
    fun mannequinGenerator() = _mannequinGenerator

    private val imageTaskScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val _imageTaskCoordinator by lazy {
        com.lolita.app.data.generation.ImageTaskCoordinator(database, imageTaskScope,
            startService = { id -> com.lolita.app.data.generation.ImageTaskService.start(appContext, id) },
            saveImage = { ImageFileHelper.saveGeneratedImage(appContext, it) },
            deleteImage = { ImageFileHelper.deleteImage(it) },
            validateImage = { ImageFileHelper.isOwnedGeneratedImage(appContext, it) })
    }
    fun imageTaskCoordinator() = _imageTaskCoordinator
    private val _imageTaskRunner by lazy {
        val executor = com.lolita.app.data.generation.ImageTaskExecutor(_imageTaskCoordinator, database,
            _tryOnSettings::readCredentials, _generationImages::prepare, _generationImages::prepareMannequin,
            _imageGenerationApi::generate)
        com.lolita.app.data.generation.ImageTaskRunner(_imageTaskCoordinator, executor::execute)
    }
    fun imageTaskRunner() = _imageTaskRunner

    private val _restoreJournalRepository by lazy { RestoreJournalRepository(database.restoreJournalDao()) }
    fun restoreJournalRepository() = _restoreJournalRepository

    private val _legacyCatalogArchiveRepository by lazy {
        LegacyCatalogArchiveRepository(database.legacyCatalogArchiveDao())
    }
    fun legacyCatalogArchiveRepository() = _legacyCatalogArchiveRepository

    private val _coordinateRepository by lazy {
        CoordinateRepository(database.coordinateDao(), database.itemDao(), database)
    }
    fun coordinateRepository() = _coordinateRepository

    private val _itemRepository by lazy {
        ItemRepository(database.itemDao(), _paymentRepository, _priceRepository, database, database.paymentDao(), appContext)
    }
    fun itemRepository() = _itemRepository

    private val _sharedLibrarySyncRepository by lazy {
        SharedLibrarySyncRepository(database, database.sharedLibrarySyncDao())
    }
    fun sharedLibrarySyncRepository() = _sharedLibrarySyncRepository

    private val _builtInLibrary by lazy { BuiltInLibrary(appContext, database) }
    fun builtInLibrary() = _builtInLibrary

    private val _brandRepository by lazy {
        BrandRepository(database.brandDao(), database.itemDao(), database, _builtInLibrary)
    }
    fun brandRepository() = _brandRepository

    private val _categoryRepository by lazy {
        CategoryRepository(database.categoryDao(), database.itemDao(), database)
    }
    fun categoryRepository() = _categoryRepository

    private val _orderRepository by lazy { OrderRepository(database, appContext) }
    fun orderRepository() = _orderRepository

    private val _priceRepository by lazy {
        PriceRepository(database.priceDao(), database.paymentDao(), appContext, database)
    }
    fun priceRepository() = _priceRepository

    private val _paymentRepository by lazy {
        PaymentRepository(database.paymentDao(), appContext, database.itemDao(), database)
    }
    fun paymentRepository() = _paymentRepository

    private val _outfitLogRepository by lazy { OutfitLogRepository(database.outfitLogDao(), database) }
    fun outfitLogRepository() = _outfitLogRepository

    private val _styleRepository by lazy {
        StyleRepository(database.styleDao(), database.itemDao(), database)
    }
    fun styleRepository() = _styleRepository

    private val _seasonRepository by lazy {
        SeasonRepository(database.seasonDao(), database.itemDao(), database)
    }
    fun seasonRepository() = _seasonRepository

    private val _locationRepository by lazy { LocationRepository(database.locationDao(), database.itemDao()) }
    fun locationRepository() = _locationRepository

    private val _sourceRepository by lazy {
        SourceRepository(database.sourceDao(), database.itemDao(), database)
    }
    fun sourceRepository() = _sourceRepository

    private val _backupManager by lazy { BackupManager(appContext, database,
        withImageTasksPaused = { action -> _imageTaskCoordinator.withRestorePaused(action) }) { _builtInLibrary.initialize() } }
    fun backupManager() = _backupManager

    private val _appPreferences by lazy { AppPreferences(appContext) }
    fun appPreferences() = _appPreferences

    private val _appUpdateRepository by lazy { AppUpdateRepository() }
    fun appUpdateRepository() = _appUpdateRepository

    private val _recommendationRepository by lazy {
        RecommendationRepository(database.itemDao(), database.outfitLogDao(), database.coordinateDao())
    }
    fun recommendationRepository() = _recommendationRepository

    private val _lookRepository by lazy { LookRepository(database) }
    fun lookRepository() = _lookRepository

    private val _budgetRepository by lazy { BudgetRepository(database.budgetDao()) }
    fun budgetRepository() = _budgetRepository

    fun context() = appContext
}
