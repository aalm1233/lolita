package com.lolita.app.data.file

import com.google.gson.Gson
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MaterialBackupFormatTest {
    private val gson=Gson()
    private fun root()=gson.toJsonTree(BackupData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())).asJsonObject
    @Test fun formatsOneThroughTenSupplyEmptyMaterialsAndOldTaskDefaults() {
        for(version in 1..10) {
            val old=root().apply{addProperty("formatVersion",version);addProperty("schemaVersion",30);remove("itemTryOnMaterials")}
            if(version>=8) {
                val task=ImageTask("9145fafd-e0b9-4a85-9e20-87bea5e3e279",ImageTaskKind.MANNEQUIN,ImageGenerationProvider.SEEDREAM,"旧任务",ImageTaskRequest(listOf(ImageTaskGarment(1,"主裙",TryOnRole.MAIN))).encode(),emptyList(),ImageTaskStatus.FAILED)
                val json=gson.toJsonTree(task).asJsonObject.apply{remove("layersJson");remove("generatedImageCount");remove("materialId")}
                old["imageTasks"].asJsonArray.add(json)
            }
            val data=BackupFormat.parse(old.toString())
            assertTrue(data.itemTryOnMaterials.isEmpty())
            data.imageTasks.forEach{assertEquals("[]",it.layersJson);assertEquals(1,it.generatedImageCount);assertNull(it.materialId)}
        }
    }
    @Test fun formatElevenRequiresMaterialTable() {
        val missing=root().apply{remove("itemTryOnMaterials")}
        assertThrows(IllegalArgumentException::class.java){BackupFormat.parse(missing.toString())}
        assertTrue(BackupFormat.parse(root().toString()).itemTryOnMaterials.isEmpty())
    }
}
