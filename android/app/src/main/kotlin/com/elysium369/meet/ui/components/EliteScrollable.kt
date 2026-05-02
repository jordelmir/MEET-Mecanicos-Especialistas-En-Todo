package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.*
import kotlin.math.abs

/**
 * A modifier that adds high-fidelity hyper-momentum physics to scrollable content.
 * Features: Deep 3D tilt, velocity-sensitive squash/stretch, and chromatic ghosting.
 */
@Composable
fun Modifier.eliteScrollPhysics(): Modifier {
    var velocity by remember { mutableStateOf(0f) }
    val animatedVelocity by animateFloatAsState(
        targetValue = velocity,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "scrollVelocity"
    )

    // Ghosting offset for chromatic effect
    val ghostOffset by animateFloatAsState(
        targetValue = abs(velocity) * 6f,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "ghostOffset"
    )

    // Reset velocity when not moving
    LaunchedEffect(velocity) {
        if (velocity != 0f) {
            kotlinx.coroutines.delay(150)
            velocity = 0f
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Amplify velocity for more visual impact
                velocity = (available.y / 25f).coerceIn(-30f, 30f)
                return Offset.Zero
            }
        }
    }

    return this
        .nestedScroll(nestedScrollConnection)
        .graphicsLayer {
            // 1. Hyper-Momentum 3D Tilt
            rotationX = -animatedVelocity * 1.5f
            rotationY = animatedVelocity * 0.3f // Subtle side wobble
            
            // 2. Volume-Preserving Squash/Stretch
            scaleY = 1f - (abs(animatedVelocity) * 0.01f)
            scaleX = 1f + (abs(animatedVelocity) * 0.005f)
            
            // 3. Kinetic Translation
            translationY = animatedVelocity * 8f
            
            // 4. Transform Origin
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            
            // 5. Dynamic Alpha Dip (Feels faster)
            alpha = 1f - (abs(animatedVelocity) * 0.003f).coerceIn(0f, 0.2f)
        }
        .drawWithContent {
            // Draw a subtle "Chromatic Ghost" during fast movement
            if (abs(velocity) > 8f) {
                drawContent()
                // Cyan Ghost Trail
                with(drawContext.canvas) {
                    save()
                    translate(0f, ghostOffset)
                    drawContent()
                    restore()
                }
            } else {
                drawContent()
            }
        }
}

/**
 * A modifier that adds a professional, high-velocity neon-bloom styled scrollbar.
 * Features: Adaptive width, velocity-sensitive glow, comet trails, and energy sparks.
 */
@Composable
fun Modifier.eliteScrollbar(
    state: LazyListState,
    color: Color = Color(0xFF39FF14),
    width: Dp = 6.dp
): Modifier {
    val isScrolling = state.isScrollInProgress
    
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0.4f,
        animationSpec = tween(durationMillis = 500),
        label = "thumbAlpha"
    )
    
    val adaptiveWidth by animateDpAsState(
        targetValue = if (isScrolling) width * 2.5f else width,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "adaptiveWidth"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "elitePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val sparkOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkOffset"
    )

    return this
        .eliteScrollPhysics()
        .then(
        drawWithContent {
            drawContent()
            
            val layoutInfo = state.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            
            if (visibleItemsInfo.isNotEmpty()) {
                val totalItems = layoutInfo.totalItemsCount
                val firstVisibleItem = visibleItemsInfo.first()
                val visibleItemsCount = visibleItemsInfo.size
                
                val scrollFraction = if (totalItems > 0) firstVisibleItem.index.toFloat() / totalItems else 0f
                val visibleFraction = if (totalItems > 0) visibleItemsCount.toFloat() / totalItems else 1f
                
                val scrollbarHeight = (visibleFraction * size.height).coerceAtLeast(90.dp.toPx())
                val scrollbarOffsetY = scrollFraction * size.height

                drawEliteScrollbar(
                    this, 
                    color, 
                    adaptiveWidth, 
                    scrollbarOffsetY, 
                    scrollbarHeight, 
                    thumbAlpha * pulseAlpha,
                    sparkOffset,
                    isScrolling
                )
            }
        }
    )
}

@Composable
fun Modifier.eliteScrollbar(
    state: LazyGridState,
    color: Color = Color(0xFF39FF14),
    width: Dp = 4.dp
): Modifier {
    val isScrolling = state.isScrollInProgress
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0.4f,
        animationSpec = tween(durationMillis = 500),
        label = "thumbAlpha"
    )
    
    val adaptiveWidth by animateDpAsState(
        targetValue = if (isScrolling) width * 2.5f else width,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "adaptiveWidth"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "gridPulse")
    val sparkOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkOffset"
    )

    return this
        .eliteScrollPhysics()
        .then(
        drawWithContent {
            drawContent()
            
            val layoutInfo = state.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            
            if (visibleItemsInfo.isNotEmpty()) {
                val totalItems = layoutInfo.totalItemsCount
                val firstVisibleItem = visibleItemsInfo.first()
                val visibleItemsCount = visibleItemsInfo.size
                
                val scrollFraction = if (totalItems > 0) firstVisibleItem.index.toFloat() / totalItems else 0f
                val visibleFraction = if (totalItems > 0) visibleItemsCount.toFloat() / totalItems else 1f
                
                val scrollbarHeight = (visibleFraction * size.height).coerceAtLeast(90.dp.toPx())
                val scrollbarOffsetY = scrollFraction * size.height

                drawEliteScrollbar(
                    this, 
                    color, 
                    adaptiveWidth, 
                    scrollbarOffsetY, 
                    scrollbarHeight, 
                    thumbAlpha,
                    sparkOffset,
                    isScrolling
                )
            }
        }
    )
}

