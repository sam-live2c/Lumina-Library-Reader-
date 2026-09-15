package com.example.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ReaderThemeMode
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

enum class PageFlipStyle {
    REALISTIC_CURL,
    BOOK_3D_FLIP,
    SMOOTH_SLIDE
}

enum class PageVerticalPosition {
    TOP,      // Elevated (Up) - Natural top-biased reading position with comfortable clearance
    CENTER,   // Centered - Symmetrical optical center
    BOTTOM    // Lower (Down) - Lowered position, closer to reader's hands and thumbs
}

enum class FlipDirection {
    FORWARD,
    BACKWARD,
    NONE
}

@Composable
fun PhysicalBookReaderCanvas(
    currentPageBitmap: Bitmap?,
    nextPageBitmap: Bitmap?,
    previousPageBitmap: Bitmap?,
    currentPageIndex: Int,
    totalPages: Int,
    isBookmarked: Boolean,
    readerTheme: ReaderThemeMode,
    flipStyle: PageFlipStyle = PageFlipStyle.REALISTIC_CURL,
    isSmartMarginFit: Boolean = false,
    pageVerticalPosition: PageVerticalPosition = PageVerticalPosition.CENTER,
    isAnnotationMode: Boolean = false,
    activeTool: com.example.data.model.AnnotationType = com.example.data.model.AnnotationType.PEN,
    isEraserActive: Boolean = false,
    activeColor: Color = Color(0xFFFFD54F),
    strokeWidth: Float = 4f,
    isAnnotationsVisible: Boolean = true,
    currentPageStrokes: List<com.example.data.repository.AnnotationStroke> = emptyList(),
    onStrokeCompleted: (com.example.data.repository.AnnotationStroke) -> Unit = {},
    onEraseStroke: (Long) -> Unit = {},
    onStrokesUpdated: (List<com.example.data.repository.AnnotationStroke>) -> Unit = {},
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onToggleHud: () -> Unit,
    onDoubleTap: () -> Unit = {},
    onZoomChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Drag page-turn progress: 0f = resting, positive = turning forward, negative = turning backward
    val animProgress = remember { Animatable(0f) }
    var activeDirection by remember { mutableStateOf(FlipDirection.NONE) }

    // Smooth pinch-to-zoom and pan state
    val zoomScale = remember { Animatable(1f) }
    val panOffsetX = remember { Animatable(0f) }
    val panOffsetY = remember { Animatable(0f) }

    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }

    val isZoomed = zoomScale.value > 1.05f

    // Timed Zoom Indicator State (appears when zooming, auto-dismisses after 2.5s)
    var isZoomIndicatorVisible by remember { mutableStateOf(false) }
    var zoomInteractionTimestamp by remember { mutableLongStateOf(0L) }
    var containerWidth by remember { mutableFloatStateOf(1080f) }
    var containerHeight by remember { mutableFloatStateOf(1920f) }
    var touchFractionY by remember { mutableFloatStateOf(0.75f) }

    LaunchedEffect(zoomInteractionTimestamp, zoomScale.value) {
        if (zoomScale.value > 1.05f) {
            isZoomIndicatorVisible = true
            delay(2500)
            isZoomIndicatorVisible = false
        } else {
            isZoomIndicatorVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(readerTheme.background)
            .onSizeChanged {
                if (it.width > 0) containerWidth = it.width.toFloat()
                if (it.height > 0) containerHeight = it.height.toFloat()
            }
            // Unified High-Performance Gesture Pipeline
            .pointerInput(currentPageIndex, totalPages, isAnnotationMode) {
                if (isAnnotationMode) {
                    // In annotation mode: never turn pages or intercept touch from AnnotationCanvas!
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isMultiTouch = false
                    var isDraggingPage = false
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var lastPosition = down.position
                    val downTime = System.currentTimeMillis()
                    val downPos = down.position
                    if (size.height > 0) {
                        touchFractionY = (downPos.y / size.height.toFloat()).coerceIn(0.05f, 0.95f)
                    }

                    do {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.filter { it.pressed }
                        val pointerCount = pressedPointers.size

                        if (pointerCount >= 2) {
                            // Multi-touch Zoom & Pan
                            isMultiTouch = true
                            isDraggingPage = false
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            coroutineScope.launch {
                                val newScale = (zoomScale.value * zoomChange).coerceIn(1f, 4f)
                                zoomScale.snapTo(newScale)
                                zoomInteractionTimestamp = System.currentTimeMillis()
                                onZoomChanged()
                                if (newScale > 1.05f) {
                                    val maxPanX = (size.width * (newScale - 1f)) / 2f
                                    val maxPanY = (size.height * (newScale - 1f)) / 2f
                                    panOffsetX.snapTo((panOffsetX.value + panChange.x * newScale).coerceIn(-maxPanX, maxPanX))
                                    panOffsetY.snapTo((panOffsetY.value + panChange.y * newScale).coerceIn(-maxPanY, maxPanY))
                                } else {
                                    panOffsetX.snapTo(0f)
                                    panOffsetY.snapTo(0f)
                                }
                            }
                            event.changes.forEach { it.consume() }
                        } else if (pointerCount == 1 && !isMultiTouch) {
                            val change = pressedPointers.first()
                            val dragDelta = change.position - lastPosition
                            lastPosition = change.position
                            totalDragX += dragDelta.x
                            totalDragY += dragDelta.y
                            if (size.height > 0) {
                                touchFractionY = (change.position.y / size.height.toFloat()).coerceIn(0.05f, 0.95f)
                            }

                            if (zoomScale.value > 1.05f) {
                                // Strictly pan around zoomed page without flipping pages during zoom
                                val maxPanX = (size.width * (zoomScale.value - 1f)) / 2f
                                val maxPanY = (size.height * (zoomScale.value - 1f)) / 2f
                                val currentPanX = panOffsetX.value
                                val currentPanY = panOffsetY.value

                                coroutineScope.launch {
                                    panOffsetX.snapTo((currentPanX + dragDelta.x).coerceIn(-maxPanX, maxPanX))
                                    panOffsetY.snapTo((currentPanY + dragDelta.y).coerceIn(-maxPanY, maxPanY))
                                }
                                change.consume()
                            } else {
                                // Direct, high-precision physical page pick & drag
                                if (!isDraggingPage) {
                                    val absX = abs(totalDragX)
                                    val absY = abs(totalDragY)
                                    if (absX > 6f && absX > absY * 0.5f) {
                                        isDraggingPage = true
                                        if (totalDragX < 0f && currentPageIndex < totalPages) {
                                            activeDirection = FlipDirection.FORWARD
                                        } else if (totalDragX > 0f && currentPageIndex > 1) {
                                            activeDirection = FlipDirection.BACKWARD
                                        }
                                    }
                                }

                                if (isDraggingPage) {
                                    change.consume()
                                    val dragWidth = (size.width * 0.85f).coerceAtLeast(100f)

                                    if (activeDirection == FlipDirection.NONE) {
                                        if (totalDragX < -6f && currentPageIndex < totalPages) {
                                            activeDirection = FlipDirection.FORWARD
                                        } else if (totalDragX > 6f && currentPageIndex > 1) {
                                            activeDirection = FlipDirection.BACKWARD
                                        }
                                    }

                                    if (activeDirection == FlipDirection.FORWARD) {
                                        val next = (-totalDragX / dragWidth).coerceIn(0f, 1f)
                                        coroutineScope.launch { animProgress.snapTo(next) }
                                    } else if (activeDirection == FlipDirection.BACKWARD) {
                                        val next = (-totalDragX / dragWidth).coerceIn(-1f, 0f)
                                        coroutineScope.launch { animProgress.snapTo(next) }
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    val upTime = System.currentTimeMillis()
                    val duration = upTime - downTime

                    if (isDraggingPage && activeDirection != FlipDirection.NONE) {
                        // Natural release physics: threshold + fling velocity
                        val progress = animProgress.value
                        val isFling = duration < 380 && abs(totalDragX) > 30f
                        coroutineScope.launch {
                            if (activeDirection == FlipDirection.FORWARD) {
                                if ((progress > 0.18f || (isFling && totalDragX < -30f)) && currentPageIndex < totalPages) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    animProgress.animateTo(1f, tween(230, easing = FastOutSlowInEasing))
                                    onNextPage()
                                    panOffsetX.snapTo(0f)
                                    panOffsetY.snapTo(0f)
                                } else {
                                    animProgress.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                                }
                            } else if (activeDirection == FlipDirection.BACKWARD) {
                                if ((progress < -0.18f || (isFling && totalDragX > 30f)) && currentPageIndex > 1) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    animProgress.animateTo(-1f, tween(230, easing = FastOutSlowInEasing))
                                    onPreviousPage()
                                    panOffsetX.snapTo(0f)
                                    panOffsetY.snapTo(0f)
                                } else {
                                    animProgress.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                            animProgress.snapTo(0f)
                            activeDirection = FlipDirection.NONE
                        }
                    } else if (!isDraggingPage && !isMultiTouch && duration < 450 && abs(totalDragX) < 24f && abs(totalDragY) < 24f) {
                        val now = System.currentTimeMillis()
                        val timeSinceLastTap = now - lastTapTimestamp
                        val distFromLastTap = (downPos - lastTapPosition).getDistance()

                        if (timeSinceLastTap < 350L && distFromLastTap < 60f) {
                            // Double Tap detected
                            lastTapTimestamp = 0L
                            lastTapPosition = Offset.Zero

                            // Cancel any pending page curl
                            coroutineScope.launch {
                                animProgress.snapTo(0f)
                                activeDirection = FlipDirection.NONE
                            }

                            val isCurrentlyZoomed = zoomScale.value > 1.05f || abs(panOffsetX.value) > 1f || abs(panOffsetY.value) > 1f
                            if (isCurrentlyZoomed) {
                                // Smoothly restore back to default unzoomed state in parallel without layout shift
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                coroutineScope.launch {
                                    launch { zoomScale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) }
                                    launch { panOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) }
                                    launch { panOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) }
                                }
                            } else {
                                // Smoothly zoom in centered at double tap location
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val targetScale = 2.2f
                                val maxPanX = (size.width * (targetScale - 1f)) / 2f
                                val maxPanY = (size.height * (targetScale - 1f)) / 2f
                                val targetPanX = ((size.width / 2f) - downPos.x) * (targetScale - 1f)
                                val targetPanY = ((size.height / 2f) - downPos.y) * (targetScale - 1f)
                                coroutineScope.launch {
                                    launch { zoomScale.animateTo(targetScale, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) }
                                    launch { panOffsetX.animateTo(targetPanX.coerceIn(-maxPanX, maxPanX), spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) }
                                    launch { panOffsetY.animateTo(targetPanY.coerceIn(-maxPanY, maxPanY), spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) }
                                }
                            }
                            // Reappear floating pen if dismissed
                            onDoubleTap()
                        } else {
                            lastTapTimestamp = now
                            lastTapPosition = downPos

                            // Tap cleanly toggles HUD controls (No accidental edge-tap page turn)
                            onToggleHud()
                        }
                    }
                }
            }
    ) {
        val progress = animProgress.value

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale.value
                    scaleY = zoomScale.value
                    translationX = panOffsetX.value
                    translationY = panOffsetY.value
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                when {
                    // RESTING STATE: Authentic physical book paper rendering
                    progress == 0f || activeDirection == FlipDirection.NONE -> {
                        val activeBmp = currentPageBitmap ?: nextPageBitmap ?: previousPageBitmap
                        val metrics = activeBmp?.let { bmp ->
                            drawPageBitmap(bmp, w, h, isSmartMarginFit, pageVerticalPosition)
                        } ?: calculatePageLayoutMetrics(w, h, w, h, isSmartMarginFit, pageVerticalPosition)
                        drawBookSpineGutter(metrics, readerTheme)
                        drawBookEdgeShadow(metrics, readerTheme)
                    }

                    // FLIPPING FORWARD
                    activeDirection == FlipDirection.FORWARD -> {
                        val p = progress.coerceIn(0f, 1f)
                        when (flipStyle) {
                            PageFlipStyle.REALISTIC_CURL -> {
                                drawRealisticPageCurlForward(
                                    currentBitmap = currentPageBitmap,
                                    nextBitmap = nextPageBitmap,
                                    progress = p,
                                    touchFractionY = touchFractionY,
                                    width = w,
                                    height = h,
                                    theme = readerTheme,
                                    isSmartMarginFit = isSmartMarginFit,
                                    pageVerticalPosition = pageVerticalPosition
                                )
                            }
                            PageFlipStyle.BOOK_3D_FLIP -> {
                                draw3DPageFlipForward(
                                    currentBitmap = currentPageBitmap,
                                    nextBitmap = nextPageBitmap,
                                    progress = p,
                                    width = w,
                                    height = h,
                                    theme = readerTheme,
                                    isSmartMarginFit = isSmartMarginFit,
                                    pageVerticalPosition = pageVerticalPosition
                                )
                            }
                            PageFlipStyle.SMOOTH_SLIDE -> {
                                drawSlideForward(
                                    currentBitmap = currentPageBitmap,
                                    nextBitmap = nextPageBitmap,
                                    progress = p,
                                    width = w,
                                    height = h,
                                    theme = readerTheme,
                                    isSmartMarginFit = isSmartMarginFit,
                                    pageVerticalPosition = pageVerticalPosition
                                )
                            }
                        }
                    }

                    // FLIPPING BACKWARD
                    activeDirection == FlipDirection.BACKWARD -> {
                        val p = (-progress).coerceIn(0f, 1f)
                        when (flipStyle) {
                            PageFlipStyle.REALISTIC_CURL -> {
                                drawRealisticPageCurlBackward(
                                    currentBitmap = currentPageBitmap,
                                    previousBitmap = previousPageBitmap,
                                    progress = p,
                                    touchFractionY = touchFractionY,
                                    width = w,
                                    height = h,
                                    theme = readerTheme,
                                    isSmartMarginFit = isSmartMarginFit,
                                    pageVerticalPosition = pageVerticalPosition
                                )
                            }
                            PageFlipStyle.BOOK_3D_FLIP -> {
                                draw3DPageFlipBackward(
                                    currentBitmap = currentPageBitmap,
                                    previousBitmap = previousPageBitmap,
                                    progress = p,
                                    width = w,
                                    height = h,
                                    theme = readerTheme,
                                    isSmartMarginFit = isSmartMarginFit,
                                    pageVerticalPosition = pageVerticalPosition
                                )
                            }
                            PageFlipStyle.SMOOTH_SLIDE -> {
                                drawSlideBackward(
                                    currentBitmap = currentPageBitmap,
                                    previousBitmap = previousPageBitmap,
                                    progress = p,
                                    width = w,
                                    height = h,
                                    theme = readerTheme,
                                    isSmartMarginFit = isSmartMarginFit,
                                    pageVerticalPosition = pageVerticalPosition
                                )
                            }
                        }
                    }
                }

                // Ribbon Bookmark Indicator directly anchored to physical page edge
                if (isBookmarked) {
                    val activeBmp = currentPageBitmap ?: nextPageBitmap ?: previousPageBitmap
                    val metrics = activeBmp?.let { bmp ->
                        calculatePageLayoutMetrics(bmp.width.toFloat(), bmp.height.toFloat(), w, h, isSmartMarginFit, pageVerticalPosition)
                    } ?: calculatePageLayoutMetrics(w, h, w, h, isSmartMarginFit, pageVerticalPosition)
                    drawBookmarkRibbon(metrics, readerTheme)
                }
            }

            // Annotation Canvas Layer locked inside the zoomed/panned coordinate space
            if (isAnnotationsVisible) {
                AnnotationCanvas(
                    strokes = currentPageStrokes,
                    isAnnotationMode = isAnnotationMode,
                    activeTool = activeTool,
                    isEraserActive = isEraserActive,
                    activeColor = activeColor,
                    strokeWidth = strokeWidth,
                    isAnnotationsVisible = isAnnotationsVisible,
                    onStrokeCompleted = onStrokeCompleted,
                    onEraseStroke = onEraseStroke,
                    onStrokesUpdated = onStrokesUpdated,
                    zoomScale = zoomScale.value,
                    onTransform = { panChange, zoomChange ->
                        coroutineScope.launch {
                            val newZoom = (zoomScale.value * zoomChange).coerceIn(1f, 3.5f)
                            val maxPanX = (containerWidth * (newZoom - 1f)) / 2f
                            val maxPanY = (containerHeight * (newZoom - 1f)) / 2f
                            val newPanX = if (newZoom <= 1.02f) 0f else (panOffsetX.value + panChange.x).coerceIn(-maxPanX, maxPanX)
                            val newPanY = if (newZoom <= 1.02f) 0f else (panOffsetY.value + panChange.y).coerceIn(-maxPanY, maxPanY)
                            zoomScale.snapTo(newZoom)
                            panOffsetX.snapTo(newPanX)
                            panOffsetY.snapTo(newPanY)
                            zoomInteractionTimestamp = System.currentTimeMillis()
                            onZoomChanged()
                        }
                    },
                    onDoubleTap = {
                        val isCurrentlyZoomed = zoomScale.value > 1.05f || abs(panOffsetX.value) > 1f || abs(panOffsetY.value) > 1f
                        coroutineScope.launch {
                            if (isCurrentlyZoomed) {
                                launch { zoomScale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) }
                                launch { panOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) }
                                launch { panOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) }
                            } else {
                                launch { zoomScale.animateTo(2.2f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) }
                            }
                            zoomInteractionTimestamp = System.currentTimeMillis()
                            onZoomChanged()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Floating Zoom Level Pill with One-Tap Reset (Auto-dismisses after 2.5s)
        AnimatedVisibility(
            visible = isZoomIndicatorVisible && isZoomed,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (readerTheme.isDark) Color(0xDD1E1E1E) else Color(0xEEFFFFFF),
                shadowElevation = 6.dp,
                modifier = Modifier.clickable {
                    coroutineScope.launch {
                        isZoomIndicatorVisible = false
                        launch { zoomScale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) }
                        launch { panOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) }
                        launch { panOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) }
                    }
                }
            ) {
                Text(
                    text = "${(zoomScale.value * 100).toInt()}% · Reset",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = readerTheme.textColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// REALISTIC 3D PAGE CURL & FOLD RENDERING ENGINE
// -------------------------------------------------------------

private fun DrawScope.drawRealisticPageCurlForward(
    currentBitmap: Bitmap?,
    nextBitmap: Bitmap?,
    progress: Float,
    touchFractionY: Float = 0.75f,
    width: Float,
    height: Float,
    theme: ReaderThemeMode,
    isSmartMarginFit: Boolean = true,
    pageVerticalPosition: PageVerticalPosition = PageVerticalPosition.CENTER
) {
    val sampleBmp = currentBitmap ?: nextBitmap
    val metrics = sampleBmp?.let { bmp ->
        calculatePageLayoutMetrics(bmp.width.toFloat(), bmp.height.toFloat(), width, height, isSmartMarginFit, pageVerticalPosition)
    } ?: calculatePageLayoutMetrics(width, height, width, height, isSmartMarginFit, pageVerticalPosition)

    val pLeft = metrics.left
    val pRight = metrics.left + metrics.width
    val pTop = metrics.top
    val pBottom = metrics.top + metrics.height
    val pW = metrics.width
    val pH = metrics.height
    val p = progress.coerceIn(0f, 1f)

    val paperColor = if (theme.isDark) {
        Color(0xFF1E242E)
    } else {
        when (theme) {
            ReaderThemeMode.CREAM -> Color(0xFFF9F5EC)
            ReaderThemeMode.SEPIA -> Color(0xFFF2EADC)
            else -> Color(0xFFFCFAF7)
        }
    }

    val versoPaperColor = if (theme.isDark) {
        Color(0xFF222834)
    } else {
        when (theme) {
            ReaderThemeMode.CREAM -> Color(0xFFF7F3E8)
            ReaderThemeMode.SEPIA -> Color(0xFFEFE6D6)
            else -> Color(0xFFFBF9F5)
        }
    }

    // 1. UNDERNEATH BASE LAYER (Blank book paper sheet + Next Page)
    // Always draw an opaque physical paper sheet behind the turning page
    drawRect(
        color = Color(0x15000000),
        topLeft = Offset(pLeft + 2f, pTop + 4f),
        size = Size(pW, pH)
    )
    drawRect(
        color = paperColor,
        topLeft = Offset(pLeft, pTop),
        size = Size(pW, pH)
    )

    if (nextBitmap != null) {
        drawPageBitmap(nextBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
    }
    drawBookSpineGutter(metrics, theme)
    drawBookEdgeShadow(metrics, theme)

    // 2. CORNER-PICKING & FOLD GEOMETRY
    val yFrac = touchFractionY.coerceIn(0.05f, 0.95f)
    val isBottomCorner = yFrac >= 0.5f
    val cornerPullBias = ((yFrac - 0.5f) * 2f).coerceIn(-1f, 1f) // +1 for bottom corner, -1 for top corner

    // Dynamic Fold Crease line (where paper bends)
    val tiltSpread = (sin(p * PI) * pW * 0.16f * cornerPullBias).toFloat()
    val foldXTop = (pRight - (p * pW * 1.08f) - tiltSpread).coerceIn(pLeft, pRight)
    val foldXBottom = (pRight - (p * pW * 1.08f) + tiltSpread).coerceIn(pLeft, pRight)

    // Paper flexural bowing inwards towards the spine
    val bowDisplacement = (sin(p * PI) * pW * 0.08f * (1f - 0.25f * abs(cornerPullBias))).toFloat()
    val foldXMid = ((foldXTop + foldXBottom) / 2f - bowDisplacement).coerceIn(pLeft, pRight)

    // Smooth Bézier Control Points for organic curved fold
    val c1x = foldXTop + (foldXMid - foldXTop) * 0.60f
    val c1y = pTop + pH * 0.32f
    val c2x = foldXBottom + (foldXMid - foldXBottom) * 0.60f
    val c2y = pBottom - pH * 0.32f

    // 3. CONTROLLED DROP SHADOW (Diffused on next revealed page)
    if (p in 0.01f..0.99f) {
        val maxShadowAlpha = (sin(p * PI) * (if (theme.isDark) 0.22f else 0.15f)).toFloat().coerceIn(0f, 0.22f)
        val shadowSpread = min(pW * 0.24f * sin(p * PI).toFloat() + 16f, 52f)

        if (maxShadowAlpha > 0.01f) {
            val shadowPath = Path().apply {
                moveTo(foldXTop, pTop + 2f)
                cubicTo(c1x, c1y, c2x, c2y, foldXBottom, pBottom - 2f)
                quadraticBezierTo(
                    (foldXBottom + shadowSpread * 0.85f).coerceAtMost(pRight), pBottom - 1f,
                    (foldXBottom + shadowSpread * 0.70f).coerceAtMost(pRight), pBottom - 12f
                )
                cubicTo(
                    (c2x + shadowSpread).coerceAtMost(pRight), c2y,
                    (c1x + shadowSpread).coerceAtMost(pRight), c1y,
                    (foldXTop + shadowSpread * 0.70f).coerceAtMost(pRight), pTop + 12f
                )
                quadraticBezierTo(
                    (foldXTop + shadowSpread * 0.85f).coerceAtMost(pRight), pTop + 1f,
                    foldXTop, pTop + 2f
                )
                close()
            }

            val startShadowX = minOf(foldXTop, foldXBottom, foldXMid)
            val endShadowX = (maxOf(foldXTop, foldXBottom, foldXMid) + shadowSpread).coerceAtMost(pRight)
            if (endShadowX > startShadowX) {
                drawPath(
                    path = shadowPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = maxShadowAlpha),
                            Color.Black.copy(alpha = maxShadowAlpha * 0.35f),
                            Color.Black.copy(alpha = maxShadowAlpha * 0.08f),
                            Color.Transparent
                        ),
                        startX = startShadowX,
                        endX = endShadowX
                    )
                )
            }
        }
    }

    // 4. FLAT UNCURLED PAGE (Current Page Recto on Spine Side)
    if (p < 0.999f) {
        val flatPagePath = Path().apply {
            moveTo(pLeft, pTop)
            lineTo(foldXTop, pTop)
            cubicTo(c1x, c1y, c2x, c2y, foldXBottom, pBottom)
            lineTo(pLeft, pBottom)
            close()
        }

        clipRect(left = pLeft, top = pTop, right = pRight, bottom = pBottom) {
            clipPath(flatPagePath) {
                // Solid paper under current page
                drawRect(
                    color = paperColor,
                    topLeft = Offset(pLeft, pTop),
                    size = Size(pW, pH)
                )

                if (currentBitmap != null) {
                    drawPageBitmap(currentBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
                }

                // Ambient crease shading approaching the fold
                val ambientWidth = min(pW * 0.14f, 36f)
                val minCreaseX = minOf(foldXTop, foldXBottom, foldXMid)
                val ambientStart = (minCreaseX - ambientWidth).coerceAtLeast(pLeft)
                if (minCreaseX > ambientStart) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.04f),
                                Color.Black.copy(alpha = 0.11f)
                            ),
                            startX = ambientStart,
                            endX = minCreaseX
                        ),
                        topLeft = Offset(ambientStart, pTop),
                        size = Size(minCreaseX - ambientStart, pH)
                    )
                }
            }
        }
    }

    // 5. THE GENEROUS, 100% SOLID OPAQUE VERSO LEAF (The Turned Paper Flap)
    if (p in 0.01f..0.999f) {
        val cornerLiftProgress = sin(p * PI).toFloat()
        val cornerLiftY = pH * (0.35f * cornerLiftProgress + 0.15f * p) * (0.50f + abs(cornerPullBias) * 0.50f)
        val flapReach = (pW * p * 2.0f).coerceIn(0f, pW * 1.15f)
        val apexX = (pRight - flapReach).coerceIn(pLeft - 20f, pRight)
        val apexY = if (isBottomCorner) {
            (pBottom - cornerLiftY).coerceIn(pTop + pH * 0.08f, pBottom)
        } else {
            (pTop + cornerLiftY).coerceIn(pTop, pBottom - pH * 0.08f)
        }

        val flapOppositeX = (pRight - flapReach * 0.85f).coerceIn(pLeft - 20f, pRight)
        val flapOppositeY = if (isBottomCorner) pTop else pBottom

        val flapPath = Path().apply {
            moveTo(foldXTop, pTop)
            cubicTo(c1x, c1y, c2x, c2y, foldXBottom, pBottom)
            if (isBottomCorner) {
                lineTo(apexX, apexY)
                quadraticBezierTo(
                    (apexX + flapOppositeX) / 2f, (apexY + flapOppositeY) / 2f,
                    flapOppositeX, flapOppositeY
                )
                lineTo(foldXTop, pTop)
            } else {
                lineTo(apexX, apexY)
                quadraticBezierTo(
                    (apexX + flapOppositeX) / 2f, (apexY + flapOppositeY) / 2f,
                    flapOppositeX, flapOppositeY
                )
                lineTo(foldXBottom, pBottom)
            }
            close()
        }

        // 100% Solid Opaque Blank Verso Paper Leaf (Never Transparent)
        drawPath(path = flapPath, color = versoPaperColor)

        // Realistic cylinder highlight and inner crease shading
        val minFlapX = minOf(foldXTop, foldXBottom, foldXMid, apexX, flapOppositeX)
        val maxFlapX = maxOf(foldXTop, foldXBottom, foldXMid, apexX, flapOppositeX)
        val highlightColor = if (theme.isDark) Color(0x1CFFFFFF) else Color(0x38FFFFFF)
        val creaseShadowColor = Color.Black.copy(alpha = if (theme.isDark) 0.16f else 0.10f)

        if (maxFlapX > minFlapX) {
            drawPath(
                path = flapPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.06f),
                        Color.Transparent,
                        highlightColor,
                        Color.Transparent,
                        creaseShadowColor
                    ),
                    startX = minFlapX,
                    endX = maxFlapX
                )
            )
        }

        // Physical paper thickness edge contour
        drawPath(
            path = flapPath,
            color = Color(0x20000000),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
        )

        // Contact shadow under the curled flap onto the left side page
        val leftShadowWidth = min(pW * 0.12f, 32f)
        if (minFlapX > pLeft) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.07f)
                    ),
                    startX = (minFlapX - leftShadowWidth).coerceAtLeast(pLeft),
                    endX = minFlapX
                ),
                topLeft = Offset((minFlapX - leftShadowWidth).coerceAtLeast(pLeft), pTop),
                size = Size(minFlapX - (minFlapX - leftShadowWidth).coerceAtLeast(pLeft), pH)
            )
        }
    }

    // 6. PERMANENT BOOK BINDING SPINES & EDGES
    drawBookSpineGutter(metrics, theme)
    drawBookEdgeShadow(metrics, theme)
}

