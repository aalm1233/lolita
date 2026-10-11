package com.lolita.app.ui.screen.item

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lolita.app.data.local.dao.PriceWithPayments
import com.lolita.app.data.local.entity.*
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.screen.price.PurchaseRecordNotice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercise the visible entry points, including routing a shared accessory to its parent's ledger. */
class PurchaseReviewEntryTest {
    @get:Rule val compose = createComposeRule()

    private fun item(status: ItemStatus = ItemStatus.PENDING_REVIEW) = Item(
        id = 10, name = "待核对主裙", brandId = 1, categoryId = 1,
        description = "入口回归", status = status
    )

    @Test fun homeShortcutShowsReviewCountAndOpensTheQueue() {
        var opened = 0
        compose.setContent { LolitaTheme { PurchaseReviewShortcut(3, onClick = { opened++ }) } }
        compose.onNodeWithTag("purchaseReviewShortcut").assertIsDisplayed().assertHasClickAction()
            .assertTextContains("3 件服饰与小物 · 查看清单").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun homeShortcutRemainsAvailableWithAnEmptyQueue() {
        var opened = 0
        compose.setContent { LolitaTheme { PurchaseReviewShortcut(0, onClick = { opened++ }) } }
        compose.onNodeWithTag("purchaseReviewShortcut").assertIsDisplayed().assertHasClickAction()
            .assertTextContains("0 件服饰与小物 · 查看清单").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun currentReviewOrderHasAnExplicitButtonToItsPurchaseConfirmation() {
        val item = item()
        val price = Price(id = 21, itemId = item.id, type = PriceType.FULL, totalPrice = 100.0,
            purchaseRecordStatus = PurchaseRecordStatus.NEEDS_REVIEW)
        val state = ItemEditUiState(pricesWithPayments = listOf(PriceWithPayments(price, emptyList(),
            OrderBinding(item.id, price.id, PurchaseRecordStatus.NEEDS_REVIEW))))
        var priceOpened = 0
        var otherItemOpened = 0
        compose.setContent { LolitaTheme {
            ItemOrderSummary(item, state, onNavigateToPriceManage = { priceOpened++ },
                onNavigateToItem = { otherItemOpened++ })
        } }
        compose.onNodeWithText("核对购买资料").assertIsDisplayed()
        compose.onNodeWithTag("reviewPurchaseStatusButton").performClick()
        compose.runOnIdle { assertEquals(1, priceOpened); assertEquals(0, otherItemOpened) }
    }

    @Test fun sharedAccessoryReviewButtonOpensOnlyTheParentPricePage() {
        val child = item().copy(id = 11, name = "共价头饰", parentItemId = 10, includedInParentPrice = true)
        val targets = mutableListOf<Long>()
        var ownPriceOpened = 0
        var detailOpened = 0
        compose.setContent { LolitaTheme {
            ItemOrderSummary(child, ItemEditUiState(), onNavigateToPriceManage = { ownPriceOpened++ },
                onNavigateToItem = { detailOpened++ }, onNavigateToParentPriceManage = { targets.add(it) })
        } }
        compose.onNodeWithText("核对主件购买资料").assertIsDisplayed()
        compose.onNodeWithTag("reviewPurchaseStatusButton").performClick()
        compose.runOnIdle {
            assertEquals(listOf(10L), targets); assertEquals(0, ownPriceOpened); assertEquals(0, detailOpened)
        }
    }

    @Test fun unboundHistoricalPricesLeadToOrderSelectionBeforeConfirmation() {
        var opened = 0
        compose.setContent { LolitaTheme {
            ItemOrderSummary(item(), ItemEditUiState(legacyPriceCount = 2),
                onNavigateToPriceManage = { opened++ }, onNavigateToItem = {})
        } }
        compose.onNodeWithText("先选择一笔历史订单，再核对购买资料。").assertIsDisplayed()
        compose.onNodeWithTag("reviewPurchaseStatusButton").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun alreadyOwnedCompleteOrderDoesNotShowAPurchaseReviewButton() {
        val item = item(ItemStatus.OWNED)
        val price = Price(id = 21, itemId = item.id, type = PriceType.FULL, totalPrice = 100.0)
        val payment = Payment(id = 31, priceId = price.id, amount = 100.0, dueDate = 1_700_000_000_000,
            paidDate = 1_700_000_000_000, isPaid = true)
        val state = ItemEditUiState(pricesWithPayments = listOf(PriceWithPayments(price, listOf(payment),
            OrderBinding(item.id, price.id, PurchaseRecordStatus.TRACKED))))
        compose.setContent { LolitaTheme {
            ItemOrderSummary(item, state, onNavigateToPriceManage = {}, onNavigateToItem = {})
        } }
        compose.onNodeWithTag("reviewPurchaseStatusButton").assertDoesNotExist()
    }

    @Test fun loadedReviewProjectionOverridesAnOlderPriceSubscription() {
        val item = item(ItemStatus.OWNED)
        val price = Price(id = 21, itemId = item.id, type = PriceType.FULL, totalPrice = 100.0,
            purchaseRecordStatus = PurchaseRecordStatus.HISTORICAL_OWNED)
        val state = ItemEditUiState(
            pricesWithPayments = listOf(PriceWithPayments(price, emptyList(),
                OrderBinding(item.id, price.id, PurchaseRecordStatus.HISTORICAL_OWNED))),
            purchaseReviews = emptyList(), purchaseReviewsLoaded = true
        )
        compose.setContent { LolitaTheme {
            ItemOrderSummary(item, state, onNavigateToPriceManage = {}, onNavigateToItem = {})
        } }
        compose.onNodeWithTag("reviewPurchaseStatusButton").assertDoesNotExist()
    }

    @Test fun historicalOwnershipKeepsThePartialLedgerReviewVisible() {
        val item = item(ItemStatus.OWNED)
        val price = Price(id = 21, itemId = item.id, type = PriceType.FULL, totalPrice = 100.0,
            purchaseRecordStatus = PurchaseRecordStatus.HISTORICAL_OWNED)
        val payment = Payment(id = 31, priceId = price.id, amount = 30.0, dueDate = 1_700_000_000_000,
            paidDate = 1_700_000_000_000, isPaid = true)
        val state = ItemEditUiState(pricesWithPayments = listOf(PriceWithPayments(price, listOf(payment),
            OrderBinding(item.id, price.id, PurchaseRecordStatus.HISTORICAL_OWNED))),
            purchaseReviews = listOf(ItemPurchaseReview(item, item.id, price.id,
                PurchaseRecordStatus.HISTORICAL_OWNED, hasUnknownPaymentDates = false)))
        var opened = 0
        compose.setContent { LolitaTheme {
            ItemOrderSummary(item, state, onNavigateToPriceManage = { opened++ }, onNavigateToItem = {})
        } }
        compose.onNodeWithText("已确认拥有，付款资料待核对").assertIsDisplayed()
        compose.onNodeWithTag("reviewPurchaseStatusButton").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun fullyPaidUnknownDateRemainsVisibleForDataReview() {
        val item = item(ItemStatus.OWNED)
        val price = Price(id = 21, itemId = item.id, type = PriceType.FULL, totalPrice = 100.0)
        val payment = Payment(id = 31, priceId = price.id, amount = 100.0, dueDate = 0L,
            paidDate = null, isPaid = true)
        val state = ItemEditUiState(pricesWithPayments = listOf(PriceWithPayments(price, listOf(payment),
            OrderBinding(item.id, price.id, PurchaseRecordStatus.TRACKED))),
            purchaseReviews = listOf(ItemPurchaseReview(item, item.id, price.id,
                PurchaseRecordStatus.TRACKED, hasUnknownPaymentDates = true)))
        var opened = 0
        compose.setContent { LolitaTheme {
            ItemOrderSummary(item, state, onNavigateToPriceManage = { opened++ }, onNavigateToItem = {})
        } }
        compose.onNodeWithText("实付日期待补").assertIsDisplayed()
        compose.onNodeWithTag("reviewPurchaseStatusButton").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun ownedSharedAccessoryKeepsItsIncompleteParentReviewEntry() {
        val child = item(ItemStatus.OWNED).copy(id = 11, parentItemId = 10, includedInParentPrice = true)
        val state = ItemEditUiState(purchaseReviews = listOf(ItemPurchaseReview(child, 10, 21,
            PurchaseRecordStatus.HISTORICAL_OWNED, hasUnknownPaymentDates = true)))
        val targets = mutableListOf<Long>()
        var childOpened = 0
        compose.setContent { LolitaTheme {
            ItemOrderSummary(child, state, onNavigateToPriceManage = { childOpened++ }, onNavigateToItem = {},
                onNavigateToParentPriceManage = { targets += it })
        } }
        compose.onNodeWithText("核对主件购买资料").assertIsDisplayed()
        compose.onNodeWithTag("reviewPurchaseStatusButton").performClick()
        compose.runOnIdle { assertEquals(listOf(10L), targets); assertEquals(0, childOpened) }
    }

    @Test fun ownedSharedAccessoryIsNotReviewedWhenTheParentProjectionIsComplete() {
        val child = item(ItemStatus.OWNED).copy(id = 11, parentItemId = 10, includedInParentPrice = true)
        compose.setContent { LolitaTheme {
            ItemOrderSummary(child, ItemEditUiState(), onNavigateToPriceManage = {}, onNavigateToItem = {})
        } }
        compose.onNodeWithTag("reviewPurchaseStatusButton").assertDoesNotExist()
    }

    @Test fun ownedItemWithoutAnOrderStillOffersDataReview() {
        val item = item(ItemStatus.OWNED)
        val state = ItemEditUiState(purchaseReviews = listOf(ItemPurchaseReview(item, item.id, null,
            null, hasUnknownPaymentDates = false)))
        var opened = 0
        compose.setContent { LolitaTheme {
            ItemOrderSummary(item, state, onNavigateToPriceManage = { opened++ }, onNavigateToItem = {})
        } }
        compose.onNodeWithText("当前订单未设置，价格与付款资料待补").assertIsDisplayed()
        compose.onNodeWithTag("reviewPurchaseStatusButton").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun historicalNoticeOffersSupplementingPaymentsWithoutClaimingCompleteness() {
        var opened = 0
        compose.setContent { LolitaTheme {
            PurchaseRecordNotice(PurchaseRecordStatus.HISTORICAL_OWNED,
                onSupplementPayment = { opened++ })
        } }
        compose.onNodeWithText("补录真实付款").assertIsDisplayed()
        compose.onNodeWithTag("reviewSupplementPaymentEntry").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
