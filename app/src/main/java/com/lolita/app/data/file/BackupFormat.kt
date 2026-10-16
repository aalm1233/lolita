package com.lolita.app.data.file

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.model.ImageTaskKind
import com.lolita.app.domain.model.ImageTaskStatus
import java.lang.reflect.Modifier

/** Explicit JSON migration before Gson: Kotlin constructor defaults are not deserialization defaults. */
internal object BackupFormat {
    const val VERSION = 9
    const val SCHEMA_VERSION = 29
    private val gson = Gson()
    private const val MAX_ACTIVE_AMOUNT = com.lolita.app.domain.usecase.OrderAccounting.MAX_AMOUNT
    private val entities = linkedMapOf(
        "brands" to Brand::class.java, "categories" to Category::class.java,
        "coordinates" to Coordinate::class.java, "items" to Item::class.java,
        "prices" to Price::class.java, "payments" to Payment::class.java,
        "outfitLogs" to OutfitLog::class.java, "outfitItemCrossRefs" to OutfitItemCrossRef::class.java,
        "styles" to Style::class.java, "seasons" to Season::class.java,
        "locations" to Location::class.java, "sources" to Source::class.java,
        "legacyCatalogArchives" to LegacyCatalogArchive::class.java,
        "syncStates" to SharedLibrarySyncState::class.java,
        "remoteBrands" to RemoteBrand::class.java, "remoteCategories" to RemoteCategory::class.java,
        "remoteStyles" to RemoteStyle::class.java, "remoteSeasons" to RemoteSeason::class.java,
        "remoteSources" to RemoteSource::class.java, "remoteCoordinates" to RemoteSharedCoordinate::class.java,
        "remoteItems" to RemoteSharedItem::class.java, "remotePricePlans" to RemoteSharedPricePlan::class.java,
        "monthlyBudgets" to MonthlyBudget::class.java, "tryOnResults" to TryOnResult::class.java,
        "imageTasks" to ImageTask::class.java,
        "personalModels" to PersonalModel::class.java, "personalModelDrafts" to PersonalModelDraft::class.java,
        "orderBindings" to OrderBinding::class.java, "paymentStages" to PaymentStage::class.java
    )
    private val requiredArrays = entities.keys.take(8)

