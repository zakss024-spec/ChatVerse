package com.chatverse.app.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/** Direction-aware transition for the five primary ChatVerse tabs. */
fun AnimatedContentTransitionScope<Int>.tabTransition(): ContentTransform {
    val direction = if (targetState > initialState) 1 else -1
    val enter = slideInHorizontally(
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        initialOffsetX = { fullWidth -> (fullWidth * 0.18f).toInt() * direction }
    ) + fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 50))
    val exit = slideOutHorizontally(
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        targetOffsetX = { fullWidth -> -(fullWidth * 0.12f).toInt() * direction }
    ) + fadeOut(animationSpec = tween(durationMillis = 140))
    return enter togetherWith exit
}
