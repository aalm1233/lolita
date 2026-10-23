package com.lolita.app.data.file

import com.google.gson.*
import com.lolita.app.data.model.*
import com.lolita.app.domain.model.OrderImportSource
import java.lang.reflect.Modifier
import java.util.UUID

/** Validates the new graph before restore touches the online database. */
internal object OrderImportBackupValidation {
    private val gson = Gson()
    private val stateFields =
        setOf(
            "orders",
            "selectedItems",
            "isLoading",
            "errorMessage",
            "fileLoaded",
            "fileCount",
            "dedupCount",
            "currentStep",
            "importItems",
            "brands",
            "categories",
            "locations",
            "storageNodes",
            "currentItemIndex",
            "missingItems",
            "importResult",
            "sessionId",
            "sessions",
            "files",
            "conflicts",
            "query",
            "selectionFilter",
            "shopFilter",
            "dateFrom",
            "dateTo",
            "isRestoring",
            "draftSaving",
            "draftError",
            "brandMappings",
            "newBrandNames",
            "resultItems",
            "linkedResultItems",
            "resultItemIds",
            "deletedResultItemIds",
        )
    private val rowFields =
        setOf(
            "originalItem",
            "name",
            "brandId",
            "categoryId",
            "color",
            "size",
            "price",
            "purchaseDate",
            "imageUrl",
            "styleSpec",
            "paymentRole",
            "pairedWith",
            "manualBalance",
            "balanceDueDate",
            "shipping",
            "paidAmount",
            "financialConfirmed",
            "sourceLineIndex",
            "sourceUnitIndex",
            "priceInput",
            "shippingInput",
            "paidInput",
            "balanceInput",
            "specifications",
            "composition",
            "locationId",
            "storageNodeId",
            "locationMappingNote",
            "sourceKey",
            "sourceGroupKey",
            "sourceOrdinal",
            "committedItemId",
            "sourceProblem",
            "identityConfirmed",
            "allowDeletedReimport",
        )

