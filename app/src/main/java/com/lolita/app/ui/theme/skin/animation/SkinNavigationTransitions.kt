package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.Alignment

// Sweet: scale + fade with bounce
fun sweetEnterTransition(): EnterTransition =
    fadeIn(tween(350)) + scaleIn(
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        initialScale = 0.85f
    )

fun sweetExitTransition(): ExitTransition =
    fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.9f)

fun sweetPopEnterTransition(): EnterTransition =
    fadeIn(tween(300)) + slideInHorizontally(
        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    ) { -it / 3 }

fun sweetPopExitTransition(): ExitTransition =
    fadeOut(tween(250)) + slideOutHorizontally(tween(300)) { it / 3 }
// Gothic: vertical split / shadow emerge
fun gothicEnterTransition(): EnterTransition =
    fadeIn(tween(400, easing = CubicBezierEasing(0.2f, 0f, 0.1f, 1f))) +
        expandVertically(tween(500), expandFrom = Alignment.CenterVertically)

fun gothicExitTransition(): ExitTransition =
    fadeOut(tween(300)) +
        shrinkVertically(tween(400), shrinkTowards = Alignment.CenterVertically)

fun gothicPopEnterTransition(): EnterTransition =
    fadeIn(tween(500, easing = CubicBezierEasing(0.2f, 0f, 0.1f, 1f))) +
        slideInHorizontally(tween(500)) { -it / 2 }

fun gothicPopExitTransition(): ExitTransition =
    fadeOut(tween(400)) + slideOutHorizontally(tween(400)) { it / 2 }

// Chinese: horizontal slide with ink feel
fun chineseEnterTransition(): EnterTransition =
    fadeIn(tween(400, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))) +
        slideInHorizontally(tween(450, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))) { it / 2 }

fun chineseExitTransition(): ExitTransition =
    fadeOut(tween(350)) + slideOutHorizontally(tween(400)) { -it / 3 }

fun chinesePopEnterTransition(): EnterTransition =
    fadeIn(tween(400)) + slideInHorizontally(tween(450)) { -it / 2 }

fun chinesePopExitTransition(): ExitTransition =
    fadeOut(tween(350)) + slideOutHorizontally(tween(400)) { it / 3 }

// Classic: page-turn feel via horizontal slide
fun classicEnterTransition(): EnterTransition =
    fadeIn(tween(380, easing = LinearOutSlowInEasing)) +
        slideInHorizontally(tween(400, easing = LinearOutSlowInEasing)) { it }

fun classicExitTransition(): ExitTransition =
    fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it }

fun classicPopEnterTransition(): EnterTransition =
    fadeIn(tween(380)) + slideInHorizontally(tween(400)) { -it }

fun classicPopExitTransition(): ExitTransition =
    fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it }

// Navy: horizontal wave-like slide
fun navyEnterTransition(): EnterTransition =
    fadeIn(tween(380, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f))) +
        slideInHorizontally(tween(380, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f))) { it / 3 }

fun navyExitTransition(): ExitTransition =
    fadeOut(tween(280)) + slideOutHorizontally(tween(320)) { -it / 3 }

fun navyPopEnterTransition(): EnterTransition =
    fadeIn(tween(380)) + slideInHorizontally(tween(380)) { -it / 3 }

fun navyPopExitTransition(): ExitTransition =
    fadeOut(tween(280)) + slideOutHorizontally(tween(320)) { it / 3 }