@Composable
fun Modifier.eliteScrollbar(
    state: ScrollState,
    color: Color = Color(0xFF39FF14),
    width: Dp = 4.dp
): Modifier {
    val isScrolling = state.isScrollInProgress
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0.4f,
        animationSpec = tween(durationMillis = 500),
        label = "thumbAlpha"
    )
    
    val adaptiveWidth by animateDpAsState(
        targetValue = if (isScrolling) width * 2.5f else width,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "adaptiveWidth"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "columnPulse")
    val sparkOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkOffset"
    )

    return this
        .eliteScrollPhysics()
        .then(
        drawWithContent {
            drawContent()
            
            if (state.maxValue > 0) {
                val totalHeight = state.maxValue + size.height
                val scrollFraction = state.value.toFloat() / totalHeight
                val visibleFraction = size.height / totalHeight
                
                val scrollbarHeight = (visibleFraction * size.height).coerceAtLeast(90.dp.toPx())
                val scrollbarOffsetY = scrollFraction * size.height

                drawEliteScrollbar(
                    this, 
                    color, 
                    adaptiveWidth, 
                    scrollbarOffsetY, 
                    scrollbarHeight, 
                    thumbAlpha,
                    sparkOffset,
                    isScrolling
                )
            }
        }
    )
}

private fun drawEliteScrollbar(
    drawScope: DrawScope,
    color: Color,
    width: Dp,
    offsetY: Float,
    height: Float,
    alpha: Float,
    sparkOffset: Float,
    isMoving: Boolean
) {
    with(drawScope) {
        val thickness = width.toPx()
        val xOffset = size.width - thickness - 8.dp.toPx()
        
        // 1. Futuristic Glass Track
        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            topLeft = Offset(size.width - 10.dp.toPx(), 0f),
            size = Size(4.dp.toPx(), size.height),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
        
        // 2. Comet Trail (Only when moving)
        if (isMoving) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        color.copy(alpha = 0.4f * alpha),
                        color.copy(alpha = 0.1f * alpha),
                        Color.Transparent
                    ),
                    startY = offsetY - 30.dp.toPx(),
                    endY = offsetY + height + 30.dp.toPx()
                ),
                topLeft = Offset(xOffset - thickness, offsetY - 15.dp.toPx()),
                size = Size(thickness * 3, height + 30.dp.toPx()),
                cornerRadius = CornerRadius(thickness * 1.5f)
            )
        }
        
        // 3. Layered Neon Bloom
        drawRoundRect(
            color = color.copy(alpha = 0.08f * alpha),
            topLeft = Offset(xOffset - thickness * 5, offsetY),
            size = Size(thickness * 11, height),
            cornerRadius = CornerRadius(thickness * 5.5f)
        )
        
        drawRoundRect(
            color = color.copy(alpha = 0.2f * alpha),
            topLeft = Offset(xOffset - thickness * 2, offsetY),
            size = Size(thickness * 5, height),
            cornerRadius = CornerRadius(thickness * 2.5f)
        )
        
        // 4. Primary Thumb
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(color, Color.White, color)
            ),
            topLeft = Offset(xOffset, offsetY),
            size = Size(thickness, height),
            cornerRadius = CornerRadius(thickness / 2),
            alpha = alpha
        )
        
        // 5. Energy Core
        drawRoundRect(
            color = Color.White.copy(alpha = 0.95f * alpha),
            topLeft = Offset(xOffset + thickness * 0.4f, offsetY + 20.dp.toPx()),
            size = Size(thickness * 0.2f, height - 40.dp.toPx()),
            cornerRadius = CornerRadius(thickness * 0.1f)
        )

        // 6. Kinetic Sparks
        if (isMoving) {
            val sparkY = offsetY + (sparkOffset % height)
            drawCircle(
                color = Color.White.copy(alpha = 0.9f * alpha),
                radius = 2.5.dp.toPx(),
                center = Offset(xOffset + thickness / 2, sparkY)
            )
            drawCircle(
                color = color.copy(alpha = 0.5f * alpha),
                radius = 6.dp.toPx(),
                center = Offset(xOffset + thickness / 2, sparkY)
            )
        }
    }
}

/**
 * A wrapper component that provides high-fidelity fading edges with Holographic Scanlines.
 */
@Composable
fun EliteScrollContainer(
    modifier: Modifier = Modifier,
    fadeHeight: Dp = 140.dp,
    fadeColor: Color = Color(0xFF0A0E1A),
    accentColor: Color = Color(0xFF39FF14),
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hologram")
    val scanlineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanline"
    )

    Box(modifier = modifier.background(fadeColor)) {
        content()
        
        // Holographic Scanlines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = scanlineY * size.height
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        accentColor.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    startY = y - 30.dp.toPx(),
                    endY = y + 30.dp.toPx()
                ),
                size = Size(size.width, 60.dp.toPx()),
                topLeft = Offset(0f, y - 30.dp.toPx())
            )
        }

        // Top Fading Edge — subtle, never covers content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0.0f to fadeColor.copy(alpha = 0.6f),
                        0.5f to fadeColor.copy(alpha = 0.15f),
                        1.0f to Color.Transparent
                    )
                )
        )
        
        // Bottom Fading Edge — subtle, never covers content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.5f to fadeColor.copy(alpha = 0.15f),
                        1.0f to fadeColor.copy(alpha = 0.6f)
                    )
                )
        )
    }
}
