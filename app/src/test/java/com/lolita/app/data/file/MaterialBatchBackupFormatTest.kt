package com.lolita.app.data.file

import com.google.gson.Gson
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MaterialBatchBackupFormatTest {
    private val gson=Gson()
    private fun root()=gson.toJsonTree(BackupData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())).asJsonObject
    private fun task()=ImageTask(UUID.randomUUID().toString(),ImageTaskKind.MANNEQUIN,ImageGenerationProvider.SEEDREAM,"旧任务",
        ImageTaskRequest(listOf(ImageTaskGarment(1,"原服饰",TryOnRole.MAIN))).encode(),emptyList(),ImageTaskStatus.FAILED)
    @Test fun formatsOneThroughElevenKeepOldTasksAndSupplyNoInventedBatch() {
        for(version in 1..11){val old=root().apply{addProperty("formatVersion",version);addProperty("schemaVersion",31)}
            if(version>=8)old["imageTasks"].asJsonArray.add(gson.toJsonTree(task()).asJsonObject.apply{remove("batchId");remove("batchName");remove("batchPosition")})
            val data=BackupFormat.parse(old.toString());data.imageTasks.forEach{assertNull(it.batchId);assertNull(it.batchName);assertNull(it.batchPosition)}
        }
    }
    @Test fun incompleteGroupingAndUnrelatedTaskKindsAreRejected() {
        val old=task();val batch=MaterialBatchIdentity(UUID.randomUUID().toString(),"批次")
        for(row in listOf(old.copy(batchId=batch.id),old.copy(batchId=batch.id,batchName=batch.name,batchPosition=0))){
            val json=root().apply{getAsJsonArray("imageTasks").add(gson.toJsonTree(row))}
            assertThrows(IllegalArgumentException::class.java){BackupFormat.parse(json.toString())}
        }
    }
}
