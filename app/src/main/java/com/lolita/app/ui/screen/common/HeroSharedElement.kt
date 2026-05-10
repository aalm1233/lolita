package com.lolita.app.ui.screen.common

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lolita.app.ui.navigation.LocalNavAnimatedVisibilityScope
import com.lolita.app.ui.navigation.LocalSharedTransitionScope
import com.lolita.app.ui.theme.LolitaSkin

/**
 * Applies `Modifier.sharedElement()` when all conditions are met:
 * 1. `SharedTransitionScope` is available (via LocalSharedTransitionScope)
 * 2. `AnimatedVisibilityScope` is available (via LocalNavAnimatedVisibilityScope)
 * 3. The current skin's `heroTransitionEnabled` is true
 * 4. The `enabled` parameter is true
 *
 * Otherwise returns the modifier unchanged (graceful fallback — image renders without hero animation).
 *
 * **Modifier ordering rule**: size/aspectRatio → heroSharedElement() → clip
 * Size-defining modifiers must come BEFORE sharedElement so the animation knows the
 * initial/target bounds. Clip must come AFTER sharedElement so it doesn't interfere
 * with the morphing animation.
 *
 * @param key Unique identifier for the shared element pair (e.g., "itemImage-42")
 * @param enabled Additional runtime guard (default true). Use false to conditionally
 *                disable per-call (e.g., for gallery preview dialog images)
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.heroSharedElement(key: String, enabled: Boolean = true): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val skin = LolitaSkin.current

    if (!enabled || !skin.heroTransitionEnabled || sharedTransitionScope == null || animatedVisibilityScope == null) {
        return this
    }

    return with(sharedTransitionScope) {
        this@heroSharedElement.sharedElement(
            rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}
