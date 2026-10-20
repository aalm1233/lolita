package com.lolita.app.domain.model

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class TryOnMaterialProtocolTest {
    private val hash = "a".repeat(64)
    private fun garment(material: String? = null) = ImageTaskGarment(7, "腰饰", TryOnRole.WAIST, CategorySemantic.WAIST_ACCESSORY, ItemComposition.SINGLE, material)
    private fun request() = ImageTaskRequest(listOf(garment()), version = 5, promptVersion = 1,
        promptSnapshot = TryOnMaterialPrompts.front("两枚主扣"), serviceModel = ImageGenerationProvider.SEEDREAM.model,
        inputSha256s = listOf(hash), materialSpec = MaterialTaskSpec(MaterialStage.FRONT_VIEW))

    @Test fun materialRoundTripIsExplicitAndCannotBecomeAnOrdinaryTryOn() {
        val request = request()
        assertEquals(request, ImageTaskRequest.decode(request.encode()))
        request.validate(ImageTaskKind.SINGLE_ITEM_MATERIAL)
        assertThrows(IllegalArgumentException::class.java) { request.validate(ImageTaskKind.TRY_ON) }
        assertThrows(IllegalArgumentException::class.java) { request.validate(ImageTaskKind.MANNEQUIN) }
    }
    @Test fun addingAnAccessoryRequiresConfirmedBaseAndExactlyOneRealWaistMaterial() {
        val base = AccessoryBaseSnapshot(AccessoryBaseType.TRY_ON_RESULT, 8, "原穿搭", true)
        val added = request().copy(garments = listOf(garment("material-id")), materialSpec = null,
            accessoryBase = base, inputSha256s = listOf(hash, hash))
        added.validate(ImageTaskKind.TRY_ON)
        assertEquals(added, ImageTaskRequest.decode(added.encode()))
        assertThrows(IllegalArgumentException::class.java) { added.copy(accessoryBase = base.copy(clothingConfirmed = false)) }
        assertThrows(IllegalArgumentException::class.java) { added.copy(garments = listOf(garment())).validate(ImageTaskKind.TRY_ON) }
        assertThrows(IllegalArgumentException::class.java) { added.copy(garments = listOf(garment("a"), garment("b").copy(itemId=9))).validate(ImageTaskKind.TRY_ON) }
        assertThrows(IllegalArgumentException::class.java) { added.copy(garments = listOf(garment("a").copy(semantic=CategorySemantic.JSK))).validate(ImageTaskKind.TRY_ON) }
    }
    @Test fun fullTryOnStillRequiresAFoundationAndDoesNotTreatTheBaseAsOne() {
        assertThrows(IllegalArgumentException::class.java) { TryOnRequest(listOf(TryOnSelection(7,"腰饰","image",TryOnRole.WAIST,CategorySemantic.WAIST_ACCESSORY))) }
        val full = request().copy(materialSpec = null, garments = listOf(garment("m")))
        assertThrows(IllegalArgumentException::class.java) { full.validate(ImageTaskKind.TRY_ON) }
        val valid = full.copy(garments = full.garments + ImageTaskGarment(10,"裙子",TryOnRole.MAIN,CategorySemantic.JSK), inputSha256s=listOf(hash,hash,hash))
        valid.validate(ImageTaskKind.TRY_ON)
    }
    @Test fun legacyRequestsKeepTheirExactFieldSetAndRejectNewFieldsAndUnknownModes() {
        val legacy = ImageTaskRequest(listOf(ImageTaskGarment(1,"裙",TryOnRole.MAIN)))
        val json = JsonParser.parseString(legacy.encode()).asJsonObject
        assertEquals(setOf("garments","instructions","version"), json.keySet())
        json.addProperty("materialSpec", "invented")
        assertThrows(IllegalArgumentException::class.java) { ImageTaskRequest.decode(json.toString()) }
        val modern = JsonParser.parseString(request().encode()).asJsonObject
        modern["materialSpec"].asJsonObject.addProperty("stage","UNKNOWN")
        assertThrows(IllegalArgumentException::class.java) { ImageTaskRequest.decode(modern.toString()) }
    }
    @Test fun layerMetadataRetainsFineDetachedPartsWithoutConnectedComponentHeuristics() {
        val row = MaterialLayerCandidate("/private/layer.png",hash,1,"含细链的腰饰","完整一件",listOf(10,20,900,950))
        assertEquals(listOf(row), MaterialLayers.decode(MaterialLayers.encode(listOf(row))))
        assertThrows(IllegalArgumentException::class.java) { MaterialLayers.decode(MaterialLayers.encode(listOf(row,row))) }
        assertThrows(IllegalArgumentException::class.java) { MaterialLayers.decode(MaterialLayers.encode(listOf(row.copy(normalizedBounds=listOf(900,0,100,1000))))) }
    }
}
