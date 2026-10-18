package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.recognition.RecognitionCoordinator
import com.lolita.app.data.repository.ItemDraftRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.RecognitionRepository
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Persistence/ownership with an injected in-process result function; never a real provider. */
@RunWith(AndroidJUnit4::class)
class RecognitionLifecycleTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private inner class Fixture {
        val root = File(target.cacheDir, "recognition-room-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = RecognitionRepository(db, context)
        val draftId = UUID.randomUUID().toString()
        var revision = 4
        var calls = 0
        lateinit var album: String
        lateinit var region: String
        lateinit var item: Item
        suspend fun image(color: Int = android.graphics.Color.GREEN): String {
            val bitmap = Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            val bytes = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
            bitmap.recycle()
            return ImageFileHelper.saveGeneratedImage(context, bytes)
        }
        suspend fun init() {
            ImageFileHelper.configure(context, db)
            album = image(android.graphics.Color.BLUE); region = image()
            db.brandDao().insertBrand(Brand(id = 1, name = "保留品牌"))
            db.categoryDao().insertCategory(Category(id = 1, name = "全头假发", categorySemantic = CategorySemantic.FULL_WIG))
            item = Item(id = 1, name = "原名", description = "保留手工备注", brandId = 1, categoryId = 1,
                imageUrls = listOf(album), status = ItemStatus.OWNED)
            db.itemDao().insertItem(item)
            db.itemDraftDao().save(ItemDraft(draftId, 1, ItemDraftData(name = "原名", brandId = 1, categoryId = 1, baselineUpdatedAt = item.updatedAt).encode(), imagePaths = listOf(album)))
        }
        suspend fun add(path: String = region): String = repository.addRegion(draftId, path,
            mapOf(RecognitionField.NAME to "原名"), WardrobeFamily.WIG, false)
        fun result(request: RecognitionRequest) = RecognitionResult(candidates = listOf(
            RecognitionCandidate("name", RecognitionField.NAME, "图中假发名称", RecognitionSource.TEXT, listOf(request.regions.first().id), "品名：图中假发名称")))
        suspend fun coordinator(ready: Boolean = true,
            run: suspend (String, RecognitionRequest, List<ByteArray>) -> RecognitionResult = { key, request, bytes ->
                calls++; assertEquals("in-process-controlled-key", key)
                assertEquals(request.regions.map { it.sha256 }, bytes.map { value -> java.security.MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) } })
                result(request)
            }): RecognitionCoordinator = RecognitionCoordinator(db, context, scope, { "in-process-controlled-key" }, { revision }, run, { ready }).also {
                it.withRestorePaused { }
            }
        suspend fun waitFor(id: String, status: RecognitionStatus): RecognitionTask = withTimeout(10_000) {
            while (true) {
                db.recognitionDao().get(id)?.takeIf { it.status == status }?.let { return@withTimeout it }
                delay(10)
            }
            error("unreachable")
        }
        suspend fun close() {
            scope.coroutineContext.job.cancelAndJoin()
            db.close(); root.deleteRecursively()
            ImageFileHelper.configure(AppModule.context(), AppModule.database())
        }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.init(); withTimeout(30_000) { block(f) } } finally { f.close() }
    }

    private suspend fun awaiting(f: Fixture, appearance: Boolean = false): String {
        val id = f.repository.addRegion(f.draftId, f.region, mapOf(RecognitionField.NAME to "原名"), WardrobeFamily.WIG, appearance)
        val task = f.repository.get(id)!!
        val candidates = listOf(
            RecognitionCandidate("name", RecognitionField.NAME, "图中名称", RecognitionSource.TEXT, listOf(task.request().regions.single().id), "品名：图中名称"),
            RecognitionCandidate("color", RecognitionField.COLOR, "棕色", RecognitionSource.VISUAL, listOf(task.request().regions.single().id), ""))
        f.db.recognitionDao().update(task.copy(status = RecognitionStatus.AWAITING_REVIEW, requestSent = true,
            resultJson = RecognitionResult(candidates = candidates).encode()))
        return id
    }
    private suspend fun apply(f: Fixture, id: String, selected: Set<String>, edit: String? = null): RecognitionMerge =
        f.repository.apply(id, RecognitionReview(selectedIds = selected, edits = edit?.let { mapOf("name" to it) }.orEmpty())) { request, candidates, review ->
            val draft = f.db.itemDraftDao().get(f.draftId)!!
            val data = draft.data()
            val current = mapOf(RecognitionField.NAME to data.name, RecognitionField.COLOR to data.colors.joinToString("、"))
            RecognitionMerge.apply(request.draftId, draft.id, request.baseline, current, candidates, review.selectedIds).also { merged ->
                f.db.itemDraftDao().save(draft.copy(dataJson = data.copy(name = merged.values[RecognitionField.NAME] ?: data.name,
                    colors = merged.values[RecognitionField.COLOR]?.let(::listOf) ?: data.colors).encode()))
            }
        }
    @Test fun savingTheItemRetargetsAnOutstandingTaskAndItsLateResultStaysReachable() = runBlocking { fixture { f ->
        f.db.itemDraftDao().save(f.db.itemDraftDao().get(f.draftId)!!.copy(targetItemId = null))
        val release = CompletableDeferred<Unit>(); val entered = CompletableDeferred<Unit>()
        val coordinator = f.coordinator(run = { _, request, _ -> f.calls++; entered.complete(Unit); release.await(); f.result(request) })
        val id = f.add(); coordinator.submit(f.repository.confirmation(id, mapOf(RecognitionField.NAME to "原名"), WardrobeFamily.WIG), 4)
        entered.await()
        val savedId = ItemDraftRepository(f.db, ItemRepository(f.db.itemDao(), database = f.db)).commit(f.draftId, f.item.copy(id = 0))
        release.complete(Unit)
        val late = f.waitFor(id, RecognitionStatus.AWAITING_REVIEW)
        assertEquals("Saving must reconnect outstanding work to the saved item", savedId, late.targetItemId)
        assertEquals(savedId, late.request().targetItemId)
        assertEquals("原名", f.db.itemDao().getItemById(1)!!.name)
        assertEquals(1, f.calls)
    } }
    @Test fun partialAcceptanceLeavesOtherFieldsReviewableAndReapplyingOneOriginIsIdempotent() = runBlocking { fixture { f ->
        val id = awaiting(f)
        assertEquals(setOf("name"), apply(f,id,setOf("name")).acceptedIds)
        assertTrue("Previously accepted tasks must permit later review", runCatching { apply(f,id,setOf("color")) }.isSuccess)
        val adjusted = runCatching { apply(f,id,setOf("name"),"核对后的名称") }.getOrNull()
        assertEquals("Already accepted values may be adjusted unless manually changed",setOf("name"),adjusted?.acceptedIds)
        assertEquals("核对后的名称", f.db.itemDraftDao().get(f.draftId)!!.data().name)
        assertEquals("One current evidence row per task/candidate",2,f.db.recognitionDao().getEvidence().size)
        assertEquals("原名", f.db.itemDao().getItemById(1)!!.name)
    } }
    @Test fun appearanceOnlyRegionsNeverBecomeQuotedTextFactsEvenWhenTheProviderMislabelsThem() = runBlocking { fixture { f ->
        val id = awaiting(f,appearance = true)
        val merged = apply(f,id,setOf("name","color"))
        assertEquals(setOf("color"),merged.acceptedIds)
        assertEquals("原名", f.db.itemDraftDao().get(f.draftId)!!.data().name)
        assertEquals(listOf("棕色"),f.db.itemDraftDao().get(f.draftId)!!.data().colors)
        assertTrue(f.db.priceDao().getPricesByItemList(1).isEmpty())
    } }
    @Test fun missingNewDraftCannotPassConfirmationOrApplyByNullableIdentityEquality() = runBlocking { fixture { f ->
        f.db.itemDraftDao().save(f.db.itemDraftDao().get(f.draftId)!!.copy(targetItemId = null))
        val id = awaiting(f)
        f.db.itemDraftDao().delete(f.draftId)
        assertTrue(runCatching { apply(f,id,setOf("name")) }.isFailure)
        val task = f.repository.get(id)!!
        f.db.recognitionDao().update(task.copy(status = RecognitionStatus.PREPARED,requestSent = false,resultJson = null))
        assertTrue(runCatching { f.repository.confirmation(id,emptyMap(),WardrobeFamily.WIG) }.isFailure)
    } }

    private suspend fun editor(f: Fixture): Pair<com.lolita.app.ui.screen.item.ItemEditViewModel,androidx.lifecycle.ViewModelStore> {
        val pair=withContext(Dispatchers.Main) {
            val vm=com.lolita.app.ui.screen.item.ItemEditViewModel(
                ItemRepository(f.db.itemDao(),database=f.db),com.lolita.app.data.repository.BrandRepository(f.db.brandDao(),f.db.itemDao(),f.db),
                com.lolita.app.data.repository.CategoryRepository(f.db.categoryDao(),f.db.itemDao(),f.db),
                com.lolita.app.data.repository.CoordinateRepository(f.db.coordinateDao(),f.db.itemDao(),f.db),
                com.lolita.app.data.repository.PriceRepository(f.db.priceDao(),f.db.paymentDao(),f.context,f.db),
                com.lolita.app.data.repository.StyleRepository(f.db.styleDao(),f.db.itemDao(),f.db),
                com.lolita.app.data.repository.SeasonRepository(f.db.seasonDao(),f.db.itemDao(),f.db),
                com.lolita.app.data.repository.SourceRepository(f.db.sourceDao(),f.db.itemDao(),f.db),
                com.lolita.app.data.repository.LocationRepository(f.db.locationDao(),f.db.itemDao()),
                ItemDraftRepository(f.db,ItemRepository(f.db.itemDao(),database=f.db)),com.lolita.app.data.repository.StorageRepository(f.db))
            vm.loadItem(1)
            vm to androidx.lifecycle.ViewModelStore().apply { put("editor",vm) }
        }
        withTimeout(5000) { while(pair.first.uiState.value.isLoading || pair.first.uiState.value.draftId==null) delay(10) }
        assertNull(pair.first.uiState.value.loadError)
        return pair
    }
    /** Runs the real pre-fix Panel route until the mutex-first entry is introduced. */
    private suspend fun editorApply(vm:com.lolita.app.ui.screen.item.ItemEditViewModel,repo:RecognitionRepository,id:String,review:RecognitionReview):RecognitionMerge {
        val method=vm.javaClass.methods.firstOrNull { it.name=="applyRecognitionTask" }
        if(method==null) return repo.apply(id,review) { request,candidates,selection ->
            vm.applyRecognitionSelection(request.draftId,request.baseline,candidates,selection.selectedIds,selection.variant,selection.sizeRow,selection.resolvedBrandId,selection.resolvedCategoryId)
        }
        return kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn { continuation -> method.invoke(vm,repo,id,review,continuation) }
    }
    @Test fun applyAndAutosaveTakeTheDraftMutexBeforeRoomSoTheyCannotDeadlock() = runBlocking { fixture { f ->
        val (vm,store)=editor(f)
        try {
            val id=awaiting(f)
            val mutex=vm.javaClass.getDeclaredField("draftMutex").apply { isAccessible=true }.get(vm) as kotlinx.coroutines.sync.Mutex
            val ownsDraft=CompletableDeferred<Unit>();val continueSaving=CompletableDeferred<Unit>()
            val completed=withTimeoutOrNull(2000) {
                coroutineScope {
                    val autosave=launch(Dispatchers.IO) { mutex.withLock {
                        ownsDraft.complete(Unit);continueSaving.await()
                        f.db.withTransaction { f.db.itemDraftDao().get(f.draftId) }
                    } }
                    ownsDraft.await()
                    val apply=async(Dispatchers.Main) { editorApply(vm,f.repository,id,RecognitionReview(selectedIds=setOf("name"))) }
                    delay(150);continueSaving.complete(Unit)
                    autosave.join();apply.await()
                }
            }
            assertNotNull("Old Room -> editor mutex order deadlocks against autosave",completed)
            assertEquals("图中名称",vm.uiState.value.name)
        } finally { withContext(Dispatchers.Main) {store.clear()} }
    } }
    @Test fun evidenceFailureRollsBackDraftAndUiTogetherWithoutPublishingUncommittedFacts() = runBlocking { fixture { f ->
        val (vm,store)=editor(f)
        try {
            val id=awaiting(f)
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_evidence BEFORE INSERT ON item_evidence BEGIN SELECT RAISE(ABORT,'controlled evidence failure'); END")
            val result=withContext(Dispatchers.Main) { runCatching { editorApply(vm,f.repository,id,RecognitionReview(selectedIds=setOf("name"))) } }
            assertTrue(result.isFailure)
            assertEquals("原名",vm.uiState.value.name)
            assertEquals("原名",f.db.itemDraftDao().get(f.draftId)!!.data().name)
            assertTrue(f.db.recognitionDao().getEvidence().isEmpty())
            assertFalse(vm.uiState.value.isSaving)
        } finally { withContext(Dispatchers.Main) {store.clear()} }
    } }
    @Test fun copiedAiImageKeepsItsRestrictionEvenAfterTheOriginalItemIsDeleted() = runBlocking { fixture { f ->
        f.db.imageProvenanceDao().upsert(ImageProvenance(f.album,1,ImageSourceType.AI_GENERATED,"controlled-test"))
        val copies=ItemDraftRepository(f.db,ItemRepository(f.db.itemDao(),database=f.db))
        val copyId=copies.copyFromItem(1)
        f.db.itemDao().deleteItem(f.item)
        val copied=f.db.itemDraftDao().get(copyId)!!
        assertEquals(listOf(f.album),copied.appearanceOnlyImagePaths)
        assertEquals(listOf(f.album),copied.imagePaths)
        assertTrue(File(f.album).isFile)
    } }
    @Test fun rapidReviewEditsArePersistedInOrderInsteadOfBeingDroppedByGlobalBusy() = runBlocking { fixture { f ->
        val id=awaiting(f)
        val coordinator=f.coordinator()
        val settingContext=object: ContextWrapper(f.context) { override fun getNoBackupFilesDir()=File(f.root,"keys").apply {mkdirs()} }
        val (vm,store)=withContext(Dispatchers.Main) {
            val vm=com.lolita.app.ui.screen.item.recognition.ItemRecognitionViewModel(f.repository,coordinator,
                com.lolita.app.data.recognition.RecognitionSettings(settingContext))
            vm.bind(f.draftId)
            vm to androidx.lifecycle.ViewModelStore().apply {put("recognition",vm)}
        }
        try {
            withTimeout(5000) {while(vm.uiState.value.tasks.none {it.id==id}) delay(10)}
            val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val blocker=launch(Dispatchers.IO) { f.db.withTransaction {entered.complete(Unit);release.await()} }
            entered.await()
            withContext(Dispatchers.Main) {vm.saveReview(id,RecognitionReview(edits=mapOf("name" to "新")))}
            delay(100)
            withContext(Dispatchers.Main) {
                vm.saveReview(id,RecognitionReview(edits=mapOf("name" to "新名称")))
                vm.saveReview(id,RecognitionReview(edits=mapOf("name" to "新名称最终")))
            }
            release.complete(Unit);blocker.join();delay(250)
            val saved=RecognitionReview.decode(f.repository.get(id)!!.reviewJson!!,setOf("name","color"))
            assertEquals("新名称最终",saved.edits["name"])
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    } }
    @Test fun applyingReservesTheEditorBeforeWaitingSoTypingAndSaveCannotRaceIt() = runBlocking { fixture { f ->
        val (vm,store)=editor(f)
        try {
            val id=awaiting(f)
            val mutex=vm.javaClass.getDeclaredField("draftMutex").apply {isAccessible=true}.get(vm) as kotlinx.coroutines.sync.Mutex
            mutex.lock()
            val applying=async(Dispatchers.Main) {editorApply(vm,f.repository,id,RecognitionReview(selectedIds=setOf("name")))}
            try {
                delay(100)
                withContext(Dispatchers.Main) {
                    vm.updateName("应用时闯入的编辑")
                    assertEquals("原名",vm.uiState.value.name)
                    val saved=withTimeoutOrNull(100) {vm.saveItem()}
                    assertTrue("Save must reject immediately while applying",saved?.isFailure==true)
                }
            } finally {mutex.unlock()}
            assertEquals(setOf("name"),applying.await().acceptedIds)
            assertEquals("图中名称",vm.uiState.value.name)
            assertEquals("原名",f.db.itemDao().getItemById(1)!!.name)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    } }

}
