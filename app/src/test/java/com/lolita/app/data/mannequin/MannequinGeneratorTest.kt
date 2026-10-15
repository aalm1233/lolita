package com.lolita.app.data.mannequin

import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.tryon.TryOnGenerator
import com.lolita.app.data.tryon.TryOnGenerationPhase
import com.lolita.app.domain.model.MannequinRequest
import com.lolita.app.domain.model.TryOnRole
import com.lolita.app.domain.model.TryOnSelection
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MannequinGeneratorTest {
    private class Fixture : AutoCloseable {
        val folder = kotlin.io.path.createTempDirectory("mannequin-").toFile()
        val source = File(folder, "dress.jpg").apply { writeBytes(byteArrayOf(1)) }
        val preview = File(folder, "preview.png")
        var item: Item? = Item(id=11, name="private name must not be uploaded", description="private notes", brandId=1, categoryId=7,
            imageUrls=listOf(source.absolutePath), status=ItemStatus.WISHED)
        var category: Category? = Category(id=7, name="OP")
        val request get() = MannequinRequest(11, source.absolutePath, "排除独立手袖")
        var key = "sk-personal-key"
        var apiCalls = 0
        var saveCalls = 0
        var prepare: suspend (List<String>)->List<ByteArray> = { paths ->
            listOf("TEMPLATE".toByteArray()) + paths.map { File(it).readBytes() }
        }
        var api: suspend (String,List<ByteArray>,String)->ByteArray = { key, images, prompt ->
            apiCalls++
            assertEquals("sk-personal-key", key)
            assertEquals(2,images.size)
            assertArrayEquals("TEMPLATE".toByteArray(),images[0])
            assertArrayEquals(byteArrayOf(1),images[1])
            assertFalse(prompt.contains("private name"))
            assertTrue(prompt.endsWith("Additional user instruction about the garment: 排除独立手袖"))
            "RESULT".toByteArray()
        }
        var save: suspend (ByteArray)->String = { bytes -> saveCalls++; preview.writeBytes(bytes); preview.absolutePath }
        fun generator() = MannequinGenerator(
            findItem={ item },findCategory={ category },readKey={ key },prepareImages={ prepare(it) },
            generateImage={ key, images, prompt -> api(key,images,prompt) },savePreview={ save(it) },
            discardPreview={ File(it).delete(); Unit }
        )
        override fun close() { folder.deleteRecursively() }
    }

    @Test fun wishedDressProducesOnlyUncommittedPreviewUsingOrderedTemplateAndPhoto() = runBlocking {
        Fixture().use { f ->
            val phases = mutableListOf<TryOnGenerationPhase>()
            val original = f.item
            assertEquals(f.preview.absolutePath,f.generator().generate(f.request,phases::add))
            assertEquals(listOf(TryOnGenerationPhase.PREPARING,TryOnGenerationPhase.GENERATING,TryOnGenerationPhase.SAVING),phases)
            assertEquals(original,f.item)
            assertArrayEquals("RESULT".toByteArray(),f.preview.readBytes())
            assertEquals(1,f.apiCalls); assertEquals(1,f.saveCalls)
            assertFalse(f.generator().isGenerating.value)
        }
    }

    @Test fun staleItemCategoryReferenceAndMissingSourceNeverStartPaidRequest() = runBlocking {
        Fixture().use { f ->
            val original = f.item!!
            f.item=null; reject { f.generator().generate(f.request) }
            f.item=original.copy(id=22); reject { f.generator().generate(f.request) }
            f.item=original.copy(imageUrls=emptyList()); reject { f.generator().generate(f.request) }
            f.item=original; f.category=Category(id=7,name="上衣"); reject { f.generator().generate(f.request) }
            f.category=null; reject { f.generator().generate(f.request) }
            f.category=Category(id=8,name="OP"); reject { f.generator().generate(f.request) }
            f.category=Category(id=7,name="OP"); f.source.delete(); reject { f.generator().generate(f.request) }
            assertEquals(0,f.apiCalls); assertEquals(0,f.saveCalls)
        }
    }

    @Test fun referenceRemovedWhilePreparingIsRejectedBeforePaidUpload() = runBlocking {
        Fixture().use { f ->
            f.prepare={ f.item=f.item!!.copy(imageUrls=emptyList()); listOf("TEMPLATE".toByteArray(),byteArrayOf(1)) }
            reject { f.generator().generate(f.request) }
            assertEquals(0,f.apiCalls); assertFalse(f.preview.exists())
        }
    }

    @Test fun invalidKeyAndPartialPreparationNeverStartPaidRequest() = runBlocking {
        Fixture().use { f ->
            for (key in listOf("", " ", "sk key", "sk-key\nheader", "x".repeat(513))) {
                f.key=key; reject { f.generator().generate(f.request) }
            }
            f.key="sk-personal-key"; f.prepare={ listOf(byteArrayOf(1)) }
            reject { f.generator().generate(f.request) }
            assertEquals(0,f.apiCalls); assertEquals(0,f.saveCalls)
        }
    }

    @Test fun failureNeverRetriesOrLeavesPreviewAndReleasesGate() = runBlocking {
        Fixture().use { f ->
            f.api={ _,_,_ -> f.apiCalls++; error("controlled service failure") }
            reject { f.generator().generate(f.request) }
            assertEquals(1,f.apiCalls); assertFalse(f.preview.exists()); assertFalse(f.generator().isGenerating.value)
        }
    }

    @Test fun bothTryOnAndMannequinSharePaidGateAndCancellationReleasesIt() = runBlocking {
        Fixture().use { f ->
            val entered=CompletableDeferred<Unit>()
            f.api={ _,_,_ -> entered.complete(Unit); awaitCancellation() }
            val mannequin=f.generator()
            val tryOn=TryOnGenerator(findItem={ f.item!!.copy(status=ItemStatus.OWNED) },readKey={ f.key },
                prepareImages={ error("gate must reject before preparation") },generateImage={ _,_,_ -> error("no paid call") },
                saveResult={ _,_,_ -> error("no result") })
            val job=async(start=CoroutineStart.UNDISPATCHED) { mannequin.generate(f.request) }
            withTimeout(2000) { entered.await() }
            assertTrue(tryOn.isGenerating.value)
            reject { tryOn.generate(listOf(TryOnSelection(11,"裙子",f.source.absolutePath,TryOnRole.MAIN))) }
            job.cancelAndJoin()
            assertFalse(mannequin.isGenerating.value); assertFalse(tryOn.isGenerating.value)
        }
    }

    @Test fun cancellationDuringPreparationOrBeforeSaveCreatesNoFile() = runBlocking {
        Fixture().use { f ->
            val entered=CompletableDeferred<Unit>()
            f.prepare={ entered.complete(Unit); awaitCancellation() }
            val preparing=async { f.generator().generate(f.request) }
            withTimeout(2000) { entered.await() }; preparing.cancelAndJoin()
            assertEquals(0,f.apiCalls); assertFalse(f.preview.exists())
            f.prepare={ listOf("TEMPLATE".toByteArray(),byteArrayOf(1)) }
            val beforeSave=async {
                val job=currentCoroutineContext().job
                f.generator().generate(f.request) { if(it==TryOnGenerationPhase.SAVING) job.cancel() }
            }
            try { beforeSave.await(); fail("cancelled request delivered preview") } catch (_: CancellationException) { }
            assertEquals(0,f.saveCalls); assertFalse(f.preview.exists()); assertFalse(f.generator().isGenerating.value)
        }
    }

    @Test fun cancellationAfterFileWriteBeforeDeliveryDeletesUncommittedPreview() = runBlocking {
        Fixture().use { f ->
            val running=async {
                val owner=currentCoroutineContext().job
                f.save={ bytes -> f.preview.writeBytes(bytes); owner.cancel(); f.preview.absolutePath }
                f.generator().generate(f.request)
            }
            try { running.await(); fail("cancelled request delivered preview") } catch (_: CancellationException) { }
            assertFalse("written preview must not be stranded",f.preview.exists())
            assertFalse(f.generator().isGenerating.value)
        }
    }

    private suspend fun reject(block:suspend()->Unit) {
        try { block(); fail("invalid generation accepted") }
        catch (e: CancellationException) { throw e }
        catch (_: IllegalArgumentException) { }
        catch (_: IllegalStateException) { }
    }
}
