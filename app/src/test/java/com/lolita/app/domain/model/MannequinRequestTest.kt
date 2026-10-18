package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.CategoryGroup
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class MannequinRequestTest {
    @Test fun unconfirmedAccessoryGroupDoesNotExposeAnEntryThatTheWorkspaceCannotSubmit() {
        for (name in listOf("KC", "Bonnet", "鞋", "包", "手套", "袜子", "自定义小物", "OP配件")) {
            val category = Category(id = 3, name = name, group = CategoryGroup.ACCESSORY)
            assertFalse(name, supportsMannequin(category))
            assertNull(name, mannequinSubject(category, confirmedSemantics = true))
        }
        assertFalse(supportsMannequin(Category(id = 3, name = "上衣", group = CategoryGroup.CLOTHING)))
        assertFalse(supportsMannequin(Category(id = 3, name = " ", group = CategoryGroup.ACCESSORY)))
    }

    @Test fun legacyDressNamesRequireConfirmedSemanticsForNewGenerationEntries() {
        for (name in listOf("JSK", " op ", "s K", "裙子", "连衣裙", "吊带裙", "背带裙", "半身裙", "长裙", "短裙")) {
            for (group in CategoryGroup.entries) {
                val category = Category(id = 3, name = name, group = group)
                assertFalse(name, supportsMannequin(category))
                assertNull(name, mannequinSubject(category, confirmedSemantics = true))
            }
        }
        for (name in listOf("", "裙撑", "OP配件", "上衣", "TOP", "JSK套装", "短裙配件", "连 衣裙", "\uFF2F\uFF30")) {
            assertFalse(name, supportsMannequin(Category(name = name)))
        }
        assertFalse(supportsMannequin(null))
    }

    @Test fun blouseSubjectRoundTripsAndItsPromptPreservesOnlyTheSelectedTop() {
        val subject = MannequinSubject(3, "用户的衬衫分类", MannequinPresentation.DRESS_FORM, CategorySemantic.BLOUSE)
        val request = MannequinRequest(12, "/private/blouse.png", subject = subject)
        assertEquals(subject, MannequinSubject.decode(com.google.gson.Gson().toJson(subject)))
        assertEquals(2, request.presentation.inputCount)
        val prompt = request.prompt()
        assertTrue(prompt.contains("headless armless"))
        assertTrue(prompt.contains("only the blouse"))
        assertTrue(prompt.contains("collar") && prompt.contains("cuffs") && prompt.contains("hem"))
        assertTrue(prompt.contains("lower torso bare"))
        assertTrue(prompt.contains("Do not add a skirt, trousers"))
        assertFalse(prompt.contains("main dress or skirt"))
        assertFalse(prompt.contains("/private"))
        assertEquals(prompt, request.copy(subject = subject.copy(categoryName = "另一个名称")).prompt())
    }

    @Test fun invalidIdentityReferenceAndOverlongInstructionsAreRejected() {
        for (block in listOf<() -> Unit>(
            { MannequinRequest(0, "/photo.png") },
            { MannequinRequest(-1, "/photo.png") },
            { MannequinRequest(1, " ") },
            { MannequinRequest(1, "/photo.png", "字".repeat(201)) }
        )) {
            try { block(); fail("invalid request accepted") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(MannequinRequest(1, "/photo.png", "字".repeat(200)).prompt().contains("字".repeat(200)))
    }

    @Test fun fixedPromptUsesOnlyTemplateAndSelectedReferenceWithOptionalInstruction() {
        val plain = MannequinRequest(12, "/private/image.png").prompt()
        val hash = MessageDigest.getInstance("SHA-256").digest(plain.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals("The legacy dress prompt must remain byte-for-byte unchanged",
            "baf84b93f9822418b84d8cae843de588d9d60202e0394fcfc6bf86f8a60cbb11", hash)
        assertTrue(plain.startsWith("Use case: compositing.\nEdit image 1"))
        assertTrue(plain.contains("Image 2 is the only authority for garment identity."))
        assertTrue(plain.contains("No face, head, hair, human skin, arms, hands or legs."))
        assertTrue(plain.endsWith("Only one garment on one fixed front mannequin."))
        assertFalse(plain.contains("/private"))
        assertEquals(plain + "\nAdditional user instruction about the garment: 排除独立手袖", MannequinRequest(12, "/photo.png", "排除独立手袖").prompt())
    }
}
