package com.lolita.app.ui.theme.skin.icon

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Compatibility entry point; navigation now uses the regular skin provider. */
@Composable
fun CountryBottomNavIcon(
    key: IconKey,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    require(key in setOf(IconKey.Home, IconKey.Wishlist, IconKey.Outfit, IconKey.Stats, IconKey.Settings))
    PolishedSkinIcon(key, SkinIconStyle.COUNTRY, modifier, tint)
}
