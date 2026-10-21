package com.lolita.app.domain.model

/** Stable semantics drive material preparation; editable category names are never classifiers. */
val CategorySemantic.supportsMaterialPreparation: Boolean get() = this != CategorySemantic.UNCONFIRMED

object MaterialPreparation {
    const val PROMPT_VERSION = 2
    fun features(semantic: CategorySemantic): String = when (semantic) {
        CategorySemantic.DOLL -> "玩偶本体的脸、头发、身体及固定服饰都属于目标物品，必须完整保留，不当作需要移除的真人或人台；只移除持有玩偶的人手及无关环境，不改变玩偶姿态或增删配件。"
        CategorySemantic.BLOUSE -> "保留原领口、袖型、袖口、排扣和下摆；只展示衬衫，不补配裙子、裤子或手袖。"
        CategorySemantic.PETTICOAT -> "保留裙撑的腰头、长度、轮廓、支撑和可见层数；不将裙撑改成主裙，不加罩裙。"
        CategorySemantic.HAIR_PIECE, CategorySemantic.BANGS_PIECE, CategorySemantic.PONYTAIL, CategorySemantic.BRAID -> "保持这类发片／刘海片／假马尾／假辫的独立形态、固定件、颜色、卷度、长度及发梢；不能补成全头假发，不加头部或原生头发。"
        else -> when (semantic.family) {
            WardrobeFamily.CLOTHING -> "服装采用清楚的正面主展示视角，保留原领口、袖型、长度、腰线、下摆、层次、蕾丝和柄图；内里留空，移除人台、衣架与其他搭配，不补造缺失的服装。"
            WardrobeFamily.WIG -> "只展示完整假发，保留原分缝、刘海、发量、颜色、长度、卷度及全部发梢；移除人物和头模，不补脸、身体或无关头饰，不把假发改短。"
            WardrobeFamily.HOSIERY -> "保留袜长、筒型、花纹、薄透、袜口、脚跟和脚尖，连裤袜保留连接结构；移除腿脚和鞋，不截短、补配另一只或改成不同袜型。"
            WardrobeFamily.FOOTWEAR -> "保留鞋楦、鞋头、鞋面扣带、跟高、鞋底、靴筒及左右差异；采用能辨认原构造的主展示视角，可保留必要侧向，不强行变成看不清结构的正面，不补造未显示的另一只或内外侧。"
            WardrobeFamily.ACCESSORY -> "保留头饰／配饰的主体、蕾丝、蝴蝶结、固定件、链条和全部细绳端及连接关系；移除人物、头发和衣服，不补成对、不增加装饰，不强制对称。"
            WardrobeFamily.BAG -> "保留原包型、提手、包带、链条、开口、扣具、花纹及连接，移除手和衣物；不为铺平而改包型，不缩短或截断包带。"
            WardrobeFamily.HANDHELD -> "保留道具原形态、展开／闭合状态、伞骨／扇骨、手柄及附属装饰；移除手和人物，不把原物品变成另一种状态或补造其他物品。"
            WardrobeFamily.UNCONFIRMED -> error("请先核对物品类型")
        }
    }
}