private fun DrawScope.drawRealisticPageCurlBackward(
    currentBitmap: Bitmap?,
    previousBitmap: Bitmap?,
    progress: Float,
    touchFractionY: Float = 0.75f,
    width: Float,
    height: Float,
    theme: ReaderThemeMode,
    isSmartMarginFit: Boolean = true,
    pageVerticalPosition: PageVerticalPosition = PageVerticalPosition.CENTER
) {
    val sampleBmp = currentBitmap ?: previousBitmap
    val metrics = sampleBmp?.let { bmp ->
        calculatePageLayoutMetrics(bmp.width.toFloat(), bmp.height.toFloat(), width, height, isSmartMarginFit, pageVerticalPosition)
    } ?: calculatePageLayoutMetrics(width, height, width, height, isSmartMarginFit, pageVerticalPosition)

    val pLeft = metrics.left
    val pRight = metrics.left + metrics.width
    val pTop = metrics.top
    val pBottom = metrics.top + metrics.height
    val pW = metrics.width
    val pH = metrics.height
    val p = progress.coerceIn(0f, 1f)

    val paperColor = if (theme.isDark) {
        Color(0xFF1E242E)
    } else {
        when (theme) {
            ReaderThemeMode.CREAM -> Color(0xFFF9F5EC)
            ReaderThemeMode.SEPIA -> Color(0xFFF2EADC)
            else -> Color(0xFFFCFAF7)
        }
    }

    val versoPaperColor = if (theme.isDark) {
        Color(0xFF222834)
    } else {
        when (theme) {
            ReaderThemeMode.CREAM -> Color(0xFFF7F3E8)
            ReaderThemeMode.SEPIA -> Color(0xFFEFE6D6)
            else -> Color(0xFFFBF9F5)
        }
    }

    // 1. UNDERNEATH BASE LAYER (Blank book paper sheet + Current Page)
    drawRect(
        color = Color(0x15000000),
        topLeft = Offset(pLeft + 2f, pTop + 4f),
        size = Size(pW, pH)
    )
    drawRect(
        color = paperColor,
        topLeft = Offset(pLeft, pTop),
        size = Size(pW, pH)
    )

    if (currentBitmap != null) {
        drawPageBitmap(currentBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
    }
    drawBookSpineGutter(metrics, theme)
    drawBookEdgeShadow(metrics, theme)

    // 2. BACKWARD CORNER-PICKING & FOLD GEOMETRY (Unrolling from spine to right)
    val yFrac = touchFractionY.coerceIn(0.05f, 0.95f)
    val isBottomCorner = yFrac >= 0.5f
    val cornerPullBias = ((yFrac - 0.5f) * 2f).coerceIn(-1f, 1f)

    // Dynamic Fold Crease line unrolling from left
    val tiltSpread = (sin(p * PI) * pW * 0.16f * cornerPullBias).toFloat()
    val foldXTop = (pLeft + (p * pW * 1.08f) - tiltSpread).coerceIn(pLeft, pRight)
    val foldXBottom = (pLeft + (p * pW * 1.08f) + tiltSpread).coerceIn(pLeft, pRight)

    // Paper flexural bowing
    val bowDisplacement = (sin(p * PI) * pW * 0.08f * (1f - 0.25f * abs(cornerPullBias))).toFloat()
    val foldXMid = ((foldXTop + foldXBottom) / 2f + bowDisplacement).coerceIn(pLeft, pRight)

    val c1x = foldXTop + (foldXMid - foldXTop) * 0.60f
    val c1y = pTop + pH * 0.32f
    val c2x = foldXBottom + (foldXMid - foldXBottom) * 0.60f
    val c2y = pBottom - pH * 0.32f

    // 3. PREVIOUS PAGE BEING UNROLLED FROM LEFT (Flat portion on spine side)
    if (p > 0.001f) {
        val prevLeafPath = Path().apply {
            moveTo(pLeft, pTop)
            lineTo(foldXTop, pTop)
            cubicTo(c1x, c1y, c2x, c2y, foldXBottom, pBottom)
            lineTo(pLeft, pBottom)
            close()
        }

        clipRect(left = pLeft, top = pTop, right = pRight, bottom = pBottom) {
            clipPath(prevLeafPath) {
                drawRect(
                    color = paperColor,
                    topLeft = Offset(pLeft, pTop),
                    size = Size(pW, pH)
                )

                if (previousBitmap != null) {
                    drawPageBitmap(previousBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
                }

                // Ambient crease shading
                val ambientWidth = min(pW * 0.14f, 36f)
                val minCreaseX = minOf(foldXTop, foldXBottom, foldXMid)
                val ambientStart = (minCreaseX - ambientWidth).coerceAtLeast(pLeft)
                if (minCreaseX > ambientStart) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.04f),
                                Color.Black.copy(alpha = 0.11f)
                            ),
                            startX = ambientStart,
                            endX = minCreaseX
                        ),
                        topLeft = Offset(ambientStart, pTop),
                        size = Size(minCreaseX - ambientStart, pH)
                    )
                }
            }
        }
    }

    // 4. CONTROLLED DROP SHADOW (Diffused on current page)
    if (p in 0.01f..0.99f) {
        val maxShadowAlpha = (sin(p * PI) * (if (theme.isDark) 0.22f else 0.15f)).toFloat().coerceIn(0f, 0.22f)
        val shadowSpread = min(pW * 0.24f * sin(p * PI).toFloat() + 16f, 52f)

        if (maxShadowAlpha > 0.01f) {
            val shadowPath = Path().apply {
                moveTo(foldXTop, pTop + 2f)
                cubicTo(c1x, c1y, c2x, c2y, foldXBottom, pBottom - 2f)
                quadraticBezierTo(
                    (foldXBottom + shadowSpread * 0.85f).coerceAtMost(pRight), pBottom - 1f,
                    (foldXBottom + shadowSpread * 0.70f).coerceAtMost(pRight), pBottom - 12f
                )
                cubicTo(
                    (c2x + shadowSpread).coerceAtMost(pRight), c2y,
                    (c1x + shadowSpread).coerceAtMost(pRight), c1y,
                    (foldXTop + shadowSpread * 0.70f).coerceAtMost(pRight), pTop + 12f
                )
                quadraticBezierTo(
                    (foldXTop + shadowSpread * 0.85f).coerceAtMost(pRight), pTop + 1f,
                    foldXTop, pTop + 2f
                )
                close()
            }

            val startShadowX = minOf(foldXTop, foldXBottom, foldXMid)
            val endShadowX = (maxOf(foldXTop, foldXBottom, foldXMid) + shadowSpread).coerceAtMost(pRight)
            if (endShadowX > startShadowX) {
                drawPath(
                    path = shadowPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = maxShadowAlpha),
                            Color.Black.copy(alpha = maxShadowAlpha * 0.35f),
                            Color.Black.copy(alpha = maxShadowAlpha * 0.08f),
                            Color.Transparent
                        ),
                        startX = startShadowX,
                        endX = endShadowX
                    )
                )
            }
        }
    }

    // 5. THE GENEROUS, 100% SOLID OPAQUE UNROLLING VERSO FLAP (Top Layer)
    if (p in 0.01f..0.999f) {
        val cornerLiftProgress = sin(p * PI).toFloat()
        val cornerLiftY = pH * (0.35f * cornerLiftProgress + 0.15f * p) * (0.50f + abs(cornerPullBias) * 0.50f)
        val flapReach = (pW * p * 2.0f).coerceIn(0f, pW * 1.15f)
        val apexX = (pLeft + flapReach).coerceIn(pLeft, pRight + 20f)
        val apexY = if (isBottomCorner) {
            (pBottom - cornerLiftY).coerceIn(pTop + pH * 0.08f, pBottom)
        } else {
            (pTop + cornerLiftY).coerceIn(pTop, pBottom - pH * 0.08f)
        }

        val flapOppositeX = (pLeft + flapReach * 0.85f).coerceIn(pLeft, pRight + 20f)
        val flapOppositeY = if (isBottomCorner) pTop else pBottom

        val flapPath = Path().apply {
            moveTo(foldXTop, pTop)
            cubicTo(c1x, c1y, c2x, c2y, foldXBottom, pBottom)
            if (isBottomCorner) {
                lineTo(apexX, apexY)
                quadraticBezierTo(
                    (apexX + flapOppositeX) / 2f, (apexY + flapOppositeY) / 2f,
                    flapOppositeX, flapOppositeY
                )
                lineTo(foldXTop, pTop)
            } else {
                lineTo(apexX, apexY)
                quadraticBezierTo(
                    (apexX + flapOppositeX) / 2f, (apexY + flapOppositeY) / 2f,
                    flapOppositeX, flapOppositeY
                )
                lineTo(foldXBottom, pBottom)
            }
            close()
        }

        // 100% Solid Opaque Blank Paper
        drawPath(path = flapPath, color = versoPaperColor)

        // Realistic cylinder highlight and crease shadow
        val minFlapX = minOf(foldXTop, foldXBottom, foldXMid, apexX, flapOppositeX)
        val maxFlapX = maxOf(foldXTop, foldXBottom, foldXMid, apexX, flapOppositeX)
        val highlightColor = if (theme.isDark) Color(0x1CFFFFFF) else Color(0x38FFFFFF)
        val creaseShadowColor = Color.Black.copy(alpha = if (theme.isDark) 0.16f else 0.10f)

        if (maxFlapX > minFlapX) {
            drawPath(
                path = flapPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        creaseShadowColor,
                        Color.Transparent,
                        highlightColor,
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.06f)
                    ),
                    startX = minFlapX,
                    endX = maxFlapX
                )
            )
        }

        // Edge contour stroke
        drawPath(
            path = flapPath,
            color = Color(0x20000000),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
        )
    }

    drawBookSpineGutter(metrics, theme)
    drawBookEdgeShadow(metrics, theme)
}

