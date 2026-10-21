package com.lolita.app.data

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.remote.GeneratedImageLayer
import com.lolita.app.data.remote.GeneratedImageLayers
import com.lolita.app.domain.model.*
import java.io.ByteArrayOutputStream

internal suspend fun MaterialTestFixture.categoryItems():List<Item>{
    val original=db.itemDao().getItemById(itemId)!!
    val types=listOf(CategorySemantic.JSK,CategorySemantic.BLOUSE,CategorySemantic.FULL_WIG,CategorySemantic.HAIR_PIECE,CategorySemantic.SHORT_SOCKS,CategorySemantic.MARY_JANE,CategorySemantic.KC,CategorySemantic.HANDBAG,CategorySemantic.PARASOL,CategorySemantic.PETTICOAT)
    return types.mapIndexed{index,type->
        val asset=when(type.family){WardrobeFamily.CLOTHING->if(type==CategorySemantic.BLOUSE)1 else if(type==CategorySemantic.PETTICOAT)9 else 0;WardrobeFamily.WIG->if(type==CategorySemantic.FULL_WIG)2 else 8;WardrobeFamily.HOSIERY->6;WardrobeFamily.FOOTWEAR->3;WardrobeFamily.ACCESSORY->4;WardrobeFamily.BAG->5;WardrobeFamily.HANDHELD->7;else->error("unsupported")}
        val image=ImageFileHelper.saveGeneratedImage(context,InstrumentationRegistry.getInstrumentation().context.assets.open("material-categories/$asset.png").use{it.readBytes()})
        val category=db.categoryDao().insertCategory(Category(name=type.label,categorySemantic=type))
        val row=original.copy(id=if(index==0)itemId else 0,categoryId=category,name="示例${type.label}",imageUrls=listOf(image),composition=if(type==CategorySemantic.SHORT_SOCKS)ItemComposition.PAIR else ItemComposition.SINGLE)
        if(index==0){db.itemDao().updateItem(row);row}else row.copy(id=db.itemDao().insertItem(row))
    }
}
/** Controlled echo transport keeps each original vector fixture identifiable; no model quality claim. */
internal fun MaterialTestFixture.echoCategoryImages(){
    frontOutput={receivedImages.single()}
    layerOutput={
        val input=receivedImages.single();val bitmap=BitmapFactory.decodeByteArray(input,0,input.size).copy(Bitmap.Config.ARGB_8888,true)
        val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        for(i in pixels.indices)if(pixels[i]==Color.WHITE)pixels[i]=Color.TRANSPARENT
        bitmap.setPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val bytes=ByteArrayOutputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle();it.toByteArray()}
        GeneratedImageLayers(listOf(GeneratedImageLayer(bytes,1,"完整物品","含全部已知组成",listOf(0,0,1000,1000))),2)
    }
}
