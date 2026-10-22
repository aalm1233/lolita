package com.lolita.app.ui.screen.item

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.lolita.app.data.local.entity.Category
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun WardrobeCategorySelector(selectedCategoryId: Long, categories: List<Category>, onCategorySelected: (Long) -> Unit,
    enabled: Boolean = true, isError: Boolean = false, preferredFamily: WardrobeFamily? = null) {
    var show by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedCategoryId }
    OutlinedButton(onClick = { show = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(selected?.let { "${it.categorySemantic.family.label} · ${it.name}" }
            ?: preferredFamily?.let { "${it.label} · 选择类型 *" } ?: "选择品类与类型 *",
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
    if (show) {
        var query by remember { mutableStateOf("") }
        var family by remember { mutableStateOf(selected?.categorySemantic?.family ?: preferredFamily) }
        val familyScroll = rememberScrollState()
        val categoriesScroll = rememberLazyListState()
        ObserveSkinScroll(familyScroll)
        ObserveSkinScroll(categoriesScroll)
        AlertDialog(onDismissRequest = { show = false }, title = { Text("选择物品类型") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(LolitaSkin.current.spacingMedium)) {
                OutlinedTextField(query, { query = it }, label = { Text("搜索类型") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(familyScroll), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = family == null, onClick = { family = null }, label = { Text("全部") })
                    WardrobeFamily.entries.forEach { value -> FilterChip(selected = family == value, onClick = { family = value }, label = { Text(value.label) }) }
                }
                LazyColumn(Modifier.heightIn(max = 340.dp), state = categoriesScroll) {
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
    semantic.family == WardrobeFamily.CLOTHING -> "clothing"
    semantic.family == WardrobeFamily.WIG -> "wig"
    semantic.family == WardrobeFamily.HOSIERY -> "hosiery"
    semantic.family == WardrobeFamily.FOOTWEAR -> "footwear"
    semantic.family == WardrobeFamily.ACCESSORY -> "accessory"
    semantic.family == WardrobeFamily.BAG -> "bag"
    semantic.family == WardrobeFamily.HANDHELD -> "handheld"
    else -> null
}
fun specificationFields(profile: String): List<SpecificationField> = when (profile) {
    "clothing" -> listOf("lengthCm" to "衣长", "bustCm" to "胸围（成衣围度）", "waistCm" to "腰围（成衣围度）", "hipCm" to "臀围（成衣围度）", "shoulderWidthCm" to "肩宽", "sleeveLengthCm" to "袖长")
    "wig" -> listOf("mainColor" to "主要发色", "lengthCm" to "长度", "curl" to "直卷", "extraHairPieces" to "附加发片", "bangs" to "刘海", "parting" to "分缝", "material" to "材质", "heatResistance" to "耐热说明", "headCircumference" to "头围适配", "stylingNotes" to "修剪造型备注")
    "hosiery" -> listOf("lengthType" to "袜长类型", "sizeText" to "尺码", "pattern" to "颜色或花纹", "thickness" to "厚薄", "denier" to "DEN 标识", "footLength" to "足长适配", "material" to "材质", "transparency" to "透肤情况")
    "footwear" -> listOf("sizeText" to "鞋码", "sizeSystem" to "尺码体系", "heelHeightCm" to "跟高", "innerLengthCm" to "内长", "shaftHeightCm" to "筒高", "toeShape" to "鞋头", "fastening" to "扣带", "comfort" to "脚感自评", "walkingNotes" to "步行时长备注")
    "petticoat" -> listOf("lengthCm" to "长度", "silhouette" to "轮廓", "volume" to "蓬度", "waistCm" to "腰围", "layers" to "层数", "material" to "材质", "notes" to "搭配备注")
    "accessory" -> listOf("wearPosition" to "穿戴位置", "dimensions" to "尺寸", "fastening" to "固定方式", "material" to "材质")
    "bag" -> listOf("carryMode" to "携带方式", "dimensions" to "尺寸", "capacity" to "容量", "phoneFit" to "手机适配说明", "material" to "材质")
    else -> listOf("usage" to "使用方式", "dimensions" to "尺寸", "foldable" to "可折叠情况", "material" to "材质")
}.map { SpecificationField(it.first, it.second) }

@Composable
fun WardrobeSpecificationEditor(semantic: CategorySemantic, specifications: WardrobeSpecifications,
    composition: ItemComposition, enabled: Boolean, onSpecifications: (WardrobeSpecifications) -> Unit, onComposition: (ItemComposition) -> Unit) {
    val profile = specificationProfile(semantic)
    val skin = LolitaSkin.current
    var expanded by rememberSaveable(profile) { mutableStateOf(false) }
    val fields = profile?.let(::specificationFields).orEmpty()
    val root = Gson().toJsonTree(specifications).asJsonObject
    val filledByProfile = root.entrySet().filter { it.value.isJsonObject }.associate { (name, values) ->
        name to values.asJsonObject.entrySet().count { it.value.asString.isNotBlank() }
    }
    val count = filledByProfile.values.sum() + (if (specifications.pattern.isNotBlank()) 1 else 0) +
        (if (composition != ItemComposition.UNCONFIRMED) 1 else 0)
    val otherCount = filledByProfile.filterKeys { it != profile }.values.sum()
    val labels = fields.filter { root[profile].asJsonObject[it.key].asString.isNotBlank() }.map { it.label }
    LolitaSection("详细规格", action = {
        TextButton(onClick = { expanded = !expanded }, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = if (expanded) "收起详细规格" else "展开详细规格" }) {
            Text(if (expanded) "收起" else "展开")
        }
    }) {
        row {
            Text(if (count == 0) "尚未填写"
                else "已填 $count 项", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            row { Text("按原资料填写数值和单位。", style = MaterialTheme.typography.bodySmall) }
            row {
                OutlinedTextField(specifications.pattern, { onSpecifications(specifications.copy(pattern = it)) },
                    label = { Text("图案") }, enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            row {
                val compositionScroll = rememberScrollState()
                ObserveSkinScroll(compositionScroll)
                Text("组成（选填）", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(compositionScroll), horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                    ItemComposition.entries.forEach { value -> FilterChip(selected = composition == value, enabled = enabled,
                        onClick = { onComposition(value) }, label = { Text(value.label) }) }
                }
            }
            fields.forEach { field -> row {
                OutlinedTextField(value = root[profile].asJsonObject[field.key].asString, onValueChange = { raw ->
                    val updated = Gson().toJsonTree(specifications).asJsonObject
                    updated[profile].asJsonObject.addProperty(field.key, raw)
                    onSpecifications(WardrobeSpecifications.decode(updated.toString()))
                }, label = { Text(field.label) }, enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
            } }
            if (otherCount > 0) row {
                Text("其他类型的 $otherCount 项规格继续保留，切换回对应类型即可查看和编辑。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    if (values.isNotEmpty() || specifications.pattern.isNotBlank() || composition != ItemComposition.UNCONFIRMED) LolitaSection("专属资料") {
        if (specifications.pattern.isNotBlank()) row { Text("图案：${specifications.pattern}") }
        if (composition != ItemComposition.UNCONFIRMED) row { Text("组成：${composition.label}") }
        values.forEach { (label, value) -> row { Text("$label：$value") } }
    }
}

@Composable
fun CategorySemanticPicker(value: CategorySemantic, onSelected: (CategorySemantic) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (value == CategorySemantic.UNCONFIRMED) "选择类型与默认穿戴位置" else "${value.family.label} · ${value.label} · ${value.defaultWearRole.label}")
    }
    if (show) {
        var query by remember { mutableStateOf("") }
        var family by remember { mutableStateOf<WardrobeFamily?>(null) }
        val familyScroll = rememberScrollState()
        val semanticScroll = rememberLazyListState()
        ObserveSkinScroll(familyScroll)
        ObserveSkinScroll(semanticScroll)
        AlertDialog(onDismissRequest = { show = false }, title = { Text("选择物品类型") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("搜索类型") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(familyScroll)) { WardrobeFamily.entries.filter { it != WardrobeFamily.UNCONFIRMED }.forEach { entry ->
                    FilterChip(family == entry, { family = entry }, label = { Text(entry.label) })
                } }
                LazyColumn(Modifier.heightIn(max = 280.dp), state = semanticScroll) {
                    items(CategorySemantic.entries.filter { it != CategorySemantic.UNCONFIRMED && (family == null || it.family == family) && it.label.contains(query, true) }) { entry ->
                        TextButton(onClick = { onSelected(entry); show = false }, modifier = Modifier.fillMaxWidth()) { Text("${entry.label} · ${entry.defaultWearRole.label}") }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { show = false }) { Text("取消") } })
    }
}
