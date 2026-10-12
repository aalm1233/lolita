package com.lolita.app.ui.theme.skin.animation

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf

/** Tracks independent scroll containers so a stopped or disposed page cannot clear another. */
@Stable
class SkinScrollActivity {
    private val activeSources = mutableStateMapOf<Any, Unit>()
    val isScrolling: State<Boolean> = derivedStateOf { activeSources.isNotEmpty() }

    internal fun setScrolling(source: Any, scrolling: Boolean) {
        if (scrolling) activeSources[source] = Unit else activeSources.remove(source)
    }

    internal fun removeSource(source: Any) {
        activeSources.remove(source)
    }
}

val LocalSkinScrollActivity = staticCompositionLocalOf<SkinScrollActivity?> { null }

/** Observes only scroll start/end and removes its own registration when leaving composition. */
@Composable
fun ObserveSkinScroll(scrollState: ScrollableState) {
    val activity = LocalSkinScrollActivity.current ?: return
    val source = remember(activity, scrollState) { Any() }
    LaunchedEffect(activity, scrollState, source) {
        try {
            snapshotFlow { scrollState.isScrollInProgress }.collect { scrolling ->
                activity.setScrolling(source, scrolling)
            }
        } finally {
            activity.removeSource(source)
        }
    }
}
