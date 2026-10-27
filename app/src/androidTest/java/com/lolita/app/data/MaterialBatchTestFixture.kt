package com.lolita.app.data

import androidx.lifecycle.SavedStateHandle
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.material.MaterialBatchViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal suspend fun MaterialTestFixture.batchItems(count:Int=3):List<Item> {
    val first=requireNotNull(db.itemDao().getItemById(itemId))
    repeat(count-1){index->val image=ImageFileHelper.saveGeneratedImage(context,opaque);db.itemDao().insertItem(first.copy(id=0,name="批量腰饰${index+2}",imageUrls=listOf(image)))}
    return db.itemDao().getAllItemsList().sortedBy{it.id}
}
internal suspend fun MaterialTestFixture.batchInputs(items:List<Item>)=items.mapIndexed{index,item->BatchMaterialInput(item.id,
    coordinator.prepareMaterialInput(item.imageUrls.first(),false),item.imageUrls.first(),db.itemTryOnMaterialDao().getForItem(item.id)?.id,"保留附属链条",index)}
internal suspend fun MaterialTestFixture.batchLayers(ids:List<String>)=ids.map{ id->val task=requireNotNull(db.imageTaskDao().get(id));BatchLayerInput(id,coordinator.prepareMaterialInput(requireNotNull(task.resultImagePath),false))}
internal fun MaterialTestFixture.batchViewModel(handle:SavedStateHandle,changes:Flow<Int> = flowOf(0),read:suspend()->ImageServiceStatus={ImageServiceStatus(ImageGenerationProvider.SEEDREAM,true)})=
    MaterialBatchViewModel(handle,db.itemDao().getAllItems(),db.categoryDao().getAllCategories(),db.itemTryOnMaterialDao().observeAll(),db.imageTaskDao().observe(),db.brandDao().getAllBrands(),
        coordinator,read,changes,pin={paths->val lease=ImageFileHelper.pinImageReferences(context,paths);val release:suspend()->Unit={lease.release()};release},cleanup={ImageFileHelper.cleanupUnreferencedImages(it);Unit},displayImages=db.imageProvenanceDao().observeAll())
