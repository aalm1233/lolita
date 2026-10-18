package com.lolita.app.ui.navigation

import com.lolita.app.domain.model.WardrobeFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemEditRouteTest {
    @Test fun cabinetCreationCarriesStableRoomNodeAndFamilyIdentities() {
        assertEquals("item_edit/0?defaultLocationId=3&defaultStorageNodeId=9&defaultFamily=FOOTWEAR",
            Screen.ItemEdit.createRoute(defaultLocationId = 3, defaultStorageNodeId = 9, defaultFamily = WardrobeFamily.FOOTWEAR))
        assertEquals("item_edit/0?defaultFamily=FOOTWEAR", Screen.ItemEdit.createRoute(defaultFamily = WardrobeFamily.FOOTWEAR))
        assertEquals("item_edit/42", Screen.ItemEdit.createRoute(itemId = 42))
    }
}