// -------------------------------------------------------------
// 3D APPLE-STYLE BOOK PAGE FLIP WITH AMBIENT GROUND SHADOWS
// -------------------------------------------------------------

private fun DrawScope.draw3DPageFlipForward(
    currentBitmap: Bitmap?,
    nextBitmap: Bitmap?,
    progress: Float,
    width: Float,
    height: Float,
    theme: ReaderThemeMode,
    isSmartMarginFit: Boolean = true,
    pageVerticalPosition: PageVerticalPosition = PageVerticalPosition.CENTER
) {
    val sampleBmp = currentBitmap ?: nextBitmap
    val metrics = sampleBmp?.let { bmp ->
        calculatePageLayoutMetrics(bmp.width.toFloat(), bmp.height.toFloat(), width, height, isSmartMarginFit, pageVerticalPosition)
    } ?: calculatePageLayoutMetrics(width, height, width, height, isSmartMarginFit, pageVerticalPosition)

    val pLeft = metrics.left
    val pW = metrics.width
    val pH = metrics.height
    val pTop = metrics.top
    val p = progress.coerceIn(0f, 1f)

    // 1. Underneath base page
    if (nextBitmap != null) {
        drawPageBitmap(nextBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
    } else {
        drawRect(
            color = if (theme.isDark) Color(0xFF1B202A) else Color(0xFFFAF8F5),
            topLeft = Offset(pLeft, pTop),
            size = Size(pW, pH)
        )
    }

    // 2. Dynamic Lifting Ground Shadow (Diffuses as page lifts higher into the air towards 90deg)
    val shadowSpread = (sin(p * PI) * pW * 0.35f + 14f).toFloat()
    val shadowAlpha = (sin(p * PI) * (if (theme.isDark) 0.22f else 0.14f)).toFloat()
    if (shadowAlpha > 0.01f) {
        val shadowX = pLeft + pW * (1f - p)
        val shadowStart = (shadowX - shadowSpread * 0.3f).coerceAtLeast(pLeft)
        val shadowEnd = (shadowX + shadowSpread * 0.7f).coerceAtMost(pLeft + pW)
        if (shadowEnd > shadowStart) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = shadowAlpha),
                        Color.Black.copy(alpha = shadowAlpha * 0.35f),
                        Color.Transparent
                    ),
                    startX = shadowStart,
                    endX = shadowEnd
                ),
                topLeft = Offset(shadowStart, pTop),
                size = Size(shadowEnd - shadowStart, pH)
            )
        }
    }

    // 3. 3D Turning Page Leaf with Perspective Camber
    if (p < 0.5f) {
        // Front face (Current Page turning toward 90deg)
        val scaleX = cos(p * Math.PI).toFloat().coerceIn(0f, 1f)
        val camberScaleY = 1f - (sin(p * Math.PI) * 0.04f).toFloat()
        val shadingAlpha = (p * 0.40f).coerceIn(0f, 0.18f)

        withTransform({
            scale(scaleX = scaleX, scaleY = camberScaleY, pivot = Offset(pLeft, pTop + pH / 2f))
        }) {
            if (currentBitmap != null) {
                drawPageBitmap(currentBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
            } else {
                drawRect(
                    color = if (theme.isDark) Color(0xFF1E242E) else Color(0xFFFAF7F2),
                    topLeft = Offset(pLeft, pTop),
                    size = Size(pW, pH)
                )
            }

            // Dynamic Lambertian Surface Shading
            drawRect(
                color = Color.Black.copy(alpha = shadingAlpha),
                topLeft = Offset(pLeft, pTop),
                size = Size(pW, pH)
            )

            // Page Spine Crease Shadow on turning leaf
            drawBookSpineGutter(metrics, theme)
        }
    } else {
        // Back face (Reverse Page leaf settling from 90deg to 180deg)
        val backProgress = 1f - p
        val scaleX = cos(backProgress * Math.PI).toFloat().coerceIn(0f, 1f)
        val camberScaleY = 1f - (sin(backProgress * Math.PI) * 0.04f).toFloat()
        val shadingAlpha = (backProgress * 0.40f).coerceIn(0f, 0.18f)

        withTransform({
            scale(scaleX = scaleX, scaleY = camberScaleY, pivot = Offset(pLeft, pTop + pH / 2f))
        }) {
            if (nextBitmap != null) {
                drawPageBitmap(nextBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
            } else {
                drawRect(
                    color = if (theme.isDark) Color(0xFF1E242E) else Color(0xFFFAF7F2),
                    topLeft = Offset(pLeft, pTop),
                    size = Size(pW, pH)
                )
            }

            // Shading as it settles down
            drawRect(
                color = Color.Black.copy(alpha = shadingAlpha),
                topLeft = Offset(pLeft, pTop),
                size = Size(pW, pH)
            )

            drawBookSpineGutter(metrics, theme)
        }
    }

    drawBookSpineGutter(metrics, theme)
    drawBookEdgeShadow(metrics, theme)
}

private fun DrawScope.draw3DPageFlipBackward(
    currentBitmap: Bitmap?,
    previousBitmap: Bitmap?,
    progress: Float,
    width: Float,
    height: Float,
    theme: ReaderThemeMode,
    isSmartMarginFit: Boolean = true,
    pageVerticalPosition: PageVerticalPosition = PageVerticalPosition.CENTER
) {
    val sampleBmp = currentBitmap ?: previousBitmap
    val metrics = sampleBmp?.let { bmp ->
        calculatePageLayoutMetrics(bmp.width.toFloat(), bmp.height.toFloat(), width, height, isSmartMarginFit, pageVerticalPosition)
    } ?: calculatePageLayoutMetrics(width, height, width, height, isSmartMarginFit, pageVerticalPosition)

    val pLeft = metrics.left
    val pW = metrics.width
    val pH = metrics.height
    val pTop = metrics.top
    val p = progress.coerceIn(0f, 1f)

    // 1. Base current page
    if (currentBitmap != null) {
        drawPageBitmap(currentBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
    } else {
        drawRect(
            color = if (theme.isDark) Color(0xFF1B202A) else Color(0xFFFAF8F5),
            topLeft = Offset(pLeft, pTop),
            size = Size(pW, pH)
        )
    }

    // 2. Dynamic Ground Shadow
    val shadowSpread = (sin(p * PI) * pW * 0.35f + 14f).toFloat()
    val shadowAlpha = (sin(p * PI) * (if (theme.isDark) 0.22f else 0.14f)).toFloat()
    if (shadowAlpha > 0.01f) {
        val shadowX = pLeft + pW * p
        val shadowStart = (shadowX - shadowSpread * 0.7f).coerceAtLeast(pLeft)
        val shadowEnd = (shadowX + shadowSpread * 0.3f).coerceAtMost(pLeft + pW)
        if (shadowEnd > shadowStart) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = shadowAlpha * 0.35f),
                        Color.Black.copy(alpha = shadowAlpha),
                        Color.Transparent
                    ),
                    startX = shadowStart,
                    endX = shadowEnd
                ),
                topLeft = Offset(shadowStart, pTop),
                size = Size(shadowEnd - shadowStart, pH)
            )
        }
    }

    // 3. 3D Turning Page Leaf
    if (p < 0.5f) {
        val scaleX = cos(p * Math.PI).toFloat().coerceIn(0f, 1f)
        val camberScaleY = 1f - (sin(p * Math.PI) * 0.04f).toFloat()
        val shadingAlpha = (p * 0.40f).coerceIn(0f, 0.18f)

        withTransform({
            scale(scaleX = scaleX, scaleY = camberScaleY, pivot = Offset(pLeft + pW, pTop + pH / 2f))
        }) {
            if (currentBitmap != null) {
                drawPageBitmap(currentBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
            } else {
                drawRect(
                    color = if (theme.isDark) Color(0xFF1E242E) else Color(0xFFFAF7F2),
                    topLeft = Offset(pLeft, pTop),
                    size = Size(pW, pH)
                )
            }

            drawRect(
                color = Color.Black.copy(alpha = shadingAlpha),
                topLeft = Offset(pLeft, pTop),
                size = Size(pW, pH)
            )

            drawBookSpineGutter(metrics, theme)
        }
    } else {
        val backProgress = 1f - p
        val scaleX = cos(backProgress * Math.PI).toFloat().coerceIn(0f, 1f)
        val camberScaleY = 1f - (sin(backProgress * Math.PI) * 0.04f).toFloat()
        val shadingAlpha = (backProgress * 0.40f).coerceIn(0f, 0.18f)

        withTransform({
            scale(scaleX = scaleX, scaleY = camberScaleY, pivot = Offset(pLeft + pW, pTop + pH / 2f))
        }) {
            if (previousBitmap != null) {
                drawPageBitmap(previousBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
            } else {
                drawRect(
                    color = if (theme.isDark) Color(0xFF1E242E) else Color(0xFFFAF7F2),
                    topLeft = Offset(pLeft, pTop),
                    size = Size(pW, pH)
                )
            }

            drawRect(
                color = Color.Black.copy(alpha = shadingAlpha),
                topLeft = Offset(pLeft, pTop),
                size = Size(pW, pH)
            )

            drawBookSpineGutter(metrics, theme)
        }
    }

    drawBookSpineGutter(metrics, theme)
    drawBookEdgeShadow(metrics, theme)
}

// -------------------------------------------------------------
// SMOOTH HORIZONTAL SLIDE WITH HIGH-PRECISION DROP SHADOW
// -------------------------------------------------------------

private fun DrawScope.drawSlideForward(
    currentBitmap: Bitmap?,
    nextBitmap: Bitmap?,
    progress: Float,
    width: Float,
    height: Float,
    theme: ReaderThemeMode,
    isSmartMarginFit: Boolean = true,
    pageVerticalPosition: PageVerticalPosition = PageVerticalPosition.CENTER
) {
    val sampleBmp = currentBitmap ?: nextBitmap
    val metrics = sampleBmp?.let { bmp ->
        calculatePageLayoutMetrics(bmp.width.toFloat(), bmp.height.toFloat(), width, height, isSmartMarginFit, pageVerticalPosition)
    } ?: calculatePageLayoutMetrics(width, height, width, height, isSmartMarginFit, pageVerticalPosition)

    val pLeft = metrics.left
    val pW = metrics.width
    val pH = metrics.height
    val pTop = metrics.top
    val p = progress.coerceIn(0f, 1f)
    val offsetX = -pW * p

    withTransform({ translate(left = offsetX, top = 0f) }) {
        currentBitmap?.let { drawPageBitmap(it, width, height, isSmartMarginFit, pageVerticalPosition) }
    }

    withTransform({ translate(left = offsetX + pW, top = 0f) }) {
        nextBitmap?.let { drawPageBitmap(it, width, height, isSmartMarginFit, pageVerticalPosition) }
    }

    // High-precision blurred drop shadow on sliding page edge
    val shadowX = pLeft + offsetX + pW
    val shadowWidth = min(48f, pW * 0.12f)
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0x55000000),
                Color(0x32000000),
                Color(0x18000000),
                Color(0x06000000),
                Color.Transparent
            ),
            startX = shadowX,
            endX = shadowX + shadowWidth
        ),
        topLeft = Offset(shadowX, pTop),
        size = Size(shadowWidth, pH)
    )

    // Crisp paper thickness edge highlight line
    drawRect(
        color = Color(0x30000000),
        topLeft = Offset(shadowX - 1f, pTop),
        size = Size(1.2f, pH)
    )
}

