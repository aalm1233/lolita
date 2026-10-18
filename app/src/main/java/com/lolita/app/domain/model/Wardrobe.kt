package com.lolita.app.domain.model

import com.google.gson.Gson
import com.google.gson.JsonParser

enum class WardrobeFamily(val label: String) {
    UNCONFIRMED("待确认"), CLOTHING("服装"), WIG("假发"), HOSIERY("袜装"), FOOTWEAR("鞋履"),
    ACCESSORY("头饰与配饰"), BAG("包袋"), HANDHELD("手持道具")
}
enum class WardrobeDisplayMode { UNCONFIRMED, DRESS_FORM, WIG_HEAD, STANDALONE }
enum class ItemComposition(val label: String) { UNCONFIRMED("组合不明"), SINGLE("单只／单件"), PAIR("成双"), SET("成组") }

/** Stable identities, independent of the editable Chinese category name. */
enum class CategorySemantic(val label: String, val family: WardrobeFamily, private val role: String = "OTHER",
    val displayMode: WardrobeDisplayMode = WardrobeDisplayMode.STANDALONE) {
    UNCONFIRMED("待确认", WardrobeFamily.UNCONFIRMED, displayMode = WardrobeDisplayMode.UNCONFIRMED),
    JSK("JSK", WardrobeFamily.CLOTHING, "MAIN", WardrobeDisplayMode.DRESS_FORM),
    OP("OP", WardrobeFamily.CLOTHING, "MAIN", WardrobeDisplayMode.DRESS_FORM),
    SK("SK", WardrobeFamily.CLOTHING, "BOTTOM", WardrobeDisplayMode.DRESS_FORM),
    BLOUSE("衬衫", WardrobeFamily.CLOTHING, "TOP"), TROUSERS("裤装", WardrobeFamily.CLOTHING, "BOTTOM"),
    OUTERWEAR("外套", WardrobeFamily.CLOTHING, "OUTER"), CAPE("斗篷", WardrobeFamily.CLOTHING, "OUTER"),
    SHAWL("披肩", WardrobeFamily.CLOTHING, "OUTER"), PETTICOAT("裙撑", WardrobeFamily.CLOTHING, "PETTICOAT"),
    OTHER_CLOTHING("其他服装", WardrobeFamily.CLOTHING, "TOP"),
    FULL_WIG("全头假发", WardrobeFamily.WIG, "WIG", WardrobeDisplayMode.WIG_HEAD),
    HAIR_PIECE("发片", WardrobeFamily.WIG, "HAIR_COMPONENT"), BANGS_PIECE("刘海片", WardrobeFamily.WIG, "HAIR_COMPONENT"),
    PONYTAIL("假马尾", WardrobeFamily.WIG, "HAIR_COMPONENT"), BRAID("假辫", WardrobeFamily.WIG, "HAIR_COMPONENT"),
    SHORT_SOCKS("短袜", WardrobeFamily.HOSIERY, "HOSIERY"), MID_SOCKS("中筒袜", WardrobeFamily.HOSIERY, "HOSIERY"),
    KNEE_SOCKS("及膝袜", WardrobeFamily.HOSIERY, "HOSIERY"), OVER_KNEE_SOCKS("过膝袜", WardrobeFamily.HOSIERY, "HOSIERY"),
    TIGHTS("连裤袜", WardrobeFamily.HOSIERY, "HOSIERY"), LEG_WARMERS("袜套", WardrobeFamily.HOSIERY, "LEG_WARMER"),
    OTHER_HOSIERY("其他袜装", WardrobeFamily.HOSIERY, "HOSIERY"),
    MARY_JANE("玛丽珍鞋", WardrobeFamily.FOOTWEAR, "SHOES"), TEA_PARTY_SHOES("茶会鞋", WardrobeFamily.FOOTWEAR, "SHOES"),
    PUMPS("浅口鞋", WardrobeFamily.FOOTWEAR, "SHOES"), ANKLE_BOOTS("短靴", WardrobeFamily.FOOTWEAR, "SHOES"),
    LONG_BOOTS("长靴", WardrobeFamily.FOOTWEAR, "SHOES"), OTHER_FOOTWEAR("其他鞋履", WardrobeFamily.FOOTWEAR, "SHOES"),
    KC("KC", WardrobeFamily.ACCESSORY, "HEAD"), BONNET("Bonnet", WardrobeFamily.ACCESSORY, "HEAD"),
    HEADBAND("发带", WardrobeFamily.ACCESSORY, "HEAD"), HAIR_CLIP("发夹", WardrobeFamily.ACCESSORY, "HEAD"), HAT("帽子", WardrobeFamily.ACCESSORY, "HEAD"),
    NECKLACE("项链", WardrobeFamily.ACCESSORY, "NECK_CHEST"), EARRING("耳饰", WardrobeFamily.ACCESSORY, "NECK_CHEST"),
    BROOCH("胸针", WardrobeFamily.ACCESSORY, "NECK_CHEST"), WAIST_ACCESSORY("腰饰", WardrobeFamily.ACCESSORY, "WAIST"),
    WRIST_CUFFS("手袖", WardrobeFamily.ACCESSORY, "HAND"), GLOVES("手套", WardrobeFamily.ACCESSORY, "HAND"), OTHER_ACCESSORY("其他配饰", WardrobeFamily.ACCESSORY),
    HANDBAG("手提包", WardrobeFamily.BAG, "BAG"), CROSSBODY_BAG("斜挎包", WardrobeFamily.BAG, "BAG"), BACKPACK("背包", WardrobeFamily.BAG, "BAG"),
    CLUTCH("手拿包", WardrobeFamily.BAG, "BAG"), OTHER_BAG("其他包袋", WardrobeFamily.BAG, "BAG"),
    PARASOL("阳伞", WardrobeFamily.HANDHELD, "HANDHELD"), FAN("扇子", WardrobeFamily.HANDHELD, "HANDHELD"),
    CANE("手杖", WardrobeFamily.HANDHELD, "HANDHELD"), DOLL("玩偶", WardrobeFamily.HANDHELD, "HANDHELD"),
    BOUQUET("花束", WardrobeFamily.HANDHELD, "HANDHELD"), OTHER_HANDHELD("其他手持道具", WardrobeFamily.HANDHELD, "HANDHELD");
    val defaultWearRole: TryOnRole get() = TryOnRole.valueOf(role)
}

