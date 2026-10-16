package com.lolita.app.data

import android.graphics.Bitmap
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lolita.app.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.PersonalImageIntegrity
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.data.local.entity.PersonalModel
import com.lolita.app.data.local.entity.PersonalModelDraft
import com.lolita.app.data.local.entity.TryOnResult
import com.lolita.app.data.repository.PersonalModelRepository
import com.lolita.app.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersonalModelPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LolitaDatabase::class.java)
    private fun request() = PersonalModelRequest("本人", PersonalModelMode.PARAMETERS, BodyMeasurements(165.0, 52.0))
    private fun model(image: String) = PersonalModel(name="本人", requestJson=request().encode(), sourceImagePaths=emptyList(),
        imageUrl=image, provider=ImageGenerationProvider.OPENAI, model="原模型", createdAt=10, updatedAt=11)
    private fun image(): String {
        val bytes=ByteArrayOutputStream().also { stream -> Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).let {
            it.compress(Bitmap.CompressFormat.PNG,100,stream); it.recycle()
        }}.toByteArray()
        return File(context.filesDir,"images/${UUID.randomUUID()}.png").apply { parentFile!!.mkdirs(); writeBytes(bytes) }.absolutePath
    }

    @Test fun migrationPreservesOldTaskAndHistoryAndAddsNullableSnapshots() {
        val name="personal-migration-${UUID.randomUUID()}"
        try {
            migration.createDatabase(name,28).use { old ->
                old.execSQL("INSERT INTO try_on_results(id,name,image_url,item_ids,item_names,model,created_at) VALUES(9,'原试衣','/original.png','[\"5\"]','[\"原裙子\"]','old-model',127)")
                old.execSQL("INSERT INTO image_tasks(id,kind,provider,title,request_json,source_image_paths,status,request_sent,created_at,updated_at) VALUES('old','MANNEQUIN','OPENAI','原人台','old-json','[\"old-source\"]','INTERRUPTED',1,10,11)")
            }
            migration.runMigrationsAndValidate(name,29,true,LolitaDatabase.MIGRATION_28_29).use { db ->
                db.query("SELECT name,image_url,model,created_at,personal_model_id,personal_model_name FROM try_on_results WHERE id=9").use {
                    assertTrue(it.moveToFirst()); assertEquals("原试衣",it.getString(0)); assertEquals("/original.png",it.getString(1))
                    assertEquals("old-model",it.getString(2)); assertEquals(127,it.getLong(3)); assertTrue(it.isNull(4)); assertTrue(it.isNull(5))
                }
                db.query("SELECT request_json,source_image_paths,request_sent,personal_model_id FROM image_tasks WHERE id='old'").use {
                    assertTrue(it.moveToFirst()); assertEquals("old-json",it.getString(0)); assertEquals("[\"old-source\"]",it.getString(1)); assertEquals(1,it.getInt(2)); assertTrue(it.isNull(3))
                }
                db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun renameOnlyChangesNameAndUpdatedTimeAndDeletingModelPreservesHistory() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        try {
            val repo=PersonalModelRepository(db)
            val original=model("/original.png")
            val id=db.personalModelDao().insert(original)
            db.tryOnDao().insert(TryOnResult(name="历史",imageUrl="/history.png",itemIds=listOf("5"),itemNames=listOf("原裙子"),model="v1", personalModelId=id,personalModelName="本人"))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.rename(id,"人".repeat(61)) } }
            repo.rename(id," 新名字 ")
            val renamed=repo.get(id)!!
            assertEquals(original.copy(id=id,name="新名字",updatedAt=renamed.updatedAt),renamed)
            assertTrue(renamed.updatedAt > original.updatedAt)
            renamed.request().copy(name=renamed.name).validate()
            repo.delete(id)
            assertNull(repo.get(id)); assertEquals("本人",db.tryOnDao().getAll().single().personalModelName)
        } finally { db.close() }
    }

    @Test fun draftReopenProtectsPhotosUntilExplicitDiscardAndTaskRetainsDeletedModelImage() = runBlocking {
        val name="personal-draft-${UUID.randomUUID()}"
        val path=image(); val result=image()
        var db=Room.databaseBuilder(context,LolitaDatabase::class.java,name).build()
        try {
            ImageFileHelper.configure(context,db)
            val data=PersonalModelDraftData(heightCm="未完成",photoRoles=listOf(PersonalModelPhotoRole.FRONT))
            val draft=PersonalModelDraft(UUID.randomUUID().toString(),data.encode(),listOf(path),15)
            PersonalModelRepository(db).saveDraft(draft)
            db.close(); db=Room.databaseBuilder(context,LolitaDatabase::class.java,name).build(); ImageFileHelper.configure(context,db)
            val repo=PersonalModelRepository(db)
            assertEquals(draft,repo.getDraft(draft.id))
            ImageFileHelper.deleteImage(path); assertTrue(File(path).isFile)
            repo.deleteDraft(draft.id); assertFalse(File(path).exists())
            val id=db.personalModelDao().insert(model(result))
            val task=ImageTask(id="keep",kind=ImageTaskKind.TRY_ON,provider=ImageGenerationProvider.OPENAI,title="试衣",
                requestJson=ImageTaskRequest(garments=listOf(ImageTaskGarment(5,"裙子",TryOnRole.MAIN)),version=2,
                    tryOnModel=TryOnModelSnapshot(id,"本人")).encode(),sourceImagePaths=listOf(result,"/garment.png"))
            db.imageTaskDao().insert(task)
            repo.delete(id); assertTrue(File(result).isFile)
            db.imageTaskDao().delete(task.id); ImageFileHelper.deleteImage(result); assertFalse(File(result).exists())
        } finally { db.close(); context.deleteDatabase(name); File(path).delete(); File(result).delete() }
    }

    @Test fun personalPendingAndInterruptedTasksProtectSourcesUntilExplicitDiscard() {
        val path="/reference.png"
        val base=ImageTask(id="pending",kind=ImageTaskKind.PERSONAL_MODEL,provider=ImageGenerationProvider.OPENAI,title="本人",
            requestJson=ImageTaskRequest(garments=emptyList(),version=2,personalModel=PersonalModelRequest("本人",PersonalModelMode.PHOTO,
                BodyMeasurements(165.0,52.0),listOf(PersonalModelPhotoRole.FRONT))).encode(),
            sourceImagePaths=listOf(path),resultImagePath="/preview.png")
        listOf(ImageTaskStatus.AWAITING_CONFIRMATION,ImageTaskStatus.FAILED,ImageTaskStatus.INTERRUPTED,ImageTaskStatus.CANCELLED).forEach { status ->
            val task=base.copy(status=status)
            task.validate()
            assertTrue(task.retainsSourceImages)
            assertTrue(task.protectedImagePaths().contains(path))
            assertEquals(listOf(path),task.releaseUnusedSourceImages().sourceImagePaths)
        }
        assertTrue(base.copy(status=ImageTaskStatus.DISCARDED).releaseUnusedSourceImages().sourceImagePaths.isEmpty())
    }

    @Test fun replacingDraftReleasesOnlyRemovedPhotoAndOwnershipTransferRetainsModelPhoto() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        val first=image(); val second=image()
        try {
            ImageFileHelper.configure(context,db)
            val repo=PersonalModelRepository(db)
            val id=UUID.randomUUID().toString()
            val draft=PersonalModelDraft(id,PersonalModelDraftData(photoRoles=listOf(PersonalModelPhotoRole.FRONT)).encode(),listOf(first))
            repo.saveDraft(draft)
            repo.saveDraft(draft.copy(sourceImagePaths=listOf(second)))
            assertFalse(File(first).exists()); assertTrue(File(second).exists())
            db.personalModelDao().insert(model(second))
            repo.deleteDraft(id)
            assertTrue(File(second).exists())
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.saveDraft(draft.copy(sourceImagePaths=emptyList())) } }
            assertNull(repo.getDraft(id))
        } finally { db.close(); File(first).delete(); File(second).delete() }
    }

    @Test fun defaultSelectionPersistsAndUnknownModelResolvesToBuiltIn() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        val directory=File(context.cacheDir,"personal-preferences-${UUID.randomUUID()}").apply { mkdirs() }
        var scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        fun preferences()=AppPreferences(context,PreferenceDataStoreFactory.create(scope=scope,produceFile={File(directory,"settings.preferences_pb")}))
        try {
            var prefs=preferences()
            assertNull(prefs.defaultPersonalModelId.first())
            prefs.setDefaultPersonalModelId(900)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope=CoroutineScope(SupervisorJob()+Dispatchers.IO); prefs=preferences()
            assertEquals(900L,prefs.defaultPersonalModelId.first())
            // A persisted id is only a preference, never evidence that the reference still exists.
            assertNull(PersonalModelRepository(db).observe().first().firstOrNull { it.id==prefs.defaultPersonalModelId.first() })
            prefs.setDefaultPersonalModelId(null); assertNull(prefs.defaultPersonalModelId.first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); directory.deleteRecursively(); db.close() }
    }

    @Test fun realJpegAndDamagedEntropyUseStrictGateAndOwnedFileInputPath() = runBlocking {
        val bytes=context.assets.open("builtin/brands/with_puji.jpg").use { it.readBytes() }
        val valid=File(context.filesDir,"images/${UUID.randomUUID()}.jpg").apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        var at=2
        var scanStart=-1
        while (at<bytes.size-4) {
            require(bytes[at++]==255.toByte())
            val marker=bytes[at++].toInt() and 255
            val size=((bytes[at].toInt() and 255) shl 8) or (bytes[at+1].toInt() and 255)
            at+=size
            if (marker==0xda) { scanStart=at; break }
        }
        require(scanStart>0)
        val damaged=bytes.copyOf(scanStart+(bytes.size-scanStart)/3)+byteArrayOf(255.toByte(),0xd9.toByte())
        val broken=File(context.filesDir,"images/${UUID.randomUUID()}.jpg").apply { writeBytes(damaged) }
        try {
            assertTrue(PersonalImageIntegrity.isComplete(bytes,"image/jpeg"))
            assertFalse(PersonalImageIntegrity.isComplete(damaged,"image/jpeg"))
            assertTrue(ImageFileHelper.isOwnedReadableImage(context,valid.path))
            assertFalse(ImageFileHelper.isOwnedReadableImage(context,broken.path))
        } finally { valid.delete(); broken.delete() }
    }

    @Test fun inputOwnershipRejectsOutsideMalformedAndAcceptsFullyDecodedUuidImage() = runBlocking {
        val path=image()
        val malformed=File(context.filesDir,"images/${UUID.randomUUID()}.png").apply { writeText("not-image") }
        try {
            assertTrue(ImageFileHelper.isOwnedReadableImage(context,path))
            assertFalse(ImageFileHelper.isOwnedReadableImage(context,malformed.path))
            assertFalse(ImageFileHelper.isOwnedReadableImage(context,File(path).parentFile!!.path+"/../images/"+File(path).name))
        } finally { File(path).delete(); malformed.delete() }
    }
}