private fun DrawScope.drawSlideBackward(
    currentBitmap: Bitmap?,
    previousBitmap: Bitmap?,
    progress: Float,
    width: Float,
    height: Float,
    theme: ReaderThemeMode,
    isSmartMarginFit: Boolean = true,
    pageVerticalPosition: PageVerticalPosition = PageVerticalPosition.CENTER
) {
    val sampleBmp = currentBitmap ?: previousBitmap
    val metrics = sampleBmp?.let { bmp ->
        calculatePageLayoutMetrics(bmp.width.toFloat(), bmp.height.toFloat(), width, height, isSmartMarginFit, pageVerticalPosition)
    } ?: calculatePageLayoutMetrics(width, height, width, height, isSmartMarginFit, pageVerticalPosition)

    val pLeft = metrics.left
    val pW = metrics.width
    val pH = metrics.height
    val pTop = metrics.top
    val p = progress.coerceIn(0f, 1f)
    val offsetX = pW * p

    withTransform({ translate(left = offsetX - pW, top = 0f) }) {
        previousBitmap?.let { drawPageBitmap(it, width, height, isSmartMarginFit, pageVerticalPosition) }
    }

    withTransform({ translate(left = offsetX, top = 0f) }) {
        currentBitmap?.let { drawPageBitmap(it, width, height, isSmartMarginFit, pageVerticalPosition) }
    }

    // High-precision blurred drop shadow on incoming page edge
    val shadowX = pLeft + offsetX
    val shadowWidth = min(48f, pW * 0.12f)
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0x06000000),
                Color(0x18000000),
                Color(0x32000000),
                Color(0x55000000)
            ),
            startX = shadowX - shadowWidth,
            endX = shadowX
        ),
        topLeft = Offset(shadowX - shadowWidth, pTop),
        size = Size(shadowWidth, pH)
    )

    drawRect(
        color = Color(0x30000000),
        topLeft = Offset(shadowX - 1f, pTop),
        size = Size(1.2f, pH)
    )
}