    private fun uuid(value: String) {
        require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) {
            "导入记录标识无效"
        }
    }

    private fun obj(json: String, fields: Set<String>): JsonObject {
        require(json.length <= 20 * 1024 * 1024) { "导入草稿内容超过限制" }
        val root = JsonParser.parseString(json).asJsonObject
        require(root.keySet().all { it in fields }) { "导入资料包含不支持的字段" }
        return root
    }

    private fun fields(type: Class<*>) =
        type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()

    private fun order(root: JsonObject) {
        require(root.keySet().all { it in fields(TaobaoOrder::class.java) }) { "订单来源字段无效" }
        require(
            root["orderId"].asString.isNotBlank() &&
                root["items"].isJsonArray &&
                root["items"].asJsonArray.size() <= 10_000
        )
        root["items"].asJsonArray.forEach { element ->
            val item = element.asJsonObject
            require(item.keySet().all { it in fields(TaobaoOrderItem::class.java) }) { "商品来源字段无效" }
            require(item["quantity"].asBigDecimal.intValueExact() in 1..100)
            require(item["orderId"].asString == root["orderId"].asString)
            require(
                item["productUrl"].asString.isEmpty() ||
                    OrderImportSource.publicUrl(item["productUrl"].asString) ==
                        item["productUrl"].asString
            ) {
                "商品来源链接无效"
            }
        }
    }

    fun validate(data: BackupData) {
        val sessions = data.orderImportSessions.associateBy { it.id }
        val files = data.orderImportFiles.associateBy { it.id }
        val commits = data.orderImportCommits.associateBy { it.id }
        val items = data.items.map { it.id }.toSet()
        require(
            sessions.size == data.orderImportSessions.size &&
                files.size == data.orderImportFiles.size &&
                commits.size == data.orderImportCommits.size
        ) {
            "导入记录标识重复"
        }
        data.orderImportSessions.forEach { session ->
            uuid(session.id)
            require(
                session.version == 1 &&
                    session.revision >= 0 &&
                    session.createdAt >= 0 &&
                    session.updatedAt >= 0 &&
                    session.state in setOf("ACTIVE", "COMPLETE", "IMPORTING")
            )
            session.pendingOperationId?.let(::uuid)
            val root = obj(session.dataJson, setOf("version", "state"))
            require(root["version"].asBigDecimal.intValueExact() == 1)
            val state = root["state"].asJsonObject
            require(state.keySet().all { it in stateFields })
            require(state["sessionId"].asString == session.id)
            require(
                state["currentStep"].asString in
                    setOf(
                        "ENTRY",
                        "FILES",
                        "SELECT",
                        "CONFLICT",
                        "PREPARE",
                        "DETAIL",
                        "REVIEW",
                        "IMPORTING",
                        "RESULT",
                        "ADDED",
                        "STORAGE",
                    )
            )
            listOf(
                    "importItems",
                    "files",
                    "resultItems",
                    "linkedResultItems",
                    "brands",
                    "categories",
                    "locations",
                    "storageNodes",
                    "sessions",
                )
                .forEach { require(state[it].asJsonArray.size() == 0) { "会话内不能隐藏业务记录或图片引用" } }
            state["orders"].asJsonArray.forEach { order(it.asJsonObject) }
        }
        data.orderImportFiles.forEach { file ->
            uuid(file.id)
            require(
                file.sessionId in sessions &&
                    file.name.isNotBlank() &&
                    file.state in setOf("READY", "FAILED")
            )
            require(file.state == "FAILED" || file.sha256.matches(Regex("[0-9a-f]{64}")))
            val orders = JsonParser.parseString(file.dataJson).asJsonArray
            orders.forEach { order(it.asJsonObject) }
        }
        require(data.orderImportRows.map { it.id }.distinct().size == data.orderImportRows.size)
        data.orderImportRows
            .groupBy { it.sessionId }
            .forEach { (_, rows) ->
                require(
                    rows.size <= 2000 && rows.map { it.position }.sorted() == rows.indices.toList()
                ) {
                    "导入资料序号无效"
                }
            }
        val rowsBySession = data.orderImportRows.groupBy { it.sessionId }
        data.orderImportRows.forEach { row ->
            require(
                row.sessionId in sessions &&
                    row.id == "${row.sessionId}:${row.position}" &&
                    row.sourceKey.startsWith("taobao:v1:")
            )
            val root = obj(row.dataJson, rowFields)
            require(root["imageUrl"] == null || root["imageUrl"].isJsonNull) { "导入图片必须使用显式路径列" }
            require(row.imagePaths.size <= 1 && row.imagePaths.all(String::isNotBlank))
            val item = gson.fromJson(root["originalItem"], TaobaoOrderItem::class.java)
            require(
                row.sourceKey == root["sourceKey"].asString &&
                    row.sourceKey == OrderImportSource.key(item, root["sourceOrdinal"].asInt)
            )
            listOf("price", "shipping", "paidAmount").forEach {
                require(root[it].asDouble.isFinite())
            }
            root["paymentRole"]
                ?.takeUnless { it.isJsonNull }
                ?.let { require(it.asString in setOf("DEPOSIT", "BALANCE")) }
            root["pairedWith"]
                ?.takeUnless { it.isJsonNull }
                ?.asInt
                ?.let { partner ->
                    val other =
                        rowsBySession[row.sessionId].orEmpty().firstOrNull {
                            it.position == partner
                        } ?: error("导入配对资料缺失")
                    require(
                        partner != row.position &&
                            JsonParser.parseString(other.dataJson)
                                .asJsonObject["pairedWith"]
                                .asInt == row.position
                    ) {
                        "导入配对关系无效"
                    }
                }
        }
        data.orderImportCommits.forEach { commit ->
            uuid(commit.id)
            require(commit.sessionId == null || commit.sessionId in sessions)
            require(
                commit.revision >= 0 &&
                    commit.createdAt >= 0 &&
                    commit.mergedCount >= 0 &&
                    commit.snapshotHash.matches(Regex("[0-9a-f]{64}"))
            )
            require(
                commit.itemIds.distinct().size == commit.itemIds.size &&
                    commit.itemIds.all { (it.toLongOrNull() ?: 0) > 0 }
            )
        }
        require(
            data.orderImportReceipts.map { it.id }.distinct().size == data.orderImportReceipts.size
        )
        require(
            data.orderImportReceipts.map { it.sourceKey to it.generation }.distinct().size ==
                data.orderImportReceipts.size
        )
        data.orderImportReceipts
            .groupBy { it.sourceKey }
            .values
            .forEach { require(it.count { r -> r.itemId != null } <= 1) { "同一来源重复关联物品" } }
        data.orderImportReceipts.forEach { receipt ->
            uuid(receipt.id)
            require(
                receipt.commitId in commits &&
                    receipt.generation > 0 &&
                    receipt.unitOrdinal >= 0 &&
                    receipt.createdAt >= 0
            )
            require(receipt.itemId == null || receipt.itemId in items)
            require(
                receipt.itemId == null ||
                    receipt.itemId.toString() in
                        (commits.getValue(receipt.commitId).itemIds +
                            commits.getValue(receipt.commitId).linkedItemIds)
            )
            require(
                receipt.paidCents in 0..99_999_999_999L &&
                    receipt.shippingCents in 0..99_999_999_999L
            )
            val source = JsonParser.parseString(receipt.sourceSnapshot).asJsonObject
            order(source)
            require(
                source["orderId"].asString == receipt.orderId &&
                    receipt.sourceKey == "taobao:v1:${receipt.groupKey}:${receipt.unitOrdinal}"
            )
        }
        require(data.itemSourceRecords.map { it.id }.distinct().size == data.itemSourceRecords.size)
        data.itemSourceRecords.forEach { source ->
            uuid(source.id)
            require(
                source.itemId in items &&
                    source.kind in setOf("ORDER_IMPORT", "PRODUCT_SUPPLEMENT") &&
                    source.createdAt >= 0
            )
            require(
                source.publicUrl == null ||
                    OrderImportSource.publicUrl(source.publicUrl) == source.publicUrl
            )
            require(
                source.imagePaths.all(String::isNotBlank) &&
                    source.appliedFields.all {
                        it in
                            setOf(
                                "purchaseEvidence",
                                "name",
                                "brandId",
                                "categoryId",
                                "colors",
                                "size",
                                "specifications",
                                "photos",
                                "details",
                            )
                    }
            )
            require(JsonParser.parseString(source.snapshotJson).isJsonObject)
        }
    }
}