    fun parse(json: String): BackupData {
        val root = JsonParser.parseString(json).asJsonObject
        val version = root.get("formatVersion")?.asBigDecimal?.intValueExact() ?: 1
        require(version in 1..VERSION) { "不支持的备份格式版本：$version" }
        val schema = root.get("schemaVersion")?.asBigDecimal?.intValueExact() ?: 0
        require(schema in 0..SCHEMA_VERSION) { "备份来自更新的数据库版本：$schema" }
        val allowedRoot = entities.keys + setOf("backupDate", "appVersion", "formatVersion", "schemaVersion")
        require(root.keySet().all { it in allowedRoot }) { "备份包含未支持的数据类型，无法无损导入" }
        if (version >= 2) require(entities.keys.filter { (version >= 3 || it !in setOf("orderBindings", "paymentStages")) && (version >= 6 || it != "monthlyBudgets") && (version >= 7 || it != "tryOnResults") && (version >= 8 || it != "imageTasks") && (version >= 9 || it !in setOf("personalModels", "personalModelDrafts")) }.all { root.has(it) && root[it].isJsonArray }) {
            "完整备份缺少数据表"
        }
        requiredArrays.forEach { require(root.has(it) && root[it].isJsonArray) { "备份缺少 $it" } }
        if (version == 1) migratePurchaseDates(root)
        if (version < 3) migrateOrders(root)
        if (version < 4) migratePurchaseRecordStatuses(root)
        if (version < 5) persistOrderPurchaseEvidence(root)
        entities.forEach { (name, type) ->
            if (!root.has(name) || root[name].isJsonNull) root.add(name, JsonArray())
            require(root[name].isJsonArray) { "$name 必须是数组" }
            val fields = type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
            root[name].asJsonArray.forEachIndexed { index, element ->
                require(element.isJsonObject) { "$name[$index] 不是有效记录" }
                val row = element.asJsonObject
                fun default(row: JsonObject, field: String, value: Any) {
                    if (version == 1) BackupFormat.default(row, field, value)
                }
                if (name in setOf("items", "coordinates", "outfitLogs")) {
                    if (row.has("imageUrl") && !row.has("imageUrls")) {
                        val image = row.remove("imageUrl")
                        row.add("imageUrls", JsonArray().apply {
                            if (!image.isJsonNull && image.asString.isNotBlank()) add(image.asString)
                        })
                    }
                    row.remove("imageUrl")
                    default(row, "imageUrls", JsonArray())
                    if (name != "outfitLogs") default(row, "description", "")
                    else default(row, "note", "")
                    default(row, "updatedAt", row.get("createdAt") ?: error("缺少 createdAt"))
                }
                if (name == "items") {
                    if (!row.has("sizeChartImageUrls")) row.add("sizeChartImageUrls", JsonArray())
                    if (version == 1) {
                        if (row.has("color") && !row.has("colors")) row.add("colors", row.remove("color"))
                        row.remove("color")
                        migrateLegacyColors(row)
                    }
                    default(row, "priority", "MEDIUM")
                    default(row, "coordinateOrder", 0)
                }
                if (name in setOf("brands", "categories", "styles", "seasons", "sources")) default(row, "isPreset", false)
                if (name == "categories") default(row, "group", "CLOTHING")
                if (name == "locations") {
                    default(row, "sortOrder", 0)
                    default(row, "description", "")
                    default(row, "updatedAt", row.get("createdAt") ?: error("缺少 createdAt"))
                }
                if (name == "payments") {
                    default(row, "isPaid", false)
                    default(row, "reminderSet", false)
                }
                require(row.keySet().all { it in fields }) { "$name[$index] 包含未支持字段" }
                // Nullable fields can be omitted; every non-null field is checked by JSON type.
                type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach { field ->
                    val value = row[field.name] ?: return@forEach
                    if (value.isJsonNull) {
                        require(!field.type.isPrimitive && field.name !in setOf("month", "name", "description", "note", "imageUrls", "sizeChartImageUrls", "colors", "status", "priority", "type", "group", "purchaseRecordStatus", "payload", "sourceTable", "recordKey", "publicId", "cacheKey", "backendBaseUrl", "assetBaseUrl", "sharedItemPublicId")) { "$name[$index].${field.name} 不能为空" }
                    } else when {
                        field.type.isEnum -> require(value.isJsonPrimitive && value.asString in requireNotNull(field.type.enumConstants).map { (it as Enum<*>).name }) { "未知枚举：$name[$index].${field.name}" }
                        List::class.java.isAssignableFrom(field.type) -> require(value.isJsonArray && value.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString }) { "$name[$index].${field.name} 必须是字符串数组" }
                        field.type == String::class.java -> require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$name[$index].${field.name} 必须是文本" }
                        field.type == Boolean::class.javaPrimitiveType -> require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "$name[$index].${field.name} 必须是布尔值" }
                        else -> {
                            require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asDouble.isFinite()) { "$name[$index].${field.name} 必须是有限数值" }
                            if (field.type in setOf(Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, java.lang.Long::class.java, java.lang.Integer::class.java)) {
                                val integer = value.asBigDecimal.longValueExact()
                                if (field.type in setOf(Int::class.javaPrimitiveType, java.lang.Integer::class.java)) require(integer in Int.MIN_VALUE..Int.MAX_VALUE) { "整数越界" }
                            }
                        }
                    }
                }
                val required = when (name) {
                    "personalModels" -> setOf("id","name","requestJson","sourceImagePaths","imageUrl","provider","model","createdAt","updatedAt")
                    "personalModelDrafts" -> setOf("id","dataJson","sourceImagePaths","updatedAt")
                    "imageTasks" -> setOf("id","kind","provider","title","requestJson","sourceImagePaths","status","requestSent","createdAt","updatedAt")
                    "tryOnResults" -> setOf("id","name","imageUrl","itemIds","itemNames","model","createdAt")
                    "monthlyBudgets" -> setOf("month", "amountCents")
                    "outfitItemCrossRefs" -> setOf("outfitLogId", "itemId")
                    "orderBindings" -> setOf("itemId", "priceId", "purchaseRecordStatus")
                    "paymentStages" -> setOf("id", "priceId", "name", "amount", "reminderSet")
                    "legacyCatalogArchives" -> setOf("sourceTable", "recordKey", "payload")
                    "syncStates" -> setOf("cacheKey", "backendBaseUrl", "assetBaseUrl", "nextCursor", "schemaVersion")
                    else -> fields.filter { field -> field in setOf("id", "name", "createdAt", "updatedAt", "publicId", "brandId", "categoryId", "itemId", "priceId", "status", "priority", "type", "group", "amount", "totalPrice", "dueDate", "date", "description", "note", "imageUrls", "colors", "priceType", "sharedItemPublicId", "coordinateOrder", "sortOrder", "isPaid", "reminderSet", "isPreset", "shipping", "includedInParentPrice", "purchaseRecordStatus") }.toSet()
                }
                required.forEach { require(row.has(it) && !row[it].isJsonNull) { "$name[$index] 缺少 $it" } }
            }
        }
        default(root, "backupDate", 0L)
        default(root, "appVersion", "历史备份")
        require(root["appVersion"].isJsonPrimitive && root["appVersion"].asJsonPrimitive.isString) { "备份版本必须是文本" }
        root["backupDate"].asBigDecimal.longValueExact()
        root.addProperty("formatVersion", version)
        root.addProperty("schemaVersion", schema)
        val data = gson.fromJson(root, BackupData::class.java)
        validate(data)
        return data
    }

    private fun persistOrderPurchaseEvidence(root: JsonObject) {
        val bindingByPrice = root["orderBindings"].asJsonArray.map { it.asJsonObject }
            .associateBy { it["priceId"].asLong }
        root["prices"].asJsonArray.forEach { element ->
            val price = element.asJsonObject
            require(!price.has("purchaseRecordStatus")) { "历史备份包含新版订单购买证据" }
            price.add("purchaseRecordStatus", bindingByPrice[price["id"].asLong]
                ?.get("purchaseRecordStatus")?.deepCopy()
                ?: gson.toJsonTree(PurchaseRecordStatus.NEEDS_REVIEW.name))
        }
    }

    /** A missing ledger is not evidence of an outstanding payment or a full payment. */
    private fun migratePurchaseRecordStatuses(root: JsonObject) {
        val items = root["items"].asJsonArray.map { it.asJsonObject }.associateBy { it["id"].asLong }
        val prices = root["prices"].asJsonArray.map { it.asJsonObject }.associateBy { it["id"].asLong }
        val paymentsByPrice = root["payments"].asJsonArray.map { it.asJsonObject }.groupBy { it["priceId"].asLong }
        val pricesWithEvidence = (root["payments"].asJsonArray + root["paymentStages"].asJsonArray)
            .map { it.asJsonObject["priceId"].asLong }.toSet()
        root["orderBindings"].asJsonArray.forEach { element ->
            val binding = element.asJsonObject
            require(!binding.has("purchaseRecordStatus")) { "历史备份包含新版购买记录状态" }
            val priceId = binding["priceId"].asLong
            val itemWasOwned = items[binding["itemId"].asLong]?.get("status")?.asString == "OWNED"
            val actualPaidCents = paymentsByPrice[priceId].orEmpty()
                .filter { it["isPaid"]?.takeUnless { value -> value.isJsonNull }?.asBoolean == true }
                .fold(0L) { total, payment ->
                    Math.addExact(total, com.lolita.app.domain.usecase.OrderAccounting.cents(payment["amount"].asDouble))
                }
            val totalCents = prices[priceId]?.get("totalPrice")?.takeUnless { it.isJsonNull }?.asDouble
                ?.let(com.lolita.app.domain.usecase.OrderAccounting::cents)
            val status = when {
                itemWasOwned && (totalCents == null || actualPaidCents < totalCents) -> PurchaseRecordStatus.HISTORICAL_OWNED
                priceId !in pricesWithEvidence -> PurchaseRecordStatus.NEEDS_REVIEW
                else -> PurchaseRecordStatus.TRACKED
            }
            binding.addProperty("purchaseRecordStatus", status.name)
        }
    }

    /** v1/v2 had payment plans and actual payments in one table. Preserve every row. */
    private fun migrateOrders(root: JsonObject) {
        root["items"].asJsonArray.forEach { element ->
            default(element.asJsonObject, "includedInParentPrice", false)
        }
        root["prices"].asJsonArray.forEach { element ->
            default(element.asJsonObject, "shipping", 0.0)
        }
        // Historical backups cannot contain the redesigned structures: rejecting them avoids
        // silently replacing any supplied relationships or installment data.
        require(!root.has("orderBindings") || root["orderBindings"].asJsonArray.size() == 0) { "历史备份包含新版订单关系" }
        require(!root.has("paymentStages") || root["paymentStages"].asJsonArray.size() == 0) { "历史备份包含新版付款阶段" }
        val paymentRowsByPrice = root["payments"].asJsonArray.map { it.asJsonObject }.groupBy { it["priceId"].asLong }
        val itemRowsById = root["items"].asJsonArray.map { it.asJsonObject }.associateBy { it["id"].asLong }
        root.add("orderBindings", JsonArray().apply {
            root["prices"].asJsonArray.map { it.asJsonObject }.groupBy { it["itemId"].asLong }.forEach { (itemId, prices) ->
                val price = prices.singleOrNull()
                if (price != null && isValidHistoricalOrder(price, paymentRowsByPrice[price["id"].asLong].orEmpty(),
                        itemRowsById[itemId]?.get("status")?.asString)) add(JsonObject().apply {
                    addProperty("itemId", itemId); add("priceId", price["id"].deepCopy())
                })
            }
        })
        root.add("paymentStages", JsonArray().apply {
            root["payments"].asJsonArray.forEach { element ->
                val payment = element.asJsonObject
                require(!payment.has("stageId") || payment["stageId"].isJsonNull) { "历史付款包含新版阶段关联" }
                payment.add("stageId", payment["id"].deepCopy())
                add(JsonObject().apply {
                    add("id", payment["id"].deepCopy()); add("priceId", payment["priceId"].deepCopy())
                    addProperty("name", "历史付款 ${payment["id"].asLong}")
                    add("amount", payment["amount"].deepCopy()); add("dueDate", payment["dueDate"].deepCopy())
                    add("reminderSet", payment["reminderSet"]?.deepCopy() ?: gson.toJsonTree(false))
                    payment["customReminderDays"]?.let { add("customReminderDays", it.deepCopy()) }
                    // The old external pointer stays in the Payment row for cleanup after restore.
                })
            }
        })
    }

    /** Match the migration's safe binding predicate without changing any historical values. */
    private fun isValidHistoricalOrder(price: JsonObject, payments: List<JsonObject>, itemStatus: String?): Boolean {
        val total = price["totalPrice"]?.takeUnless { it.isJsonNull }?.asDouble ?: return false
        if (!com.lolita.app.domain.usecase.OrderAccounting.hasCentPrecision(total) || total <= 0 || total >= MAX_ACTIVE_AMOUNT) return false
        // A wishlist quote is not an order. Preserve it for review until actual payment exists.
        if (itemStatus == "WISHED" && payments.none { it["isPaid"]?.takeUnless { value -> value.isJsonNull }?.asBoolean == true }) return false
        return payments.none { payment ->
            payment["amount"].asDouble.let { !com.lolita.app.domain.usecase.OrderAccounting.hasCentPrecision(it) || it <= 0 || it >= MAX_ACTIVE_AMOUNT }
        }
    }

    /** Only unversioned/v1 exports used scalar or doubly encoded colors. Modern arrays are literal data. */
    private fun migrateLegacyColors(row: JsonObject) {
        val colors = row["colors"]
        val array = when {
            colors == null || colors.isJsonNull -> JsonArray()
            colors.isJsonArray -> colors.asJsonArray
            colors.isJsonPrimitive && colors.asJsonPrimitive.isString -> {
                val value = colors.asString
                if (value.trim().startsWith("[")) JsonParser.parseString(value).asJsonArray
                else JsonArray().apply { if (value.isNotBlank()) add(value) }
            }
            else -> error("颜色格式错误")
        }
        row.add("colors", array)
        if (array.size() == 1 && array[0].isJsonPrimitive && array[0].asJsonPrimitive.isString && array[0].asString.trim().startsWith("[")) {
            row.add("colors", JsonParser.parseString(array[0].asString).asJsonArray)
        }
    }

    private fun migratePurchaseDates(root: JsonObject) {
        val payments = root["payments"].asJsonArray
        var nextId = payments.maxOfOrNull { it.asJsonObject["id"].asLong } ?: 0L
        root["prices"].asJsonArray.forEach { element ->
            val price = element.asJsonObject
            // Historical Gson omitted nullable fields. Missing and explicit null both mean unknown.
            val date = price.remove("purchaseDate")?.takeUnless { it.isJsonNull }
            val priceId = price["id"].asLong
            if (date != null && payments.none { it.asJsonObject["priceId"].asLong == priceId }) {
                val knownPaidAt = date.asBigDecimal.longValueExact()
                payments.add(JsonObject().apply {
                    addProperty("id", Math.addExact(nextId, 1).also { nextId = it })
                    addProperty("priceId", priceId); add("amount", price["totalPrice"])
                    addProperty("dueDate", knownPaidAt)
                    addProperty("paidDate", knownPaidAt)
                    add("createdAt", price["createdAt"])
                    addProperty("isPaid", true); addProperty("reminderSet", false)
                })
            }
        }
    }

    private fun default(row: JsonObject, name: String, value: Any) {
        if (!row.has(name) || row[name].isJsonNull) row.add(name, gson.toJsonTree(value))
    }

    fun validate(data: BackupData) {
        val taskIds = data.imageTasks.map { it.id }
        require(taskIds.distinct().size == taskIds.size) { "图像任务标识重复" }
        data.imageTasks.forEach { task ->
            require(runCatching { java.util.UUID.fromString(task.id).toString() == task.id }.getOrDefault(false)) { "图像任务标识无效" }
            require(task.createdAt >= 0 && task.updatedAt >= 0 &&
                (task.error == null || task.error.length <= 180) && (task.tryOnResultId == null || task.tryOnResultId > 0)) { "图像任务内容无效" }
            task.validate()
            require(task.retainsSourceImages || task.sourceImagePaths.isEmpty()) { "已结束任务仍保留临时参考图片" }
            require(task.kind == ImageTaskKind.TRY_ON || task.tryOnResultId == null) { "人台任务不能关联试衣历史" }
            require(task.personalModelId == null || task.personalModelId > 0 && task.kind == ImageTaskKind.PERSONAL_MODEL && task.status == ImageTaskStatus.COMPLETED) { "模特任务关联无效" }
            require(task.kind != ImageTaskKind.PERSONAL_MODEL || task.status != ImageTaskStatus.COMPLETED || task.personalModelId != null) { "已完成模特任务缺少已保存模特标识" }
            require(task.status !in setOf(ImageTaskStatus.GENERATING,ImageTaskStatus.SAVING,ImageTaskStatus.AWAITING_CONFIRMATION,ImageTaskStatus.COMPLETED) || task.requestSent) { "图像任务请求状态不一致" }
        }
        val previews = data.imageTasks.filter { it.status == ImageTaskStatus.AWAITING_CONFIRMATION }.map { it.resultImagePath }
        require(previews.distinct().size == previews.size) { "待确认图片不能由多个任务持有" }
        val retainedInputs = data.imageTasks.filter { it.retainsSourceImages }.flatMap { it.sourceImagePaths }.toSet()
        require(previews.none { it in retainedInputs }) { "待确认图片不能复用任务输入照片" }
        require(data.monthlyBudgets.map { it.month }.distinct().size == data.monthlyBudgets.size) { "预算月份重复" }
        data.monthlyBudgets.forEach { com.lolita.app.domain.usecase.BudgetAccounting.validate(it.month, it.amountCents) }
        fun ids(values: List<Long>, table: String): Set<Long> {
            require(values.all { it > 0 } && values.toSet().size == values.size) { "$table 主键无效或重复" }
            return values.toSet()
        }
        ids(data.tryOnResults.map { it.id },"试衣记录")
        data.tryOnResults.forEach {
            require(it.name.isNotBlank() && it.name.length <= 120 && it.imageUrl.isNotBlank() && it.model.isNotBlank() && it.createdAt >= 0 &&
                it.itemIds.size in 1..8 && it.itemIds.size == it.itemNames.size && it.itemIds.distinct().size == it.itemIds.size &&
                it.itemIds.all { id -> (id.toLongOrNull() ?: 0L) > 0 } && it.itemNames.all(String::isNotBlank)) { "试衣记录内容无效" }
            require((it.personalModelId == null && it.personalModelName == null) ||
                (it.personalModelId != null && it.personalModelId > 0 && !it.personalModelName.isNullOrBlank())) { "试衣人物快照无效" }
        }
        ids(data.personalModels.map { it.id }, "个人模特")
        data.personalModels.forEach {
            require(it.name == it.name.trim() && it.name.isNotBlank() && it.name.length <= 60 &&
                it.imageUrl.isNotBlank() && it.model.isNotBlank() && it.createdAt >= 0 && it.updatedAt >= 0) { "个人模特内容无效" }
            it.request().validatePaths(it.sourceImagePaths)
        }
        require(data.personalModelDrafts.map { it.id }.distinct().size == data.personalModelDrafts.size) { "人物草稿标识重复" }
        data.personalModelDrafts.forEach { it.validate(); require(it.updatedAt >= 0) { "人物草稿时间无效" } }
        val profiles = data.personalModels.associateBy { it.id }
        val results = data.tryOnResults.associateBy { it.id }
        data.imageTasks.forEach { task ->
            // Deleted historical targets are allowed; an existing target must be the exact owned result.
            task.personalModelId?.let(profiles::get)?.let { model ->
                val request = com.lolita.app.domain.model.ImageTaskRequest.decode(task.requestJson)
                require(task.resultImagePath == model.imageUrl && request.personalModel == model.request() && task.provider == model.provider) { "模特任务与已保存底图不匹配" }
            }
            task.tryOnResultId?.let(results::get)?.let { result ->
                val request = com.lolita.app.domain.model.ImageTaskRequest.decode(task.requestJson)
                require(task.status == ImageTaskStatus.COMPLETED && task.resultImagePath == result.imageUrl &&
                    request.garments.map { it.itemId.toString() } == result.itemIds && request.garments.map { it.name } == result.itemNames &&
                    request.tryOnModel?.personalModelId == result.personalModelId && request.tryOnModel?.name == result.personalModelName) { "图像任务与试衣历史不匹配" }
            }
        }
        val brands = ids(data.brands.map { it.id }, "品牌")
        val categories = ids(data.categories.map { it.id }, "分类")
        val coordinates = ids(data.coordinates.map { it.id }, "套装")
        val locations = ids(data.locations.map { it.id }, "位置")
        val items = ids(data.items.map { it.id }, "服饰")
        val importTokens = data.items.mapNotNull { it.importToken }
        require(importTokens.distinct().size == importTokens.size) { "服饰导入标识重复" }
        val prices = ids(data.prices.map { it.id }, "价格")
        ids(data.payments.map { it.id }, "付款")
        val logs = ids(data.outfitLogs.map { it.id }, "穿搭")
        ids(data.styles.map { it.id }, "风格"); ids(data.seasons.map { it.id }, "季节"); ids(data.sources.map { it.id }, "来源")
        require(data.items.all { it.brandId in brands && it.categoryId in categories && (it.coordinateId == null || it.coordinateId in coordinates) && (it.locationId == null || it.locationId in locations) }) { "服饰关联无效" }
        // Archived rows are original evidence, including old signed amounts. Validate their
        // shape and references; apply current positive-money rules only to active orders.
        require(data.prices.all { it.itemId in items && it.totalPrice.isFinite() &&
            (it.deposit == null || it.deposit.isFinite()) && (it.balance == null || it.balance.isFinite()) &&
            it.shipping.isFinite() }) { "价格或关联无效" }
        require(data.payments.all { it.priceId in prices && it.amount.isFinite() }) { "付款或关联无效" }
        val itemById = data.items.associateBy { it.id }
        require(data.items.all { item ->
            val parent = item.parentItemId?.let { itemById[it] }
            (item.parentItemId == null || parent != null && parent.id != item.id && parent.parentItemId == null) &&
                (!item.includedInParentPrice || parent != null)
        }) { "主件与小物关系无效，不能循环或多层挂载" }
        ids(data.orderBindings.map { it.itemId }, "订单服饰关系")
        ids(data.orderBindings.map { it.priceId }, "订单价格关系")
        val priceById = data.prices.associateBy { it.id }
        require(data.orderBindings.all { binding ->
            binding.itemId in items && priceById[binding.priceId]?.itemId == binding.itemId &&
                itemById[binding.itemId]?.includedInParentPrice == false &&
                priceById[binding.priceId]?.purchaseRecordStatus == binding.purchaseRecordStatus
        }) { "订单价格关联无效、购买证据不一致或共用价格的小物重复计价" }
        val activePrices = data.orderBindings.map { it.priceId }.toSet()
        fun validActiveAmount(amount: Double) = com.lolita.app.domain.usecase.OrderAccounting.hasCentPrecision(amount) && amount > 0 && amount < MAX_ACTIVE_AMOUNT
        require(data.prices.filter { it.id in activePrices }.all {
            validActiveAmount(it.totalPrice) && com.lolita.app.domain.usecase.OrderAccounting.hasCentPrecision(it.shipping) && it.shipping >= 0 && it.shipping <= it.totalPrice
        }) { "当前订单金额无效，请将原始异常记录保留为历史订单" }
        require(data.payments.filter { it.priceId in activePrices }.all { validActiveAmount(it.amount) }) {
            "当前订单付款金额无效，请先核对历史记录"
        }
        val stages = ids(data.paymentStages.map { it.id }, "付款阶段")
        require(data.paymentStages.all { it.priceId in prices && it.name.isNotBlank() && it.amount.isFinite() }) { "付款阶段或关联无效" }
        require(data.paymentStages.filter { it.priceId in activePrices }.all { validActiveAmount(it.amount) }) {
            "当前订单付款阶段金额无效，请先核对历史记录"
        }
        val stageById = data.paymentStages.associateBy { it.id }
        require(data.payments.all { it.stageId == null || it.stageId in stages && stageById[it.stageId]?.priceId == it.priceId }) { "付款阶段与价格不匹配" }
        require(data.outfitItemCrossRefs.all { it.itemId in items && it.outfitLogId in logs } && data.outfitItemCrossRefs.distinct().size == data.outfitItemCrossRefs.size) { "穿搭关联无效或重复" }
        data.legacyCatalogArchives.forEach { archive ->
            require(archive.sourceTable in setOf("catalog_entries", "remote_catalog_entries")) { "未知历史图鉴表" }
            val row = JsonParser.parseString(archive.payload).asJsonObject
            val key = if (archive.sourceTable == "catalog_entries") "id" else "public_id"
            require(row[key]?.asString == archive.recordKey) { "历史图鉴主键不匹配" }
            row["image_urls"]?.takeUnless { it.isJsonNull }?.let { value ->
                val images = if (value.isJsonArray) value.asJsonArray else JsonParser.parseString(value.asString).asJsonArray
                require(images.all { it.isJsonPrimitive && it.asJsonPrimitive.isString }) { "历史图鉴图片格式错误" }
            }
        }
    }
}