// -------------------------------------------------------------
// REAL BOOK PHYSICAL PAPER RENDERING HELPERS & GEOMETRY
// -------------------------------------------------------------

data class PageLayoutMetrics(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

fun calculatePageLayoutMetrics(
    bmpW: Float,
    bmpH: Float,
    containerW: Float,
    containerH: Float,
    isSmartMarginFit: Boolean,
    pageVerticalPosition: PageVerticalPosition = PageVerticalPosition.CENTER
): PageLayoutMetrics {
    if (bmpW <= 0f || bmpH <= 0f || containerW <= 0f || containerH <= 0f) {
        return PageLayoutMetrics(0f, 0f, containerW, containerH)
    }

    val baseScale = minOf(containerW / bmpW, containerH / bmpH)
    val scale = if (isSmartMarginFit) {
        // Smart margin fit expands to comfortably fill the screen width while fitting inside container height
        val fitWidthScale = containerW / bmpW
        val maxScale = containerH / bmpH
        minOf(fitWidthScale, maxScale).coerceAtLeast(baseScale)
    } else {
        baseScale
    }

    val targetW = bmpW * scale
    val targetH = bmpH * scale

    val left = (containerW - targetW) / 2f
    val top = if (targetH < containerH) {
        val remainingSpace = containerH - targetH
        when (pageVerticalPosition) {
            PageVerticalPosition.TOP -> {
                // Elevated: Natural top-biased reading position with comfortable clearance
                val topMargin = minOf(remainingSpace * 0.20f, containerH * 0.08f).coerceAtLeast(16f)
                topMargin
            }
            PageVerticalPosition.CENTER -> {
                // Symmetrical optical center
                remainingSpace / 2f
            }
            PageVerticalPosition.BOTTOM -> {
                // Lowered: Positioned lower down, closer to thumbs and bottom of device
                val bottomMargin = minOf(remainingSpace * 0.20f, containerH * 0.08f).coerceAtLeast(16f)
                remainingSpace - bottomMargin
            }
        }
    } else {
        (containerH - targetH) / 2f
    }

    return PageLayoutMetrics(left, top, targetW, targetH)
}

private fun DrawScope.drawPageBitmap(
    bitmap: Bitmap,
    width: Float,
    height: Float,
    isSmartMarginFit: Boolean = true,
    pageVerticalPosition: PageVerticalPosition = PageVerticalPosition.CENTER
): PageLayoutMetrics {
    val bmpW = bitmap.width.toFloat()
    val bmpH = bitmap.height.toFloat()
    val metrics = calculatePageLayoutMetrics(bmpW, bmpH, width, height, isSmartMarginFit, pageVerticalPosition)
    if (bmpW <= 0f || bmpH <= 0f) return metrics

    val imageBitmap = bitmap.asImageBitmap()
    val left = metrics.left
    val top = metrics.top
    val targetW = metrics.width
    val targetH = metrics.height

    // 1. Subtle Paper Ambient Shadow underneath the page bounds
    drawRect(
        color = Color(0x18000000),
        topLeft = Offset(left + 2f, top + 4f),
        size = Size(targetW, targetH)
    )

    // 2. Realistic Crisp White / Ivory Paper Canvas
    drawRect(
        color = Color.White,
        topLeft = Offset(left, top),
        size = Size(targetW, targetH)
    )

    // 3. Render Book Page Graphic with 100% natural proportions
    drawImage(
        image = imageBitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(left.toInt(), top.toInt()),
        dstSize = IntSize(targetW.toInt(), targetH.toInt())
    )

    // 4. Subtle Page Border
    drawRect(
        color = Color(0x12000000),
        topLeft = Offset(left, top),
        size = Size(targetW, targetH),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
    )

    return metrics
}

/**
 * Draws the realistic physical book spine gutter gradient along the page left edge
 */
private fun DrawScope.drawBookSpineGutter(metrics: PageLayoutMetrics, theme: ReaderThemeMode) {
    val gutterWidth = minOf(42f, metrics.width * 0.09f)
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                theme.spineShadowColor,
                theme.spineShadowColor.copy(alpha = theme.spineShadowColor.alpha * 0.70f),
                theme.spineShadowColor.copy(alpha = theme.spineShadowColor.alpha * 0.35f),
                theme.spineShadowColor.copy(alpha = theme.spineShadowColor.alpha * 0.10f),
                Color.Transparent
            ),
            startX = metrics.left,
            endX = metrics.left + gutterWidth
        ),
        topLeft = Offset(metrics.left, metrics.top),
        size = Size(gutterWidth, metrics.height)
    )
}

