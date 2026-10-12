package com.lolita.app.ui.theme.skin.animation

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

val LocalIsListScrolling = staticCompositionLocalOf<State<Boolean>> {
    mutableStateOf(false)
}
