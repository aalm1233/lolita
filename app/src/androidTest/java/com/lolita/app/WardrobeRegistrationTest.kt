package com.lolita.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.item.ItemEditViewModel
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class WardrobeRegistrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: LolitaDatabase
    private lateinit var items: ItemRepository
    private lateinit var brands: BrandRepository
    private lateinit var categories: CategoryRepository
    private lateinit var drafts: ItemDraftRepository
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        items = ItemRepository(database.itemDao(), database = database)
        brands = BrandRepository(database.brandDao(), database.itemDao(), database)
        categories = CategoryRepository(database.categoryDao(), database.itemDao(), database)
        drafts = ItemDraftRepository(database, items)
    }
    @After fun close() = runBlocking {
        withContext(Dispatchers.Main) { stores.forEach { it.clear() } }
        database.close()
    }
    private suspend fun editor(handle: SavedStateHandle = SavedStateHandle()): ItemEditViewModel = withContext(Dispatchers.Main) {
        ItemEditViewModel(items, brands, categories, CoordinateRepository(database.coordinateDao(), database.itemDao(), database),
            PriceRepository(database.priceDao(), database.paymentDao(), context, database),
            StyleRepository(database.styleDao(), database.itemDao(), database), SeasonRepository(database.seasonDao(), database.itemDao(), database),
            SourceRepository(database.sourceDao(), database.itemDao(), database), LocationRepository(database.locationDao(), database.itemDao()),
            drafts, StorageRepository(database), handle).also { vm -> stores += ViewModelStore().apply { put("editor", vm) } }
    }
    private suspend fun loaded(vm: ItemEditViewModel, id: Long = 0) {
        withContext(Dispatchers.Main) { vm.loadItem(id) }
        withTimeout(10000) { while (vm.uiState.value.isLoading || vm.uiState.value.draftId == null) delay(20) }
        assertNull(vm.uiState.value.loadError)
    }
    @Test fun nameAndFullWigTypeSaveWithoutBrandPhotoOrPrice() = runBlocking {
        val category = categories.insertCategory(Category(name = "收藏头发", categorySemantic = CategorySemantic.FULL_WIG))
        val vm = editor(); loaded(vm)
        val id = withContext(Dispatchers.Main) { vm.updateName("奶茶长卷"); vm.updateCategory(category); vm.updateBrand(0); vm.saveItem().getOrThrow() }
        val row = items.getItemById(id)!!
        assertTrue(row.imageUrls.isEmpty())
        assertTrue(database.priceDao().getPricesByItemList(id).isEmpty())
        assertEquals(Brand.UNSET_BRAND_KEY, database.brandDao().getAllBrandsList().first { it.id == row.brandId }.systemKey)
        assertTrue(database.itemDraftDao().getAll().isEmpty())
    }
    @Test fun oldSameNamedBrandIsNotTakenOverAndSystemIdentityCannotBeEditedOrDeleted() = runBlocking {
        val old = database.brandDao().insertBrand(Brand(name = "未设置品牌", logoUrl = "/historical.png"))
        val system = brands.ensureUnsetBrand()
        assertNotEquals(old, system)
        assertEquals(system, brands.ensureUnsetBrand())
        val row = database.brandDao().getAllBrandsList().first { it.id == system }
        assertTrue(runCatching { brands.updateBrand(row.copy(name = "重命名")) }.isFailure)
        assertTrue(runCatching { brands.deleteBrand(row) }.isFailure)
        val historical = database.brandDao().getAllBrandsList().first { it.id == old }
        assertNull(historical.systemKey); assertEquals("/historical.png", historical.logoUrl)
    }
    @Test fun restoredDraftRetainsEveryProfileRawSizeAndImagesUntilExplicitDiscard() = runBlocking {
        val vm = editor(); loaded(vm)
        withContext(Dispatchers.Main) {
            vm.updateName("资料待补")
            vm.updateSize("23 至 25 cm")
            vm.updateSpecifications(WardrobeSpecifications(footwear = FootwearSpecifications(sizeText = "EU 37"), wig = WigSpecifications(lengthCm = "1.")))
            vm.addImage("/draft-image.png")
            vm.keepDraft()
        }
        val draftId = vm.uiState.value.draftId!!
        assertEquals(listOf("/draft-image.png"), drafts.get(draftId)!!.imagePaths)
        val restored = editor(SavedStateHandle(mapOf("itemDraftId" to draftId))); loaded(restored)
        assertEquals("23 至 25 cm", restored.uiState.value.size)
        assertEquals("EU 37", restored.uiState.value.specifications.footwear.sizeText)
        assertEquals("1.", restored.uiState.value.specifications.wig.lengthCm)
        withContext(Dispatchers.Main) { restored.discardDraft() }
        assertNull(drafts.get(draftId))
        assertTrue(database.itemDao().getAllItemsList().isEmpty())
    }
    @Test fun legacyCategoryAndExistingIdentityImagesAndParentSurviveEdit() = runBlocking {
        val brand = database.brandDao().insertBrand(Brand(name = "历史品牌"))
        val category = categories.insertCategory(Category(name = "全头假发", isPreset = true))
        val parent = items.insertItem(Item(brandId = brand, categoryId = category, name = "主件", description = "", status = ItemStatus.OWNED))
        val id = items.insertItem(Item(brandId = brand, categoryId = category, name = "小物", description = "", status = ItemStatus.OWNED,
            parentItemId = parent, imageUrls = listOf("/original.png", "/second.png"), size = "定制 XS", importToken = "historical-import"))
        val before = items.getItemById(id)!!
        categories.ensurePresetCategories()
        assertEquals(CategorySemantic.UNCONFIRMED, categories.getCategoryById(category)!!.categorySemantic)
        val vm = editor(); loaded(vm, id)
        withContext(Dispatchers.Main) { vm.updateName("改名"); vm.saveItem().getOrThrow() }
        val after = items.getItemById(id)!!
        assertEquals(before.id, after.id); assertEquals(before.createdAt, after.createdAt)
        assertEquals(before.imageUrls, after.imageUrls); assertEquals(before.parentItemId, after.parentItemId)
        assertEquals(before.size, after.size); assertEquals(before.importToken, after.importToken)
    }
    @Test fun storageSelectionMustBelongToItsRoomAndRemovedPicturesPruneOnlyTheirProvenance() = runBlocking {
        val brand = brands.ensureUnsetBrand()
        val category = categories.insertCategory(Category(name = "鞋", categorySemantic = CategorySemantic.MARY_JANE))
        val room = database.locationDao().insertLocation(Location(name = "玄关", description = ""))
        val otherRoom = database.locationDao().insertLocation(Location(name = "卧室", description = ""))
        val node = StorageRepository(database).createNode(room, null, "鞋柜")
        val item = Item(brandId = brand, categoryId = category, name = "鞋", description = "", status = ItemStatus.OWNED,
            locationId = otherRoom, storageNodeId = node, imageUrls = listOf("/first.png", "/second.png"))
        assertTrue(runCatching { items.insertItem(item) }.isFailure)
        val id = items.insertItem(item.copy(locationId = room))
        val before = items.getItemById(id)!!
        assertTrue(runCatching { items.updateItem(before.copy(locationId = otherRoom)) }.isFailure)
        database.imageProvenanceDao().upsert(ImageProvenance(itemId = id, imagePath = "/first.png", sourceType = ImageSourceType.ORIGINAL, mode = "original"))
        database.imageProvenanceDao().upsert(ImageProvenance(itemId = id, imagePath = "/second.png", sourceType = ImageSourceType.ORIGINAL, mode = "original"))
        items.updateItem(before.copy(imageUrls = listOf("/second.png")))
        assertNull(database.imageProvenanceDao().get(id, "/first.png"))
        assertNotNull(database.imageProvenanceDao().get(id, "/second.png"))
        assertEquals(listOf("/second.png"), items.getItemById(id)!!.imageUrls)
    }
    @Test fun recognitionMergesOnlyConfirmedCurrentDraftAndNeverCreatesBrandOrCategory() = runBlocking {
        val category = categories.insertCategory(Category(name = "全头假发", categorySemantic = CategorySemantic.FULL_WIG))
        val vm = editor(); loaded(vm)
        withContext(Dispatchers.Main) { vm.updateCategory(category); vm.keepDraft() }
        val id = vm.uiState.value.draftId!!
        val baseline = vm.currentRecognitionValues()
        val region = listOf("region-1")
        val name = RecognitionCandidate("name", RecognitionField.NAME, "AI建议名", RecognitionSource.TEXT, region, "AI建议名")
        val length = RecognitionCandidate("length", RecognitionField.WIG_LENGTH, "60 cm", RecognitionSource.TEXT, region, "发长60 cm")
        val brand = RecognitionCandidate("brand", RecognitionField.BRAND, "未存在品牌", RecognitionSource.TEXT, region, "未存在品牌")
        withContext(Dispatchers.Main) { vm.updateName("识别期间手动改名") }
        val merge = withContext(Dispatchers.Main) { vm.applyRecognitionSelection(id, baseline, listOf(name, length, brand), setOf("name", "length", "brand")) }
        assertEquals("识别期间手动改名", vm.uiState.value.name)
        assertEquals("60 cm", vm.uiState.value.specifications.wig.lengthCm)
        assertEquals(setOf("length"), merge.acceptedIds)
        assertTrue(merge.conflicts.containsAll(listOf("name", "brand")))
        assertNull(brands.getBrandByName("未存在品牌"))
        assertTrue(database.itemDao().getAllItemsList().isEmpty())
        assertTrue(runCatching { withContext(Dispatchers.Main) { vm.applyRecognitionSelection("other-draft", baseline, listOf(length), setOf("length")) } }.isFailure)
        withContext(Dispatchers.Main) { vm.discardDraft() }
        assertTrue(runCatching { withContext(Dispatchers.Main) { vm.applyRecognitionSelection(id, baseline, listOf(length), setOf("length")) } }.isFailure)
    }
    @Test fun readingDeletedTargetNeverCreatesReplacementRecord() = runBlocking {
        val vm = editor()
        withContext(Dispatchers.Main) { vm.loadItem(9999) }
        withTimeout(10000) { while (vm.uiState.value.isLoading) delay(20) }
        assertNotNull(vm.uiState.value.loadError)
        assertTrue(withContext(Dispatchers.Main) { vm.saveItem() }.isFailure)
        assertTrue(database.itemDao().getAllItemsList().isEmpty())
    }
    @Test fun storageEntryPrefillsItsRoomAndNodeWithoutReusingAnotherNewDraft() = runBlocking {
        val unrelated = editor(); loaded(unrelated)
        withContext(Dispatchers.Main) { unrelated.updateName("别处未完成"); unrelated.keepDraft() }
        val unrelatedId = unrelated.uiState.value.draftId!!
        val room = database.locationDao().insertLocation(Location(name = "玄关", description = ""))
        val node = StorageRepository(database).createNode(room, null, "鞋柜")
        val vm = editor(SavedStateHandle(mapOf("defaultLocationId" to room, "defaultStorageNodeId" to node)))
        loaded(vm)
        assertEquals(room, vm.uiState.value.locationId)
        assertEquals(node, vm.uiState.value.storageNodeId)
        assertEquals("", vm.uiState.value.name)
        assertNotEquals(unrelatedId, vm.uiState.value.draftId)
        assertEquals("别处未完成", drafts.get(unrelatedId)!!.data().name)
        val category = categories.insertCategory(Category(name = "本次登记鞋", categorySemantic = CategorySemantic.MARY_JANE))
        val itemId = withContext(Dispatchers.Main) { vm.updateName("新鞋"); vm.updateCategory(category); vm.saveItem().getOrThrow() }
        assertEquals(room, items.getItemById(itemId)!!.locationId)
        assertEquals(node, items.getItemById(itemId)!!.storageNodeId)
        assertTrue(database.priceDao().getPricesByItemList(itemId).isEmpty())
    }
    @Test fun exactDraftRestorationKeepsUserChangedLocationOverEntryDefaults() = runBlocking {
        val room = database.locationDao().insertLocation(Location(name = "玄关", description = ""))
        val otherRoom = database.locationDao().insertLocation(Location(name = "卧室", description = ""))
        val node = StorageRepository(database).createNode(room, null, "鞋柜")
        val draftId = java.util.UUID.randomUUID().toString()
        drafts.save(ItemDraft(id = draftId, dataJson = ItemDraftData(name = "已换位置", locationId = otherRoom).encode()))
        val vm = editor(SavedStateHandle(mapOf("itemDraftId" to draftId,
            "defaultLocationId" to room, "defaultStorageNodeId" to node)))
        loaded(vm)
        assertEquals(draftId, vm.uiState.value.draftId)
        assertEquals(otherRoom, vm.uiState.value.locationId)
        assertNull(vm.uiState.value.storageNodeId)
        assertEquals("已换位置", vm.uiState.value.name)
    }
    @Test fun storageEntryRejectsANodeFromADifferentRoom() = runBlocking {
        val room = database.locationDao().insertLocation(Location(name = "玄关", description = ""))
        val otherRoom = database.locationDao().insertLocation(Location(name = "卧室", description = ""))
        val node = StorageRepository(database).createNode(room, null, "鞋柜")
        val vm = editor(SavedStateHandle(mapOf("defaultLocationId" to otherRoom, "defaultStorageNodeId" to node)))
        withContext(Dispatchers.Main) { vm.loadItem(0) }
        withTimeout(10000) { while (vm.uiState.value.isLoading) delay(20) }
        assertNotNull(vm.uiState.value.loadError)
        assertTrue(withContext(Dispatchers.Main) { vm.saveItem() }.isFailure)
        assertTrue(database.itemDao().getAllItemsList().isEmpty())
    }
    @Test fun footwearEntryStartsFreshWhileTheOtherFamilyDraftStaysRecoverable() = runBlocking {
        val unrelated = editor(); loaded(unrelated)
        withContext(Dispatchers.Main) { unrelated.updateName("假发未完成"); unrelated.keepDraft() }
        val unrelatedId = unrelated.uiState.value.draftId!!
        val vm = editor(SavedStateHandle(mapOf("defaultFamily" to "FOOTWEAR")))
        loaded(vm)
        assertEquals(WardrobeFamily.FOOTWEAR, vm.uiState.value.preferredFamily)
        assertEquals("", vm.uiState.value.name)
        assertEquals(0L, vm.uiState.value.categoryId)
        assertNotEquals(unrelatedId, vm.uiState.value.draftId)
        assertEquals("假发未完成", drafts.get(unrelatedId)!!.data().name)
    }
}
