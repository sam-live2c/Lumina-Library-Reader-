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
            ReaderThemeMode.CREAM -> Color(0xFFF6F1E3)
            ReaderThemeMode.SEPIA -> Color(0xFFECE1CE)
            else -> Color(0xFFF7F4EE)
        }
    }

    // 1. UNDERNEATH REVEALED PAGE (Next Page Base - Seamless with background)
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

    // 2. DYNAMIC FOLD CREASE GEOMETRY (Progressing from Right to Left with natural straight release)
    val yFrac = touchFractionY.coerceIn(0.05f, 0.95f)
    val cornerPullBias = ((yFrac - 0.5f) * 2f).coerceIn(-1f, 1f) // -1 top corner, 0 center, +1 bottom corner

    // Natural tension and smooth fall-in after 2/3 page coverage
    val fallInFraction = ((p - (2f / 3f)) / (1f / 3f)).coerceIn(0f, 1f)
    val straightenDecay = (1f - fallInFraction * 0.75f).coerceIn(0.25f, 1f)
    val tiltSpread = (sin(p * PI) * straightenDecay * pW * 0.045f * cornerPullBias).toFloat()
    val foldX = pRight - (p * pW)
    val foldXTop = (foldX + tiltSpread).coerceIn(pLeft, pRight)
    val foldXBottom = (foldX - tiltSpread).coerceIn(pLeft, pRight)
    val foldXMid = ((foldXTop + foldXBottom) / 2f).coerceIn(pLeft, pRight)

    val c1x = foldXTop + (foldXMid - foldXTop) * 0.15f
    val c1y = pTop + pH * 0.32f
    val c2x = foldXBottom + (foldXMid - foldXBottom) * 0.15f
    val c2y = pBottom - pH * 0.32f

    // 3. CURRENT PAGE (Recto on Left / Spine side remaining flat and clean)
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
                drawRect(
                    color = paperColor,
                    topLeft = Offset(pLeft, pTop),
                    size = Size(pW, pH)
                )

                if (currentBitmap != null) {
                    drawPageBitmap(currentBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
                }
            }
        }
    }

    // 4. 3D SOLID FOLDED VERSO LEAF WITH REAL PHYSICAL OPTICS INSIDE THE FOLD
    if (p in 0.005f..0.995f) {
        // Full, realistic page folding size based on page width
        val wFoldedTop = (pRight - foldXTop).coerceAtLeast(0f)
        val wFoldedBottom = (pRight - foldXBottom).coerceAtLeast(0f)

        // Generous, natural page curl factor that smoothly drapes flat in final 1/3 of navigation
        val curlFactor = (0.92f - fallInFraction * 0.15f).coerceIn(0.70f, 0.95f)

        val curlXTop = (foldXTop - wFoldedTop * curlFactor).coerceIn(pLeft, pRight)
        val curlXBottom = (foldXBottom - wFoldedBottom * curlFactor).coerceIn(pLeft, pRight)

        val curlIntensity = (sin(p * PI) * straightenDecay).toFloat()
        val curlYTop = if (cornerPullBias < 0f) {
            pTop + (pH * 0.09f * curlIntensity * (-cornerPullBias).coerceIn(0f, 1f) * (1f - fallInFraction))
        } else {
            pTop
        }.coerceIn(pTop, pBottom - pH * 0.1f)

        val curlYBottom = if (cornerPullBias > 0f) {
            pBottom - (pH * 0.09f * curlIntensity * cornerPullBias.coerceIn(0f, 1f) * (1f - fallInFraction))
        } else {
            pBottom
        }.coerceIn(pTop + pH * 0.1f, pBottom)

        val curlXMid = ((curlXTop + curlXBottom) / 2f - (pW * 0.035f * curlIntensity * (1f - fallInFraction))).coerceIn(pLeft, pRight)
        val curlYMid = (curlYTop + curlYBottom) / 2f

        val cOuter1X = curlXBottom + (curlXMid - curlXBottom) * 0.20f
        val cOuter1Y = curlYBottom - (curlYBottom - curlYMid) * 0.50f
        val cOuter2X = curlXTop + (curlXMid - curlXTop) * 0.20f
        val cOuter2Y = curlYTop + (curlYMid - curlYTop) * 0.50f

        val flapPath = Path().apply {
            moveTo(foldXTop, pTop)
            cubicTo(c1x, c1y, c2x, c2y, foldXBottom, pBottom)
            lineTo(curlXBottom, curlYBottom)
            cubicTo(cOuter1X, cOuter1Y, cOuter2X, cOuter2Y, curlXTop, curlYTop)
            lineTo(foldXTop, pTop)
            close()
        }

        // 100% Solid Opaque Verso Paper Flap
        drawPath(path = flapPath, color = versoPaperColor)

        // Physics-accurate optical lighting inside the 3D fold:
        // - Deep dark occlusion shadow inside the fold crevice at maxFlapX
        // - Specular reflection along the curved cylindrical crest (highlight)
        // - Natural ambient falloff toward outer edge
        val minFlapX = minOf(foldXTop, foldXBottom, curlXTop, curlXBottom, curlXMid)
        val maxFlapX = maxOf(foldXTop, foldXBottom)
        val deepCreaseShadowAlpha = (curlIntensity * (if (theme.isDark) 0.52f else 0.42f)).coerceIn(0f, 0.65f)
        val highlightAlpha = if (theme.isDark) 0.22f else 0.32f

        if (maxFlapX > minFlapX) {
            drawPath(
                path = flapPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.06f),
                        Color.Transparent,
                        Color.White.copy(alpha = highlightAlpha),
                        Color.Transparent,
                        Color.Black.copy(alpha = deepCreaseShadowAlpha)
                    ),
                    startX = minFlapX,
                    endX = maxFlapX
                )
            )
        }
    }

    // 5. PERMANENT BOOK BINDING SPINES & EDGES
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
            ReaderThemeMode.CREAM -> Color(0xFFF6F1E3)
            ReaderThemeMode.SEPIA -> Color(0xFFECE1CE)
            else -> Color(0xFFF7F4EE)
        }
    }

    // 1. UNDERNEATH BASE PAGE (Current Page - Seamless with background)
    drawRect(
        color = paperColor,
        topLeft = Offset(pLeft, pTop),
        size = Size(pW, pH)
    )
    val baseBitmap = currentBitmap ?: previousBitmap
    if (baseBitmap != null) {
        drawPageBitmap(baseBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
    }
    drawBookSpineGutter(metrics, theme)
    drawBookEdgeShadow(metrics, theme)

    // 2. DYNAMIC FOLD CREASE GEOMETRY (Mirrored opposite: progressing from Left to Right with straight release)
    val yFrac = touchFractionY.coerceIn(0.05f, 0.95f)
    val cornerPullBias = ((yFrac - 0.5f) * 2f).coerceIn(-1f, 1f) // -1 top corner, 0 center, +1 bottom corner

    // Natural tension and smooth fall-in after 2/3 page coverage
    val fallInFraction = ((p - (2f / 3f)) / (1f / 3f)).coerceIn(0f, 1f)
    val straightenDecay = (1f - fallInFraction * 0.75f).coerceIn(0.25f, 1f)
    val tiltSpread = (sin(p * PI) * straightenDecay * pW * 0.045f * cornerPullBias).toFloat()
    val foldX = pLeft + (p * pW)
    val foldXTop = (foldX - tiltSpread).coerceIn(pLeft, pRight)
    val foldXBottom = (foldX + tiltSpread).coerceIn(pLeft, pRight)
    val foldXMid = ((foldXTop + foldXBottom) / 2f).coerceIn(pLeft, pRight)

    val c1x = foldXTop + (foldXMid - foldXTop) * 0.15f
    val c1y = pTop + pH * 0.32f
    val c2x = foldXBottom + (foldXMid - foldXBottom) * 0.15f
    val c2y = pBottom - pH * 0.32f

    // 3. PREVIOUS PAGE (Revealed on Left side from Spine to fold crease - Clean)
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

                val incomingBitmap = previousBitmap ?: currentBitmap
                if (incomingBitmap != null) {
                    drawPageBitmap(incomingBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
                }
            }
        }
    }

    // 4. 3D SOLID FOLDED VERSO LEAF WITH REAL PHYSICAL OPTICS INSIDE THE FOLD (Curling over to the Right)
    if (p in 0.005f..0.995f) {
        // Full, realistic page folding size based on page width
        val wFoldedTop = (foldXTop - pLeft).coerceAtLeast(0f)
        val wFoldedBottom = (foldXBottom - pLeft).coerceAtLeast(0f)

        // Generous, natural page curl factor that smoothly drapes flat in final 1/3 of navigation
        val curlFactor = (0.92f - fallInFraction * 0.15f).coerceIn(0.70f, 0.95f)

        val curlXTop = (foldXTop + wFoldedTop * curlFactor).coerceIn(pLeft, pRight)
        val curlXBottom = (foldXBottom + wFoldedBottom * curlFactor).coerceIn(pLeft, pRight)

        val curlIntensity = (sin(p * PI) * straightenDecay).toFloat()
        val curlYTop = if (cornerPullBias < 0f) {
            pTop + (pH * 0.09f * curlIntensity * (-cornerPullBias).coerceIn(0f, 1f) * (1f - fallInFraction))
        } else {
            pTop
        }.coerceIn(pTop, pBottom - pH * 0.1f)

        val curlYBottom = if (cornerPullBias > 0f) {
            pBottom - (pH * 0.09f * curlIntensity * cornerPullBias.coerceIn(0f, 1f) * (1f - fallInFraction))
        } else {
            pBottom
        }.coerceIn(pTop + pH * 0.1f, pBottom)

        val curlXMid = ((curlXTop + curlXBottom) / 2f + (pW * 0.035f * curlIntensity * (1f - fallInFraction))).coerceIn(pLeft, pRight)
        val curlYMid = (curlYTop + curlYBottom) / 2f

        val cOuter1X = curlXBottom + (curlXMid - curlXBottom) * 0.20f
        val cOuter1Y = curlYBottom - (curlYBottom - curlYMid) * 0.50f
        val cOuter2X = curlXTop + (curlXMid - curlXTop) * 0.20f
        val cOuter2Y = curlYTop + (curlYMid - curlYTop) * 0.50f

        val flapPath = Path().apply {
            moveTo(foldXTop, pTop)
            cubicTo(c1x, c1y, c2x, c2y, foldXBottom, pBottom)
            lineTo(curlXBottom, curlYBottom)
            cubicTo(cOuter1X, cOuter1Y, cOuter2X, cOuter2Y, curlXTop, curlYTop)
            lineTo(foldXTop, pTop)
            close()
        }

        // 100% Solid Opaque Verso Paper Flap
        drawPath(path = flapPath, color = versoPaperColor)

        // Physics-accurate optical lighting inside the 3D fold (Opposite direction):
        // - Deep dark occlusion shadow inside the fold crevice at minFlapX (the crease)
        // - Specular reflection along the curved cylindrical crest (highlight)
        // - Natural ambient falloff toward outer edge at maxFlapX
        val minFlapX = minOf(foldXTop, foldXBottom)
        val maxFlapX = maxOf(foldXTop, foldXBottom, curlXTop, curlXBottom, curlXMid)
        val deepCreaseShadowAlpha = (curlIntensity * (if (theme.isDark) 0.52f else 0.42f)).coerceIn(0f, 0.65f)
        val highlightAlpha = if (theme.isDark) 0.22f else 0.32f

        if (maxFlapX > minFlapX) {
            drawPath(
                path = flapPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = deepCreaseShadowAlpha),
                        Color.Transparent,
                        Color.White.copy(alpha = highlightAlpha),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.06f)
                    ),
                    startX = minFlapX,
                    endX = maxFlapX
                )
            )
        }
    }

    // 5. PERMANENT BOOK BINDING SPINES & EDGES
    drawBookSpineGutter(metrics, theme)
    drawBookEdgeShadow(metrics, theme)
}

