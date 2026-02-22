package com.lolita.app.ui.theme.skin.animation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

val LocalIsListScrolling = staticCompositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(false)
}