data class WigSpecifications(val mainColor: String = "", val lengthCm: String = "", val curl: String = "", val extraHairPieces: String = "",
    val bangs: String = "", val parting: String = "", val material: String = "", val heatResistance: String = "", val headCircumference: String = "", val stylingNotes: String = "")
data class HosierySpecifications(val lengthType: String = "", val sizeText: String = "", val pattern: String = "", val thickness: String = "", val denier: String = "",
    val footLength: String = "", val material: String = "", val transparency: String = "")
data class FootwearSpecifications(val sizeText: String = "", val sizeSystem: String = "", val heelHeightCm: String = "", val innerLengthCm: String = "", val shaftHeightCm: String = "",
    val toeShape: String = "", val fastening: String = "", val comfort: String = "", val walkingNotes: String = "")
data class PetticoatSpecifications(val lengthCm: String = "", val silhouette: String = "", val volume: String = "", val waistCm: String = "", val layers: String = "", val material: String = "", val notes: String = "")
data class AccessorySpecifications(val wearPosition: String = "", val dimensions: String = "", val fastening: String = "", val material: String = "")
data class BagSpecifications(val carryMode: String = "", val dimensions: String = "", val capacity: String = "", val phoneFit: String = "", val material: String = "")
data class HandheldSpecifications(val usage: String = "", val dimensions: String = "", val foldable: String = "", val material: String = "")

/** Raw strings preserve units, ranges and incomplete editor input without invented conversions. */
data class WardrobeSpecifications(val version: Int = 1, val wig: WigSpecifications = WigSpecifications(),
    val hosiery: HosierySpecifications = HosierySpecifications(), val footwear: FootwearSpecifications = FootwearSpecifications(),
    val petticoat: PetticoatSpecifications = PetticoatSpecifications(), val accessory: AccessorySpecifications = AccessorySpecifications(),
    val bag: BagSpecifications = BagSpecifications(), val handheld: HandheldSpecifications = HandheldSpecifications()) {
    init { require(version == 1) { "不支持的物品规格版本" } }
    fun encode(): String = Gson().toJson(this)
    companion object {
        fun decode(json: String): WardrobeSpecifications {
            if (json.isBlank()) return WardrobeSpecifications()
            try {
                val root = JsonParser.parseString(json).asJsonObject
                val defaults = Gson().toJsonTree(WardrobeSpecifications()).asJsonObject
                require(root.keySet().all { it in defaults.keySet() }) { "未知规格字段" }
                require(root["version"]?.asJsonPrimitive?.let { it.isNumber && it.asBigDecimal.intValueExact() == 1 } == true) { "不支持的物品规格版本" }
                root.entrySet().filter { it.key != "version" }.forEach { (key, value) ->
                    require(value.isJsonObject) { "规格类型无效" }
                    val profile = defaults[key].asJsonObject
                    require(value.asJsonObject.keySet().all { it in profile.keySet() }) { "未知专属规格字段" }
                    value.asJsonObject.entrySet().forEach { (field, raw) ->
                        require(raw.isJsonPrimitive && raw.asJsonPrimitive.isString && raw.asString.length <= 4000) { "规格必须是原始文字" }
                        profile.add(field, raw)
                    }
                }
                return Gson().fromJson(defaults, WardrobeSpecifications::class.java)
            } catch (failure: Exception) { throw IllegalArgumentException("物品规格无效", failure) }
        }
    }
}
