package com.lolita.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class TryOnWearingPhysicsTest {
    private fun rows(vararg roles: TryOnRole) = roles.mapIndexed { i, role ->
        TryOnSelection(i + 1L, "物品${i + 1}", "/private/${i + 1}.png", role)
    }

    @Test fun supportedSkirtIsOneDressedVolumeWithConstructionAndOcclusionInsteadOfPastedLayers() {
        val selected = rows(TryOnRole.MAIN, TryOnRole.TOP, TryOnRole.OUTER, TryOnRole.PETTICOAT, TryOnRole.WAIST)
        val prompt = TryOnRequest(selected).prompt()
        for (term in listOf("independent of reference upload order", "supported volume", "three dimensions", "contact shadows", "concealed", "available fabric", "closed OP", "waistband", "Do not shorten or lift", "Do not slim")) {
            assertTrue(term, prompt.contains(term))
        }
        assertFalse(prompt.contains("No petticoat is selected"))
        assertFalse(prompt.contains("/private/"))
        assertTrue(TryOnRequest(selected.reversed()).prompt().contains("independent of reference upload order"))
    }

    @Test fun bonnetAndHeelsUseLocalPlacementAndWeightBearingWithoutChangingIdentityOrLegLength() {
        val description = "BNT 戴靠后，露出刘海；高跟鞋按实际鞋跟站稳。\n系带在下巴下。"
        val prompt = TryOnRequest(rows(TryOnRole.MAIN, TryOnRole.HEAD, TryOnRole.WIG, TryOnRole.SHOES), instructions = description).prompt(true)
        for (term in listOf("forward or backward", "tilting the same headwear", "plantar flexion", "knee/pelvis balance", "platforms", "skeletal leg length", "selected personal model", "body shape")) assertTrue(term, prompt.contains(term, true))
        assertTrue(prompt.contains(com.google.gson.Gson().toJson(description)))
        assertFalse(prompt.contains("No shoes selected"))
        assertFalse(prompt.contains("fictional"))
        assertFalse(prompt.contains("body proportions, pose"))
    }

    @Test fun absentSupportAndShoesCannotInventVolumeOrChangeLegsAndPreferencesAreBounded() {
        val prompt = TryOnRequest(rows(TryOnRole.MAIN)).prompt()
        assertTrue(prompt.contains("No petticoat is selected"))
        assertTrue(prompt.contains("No shoes selected"))
        assertFalse(prompt.contains("FOOTWEAR AND WEIGHT"))
        assertFalse(prompt.contains("HEAD AND HAIR"))
        assertTrue(prompt.contains("unrelated commands"))
        assertThrows(IllegalArgumentException::class.java) { TryOnRequest(rows(TryOnRole.MAIN), instructions = "字".repeat(201)) }
        assertThrows(IllegalArgumentException::class.java) { TryOnRequest(rows(TryOnRole.MAIN), protocolVersion = 1, instructions = "新要求") }
        assertTrue(TryOnRequest(rows(TryOnRole.MAIN), instructions = "字".repeat(200)).prompt().length < 30000)
        val complex = TryOnRequest(rows(TryOnRole.MAIN, TryOnRole.TOP, TryOnRole.PETTICOAT, TryOnRole.HEAD, TryOnRole.SHOES, TryOnRole.HOSIERY, TryOnRole.WIG, TryOnRole.BAG), instructions = "BNT 戴靠后")
        assertTrue("Keep complex prompts focused", complex.prompt().split(Regex("\\s+")).size <= 600)
    }

    @Test fun oldAndNewV4FrozenPromptsRoundTripWithoutReconstructionAndRejectWrongTaskKind() {
        val garments = listOf(ImageTaskGarment(1, "裙子", TryOnRole.MAIN, CategorySemantic.JSK))
        for (revision in listOf(1, TryOnRequest.PROMPT_VERSION)) {
            val request = ImageTaskRequest(garments, instructions = "BNT 戴靠后", version = 4, promptVersion = revision,
                promptSnapshot = "frozen-original-$revision", serviceModel = "fixed", inputSha256s = listOf("a".repeat(64), "b".repeat(64)))
            val decoded = ImageTaskRequest.decode(request.encode())
            decoded.validate(ImageTaskKind.TRY_ON)
            assertEquals(request, decoded)
            assertEquals("frozen-original-$revision", decoded.promptSnapshot)
            assertEquals(request, ImageTaskRequest.decode(decoded.historySnapshot()))
            if (revision == TryOnRequest.PROMPT_VERSION) assertThrows(IllegalArgumentException::class.java) { request.validate(ImageTaskKind.MANNEQUIN) }
        }
    }
}