// -------------------------------------------------------------
// 3D APPLE-STYLE BOOK PAGE FLIP
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
            ReaderThemeMode.CREAM -> Color(0xFFF6F1E3)
            ReaderThemeMode.SEPIA -> Color(0xFFECE1CE)
            else -> Color(0xFFF7F4EE)
        }
    }

    // 1. Underneath revealed page base (Seamless with background)
    drawRect(
        color = paperColor,
        topLeft = Offset(pLeft, pTop),
        size = Size(pW, pH)
    )
    if (nextBitmap != null) {
        drawPageBitmap(nextBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
    }

    // 2. 3D Turning Page Leaf with True Perspective & Solid Opaque Paper
    if (p < 0.5f) {
        val scaleX = cos(p * Math.PI).toFloat().coerceIn(0f, 1f)
        val camberScaleY = 1f - (sin(p * Math.PI) * 0.05f).toFloat()

        withTransform({
            scale(scaleX = scaleX, scaleY = camberScaleY, pivot = Offset(pLeft, pTop + pH / 2f))
        }) {
            drawRect(
                color = paperColor,
                topLeft = Offset(pLeft, pTop),
                size = Size(pW, pH)
            )
            if (currentBitmap != null) {
                drawPageBitmap(currentBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
            }

            // Spine crease depth shading on turning page inside the spine fold
            val creaseWidth = min(pW * 0.12f, 30f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = (p * 0.35f)),
                        Color.Transparent
                    ),
                    startX = pLeft,
                    endX = pLeft + creaseWidth
                ),
                topLeft = Offset(pLeft, pTop),
                size = Size(creaseWidth, pH)
            )

            // Paper edge stroke
            drawRect(
                color = Color(0x30000000),
                topLeft = Offset(pLeft, pTop),
                size = Size(pW, pH),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.0f)
            )

            drawBookSpineGutter(metrics, theme)
        }
    } else {
        val backProgress = 1f - p
        val scaleX = cos(backProgress * Math.PI).toFloat().coerceIn(0f, 1f)
        val camberScaleY = 1f - (sin(backProgress * Math.PI) * 0.05f).toFloat()

        withTransform({
            scale(scaleX = scaleX, scaleY = camberScaleY, pivot = Offset(pLeft, pTop + pH / 2f))
        }) {
            // Solid Verso Leaf
            drawRect(
                color = versoPaperColor,
                topLeft = Offset(pLeft, pTop),
                size = Size(pW, pH)
            )

            // Verso 3D paper cylinder lighting inside the fold
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = (backProgress * 0.35f)),
                        Color.Transparent,
                        if (theme.isDark) Color(0x18FFFFFF) else Color(0x30FFFFFF),
                        Color.Transparent
                    ),
                    startX = pLeft,
                    endX = pLeft + pW
                ),
                topLeft = Offset(pLeft, pTop),
                size = Size(pW, pH)
            )

            // Paper edge stroke
            drawRect(
                color = Color(0x30000000),
                topLeft = Offset(pLeft, pTop),
                size = Size(pW, pH),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.0f)
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
            ReaderThemeMode.CREAM -> Color(0xFFF6F1E3)
            ReaderThemeMode.SEPIA -> Color(0xFFECE1CE)
            else -> Color(0xFFF7F4EE)
        }
    }

    // 1. Base current page (Seamless with background)
    drawRect(
        color = paperColor,
        topLeft = Offset(pLeft, pTop),
        size = Size(pW, pH)
    )
    if (currentBitmap != null) {
        drawPageBitmap(currentBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
    }

    // 2. 3D Turning Page Leaf
    if (p < 0.5f) {
        val scaleX = cos(p * Math.PI).toFloat().coerceIn(0f, 1f)
        val camberScaleY = 1f - (sin(p * Math.PI) * 0.05f).toFloat()

        withTransform({
            scale(scaleX = scaleX, scaleY = camberScaleY, pivot = Offset(pLeft + pW, pTop + pH / 2f))
        }) {
            drawRect(
                color = paperColor,
                topLeft = Offset(pLeft, pTop),
                size = Size(pW, pH)
            )
            if (currentBitmap != null) {
                drawPageBitmap(currentBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
            }

            // Depth crease shading inside the fold
            val creaseWidth = min(pW * 0.12f, 30f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = (p * 0.35f))
                    ),
                    startX = pLeft + pW - creaseWidth,
                    endX = pLeft + pW
                ),
                topLeft = Offset(pLeft + pW - creaseWidth, pTop),
                size = Size(creaseWidth, pH)
            )

            // Paper edge stroke
            drawRect(
                color = Color(0x30000000),
                topLeft = Offset(pLeft, pTop),
                size = Size(pW, pH),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.0f)
            )

            drawBookSpineGutter(metrics, theme)
        }
    } else {
        val backProgress = 1f - p
        val scaleX = cos(backProgress * Math.PI).toFloat().coerceIn(0f, 1f)
        val camberScaleY = 1f - (sin(backProgress * Math.PI) * 0.05f).toFloat()

        withTransform({
            scale(scaleX = scaleX, scaleY = camberScaleY, pivot = Offset(pLeft + pW, pTop + pH / 2f))
        }) {
            drawRect(
                color = paperColor,
                topLeft = Offset(pLeft, pTop),
                size = Size(pW, pH)
            )
            if (previousBitmap != null) {
                drawPageBitmap(previousBitmap, width, height, isSmartMarginFit, pageVerticalPosition)
            }

            // Paper edge stroke
            drawRect(
                color = Color(0x30000000),
                topLeft = Offset(pLeft, pTop),
                size = Size(pW, pH),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.0f)
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

    // Crisp paper thickness edge divider line
    val shadowX = pLeft + offsetX + pW
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

    // Crisp paper thickness edge divider line
    val shadowX = pLeft + offsetX
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

    // 1. Realistic Crisp Paper Canvas
    drawRect(
        color = Color.White,
        topLeft = Offset(left, top),
        size = Size(targetW, targetH)
    )

    // 2. Render Book Page Graphic with 100% natural proportions
    drawImage(
        image = imageBitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(left.toInt(), top.toInt()),
        dstSize = IntSize(targetW.toInt(), targetH.toInt())
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
