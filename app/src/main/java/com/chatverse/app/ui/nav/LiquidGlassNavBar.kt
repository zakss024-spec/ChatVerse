package com.chatverse.app.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class NavDestination(val label: String, val icon: ImageVector)

/**
 * Floating true-glass navigation. The active pill is driven by an Animatable so drag
 * updates are applied directly to the graphics layer, then spring-settled on release.
 */
@Composable
fun LiquidGlassNavBar(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    accent: Color,
    backdrop: GlassBackdrop? = null,
    modifier: Modifier = Modifier
) {
    if (destinations.isEmpty()) return

    val count = destinations.size
    val index = selectedIndex.coerceIn(0, count - 1)
    val latestIndex by rememberUpdatedState(index)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val barShape = RoundedCornerShape(32.dp)
    val pillShape = RoundedCornerShape(28.dp)
    var dragging by remember { mutableStateOf(false) }
    val animationScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .shadow(
                elevation = 24.dp,
                shape = barShape,
                ambientColor = Color.Black.copy(alpha = 0.36f),
                spotColor = Color.Black.copy(alpha = 0.48f)
            )
            .clip(barShape)
            .glassSurface(
                shape = barShape,
                backdrop = backdrop,
                blurRadius = 24.dp,
                tint = Color(0xFF18232A).copy(alpha = 0.20f),
                saturation = 1.45f,
                highlight = 1.15f
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.54f), Color.White.copy(alpha = 0.10f))
                ),
                shape = barShape
            )
            .padding(6.dp)
    ) {
        val itemWidth = maxWidth / count
        val itemWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { itemWidth.toPx() }
        val indicator = remember { Animatable(0f) }

        LaunchedEffect(index, itemWidthPx) {
            if (!dragging) {
                indicator.animateTo(
                    targetValue = index * itemWidthPx,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 440f)
                )
            }
        }

        val pillScale by animateFloatAsState(
            targetValue = if (dragging) 1.075f else 1f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = 390f),
            label = "liquidPillScale"
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = indicator.value
                    scaleX = pillScale
                    scaleY = pillScale
                }
                .width(itemWidth)
                .fillMaxHeight()
                .glassSurface(
                    shape = pillShape,
                    backdrop = backdrop,
                    blurRadius = 12.dp,
                    tint = accent.copy(alpha = 0.19f),
                    saturation = 1.55f,
                    highlight = 1.35f
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.24f),
                    shape = pillShape
                )
        )

        Row(
            modifier = Modifier.fillMaxSize().selectableGroup().pointerInput(count, itemWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val maxX = (size.width.toFloat() - itemWidthPx).coerceAtLeast(0f)
                        indicator.snapTo((indicator.value + dragAmount).coerceIn(0f, maxX))
                    },
                    onDragEnd = {
                        val target = (indicator.value / itemWidthPx.coerceAtLeast(1f))
                            .roundToInt().coerceIn(0, count - 1)
                        animationScope.launch {
                            indicator.animateTo(
                                targetValue = target * itemWidthPx,
                                animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f)
                            )
                            dragging = false
                            latestOnSelect(target)
                        }
                    },
                    onDragCancel = {
                        animationScope.launch {
                            indicator.animateTo(
                                targetValue = latestIndex * itemWidthPx,
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
                            )
                            dragging = false
                        }
                    }
                )
            },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEachIndexed { itemIndex, destination ->
                NavBarItem(
                    destination = destination,
                    selected = itemIndex == index,
                    activeProgress = (1f - kotlin.math.abs(indicator.value / itemWidthPx.coerceAtLeast(1f) - itemIndex))
                        .coerceIn(0f, 1f),
                    accent = accent,
                    onClick = { latestOnSelect(itemIndex) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    destination: NavDestination,
    selected: Boolean,
    activeProgress: Float,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "liquidNavItemScale"
    )
    val tint by animateColorAsState(
        targetValue = lerp(
            Color.White.copy(alpha = 0.62f),
            lerp(accent, Color.White, 0.38f),
            activeProgress
        ),
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 520f),
        label = "liquidNavItemTint"
    )

    Column(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = destination.label,
            color = tint,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
