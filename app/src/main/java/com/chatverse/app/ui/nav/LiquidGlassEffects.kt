package com.chatverse.app.ui.nav

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Captures the content behind glass surfaces so their blur is based on the real UI,
 * rather than a flat translucent fill.
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var coords: LayoutCoordinates? = null
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Apply to the content that should be visible through glass surfaces. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.coords = it }
    .drawWithContent {
        val recordedSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        if (recordedSize.width > 0 && recordedSize.height > 0) {
            backdrop.layer.record(this, layoutDirection, recordedSize) {
                this@drawWithContent.drawContent()
            }
            drawLayer(backdrop.layer)
        } else {
            drawContent()
        }
    }

private class GlassCoordsHolder {
    var coords: LayoutCoordinates? = null
}

/**
 * Frosted-glass surface with real backdrop blur on Android 12+ and a lightweight
 * tinted fallback on Android 8–11.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    blurRadius: Dp = 18.dp,
    tint: Color = Color.White.copy(alpha = 0.10f),
    saturation: Float = 1.35f,
    highlight: Float = 1f
): Modifier {
    val holder = remember { GlassCoordsHolder() }
    val blurLayer = rememberGraphicsLayer()
    val blurPx = with(LocalDensity.current) { blurRadius.toPx() }
    val supportsRenderBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    SideEffect {
        blurLayer.renderEffect =
            if (supportsRenderBlur && blurPx > 0f) BlurEffect(blurPx, blurPx, TileMode.Clamp) else null
        blurLayer.colorFilter = ColorFilter.colorMatrix(
            ColorMatrix().apply { setToSaturation(saturation) }
        )
    }

    return this
        .onGloballyPositioned { holder.coords = it }
        .drawWithContent {
            drawGlassSurface(
                scope = this,
                shape = shape,
                backdrop = backdrop,
                surfaceCoords = holder.coords,
                blurLayer = blurLayer,
                blurPx = blurPx,
                supportsRenderBlur = supportsRenderBlur,
                tint = tint,
                highlight = highlight
            )
            drawContent()
        }
}

private fun drawGlassSurface(
    scope: DrawScope,
    shape: Shape,
    backdrop: GlassBackdrop?,
    surfaceCoords: LayoutCoordinates?,
    blurLayer: GraphicsLayer,
    blurPx: Float,
    supportsRenderBlur: Boolean,
    tint: Color,
    highlight: Float
) = with(scope) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val clip = Path().apply { addOutline(outline) }
    val sourceCoords = backdrop?.coords
    val sourceReady = backdrop != null &&
        sourceCoords != null &&
        sourceCoords.isAttached &&
        surfaceCoords != null &&
        surfaceCoords.isAttached

    clipPath(clip) {
        if (sourceReady) {
            val sourceOffset = sourceCoords!!.localPositionOf(surfaceCoords!!, Offset.Zero)
            if (supportsRenderBlur && blurPx > 0f) {
                val pad = (blurPx * 2f).roundToInt()
                val expandedSize = IntSize(
                    size.width.roundToInt() + pad * 2,
                    size.height.roundToInt() + pad * 2
                )
                blurLayer.record(density = this, layoutDirection = layoutDirection, size = expandedSize) {
                    translate(pad - sourceOffset.x, pad - sourceOffset.y) {
                        drawLayer(backdrop!!.layer)
                    }
                }
                translate(-pad.toFloat(), -pad.toFloat()) { drawLayer(blurLayer) }
                drawRect(tint)
            } else {
                translate(-sourceOffset.x, -sourceOffset.y) { drawLayer(backdrop!!.layer) }
                drawRect(Color(0xFF0B1418).copy(alpha = 0.62f))
                drawRect(tint)
            }
        } else {
            drawRect(Color(0xFF0B1418).copy(alpha = 0.58f))
            drawRect(tint)
        }

        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.18f * highlight),
                0.46f to Color.Transparent
            )
        )
        drawRect(
            Brush.verticalGradient(
                0.56f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.15f)
            )
        )
    }

    drawOutline(
        outline = outline,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = (0.78f * highlight).coerceAtMost(1f)),
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = (0.48f * highlight).coerceAtMost(1f))
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        style = Stroke(width = 1.15.dp.toPx())
    )
}

/** Optional animated ambient background used by screens that want a more vivid glass backdrop. */
@Composable
fun LiquidGlassBackground(
    base: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    animated: Boolean = false
) {
    val progress = if (animated) {
        val transition = rememberInfiniteTransition(label = "glassAmbient")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(14000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glassAmbientProgress"
        ).value
    } else 0f

    Box(
        modifier = modifier
            .background(base)
            .drawBehind {
                val radius = size.minDimension * 0.72f
                val first = Offset(size.width * (0.14f + 0.10f * progress), size.height * 0.14f)
                val second = Offset(size.width * (0.88f - 0.10f * progress), size.height * (0.54f + 0.07f * progress))
                val third = Offset(size.width * 0.32f, size.height * (0.92f - 0.06f * progress))
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent), first, radius), radius, first)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF69CBE8).copy(alpha = 0.20f), Color.Transparent), second, radius), radius, second)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF8B6CFF).copy(alpha = 0.18f), Color.Transparent), third, radius), radius, third)
            }
    )
}

/** Reusable glass button with a soft spring press response. */
@Composable
fun GlassButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 1.045f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f),
        label = "glassButtonScale"
    )
    Box(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .glassSurface(
                shape = RoundedCornerShape(percent = 50),
                blurRadius = 14.dp,
                tint = accent.copy(alpha = if (pressed) 0.30f else 0.18f),
                highlight = if (pressed) 1.25f else 1f
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** Page depth treatment for pager-based screens. */
fun Modifier.liquidPageTransition(pageOffset: () -> Float): Modifier = graphicsLayer {
    val offset = kotlin.math.abs(pageOffset()).coerceIn(0f, 1f)
    alpha = 1f - 0.30f * offset
    val scale = 1f - 0.045f * offset
    scaleX = scale
    scaleY = scale
}

@Composable
fun glassTextFieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color.White.copy(alpha = 0.10f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
    focusedBorderColor = accent.copy(alpha = 0.90f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
    cursorColor = accent,
    focusedLabelColor = accent,
    unfocusedLabelColor = Color.White.copy(alpha = 0.65f)
)
