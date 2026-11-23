package com.chatverse.app.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NavDestination(val label: String, val icon: ImageVector)

/** Floating glass-style navigation with a single spring-animated active indicator. */
@Composable
fun LiquidGlassNavBar(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    if (destinations.isEmpty()) return
    val count = destinations.size
    val index = selectedIndex.coerceIn(0, count - 1)
    val barShape = RoundedCornerShape(32.dp)
    val pillShape = RoundedCornerShape(28.dp)
    var dragIndicatorX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .shadow(
                elevation = 22.dp,
                shape = barShape,
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.6f)
            )
            .clip(barShape)
            .background(Color(0xCC0E1A20))
            .background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0DFFFFFF))))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x1FFFFFFF))),
                shape = barShape
            )
            .padding(6.dp)
    ) {
        val itemWidth = maxWidth / count
        val density = androidx.compose.ui.platform.LocalDensity.current
        val itemWidthPx = with(density) { itemWidth.toPx() }
        val indicatorX by animateDpAsState(
            targetValue = itemWidth * index,
            animationSpec = spring(
                dampingRatio = 0.74f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "navIndicatorX"
        )

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = if (isDragging) dragIndicatorX.roundToInt() else with(density) { indicatorX.roundToPx() },
                        y = 0
                    )
                }
                .width(itemWidth)
                .fillMaxHeight()
                .clip(pillShape)
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.14f))
                    )
                )
                .border(1.dp, accent.copy(alpha = 0.55f), pillShape)
        )

        Row(
            modifier = Modifier.fillMaxSize().selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEachIndexed { i, destination ->
                NavBarItem(
                    destination = destination,
                    selected = i == index,
                    accent = accent,
                    onClick = { onSelect(i) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
    .pointerInput(count, index, itemWidthPx) {
        detectHorizontalDragGestures(
            onDragStart = {
                dragIndicatorX = itemWidthPx * index
                isDragging = true
            },
            onHorizontalDrag = { change, dragAmount ->
                val maxX = (size.width.toFloat() - itemWidthPx).coerceAtLeast(0f)
                dragIndicatorX = (dragIndicatorX + dragAmount).coerceIn(0f, maxX)
                change.consume()
            },
            onDragEnd = {
                val target = (dragIndicatorX / itemWidthPx.coerceAtLeast(1f))
                    .roundToInt().coerceIn(0, count - 1)
                isDragging = false
                onSelect(target)
            },
            onDragCancel = {
                isDragging = false
            }
        )
    }
}

@Composable
private fun NavBarItem(
    destination: NavDestination,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "navItemScale"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) lerp(accent, Color.White, 0.35f) else Color.White.copy(alpha = 0.66f),
        label = "navItemTint"
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