private fun DrawScope.drawBookSpineGutter(width: Float, height: Float, theme: ReaderThemeMode) {
    drawBookSpineGutter(PageLayoutMetrics(0f, 0f, width, height), theme)
}

/**
 * Draws subtle page edge thickness shadow on right edge to give paper depth
 */
private fun DrawScope.drawBookEdgeShadow(metrics: PageLayoutMetrics, theme: ReaderThemeMode) {
    val edgeWidth = minOf(16f, metrics.width * 0.035f)
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0x08000000),
                Color(0x15000000),
                Color(0x28000000)
            ),
            startX = metrics.left + metrics.width - edgeWidth,
            endX = metrics.left + metrics.width
        ),
        topLeft = Offset(metrics.left + metrics.width - edgeWidth, metrics.top),
        size = Size(edgeWidth, metrics.height)
    )
    // 1px stacked paper rim
    drawRect(
        color = Color(0x18000000),
        topLeft = Offset(metrics.left + metrics.width - 1f, metrics.top),
        size = Size(1f, metrics.height)
    )
}

private fun DrawScope.drawBookEdgeShadow(width: Float, height: Float, theme: ReaderThemeMode) {
    drawBookEdgeShadow(PageLayoutMetrics(0f, 0f, width, height), theme)
}

/**
 * Draws a luxurious silk ribbon bookmark anchored at the physical top-right edge of the page
 */
private fun DrawScope.drawBookmarkRibbon(metrics: PageLayoutMetrics, theme: ReaderThemeMode) {
    val ribbonWidth = 26f
    val ribbonLength = 65f
    val startX = (metrics.left + metrics.width - 48f).coerceAtLeast(metrics.left)
    val startY = metrics.top

    val ribbonPath = Path().apply {
        moveTo(startX, startY)
        lineTo(startX + ribbonWidth, startY)
        lineTo(startX + ribbonWidth, startY + ribbonLength)
        lineTo(startX + ribbonWidth / 2f, startY + ribbonLength - 12f)
        lineTo(startX, startY + ribbonLength)
        close()
    }

    drawPath(
        path = ribbonPath,
        brush = Brush.verticalGradient(
            colors = listOf(theme.accentColor, theme.accentColor.copy(alpha = 0.85f)),
            startY = startY,
            endY = startY + ribbonLength
        )
    )
}

private fun DrawScope.drawBookmarkRibbon(width: Float, height: Float, theme: ReaderThemeMode) {
    drawBookmarkRibbon(PageLayoutMetrics(0f, 0f, width, height), theme)
}
