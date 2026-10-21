package com.lolita.app.domain.model

import com.google.gson.Gson
import com.lolita.app.data.file.BackupData
import com.lolita.app.data.file.BackupFormat
import com.lolita.app.data.local.entity.ImageTask
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class MaterialCategoriesTest {
    private fun request(semantic:CategorySemantic,composition:ItemComposition=ItemComposition.SINGLE)=ImageTaskRequest(
        listOf(ImageTaskGarment(1,"原物品",semantic.defaultWearRole,semantic,composition)),version=6,promptVersion=2,
        promptSnapshot=TryOnMaterialPrompts.front(semantic,composition,"保留原结构"),serviceModel=ImageGenerationProvider.SEEDREAM.model,
        inputSha256s=listOf("a".repeat(64)),materialSpec=MaterialTaskSpec(MaterialStage.FRONT_VIEW))
    @Test fun everyConfirmedSemanticAndCompositionSurvivesFrozenRoundTripWithItsRealRole(){
        for(semantic in CategorySemantic.entries.filter{it.supportsMaterialPreparation})for(composition in ItemComposition.entries){
            val request=request(semantic,composition);request.validate(ImageTaskKind.SINGLE_ITEM_MATERIAL)
            assertEquals(request,ImageTaskRequest.decode(request.encode()));assertEquals(semantic.defaultWearRole,request.garments.single().role)
            val extract=request.copy(promptSnapshot=TryOnMaterialPrompts.extract(semantic,composition,"保留原结构"),materialSpec=MaterialTaskSpec(MaterialStage.EXTRACT_LAYER,parentTaskId="parent"))
            assertEquals(extract,ImageTaskRequest.decode(extract.encode()))
        }
    }
    @Test fun v6CannotChangeUnconfirmedIdentityOrBecomeTryOnAndV5StaysWaistOnly(){
        val request=request(CategorySemantic.BLOUSE)
        assertThrows(IllegalArgumentException::class.java){request.copy(garments=listOf(request.garments.single().copy(role=TryOnRole.WAIST)))}
        assertThrows(IllegalArgumentException::class.java){request.copy(garments=listOf(request.garments.single().copy(semantic=CategorySemantic.UNCONFIRMED,role=TryOnRole.OTHER)))}
        assertThrows(IllegalArgumentException::class.java){request.copy(garments=listOf(request.garments.single().copy(composition=null)))}
        for(kind in listOf(ImageTaskKind.TRY_ON,ImageTaskKind.MANNEQUIN,ImageTaskKind.PERSONAL_MODEL))assertThrows(IllegalArgumentException::class.java){request.validate(kind)}
        assertThrows(IllegalArgumentException::class.java){request.copy(version=5,promptVersion=1)}
        val waist=request.copy(version=5,promptVersion=1,garments=listOf(ImageTaskGarment(1,"腰封",TryOnRole.WAIST,CategorySemantic.WAIST_ACCESSORY,ItemComposition.SINGLE)),promptSnapshot=TryOnMaterialPrompts.front("原要求"))
        assertEquals(waist,ImageTaskRequest.decode(waist.encode()));waist.validate(ImageTaskKind.SINGLE_ITEM_MATERIAL)
    }
    @Test fun categoryPromptsProtectCriticalShapesAndDoNotTreatSinglesAsPairs(){
        assertTrue(TryOnMaterialPrompts.front(CategorySemantic.DOLL,ItemComposition.SINGLE,"").contains("不当作需要移除的真人"))
        assertTrue(TryOnMaterialPrompts.front(CategorySemantic.BLOUSE,ItemComposition.SINGLE,"").contains("不补配裙子"))
        assertTrue(TryOnMaterialPrompts.front(CategorySemantic.PETTICOAT,ItemComposition.SINGLE,"").contains("不加罩裙"))
        assertTrue(TryOnMaterialPrompts.front(CategorySemantic.HAIR_PIECE,ItemComposition.SET,"").contains("不能补成全头假发"))
        assertTrue(TryOnMaterialPrompts.front(CategorySemantic.FULL_WIG,ItemComposition.SINGLE,"").contains("不补脸"))
        assertTrue(TryOnMaterialPrompts.front(CategorySemantic.MARY_JANE,ItemComposition.SINGLE,"").contains("可保留必要侧向"))
        for(semantic in CategorySemantic.entries.filter{it.supportsMaterialPreparation}){
            assertTrue(TryOnMaterialPrompts.front(semantic,ItemComposition.UNCONFIRMED,"").contains("单只不补双"))
            assertTrue(TryOnMaterialPrompts.extract(semantic,ItemComposition.PAIR,"").contains("同一个完整前景图层"))
        }
    }
    @Test fun backup13CarriesGeneralMaterialsWhileOld12KeepsOldRequestsAndRejectsNewProtocol(){
        val gson=Gson();val task=ImageTask(UUID.randomUUID().toString(),ImageTaskKind.SINGLE_ITEM_MATERIAL,ImageGenerationProvider.SEEDREAM,"衬衫",request(CategorySemantic.BLOUSE).encode(),listOf("source"),keepSourceImages=true)
        fun root()=gson.toJsonTree(BackupData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())).asJsonObject
        val modern=root().apply{getAsJsonArray("imageTasks").add(gson.toJsonTree(task))}
        assertEquals(6,ImageTaskRequest.decode(BackupFormat.parse(modern.toString()).imageTasks.single().requestJson).version)
        modern.addProperty("formatVersion",12);assertThrows(IllegalArgumentException::class.java){BackupFormat.parse(modern.toString())}
        val batch=MaterialBatchIdentity(UUID.randomUUID().toString(),"旧腰饰批次")
        val legacy=request(CategorySemantic.WAIST_ACCESSORY).copy(version=5,promptVersion=1,promptSnapshot=TryOnMaterialPrompts.front("旧要求"))
        val oldTask=task.copy(requestJson=legacy.encode(),batchId=batch.id,batchName=batch.name,batchPosition=0)
        val old=root().apply{addProperty("formatVersion",12);getAsJsonArray("imageTasks").add(gson.toJsonTree(oldTask))}
        val restored=BackupFormat.parse(old.toString()).imageTasks.single()
        assertEquals(batch.id,restored.batchId);assertEquals(legacy,ImageTaskRequest.decode(restored.requestJson))
    }
}
