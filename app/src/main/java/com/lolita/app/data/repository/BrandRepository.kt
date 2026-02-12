package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.BrandDao
import com.lolita.app.data.local.entity.Brand
import kotlinx.coroutines.flow.Flow

class BrandRepository(
    private val brandDao: BrandDao
) {
    fun getAllBrands(): Flow<List<Brand>> = brandDao.getAllBrands()

    fun getPresetBrands(): Flow<List<Brand>> = brandDao.getPresetBrands()

    suspend fun insertBrand(brand: Brand): Long = brandDao.insertBrand(brand)

    suspend fun updateBrand(brand: Brand) = brandDao.updateBrand(brand)

    suspend fun deleteBrand(brand: Brand) {
        require(!brand.isPreset) { "预置品牌不可删除" }
        brandDao.deleteBrand(brand)
    }

    suspend fun getBrandByName(name: String): Brand? = brandDao.getBrandByName(name)

    suspend fun ensurePresetBrands() {
        val presetNames = listOf(
                "古典玩偶（Classical Puppets）",
                "仲夏物语（Elpress L）",
                "玻璃纸之夜（NDC）",
                "摇篮曲（Lullaby）",
                "圆点点小姐 Lolita 洋服社",
                "小宝石洋服（Petit Bijou）",
                "表面咒语（Surface Spell）",
                "酒心可可（Chocolate Lovers）",
                "花与珍珠匣",
                "莉莉子 Lolita",
                "新路德维希（Neo Ludwig）",
                "CuteQ",
                "纪念岛",
                "Nololita",
                "平成魔女",
                "七愚",
                "织锦园",
                "无名诗（东之森）",
                "白雪姬 Lolita",
                "樱桃爆弹",
                "告白气球家",
                "绵羊泡芙（Sheep Puff）",
                "Honey Honey Lolita",
                "沉默编织",
                "悠尼蒂鹿（Unideer）",
                "JS 洋装贩售",
                "暗星之岛",
                "晴木",
                "银河有迹可循",
                "玻璃花信",
                "Angelic Pretty（AP，甜系代表）",
                "Baby, the Stars Shine Bright（BABY，甜系经典）",
                "Alice and the Pirates（ANP，BABY 副线，哥特甜）",
                "Innocent World（IW，古典优雅）",
                "Metamorphose Temps de Fille（Meta，风格多元）",
                "Victorian Maiden（古典洛丽塔标杆）",
                "Juliette et Justine（JEJ，优雅复古）",
                "Mary Magdalene（MM，华丽古典）",
                "Atelier Boz（哥特 / 暗黑系）",
                "Atelier Pierrot（哥特贵族风）",
                "Moi-même-Moitié（MmM，mana 主理的哥特牌）",
                "Jane Marple（日常优雅古典）",
                "Emily Temple Cute（轻甜日常）",
                "Pink House（田园清新，碎花为主）",
                "Bodyline（平价入门，款式多）",
                "Antique Beast（复古奇幻）",
                "Excentrique（古典简约）",
                "Heart E（甜系小物 + 洋装）",
                "Millefleurs（古典花柄）",
                "Putumayo（原宿风混搭 Lolita）",
                "h.NAOTO（哥特朋克融合）",
                "MAXICIMAM（华丽茶会款）",
                "Grimoire（哥特洛丽塔）",
                "Sheglit（暗黑精致风）",
                "MILK（日常休闲 Lolita）",
                "Chocochip Cookie（软萌甜系）",
                "Triple Fortune（优雅日常）",
                "Royal Princess Alice（公主甜系）",
                "Enchantlic Enchantilly（梦幻甜系）",
                "Q-pot.（Lolita 风配饰 + 洋装）"
        )

        presetNames.forEach { name ->
            if (brandDao.getBrandByName(name) == null) {
                brandDao.insertBrand(Brand(name = name, isPreset = true))
            }
        }
    }
}
