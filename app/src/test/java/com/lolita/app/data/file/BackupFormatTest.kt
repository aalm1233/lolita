package com.lolita.app.data.file

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonNull
import com.lolita.app.data.local.entity.*
import org.junit.Assert.*
import org.junit.Test

class BackupFormatTest {
    private fun data() = BackupData(
        brands = listOf(Brand(id = 101, name = "品牌", createdAt = 100)),
        categories = listOf(Category(id = 307, name = "分类", createdAt = 101)),
        coordinates = emptyList(),
        items = listOf(Item(id = 9001, brandId = 101, categoryId = 307, name = "服饰", description = "备注", status = ItemStatus.OWNED, createdAt = 102, updatedAt = 103)),
        prices = listOf(Price(id = 8, itemId = 9001, type = PriceType.FULL, totalPrice = 1234.56, createdAt = 104)),
        payments = listOf(Payment(id = 9, priceId = 8, stageId = 9, amount = 1234.56, dueDate = 105, createdAt = 106)),
        orderBindings = listOf(OrderBinding(itemId = 9001, priceId = 8)),
        paymentStages = listOf(PaymentStage(id = 9, priceId = 8, name = "历史付款 9", amount = 1234.56, dueDate = 105)),
        outfitLogs = emptyList(), outfitItemCrossRefs = emptyList(), backupDate = 107
    )
    private fun json() = Gson().toJsonTree(data()).asJsonObject
    private fun historicalJson(version: Int = 1) = json().apply {
        addProperty("formatVersion", version); addProperty("schemaVersion", 20)
        remove("orderBindings"); remove("paymentStages")
        get("items").asJsonArray.forEach { it.asJsonObject.remove("includedInParentPrice") }
        get("prices").asJsonArray.forEach { it.asJsonObject.remove("shipping"); it.asJsonObject.remove("purchaseRecordStatus") }
        get("payments").asJsonArray.forEach { it.asJsonObject.remove("stageId") }
    }
    private fun v3Json() = json().apply {
        addProperty("formatVersion", 3); addProperty("schemaVersion", 21)
        get("orderBindings").asJsonArray.forEach { it.asJsonObject.remove("purchaseRecordStatus") }
        get("prices").asJsonArray.forEach { it.asJsonObject.remove("purchaseRecordStatus") }
    }
    private fun v4Json() = json().apply {
        addProperty("formatVersion", 4); addProperty("schemaVersion", 22)
        get("prices").asJsonArray.forEach { it.asJsonObject.remove("purchaseRecordStatus") }
    }

    @Test fun currentColorArraysPreserveLiteralBracketNames() {
        for (colors in listOf(listOf("[浅粉]"), listOf("[]"), listOf("["), listOf("[\"浅粉\"]"), listOf("浅粉", "[深粉]"))) {
            val original = data().copy(items = listOf(data().items.single().copy(colors = colors)))
            assertEquals(colors, BackupFormat.parse(Gson().toJson(original)).items.single().colors)
        }
    }

