package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.CategoryGroup
import java.util.Locale

/** Eligibility is based on the explicit category, never a garment's free-form name. */
fun supportsMannequin(category: Category?): Boolean {
    if (category?.group != CategoryGroup.CLOTHING) return false
    if (category.name.trim() in setOf("裙子", "连衣裙", "吊带裙", "背带裙", "半身裙", "长裙", "短裙")) return true
    val normalized = category.name.filterNot { it.isWhitespace() }.uppercase(Locale.ROOT)
    return normalized in setOf("JSK", "OP", "SK")
}

data class MannequinRequest(val itemId: Long, val imagePath: String, val instructions: String = "") {
    init {
        require(itemId > 0 && imagePath.isNotBlank()) { "请选择裙子的参考图片" }
        require(instructions.length <= 200) { "补充说明最多200字" }
    }

    fun prompt(): String = if (instructions.isBlank()) BASE_PROMPT else
        BASE_PROMPT + "\nAdditional user instruction about the garment: " + instructions

    companion object {
        // Same production prompt as the archived, manually reviewed front mannequin evaluation.
        private val BASE_PROMPT = """
            Use case: compositing.
            Edit image 1, the canonical empty warm-white tailoring mannequin, by dressing it in only the main dress or skirt visible in image 2, the user's garment reference. Produce ONE frontal catalog image.
            Image 1 is the edit target and fixes the full portrait 2:3 frame, camera, pale-gray seamless studio, diffuse lighting, upright headless armless adult dress form, silver neck cap, thin support pole and round silver base. Preserve these proportions and positions as closely as possible. Do not crop any hem, sleeve or stand. Do not lengthen the bodice to fill the frame.
            Image 2 is the only authority for garment identity. Preserve its real color, neckline, straps, sleeve silhouette, waist height relative to the bodice, skirt length and width, layer count, lace and trim placements, fastening, fabric and print design. Fit the garment naturally to the same mannequin, using only invisible internal skirt support. Sleeves must hang naturally without human arms or hands.
            If the source shows multiple views or a product board, use the front view of the main dress/skirt only. Do not combine front and back or copy separate products. Retain decorations visibly belonging to the dress, but exclude separate headwear, wigs, necklaces, bags, parasols, wrist cuffs, gloves, shoes, stockings, additional outerwear and hand-held props. For a skirt-only source, leave the white upper torso bare; do not invent a matching top.
            Remove the original mannequin or person, source background, watermark, logo, border, layout and text. No face, head, hair, human skin, arms, hands or legs. No extra garment, no new accessories, no added jewels, no text, no labels, no collage, no side or back view.
            Do not redesign, beautify by adding bows or lace, symmetrize distinctive asymmetric garment construction, or replace detailed prints with generic motifs. Where a tiny detail is not readable, keep it visually restrained rather than inventing elaborate decoration. Only one garment on one fixed front mannequin.
        """.trimIndent()
    }
}
