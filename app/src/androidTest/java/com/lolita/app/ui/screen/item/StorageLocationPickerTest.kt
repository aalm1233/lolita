package com.lolita.app.ui.screen.item

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.local.entity.StorageNode
import com.lolita.app.ui.screen.common.StorageLocationPicker
import com.lolita.app.ui.theme.LolitaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StorageLocationPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchSelectsFullPathAndChangingRoomRequiresClearConfirmation() {
        var room by mutableStateOf<Long?>(1L)
        var node by mutableStateOf<Long?>(2L)
        compose.setContent {
            LolitaTheme {
                StorageLocationPicker(room, node,
                    listOf(Location(id = 1, name = "玄关"), Location(id = 2, name = "卧室")),
                    listOf(StorageNode(id = 1, locationId = 1, name = "鞋柜"),
                        StorageNode(id = 2, locationId = 1, parentNodeId = 1, name = "第二层")),
                    onLocationSelected = { r, n -> room = r; node = n })
            }
        }
        compose.onNodeWithText("玄关 → 鞋柜 → 第二层").performClick()
        compose.onNodeWithText("搜索位置路径").performTextInput("卧室")
        compose.onNode(hasText("卧室") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.onNodeWithText("更换房间并清除原层格？").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1L, room); assertEquals(2L, node) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(1L, room); assertEquals(2L, node) }
        compose.onNode(hasText("卧室") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.onNodeWithText("清除并更换").performClick()
        compose.runOnIdle { assertEquals(2L, room); assertNull(node) }
    }
}