    @Test fun completeVersionedBackupsKeepLiteralColorsAndRejectScalarColors() {
        for (version in 2..BackupFormat.VERSION) {
            val root = when (version) { 2 -> historicalJson(2); 3 -> v3Json(); 4 -> v4Json(); else -> json() }
            root.addProperty("formatVersion", version)
            val row = root["items"].asJsonArray[0].asJsonObject
            row.add("colors", Gson().toJsonTree(listOf("[")))
            assertEquals(listOf("["), BackupFormat.parse(root.toString()).items.single().colors)
            row.addProperty("colors", "粉色")
            assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(root.toString()) }
        }
    }

    @Test fun legacyEncodedColorsRemainCompatible() {
        for (raw in listOf("[\"粉色\",\"白色\"]", "粉色")) {
            val old = historicalJson()
            old["items"].asJsonArray[0].asJsonObject.addProperty("colors", raw)
            val expected = if (raw == "粉色") listOf("粉色") else listOf("粉色", "白色")
            assertEquals(expected, BackupFormat.parse(old.toString()).items.single().colors)
        }
        val wrapped = historicalJson()
        wrapped["items"].asJsonArray[0].asJsonObject.add("colors", Gson().toJsonTree(listOf("[\"粉色\"]")))
        assertEquals(listOf("粉色"), BackupFormat.parse(wrapped.toString()).items.single().colors)
    }

    @Test fun preservesEveryFieldOnRoundTrip() {
        assertEquals(data(), BackupFormat.parse(Gson().toJson(data())))
    }

    @Test fun migratesHistoricalDefaultsWithoutLosingTimestamps() {
        val json = historicalJson()
        json.remove("formatVersion"); json.remove("schemaVersion")
        val item = json["items"].asJsonArray[0].asJsonObject
        item.remove("priority"); item.remove("imageUrls"); item.remove("coordinateOrder"); item.remove("updatedAt")
        item.remove("colors"); item.addProperty("color", "粉色")
        json["categories"].asJsonArray[0].asJsonObject.remove("group")
        listOf("styles", "seasons", "locations", "sources").forEach { json.remove(it) }
        val restored = BackupFormat.parse(json.toString())
        assertEquals(listOf("粉色"), restored.items.single().colors)
        assertEquals(102L, restored.items.single().updatedAt)
        assertEquals(ItemPriority.MEDIUM, restored.items.single().priority)
        assertEquals(CategoryGroup.CLOTHING, restored.categories.single().group)
        assertTrue(restored.items.single().imageUrls.isEmpty())
    }

    @Test fun rejectsFutureVersionsAndUnknownFields() {
        val json = json(); json.addProperty("formatVersion", 99)
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
        json.addProperty("formatVersion", BackupFormat.VERSION)
        json["items"].asJsonArray[0].asJsonObject.addProperty("futureData", "不可丢失")
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
    }

    @Test fun rejectsBrokenAssociationsDuplicateKeysAndFractionalIds() {
        val json = json(); val item = json["items"].asJsonArray[0].asJsonObject
        item.addProperty("brandId", 999)
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
        item.addProperty("brandId", 101)
        json["items"].asJsonArray.add(item.deepCopy())
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
        json["items"].asJsonArray.remove(1); item.addProperty("id", 1.5)
        assertThrows(ArithmeticException::class.java) { BackupFormat.parse(json.toString()) }
    }

    @Test fun rejectsCorruptColorsAndUnknownEnums() {
        val json = historicalJson(); json.remove("formatVersion")
        val item = json["items"].asJsonArray[0].asJsonObject
        item.addProperty("colors", "[broken")
        assertThrows(RuntimeException::class.java) { BackupFormat.parse(json.toString()) }
        item.add("colors", JsonParser.parseString("[]")); item.addProperty("status", "FUTURE")
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
    }

    @Test fun rejectsIncompleteModernBackupAndInvalidAmounts() {
        val json = json(); json.remove("remoteItems")
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
        val second = json(); second["prices"].asJsonArray[0].asJsonObject.addProperty("totalPrice", -1)
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(second.toString()) }
        val third = json(); third["items"].asJsonArray[0].asJsonObject.remove("priority")
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(third.toString()) }
    }

    @Test fun migratesLegacyPurchaseDateToPaymentWithoutDuplicatingExistingPayments() {
        val json = historicalJson(); json.remove("formatVersion")
        json["prices"].asJsonArray[0].asJsonObject.addProperty("purchaseDate", 456L)
        val existing = BackupFormat.parse(json.toString())
        assertEquals(data().payments, existing.payments)
        json["payments"].asJsonArray.remove(0)
        val restored = BackupFormat.parse(json.toString())
        assertEquals(456L, restored.payments.single().paidDate)
        assertEquals(456L, restored.payments.single().dueDate)
        assertEquals(1234.56, restored.payments.single().amount, 0.0)
        assertTrue(restored.payments.single().isPaid)
        assertEquals(PurchaseRecordStatus.TRACKED, restored.orderBindings.single().purchaseRecordStatus)
    }

    @Test fun preservesSharedAccessoriesShippingAndPartialInstallments() {
        val original = data().copy(
            items = data().items + data().items.single().copy(id = 9002, name = "配套头饰", parentItemId = 9001, includedInParentPrice = true),
            prices = listOf(data().prices.single().copy(type = PriceType.MULTI_STAGE, shipping = 20.0)),
            paymentStages = listOf(
                PaymentStage(id = 9, priceId = 8, name = "定金", amount = 300.0, dueDate = 105, reminderSet = true, customReminderDays = 3, calendarEventId = 555),
                PaymentStage(id = 10, priceId = 8, name = "尾款与邮费", amount = 934.56, dueDate = null)
            ),
            payments = listOf(data().payments.single().copy(amount = 150.0, isPaid = true, paidDate = 108))
        )
        assertEquals(original, BackupFormat.parse(Gson().toJson(original)))
    }

    @Test fun migratesV2PlansWithoutDiscardingUnpaidRowsOrReminderPointers() {
        val json = historicalJson(2)
        json["payments"].asJsonArray[0].asJsonObject.apply {
            addProperty("calendarEventId", 444); addProperty("reminderSet", true)
        }
        val restored = BackupFormat.parse(json.toString())
        assertEquals(listOf(OrderBinding(itemId = 9001, priceId = 8,
            purchaseRecordStatus = PurchaseRecordStatus.HISTORICAL_OWNED)), restored.orderBindings)
        assertFalse(restored.payments.single().isPaid)
        assertEquals(9L, restored.payments.single().stageId)
        assertEquals(444L, restored.payments.single().calendarEventId)
        assertTrue(restored.paymentStages.single().reminderSet)
        assertNull(restored.paymentStages.single().calendarEventId)
        assertEquals(0.0, restored.prices.single().shipping, 0.0)
        assertFalse(restored.items.single().includedInParentPrice)
    }

    @Test fun preservesAmbiguousHistoricalPricesWithoutPickingOrAddingThem() {
        val json = historicalJson(2)
        val extra = json["prices"].asJsonArray[0].deepCopy().asJsonObject.apply {
            addProperty("id", 88); addProperty("totalPrice", 456.78)
        }
        json["prices"].asJsonArray.add(extra)
        val restored = BackupFormat.parse(json.toString())
        assertEquals(2, restored.prices.size)
        assertEquals(456.78, restored.prices.last().totalPrice, 0.0)
        assertTrue(restored.orderBindings.isEmpty())
        assertEquals(1, restored.paymentStages.size)
        assertEquals(1, restored.payments.size)
    }

    @Test fun rejectsSharedAccessoryDoublePricingAndInvalidParentGraphs() {
        val base = data()
        val child = base.items.single().copy(id = 9002, parentItemId = 9001, includedInParentPrice = true)
        BackupFormat.validate(base.copy(items = base.items + child))
        assertThrows(IllegalArgumentException::class.java) {
            BackupFormat.validate(base.copy(items = base.items + child,
                prices = base.prices + base.prices.single().copy(id = 88, itemId = 9002),
                orderBindings = base.orderBindings + OrderBinding(itemId = 9002, priceId = 88)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupFormat.validate(base.copy(items = listOf(base.items.single().copy(parentItemId = 9002), child)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupFormat.validate(base.copy(items = base.items + child + child.copy(id = 9003, parentItemId = 9002)))
        }
    }

    @Test fun rejectsDuplicateBindingsCrossOrderStagesAndMissingModernFields() {
        val base = data()
        assertThrows(IllegalArgumentException::class.java) {
            BackupFormat.validate(base.copy(orderBindings = base.orderBindings + base.orderBindings))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupFormat.validate(base.copy(prices = base.prices + base.prices.single().copy(id = 88),
                paymentStages = listOf(base.paymentStages.single().copy(priceId = 88))))
        }
        val json = json(); json["items"].asJsonArray[0].asJsonObject.remove("includedInParentPrice")
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
        val shipping = json(); shipping["prices"].asJsonArray[0].asJsonObject.remove("shipping")
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(shipping.toString()) }
        val stages = json(); stages.remove("paymentStages")
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(stages.toString()) }
    }


    @Test fun preservesSignedArchivedAmountsAndRefusesToActivateThem() {
        val archived = data().copy(
            orderBindings = emptyList(),
            prices = listOf(data().prices.single().copy(totalPrice = -500.0, deposit = -100.0, balance = -400.0, shipping = -20.0)),
            payments = listOf(data().payments.single().copy(amount = -50.0, isPaid = true, paidDate = 108)),
            paymentStages = listOf(data().paymentStages.single().copy(amount = -50.0))
        )
        assertEquals(archived, BackupFormat.parse(Gson().toJson(archived)))
        assertThrows(IllegalArgumentException::class.java) {
            BackupFormat.validate(archived.copy(orderBindings = data().orderBindings))
        }
        val old = historicalJson(2)
        old["prices"].asJsonArray[0].asJsonObject.addProperty("totalPrice", -500.0)
        old["payments"].asJsonArray[0].asJsonObject.addProperty("amount", -50.0)
        val migrated = BackupFormat.parse(old.toString())
        assertTrue(migrated.orderBindings.isEmpty())
        assertEquals(-500.0, migrated.prices.single().totalPrice, 0.0)
        assertEquals(-50.0, migrated.payments.single().amount, 0.0)
        assertEquals(-50.0, migrated.paymentStages.single().amount, 0.0)
        val modern = migrated.copy(formatVersion = BackupFormat.VERSION, schemaVersion = BackupFormat.SCHEMA_VERSION)
        assertEquals(modern, BackupFormat.parse(Gson().toJson(modern)))
    }

    @Test fun historicalFractionalCentsRemainExactAndUnbound() {
        val old = historicalJson(2)
        old["prices"].asJsonArray[0].asJsonObject.addProperty("totalPrice",1.005)
        old["payments"].asJsonArray[0].asJsonObject.addProperty("amount",1.005)
        val restored = BackupFormat.parse(old.toString())
        assertTrue(restored.orderBindings.isEmpty())
        assertEquals(1.005,restored.prices.single().totalPrice,0.0)
        assertEquals(1.005,restored.payments.single().amount,0.0)
        val modern = restored.copy(formatVersion=BackupFormat.VERSION,schemaVersion=BackupFormat.SCHEMA_VERSION)
        assertEquals(modern,BackupFormat.parse(Gson().toJson(modern)))
    }

    @Test fun zeroTotalHistoricalOrderIsPreservedWithoutAssumingPaymentOrReceipt() {
        val old = historicalJson(2)
        old["prices"].asJsonArray[0].asJsonObject.addProperty("totalPrice", 0.0)
        old["items"].asJsonArray[0].asJsonObject.addProperty("status", "WISHED")
        val restored = BackupFormat.parse(old.toString())
        assertTrue(restored.orderBindings.isEmpty())
        assertEquals(0.0, restored.prices.single().totalPrice, 0.0)
        assertEquals(ItemStatus.WISHED, restored.items.single().status)
        assertFalse(restored.payments.single().isPaid)
        assertEquals(1, restored.paymentStages.size)
    }


    @Test fun ownedV1AndV2ItemsWithoutALedgerRemainHistoricalPurchasesWithoutInventedPayments() {
        for (version in listOf(1, 2)) {
            for (explicitNull in if (version == 1) listOf(true, false) else listOf(false)) {
                val old = historicalJson(version)
                val price = old["prices"].asJsonArray[0].asJsonObject
                if (explicitNull) price.add("purchaseDate", JsonNull.INSTANCE) else price.remove("purchaseDate")
                old["payments"].asJsonArray.remove(0)
                val restored = BackupFormat.parse(old.toString())
                assertTrue(restored.payments.isEmpty())
                assertTrue(restored.paymentStages.isEmpty())
                assertEquals(ItemStatus.OWNED, restored.items.single().status)
                assertEquals(1234.56, restored.prices.single().totalPrice, 0.0)
                assertEquals(104L, restored.prices.single().createdAt)
                assertEquals(OrderBinding(itemId = 9001, priceId = 8,
                    purchaseRecordStatus = PurchaseRecordStatus.HISTORICAL_OWNED), restored.orderBindings.single())
            }
        }
        // Preserve the existing plan without letting it replace explicit historical ownership.
        val withPayment = historicalJson()
        withPayment["prices"].asJsonArray[0].asJsonObject.add("purchaseDate", JsonNull.INSTANCE)
        val restoredWithPayment = BackupFormat.parse(withPayment.toString())
        assertEquals(data().payments, restoredWithPayment.payments)
        assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED, restoredWithPayment.orderBindings.single().purchaseRecordStatus)
    }

    @Test fun legacyPendingStatusWithoutALedgerNeedsReviewInsteadOfInventingDebtEvidence() {
        for (version in listOf(1, 2)) {
            val old = historicalJson(version)
            old["items"].asJsonArray[0].asJsonObject.addProperty("status", "PENDING_BALANCE")
            old["payments"].asJsonArray.remove(0)
            val restored = BackupFormat.parse(old.toString())
            assertTrue(restored.payments.isEmpty())
            assertTrue(restored.paymentStages.isEmpty())
            assertEquals(PurchaseRecordStatus.NEEDS_REVIEW, restored.orderBindings.single().purchaseRecordStatus)
            assertEquals(data().prices.single().totalPrice, restored.prices.single().totalPrice, 0.0)
        }
    }

    @Test fun v3StatusAlreadyReplacedWithPendingCannotBeGuessedAsHistoricalOwnership() {
        for (status in listOf(ItemStatus.PENDING_PAYMENT, ItemStatus.PENDING_BALANCE)) {
            val old = v3Json()
            old["items"].asJsonArray[0].asJsonObject.addProperty("status", status.name)
            old["payments"].asJsonArray.remove(0)
            old["paymentStages"].asJsonArray.remove(0)
            val restored = BackupFormat.parse(old.toString())
            assertEquals(PurchaseRecordStatus.NEEDS_REVIEW, restored.orderBindings.single().purchaseRecordStatus)
            assertTrue(restored.payments.isEmpty())
            assertTrue(restored.paymentStages.isEmpty())
            assertEquals(status, restored.items.single().status)
        }
    }

    @Test fun v3OwnedWithoutALedgerIsHistoricalButExistingPlansRemainTracked() {
        val old = v3Json()
        old["payments"].asJsonArray.remove(0)
        old["paymentStages"].asJsonArray.remove(0)
        assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED,
            BackupFormat.parse(old.toString()).orderBindings.single().purchaseRecordStatus)
        val planned = v3Json()
        planned["items"].asJsonArray[0].asJsonObject.addProperty("status", ItemStatus.PENDING_BALANCE.name)
        planned["payments"].asJsonArray.remove(0)
        val restored = BackupFormat.parse(planned.toString())
        assertEquals(PurchaseRecordStatus.TRACKED, restored.orderBindings.single().purchaseRecordStatus)
        assertEquals(data().paymentStages, restored.paymentStages)
        assertTrue(restored.payments.isEmpty())
    }

    @Test fun legacyOwnedEvidenceIsPreservedDespiteUnpaidPlansOrIncompleteActualPayments() {
        for (version in listOf(1, 2, 3)) {
            for (actualAmount in listOf<Double?>(null, 100.0)) {
                val old = if (version == 3) v3Json() else historicalJson(version)
                val payment = old["payments"].asJsonArray[0].asJsonObject
                payment.addProperty("isPaid", actualAmount != null)
                if (actualAmount != null) {
                    payment.addProperty("amount", actualAmount)
                    payment.addProperty("paidDate", 456L)
                }
                val originalPayment = payment.deepCopy()
                val originalItem = old["items"].asJsonArray[0].asJsonObject.deepCopy()
                val restored = BackupFormat.parse(old.toString())
                assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED,
                    restored.orderBindings.single().purchaseRecordStatus)
                assertEquals(ItemStatus.OWNED, restored.items.single().status)
                assertEquals(1, restored.payments.size)
                assertEquals(originalPayment["amount"].asDouble, restored.payments.single().amount, 0.0)
                assertEquals(originalPayment["isPaid"].asBoolean, restored.payments.single().isPaid)
                assertEquals(actualAmount?.let { 456L }, restored.payments.single().paidDate)
                assertEquals(originalItem["createdAt"].asLong, restored.items.single().createdAt)
                assertEquals(originalItem["updatedAt"].asLong, restored.items.single().updatedAt)
                assertEquals(1, restored.paymentStages.size)
                val expectedStageAmount = if (version == 3) data().paymentStages.single().amount else restored.payments.single().amount
                assertEquals(expectedStageAmount, restored.paymentStages.single().amount, 0.0)
            }
        }
    }

    @Test fun legacyOwnedWithSufficientActualPaymentIsTrackedWithoutChangingItsLedger() {
        for (version in listOf(1, 2, 3)) {
            val old = if (version == 3) v3Json() else historicalJson(version)
            old["payments"].asJsonArray[0].asJsonObject.apply {
                addProperty("isPaid", true); addProperty("paidDate", 456L)
            }
            val restored = BackupFormat.parse(old.toString())
            assertEquals(PurchaseRecordStatus.TRACKED, restored.orderBindings.single().purchaseRecordStatus)
            assertEquals(1, restored.payments.size)
            assertEquals(1234.56, restored.payments.single().amount, 0.0)
            assertEquals(456L, restored.payments.single().paidDate)
        }
    }

    @Test fun v4RequiresAnExplicitKnownPurchaseRecordStatus() {
        val missing = json()
        missing["orderBindings"].asJsonArray[0].asJsonObject.remove("purchaseRecordStatus")
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(missing.toString()) }
        val nullStatus = json()
        nullStatus["orderBindings"].asJsonArray[0].asJsonObject.add("purchaseRecordStatus", JsonNull.INSTANCE)
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(nullStatus.toString()) }
        val unknown = json()
        unknown["orderBindings"].asJsonArray[0].asJsonObject.addProperty("purchaseRecordStatus", "FUTURE")
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(unknown.toString()) }
    }

    @Test fun modernPurchaseRecordStatusesRoundTripAndCannotBypassMoneyValidation() {
        for (status in PurchaseRecordStatus.entries) {
            val original = data().copy(orderBindings = listOf(data().orderBindings.single().copy(purchaseRecordStatus = status)),
                prices = listOf(data().prices.single().copy(purchaseRecordStatus = status)))
            assertEquals(original, BackupFormat.parse(Gson().toJson(original)))
            assertThrows(IllegalArgumentException::class.java) {
                BackupFormat.validate(original.copy(prices = listOf(original.prices.single().copy(totalPrice = -1.0))))
            }
        }
    }

    @Test fun newlyTrackedUnpaidOrderKeepsItsExplicitStatusWithoutInventingPaymentRecords() {
        val original = data().copy(items = listOf(data().items.single().copy(status = ItemStatus.PENDING_PAYMENT)),
            payments = emptyList(), paymentStages = emptyList())
        val restored = BackupFormat.parse(Gson().toJson(original))
        assertEquals(original, restored)
        assertEquals(PurchaseRecordStatus.TRACKED, restored.orderBindings.single().purchaseRecordStatus)
        assertTrue(restored.payments.isEmpty())
        assertTrue(restored.paymentStages.isEmpty())
    }

    @Test fun wishedLegacyQuoteWithUnknownPurchaseDateDoesNotInventPaymentOrAnOrder() {
        for (explicitNull in listOf(true, false)) {
            val old = historicalJson()
            old["items"].asJsonArray[0].asJsonObject.addProperty("status", "WISHED")
            val price = old["prices"].asJsonArray[0].asJsonObject
            if (explicitNull) price.add("purchaseDate", JsonNull.INSTANCE) else price.remove("purchaseDate")
            old["payments"].asJsonArray.remove(0)
            val restored = BackupFormat.parse(old.toString())
            assertTrue(restored.payments.isEmpty())
            assertTrue(restored.paymentStages.isEmpty())
            assertTrue(restored.orderBindings.isEmpty())
            assertEquals(ItemStatus.WISHED, restored.items.single().status)
            assertEquals(1234.56, restored.prices.single().totalPrice, 0.0)
        }
        // v2 wishlist quotes can have old unpaid plans; those also remain unbound evidence.
        val v2 = historicalJson(2)
        v2["items"].asJsonArray[0].asJsonObject.addProperty("status", "WISHED")
        val restored = BackupFormat.parse(v2.toString())
        assertEquals(1, restored.payments.size)
        assertFalse(restored.payments.single().isPaid)
        assertTrue(restored.orderBindings.isEmpty())
    }

    @Test fun v4MigrationPersistsBoundEvidenceAndDoesNotGuessArchivedEvidence() {
        for (status in PurchaseRecordStatus.entries) {
            val old = v4Json()
            old["orderBindings"].asJsonArray[0].asJsonObject.addProperty("purchaseRecordStatus", status.name)
            old["prices"].asJsonArray.add(old["prices"].asJsonArray[0].deepCopy().asJsonObject.apply { addProperty("id", 18) })
            val restored = BackupFormat.parse(old.toString())
            assertEquals(status, restored.prices.first { it.id == 8L }.purchaseRecordStatus)
            assertEquals(PurchaseRecordStatus.NEEDS_REVIEW, restored.prices.first { it.id == 18L }.purchaseRecordStatus)
            assertEquals(status, restored.orderBindings.single().purchaseRecordStatus)
            assertEquals(data().payments, restored.payments)
        }
    }

    @Test fun archivedEvidenceRoundTripsWithoutACurrentBinding() {
        val original = data().copy(orderBindings = emptyList(),
            prices = listOf(data().prices.single().copy(purchaseRecordStatus = PurchaseRecordStatus.HISTORICAL_OWNED)))
        assertEquals(original, BackupFormat.parse(Gson().toJson(original)))
    }

    @Test fun modernOrderEvidenceCannotBeMissingUnknownOrDisagreeWithBinding() {
        for (invalid in listOf<String?>(null, "FUTURE", "HISTORICAL_OWNED")) {
            val broken = json()
            val price = broken["prices"].asJsonArray[0].asJsonObject
            if (invalid == null) price.remove("purchaseRecordStatus") else price.addProperty("purchaseRecordStatus", invalid)
            assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(broken.toString()) }
        }
        val nullStatus = json()
        nullStatus["prices"].asJsonArray[0].asJsonObject.add("purchaseRecordStatus", JsonNull.INSTANCE)
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(nullStatus.toString()) }
    }

    @Test fun oldSingleSizeChartDefaultsAdditionalImagesAndNewListRoundTrips() {
        val old = json()
        val item = old["items"].asJsonArray[0].asJsonObject
        item.remove("sizeChartImageUrls")
        item.addProperty("sizeChartImageUrl", "/old/chart.jpg")
        val parsed = BackupFormat.parse(old.toString()).items.single()
        assertEquals("/old/chart.jpg", parsed.sizeChartImageUrl)
        assertEquals(emptyList<String>(), parsed.sizeChartImageUrls)
        item.add("sizeChartImageUrls", JsonParser.parseString("[\"/new/1.jpg\",\"/new/2.jpg\"]"))
        val multi = BackupFormat.parse(old.toString()).items.single()
        assertEquals(listOf("/new/1.jpg", "/new/2.jpg"), multi.sizeChartImageUrls)
        item.add("sizeChartImageUrls", JsonNull.INSTANCE)
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(old.toString()) }
    }
}
