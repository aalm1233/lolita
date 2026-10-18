package com.lolita.app.ui.screen.item

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.lolita.app.data.local.entity.Category
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun WardrobeCategorySelector(selectedCategoryId: Long, categories: List<Category>, onCategorySelected: (Long) -> Unit,
    enabled: Boolean = true, isError: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedCategoryId }
    OutlinedButton(onClick = { show = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(selected?.let { "${it.categorySemantic.family.label} · ${it.name}" } ?: "选择品类与类型 *",
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
    if (show) {
        var query by remember { mutableStateOf("") }
        var family by remember { mutableStateOf<WardrobeFamily?>(null) }
        AlertDialog(onDismissRequest = { show = false }, title = { Text("选择物品类型") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(LolitaSkin.current.spacingMedium)) {
                OutlinedTextField(query, { query = it }, label = { Text("搜索类型") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = family == null, onClick = { family = null }, label = { Text("全部") })
                    WardrobeFamily.entries.forEach { value -> FilterChip(selected = family == value, onClick = { family = value }, label = { Text(value.label) }) }
                }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(categories.filter { (family == null || it.categorySemantic.family == family) && it.name.contains(query, true) }, key = { it.id }) { category ->
                        TextButton(onClick = { onCategorySelected(category.id); show = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("${if (category.id == selectedCategoryId) "✓ " else ""}${category.name} · ${category.categorySemantic.family.label}")
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { show = false }) { Text("取消") } })
    }
}

data class SpecificationField(val key: String, val label: String)
fun specificationProfile(semantic: CategorySemantic): String? = when {
    semantic == CategorySemantic.PETTICOAT -> "petticoat"
    semantic.family == WardrobeFamily.WIG -> "wig"
    semantic.family == WardrobeFamily.HOSIERY -> "hosiery"
    semantic.family == WardrobeFamily.FOOTWEAR -> "footwear"
    semantic.family == WardrobeFamily.ACCESSORY -> "accessory"
    semantic.family == WardrobeFamily.BAG -> "bag"
    semantic.family == WardrobeFamily.HANDHELD -> "handheld"
    else -> null
}
fun specificationFields(profile: String): List<SpecificationField> = when (profile) {
    "wig" -> listOf("mainColor" to "主要发色", "lengthCm" to "长度厘米", "curl" to "直卷", "extraHairPieces" to "附加发片", "bangs" to "刘海", "parting" to "分缝", "material" to "材质", "heatResistance" to "耐热说明", "headCircumference" to "头围适配", "stylingNotes" to "修剪造型备注")
    "hosiery" -> listOf("lengthType" to "袜长类型", "sizeText" to "尺码原文", "pattern" to "颜色或花纹", "thickness" to "厚薄", "denier" to "DEN 原始标识", "footLength" to "足长适配", "material" to "材质", "transparency" to "透肤情况")
    "footwear" -> listOf("sizeText" to "鞋码原文", "sizeSystem" to "尺码体系", "heelHeightCm" to "跟高厘米", "innerLengthCm" to "内长厘米", "shaftHeightCm" to "筒高厘米", "toeShape" to "鞋头", "fastening" to "扣带", "comfort" to "脚感自评", "walkingNotes" to "步行时长备注")
    "petticoat" -> listOf("lengthCm" to "长度厘米", "silhouette" to "轮廓", "volume" to "蓬度", "waistCm" to "腰围厘米", "layers" to "层数", "material" to "材质", "notes" to "搭配备注")
    "accessory" -> listOf("wearPosition" to "穿戴位置", "dimensions" to "尺寸", "fastening" to "固定方式", "material" to "材质")
    "bag" -> listOf("carryMode" to "携带方式", "dimensions" to "尺寸", "capacity" to "容量", "phoneFit" to "手机适配说明", "material" to "材质")
    else -> listOf("usage" to "使用方式", "dimensions" to "尺寸", "foldable" to "可折叠情况", "material" to "材质")
}.map { SpecificationField(it.first, it.second) }

@Composable
fun WardrobeSpecificationEditor(semantic: CategorySemantic, specifications: WardrobeSpecifications,
    composition: ItemComposition, enabled: Boolean, onSpecifications: (WardrobeSpecifications) -> Unit, onComposition: (ItemComposition) -> Unit) {
    val profile = specificationProfile(semantic)
    LolitaSection("专属资料（选填）") {
        row {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ItemComposition.entries.forEach { value -> FilterChip(selected = composition == value, enabled = enabled,
                    onClick = { onComposition(value) }, label = { Text(value.label) }) }
            }
        }
        if (profile != null) {
            val fields = specificationFields(profile)
            fields.forEach { field -> row {
                val root = Gson().toJsonTree(specifications).asJsonObject
                OutlinedTextField(value = root[profile].asJsonObject[field.key].asString, onValueChange = { raw ->
                    root[profile].asJsonObject.addProperty(field.key, raw)
                    onSpecifications(WardrobeSpecifications.decode(root.toString()))
                }, label = { Text(field.label) }, enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
            } }
        }
    }
}

@Composable
fun WardrobeSpecificationDetails(semantic: CategorySemantic, specifications: WardrobeSpecifications, composition: ItemComposition) {
    val profile = specificationProfile(semantic)
    val values = profile?.let { name ->
        val data = Gson().toJsonTree(specifications).asJsonObject[name].asJsonObject
        specificationFields(name).mapNotNull { field -> data[field.key].asString.takeIf { it.isNotBlank() }?.let { field.label to it } }
    }.orEmpty()
    if (values.isNotEmpty() || composition != ItemComposition.UNCONFIRMED) LolitaSection("专属资料") {
        if (composition != ItemComposition.UNCONFIRMED) row { Text("组成：${composition.label}") }
        values.forEach { (label, value) -> row { Text("$label：$value") } }
    }
}
