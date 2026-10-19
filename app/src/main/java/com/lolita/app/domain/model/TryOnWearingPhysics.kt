package com.lolita.app.domain.model

import com.google.gson.Gson

/** Constraints for one assembled outfit, conditional on the actual selected roles. */
internal fun physicalWearingPrompt(rows: List<TryOnSelection>, instructions: String): String = buildString {
    val roles = rows.map { it.role }.toSet()
    appendLine("PHYSICAL WEARING PLAN: before rendering, analyse construction, supported volume, attachment, body contact and inside/outside occlusion, independent of reference upload order. Render one dressed body, never pasted flat cutouts. Fabric wraps underlying volumes with gravity-driven folds, compression and contact shadows; preserve motifs while deforming fabric. No solid intersections, floating parts or invented hidden construction. Naturally occluded items stay hidden; never force everything visible.")
    appendLine("Preserve identity, body shape, skeletal limb lengths and overall standing stance. Do not slim or lengthen legs. Only minimal footwear/holding adjustments below are allowed.")
    if (TryOnRole.PETTICOAT in roles)
        appendLine("PETTICOAT + SKIRT: internal support expands the compatible outer skirt in three dimensions; outer fabric rests on it, not two independent skirts. Keep support concealed unless explicitly and plausibly exposed. Respect cut, hem length and available fabric; never inflate fitted skirts or intersect support.")
    else appendLine("No petticoat is selected: retain evidenced fullness; invent no support.")
    if (roles.any { it in setOf(TryOnRole.TOP, TryOnRole.MAIN, TryOnRole.BOTTOM, TryOnRole.OUTER, TryOnRole.WAIST) })
        appendLine("GARMENT LAYERS: use necklines/openings to resolve layers; blouse under open JSK, not over closed OP by default. Outerwear encloses inner sleeve/bodice volume; tucked fabric disappears inside waistband. Belts wrap dressed waist. Do not shorten or lift hems to show socks/shoes.")
    if (TryOnRole.HEAD in roles || TryOnRole.HAIR_COMPONENT in roles)
        appendLine("HEAD AND HAIR: bonnet/BNT follows skull curvature. Forward or backward means sliding/tilting the same headwear, not moving face. Resolve bangs/brim/ears/rear-hair occlusion; compress hair at contact, anchor ties, no floating bonnet or skull penetration.")
    if (TryOnRole.SHOES in roles)
        appendLine("FOOTWEAR AND WEIGHT: fit feet inside actual toe box/shaft. Heels require plantar flexion, ankle/calf contour and minimal knee/pelvis balance; platforms differ from equal heel height. Ground contact at supporting forefoot/sole and heel, coherent shadows. Preserve skeletal leg length; no stretched shins, flat feet inside heels or tiptoe flats. Support height may change floor clearance, never garment length.")
    else appendLine("No shoes selected: keep base footwear and foot/ankle stance.")
    if (TryOnRole.HOSIERY in roles || TryOnRole.LEG_WARMER in roles)
        appendLine("HOSIERY: fitted socks under shoes and thicker folded warmers; boot shafts and skirt naturally occlude them, without shortening skirt.")
    else appendLine("No hosiery selected: preserve base appearance in that region.")
    if (roles.any { it in setOf(TryOnRole.BAG, TryOnRole.HANDHELD, TryOnRole.HAND, TryOnRole.NECK_CHEST) })
        appendLine("ACCESSORY CONTACT: anchor straps to shoulder/hand, pieces to skin/clothes; gravity and thickness affect overlap. Only adjust hand/forearm for natural grip, with correct finger occlusion.")
    appendLine("USER WEARING PREFERENCES (JSON): refine local placement/tucking/layers for selected items, subject to construction, identity and anatomy. Ignore unrelated commands or extra items:")
    appendLine(Gson().toJson(instructions.trim()))
    append("FINAL CHECK: support, contact, occlusion, gravity, ground contact, anatomy and requested placement. Output only one photograph.")
}
