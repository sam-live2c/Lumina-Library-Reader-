package com.example.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.data.model.AnnotationType
import com.example.data.repository.AnnotationStroke
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

@Composable
fun AnnotationCanvas(
    strokes: List<AnnotationStroke>,
    isAnnotationMode: Boolean,
    activeTool: AnnotationType,
    isEraserActive: Boolean,
    activeColor: Color,
    strokeWidth: Float,
    isAnnotationsVisible: Boolean,
    onStrokeCompleted: (AnnotationStroke) -> Unit,
    onEraseStroke: (Long) -> Unit = {},
    onStrokesUpdated: ((List<AnnotationStroke>) -> Unit)? = null,
    onTransform: ((pan: Offset, zoom: Float) -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    zoomScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val currentPoints = remember { mutableStateListOf<Pair<Float, Float>>() }
    var eraserTouchPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    // Use rememberUpdatedState to avoid resetting pointerInput gesture coroutine during continuous erasing
    val currentStrokesState by androidx.compose.runtime.rememberUpdatedState(strokes)
    val currentOnStrokesUpdated by androidx.compose.runtime.rememberUpdatedState(onStrokesUpdated)
    val currentOnEraseStroke by androidx.compose.runtime.rememberUpdatedState(onEraseStroke)
    val currentOnStrokeCompleted by androidx.compose.runtime.rememberUpdatedState(onStrokeCompleted)
    val currentOnTransform by androidx.compose.runtime.rememberUpdatedState(onTransform)
    val currentZoomScale by androidx.compose.runtime.rememberUpdatedState(zoomScale)

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isAnnotationMode) {
                    Modifier.pointerInput(isEraserActive, activeTool, activeColor, strokeWidth) {
                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            var isMultiTouch = false
                            val canvasW = size.width.toFloat().coerceAtLeast(1f)
                            val canvasH = size.height.toFloat().coerceAtLeast(1f)

                            val normX = (firstDown.position.x / canvasW).coerceIn(0f, 1f)
                            val normY = (firstDown.position.y / canvasH).coerceIn(0f, 1f)

                            var lastEraserNormX = normX
                            var lastEraserNormY = normY
                            var activeStrokesList = currentStrokesState

                            // Dynamically regulate eraser radius according to current zoom level
                            val dynamicEraserRadius = (0.038f / sqrt(currentZoomScale.coerceAtLeast(1f))).coerceIn(0.010f, 0.045f)

                            if (isEraserActive) {
                                eraserTouchPoint = Pair(normX, normY)
                                val updated = performLocalizedErase(
                                    normX = normX,
                                    normY = normY,
                                    strokes = activeStrokesList,
                                    eraserRadius = dynamicEraserRadius,
                                    haptic = haptic
                                )
                                if (updated != null) {
                                    activeStrokesList = updated
                                    currentOnStrokesUpdated?.invoke(updated) ?: run {
                                        val remainingIds = updated.map { it.id }.toSet()
                                        currentStrokesState.filter { it.id !in remainingIds }.forEach { currentOnEraseStroke(it.id) }
                                    }
                                }
                            } else {
                                eraserTouchPoint = null
                                currentPoints.clear()
                                currentPoints.add(Pair(normX, normY))
                            }
                            firstDown.consume()

                            do {
                                val event = awaitPointerEvent()
                                val activePointers = event.changes.filter { it.pressed }

                                if (activePointers.size >= 2) {
                                    // 2-Finger Multi-Touch: Pinch-to-Zoom & Pan Gesture
                                    isMultiTouch = true
                                    currentPoints.clear()
                                    eraserTouchPoint = null

                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()

                                    if (zoom != 1f || pan != Offset.Zero) {
                                        currentOnTransform?.invoke(pan, zoom)
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (activePointers.size == 1 && !isMultiTouch) {
                                    // 1-Finger Gesture: Draw or Erase
                                    val change = activePointers.first()
                                    val rawNormX = (change.position.x / canvasW).coerceIn(0f, 1f)
                                    val rawNormY = (change.position.y / canvasH).coerceIn(0f, 1f)

                                    if (change.positionChange() != Offset.Zero) {
                                        change.consume()

                                        if (isEraserActive) {
                                            eraserTouchPoint = Pair(rawNormX, rawNormY)

                                            // Smooth path interpolation between points during fast continuous drag
                                            val dist = hypot(rawNormX - lastEraserNormX, rawNormY - lastEraserNormY)
                                            val stepCount = (dist / 0.005f).toInt().coerceIn(1, 16)
                                            var intermediateChanges = false

                                            for (step in 1..stepCount) {
                                                val fraction = step.toFloat() / stepCount
                                                val stepX = lastEraserNormX + (rawNormX - lastEraserNormX) * fraction
                                                val stepY = lastEraserNormY + (rawNormY - lastEraserNormY) * fraction

                                                val stepUpdated = performLocalizedErase(
                                                    normX = stepX,
                                                    normY = stepY,
                                                    strokes = activeStrokesList,
                                                    eraserRadius = dynamicEraserRadius,
                                                    haptic = haptic
                                                )
                                                if (stepUpdated != null) {
                                                    activeStrokesList = stepUpdated
                                                    intermediateChanges = true
                                                }
                                            }

                                            if (intermediateChanges) {
                                                currentOnStrokesUpdated?.invoke(activeStrokesList) ?: run {
                                                    val remainingIds = activeStrokesList.map { it.id }.toSet()
                                                    currentStrokesState.filter { it.id !in remainingIds }.forEach { currentOnEraseStroke(it.id) }
                                                }
                                            }

                                            lastEraserNormX = rawNormX
                                            lastEraserNormY = rawNormY
                                        } else {
                                            eraserTouchPoint = null
                                            if (currentPoints.isNotEmpty()) {
                                                val lastPt = currentPoints.last()
                                                val dist = hypot(rawNormX - lastPt.first, rawNormY - lastPt.second)
                                                // Micro-jitter dampener: Ignore micro-movements smaller than threshold
                                                if (dist >= 0.0012f) {
                                                    // Smooth low-pass interpolation to prevent jitter when zoomed in
                                                    val smoothedX = lastPt.first * 0.25f + rawNormX * 0.75f
                                                    val smoothedY = lastPt.second * 0.25f + rawNormY * 0.75f
                                                    currentPoints.add(Pair(smoothedX, smoothedY))
                                                }
                                            } else {
                                                currentPoints.add(Pair(rawNormX, rawNormY))
                                            }
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            eraserTouchPoint = null

                            // Gesture Complete: Smart Highlighter & Stroke Processing
                            if (!isMultiTouch && !isEraserActive && currentPoints.size > 1) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                val finalPoints = when (activeTool) {
                                    AnnotationType.HIGHLIGHTER -> {
                                        processSmartHighlightPoints(currentPoints)
                                    }
                                    AnnotationType.STRIKE_THROUGH -> {
                                        val first = currentPoints.first()
                                        val last = currentPoints.last()
                                        val avgY = (first.second + last.second) / 2f
                                        val minX = minOf(first.first, last.first)
                                        val maxX = maxOf(first.first, last.first)
                                        listOf(Pair(minX, avgY), Pair(maxX, avgY))
                                    }
                                    AnnotationType.PEN -> {
                                        currentPoints.toList()
                                    }
                                }

                                val newStroke = AnnotationStroke(
                                    id = System.currentTimeMillis(),
                                    bookId = 0,
                                    pageIndex = 0,
                                    type = activeTool,
                                    colorArgb = activeColor.value.toLong(),
                                    strokeWidth = strokeWidth,
                                    points = finalPoints
                                )
                                currentOnStrokeCompleted(newStroke)
                            }
                            currentPoints.clear()
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        if (isAnnotationsVisible) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val zoom = zoomScale

                // 1. Draw Saved Strokes
                strokes.forEach { stroke ->
                    drawSingleStroke(stroke, w, h, zoom)
                }

                // 2. Draw Live In-Progress Stroke
                if (currentPoints.size > 1 && !isEraserActive) {
                    val livePoints = if (activeTool == AnnotationType.HIGHLIGHTER) {
                        processSmartHighlightPoints(currentPoints)
                    } else {
                        currentPoints.toList()
                    }
                    val liveStroke = AnnotationStroke(
                        id = 0,
                        bookId = 0,
                        pageIndex = 0,
                        type = activeTool,
                        colorArgb = activeColor.value.toLong(),
                        strokeWidth = strokeWidth,
                        points = livePoints
                    )
                    drawSingleStroke(liveStroke, w, h, zoom)
                }

                // 3. Draw Colorless Floating Eraser Cursor Indicator while erasing (zoom-calibrated)
                eraserTouchPoint?.let { (ex, ey) ->
                    val cx = ex * w
                    val cy = ey * h
                    val dynamicRadiusNormalized = (0.038f / sqrt(zoom.coerceAtLeast(1f))).coerceIn(0.010f, 0.045f)
                    val cursorRadius = (w * dynamicRadiusNormalized).coerceIn(16f, 48f)

                    // Subtle neutral translucent fill (no color tint)
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.08f),
                        radius = cursorRadius,
                        center = Offset(cx, cy)
                    )
                    // Outer dark hairline ring for crisp visibility on bright/white pages
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.50f),
                        radius = cursorRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.8f)
                    )
                    // Inner light ring for crisp visibility on dark/sepia backgrounds
                    drawCircle(
                        color = Color.White.copy(alpha = 0.80f),
                        radius = (cursorRadius - 1.5f).coerceAtLeast(1f),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2f)
                    )
                    // Colorless precision center dot
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.65f),
                        radius = 2.5f,
                        center = Offset(cx, cy)
                    )
                }
            }
        }
    }
}

/**
 * Natural Smart Highlighter Algorithm:
 * - When swiping across a printed text line (reading direction), gently stabilizes vertical hand wobble
 *   toward the line's optical baseline while preserving organic entry/exit tapers and natural multi-point curvature.
 * - Retains rich continuous spline points so it feels like authentic translucent ink on paper rather than a rigid pill capsule.
 * - When drawing freehand marks, margin brackets, circles, or diagrams, fully preserves freehand paths.
 */
private fun processSmartHighlightPoints(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    if (points.size < 2) return points

    val first = points.first()
    val last = points.last()

    val dx = abs(last.first - first.first)
    val dy = abs(last.second - first.second)

    // Calculate vertical variation across all points
    val avgY = points.map { it.second }.average().toFloat()
    val maxDevY = points.maxOf { abs(it.second - avgY) }

    // Predominantly horizontal swipe (Text line reading highlight gesture)
    val isTextHighlight = (dx >= 0.020f && maxDevY < 0.040f) || (dx > dy * 2.0f && maxDevY < 0.060f)

    return if (isTextHighlight) {
        // Line-following assist: Smooth vertical wobble toward optical baseline, but preserve organic multi-point flow
        points.mapIndexed { index, pt ->
            // Subtle natural taper at start and end of stroke
            val t = index.toFloat() / (points.size - 1).coerceAtLeast(1)
            val baselineWeight = if (t < 0.10f || t > 0.90f) 0.65f else 0.85f
            val smoothedY = pt.second * (1f - baselineWeight) + avgY * baselineWeight
            Pair(pt.first, smoothedY)
        }
    } else {
        // Freehand diagram / margin / curved highlight
        points.toList()
    }
}

/**
 * Localized Partial Eraser:
 * Erases only the portions of strokes that intersect the eraser brush,
 * cutting or splitting segments as the user rubs over them, without wiping out the entire stroke.
 */
private fun performLocalizedErase(
    normX: Float,
    normY: Float,
    strokes: List<AnnotationStroke>,
    eraserRadius: Float = 0.038f,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
): List<AnnotationStroke>? {
    var hasChanges = false
    val updatedStrokes = mutableListOf<AnnotationStroke>()

    for (stroke in strokes) {
        if (stroke.points.size < 2) {
            hasChanges = true
            continue
        }

        when (stroke.type) {
            AnnotationType.HIGHLIGHTER, AnnotationType.STRIKE_THROUGH -> {
                if (stroke.points.size == 2) {
                    // Straight-line / 2-point highlight segment
                    val p1 = stroke.points[0]
                    val p2 = stroke.points[1]
                    val x1 = p1.first
                    val y1 = p1.second
                    val x2 = p2.first
                    val y2 = p2.second

                    val segDx = x2 - x1
                    val segDy = y2 - y1
                    val segLenSq = segDx * segDx + segDy * segDy

                    if (segLenSq < 0.00005f) {
                        val d = hypot(normX - x1, normY - y1)
                        if (d < eraserRadius) {
                            hasChanges = true // Erased tiny stroke
                        } else {
                            updatedStrokes.add(stroke)
                        }
                    } else {
                        val segLen = sqrt(segLenSq)
                        // Project touch point onto line segment
                        val t = ((normX - x1) * segDx + (normY - y1) * segDy) / segLenSq
                        val tClamped = t.coerceIn(0f, 1f)
                        val projX = x1 + tClamped * segDx
                        val projY = y1 + tClamped * segDy
                        val distToSeg = hypot(normX - projX, normY - projY)

                        val effRadius = eraserRadius + (stroke.strokeWidth * 0.002f)

                        if (distToSeg < effRadius) {
                            hasChanges = true
                            val tRadius = effRadius / segLen
                            val tStart = (t - tRadius).coerceIn(0f, 1f)
                            val tEnd = (t + tRadius).coerceIn(0f, 1f)

                            // Left remaining piece (if long enough)
                            if (tStart > 0.03f && (tStart * segLen) >= 0.010f) {
                                val pLeftEnd = Pair(x1 + tStart * segDx, y1 + tStart * segDy)
                                updatedStrokes.add(stroke.copy(points = listOf(p1, pLeftEnd)))
                            }

                            // Right remaining piece (if long enough)
                            if (tEnd < 0.97f && ((1f - tEnd) * segLen) >= 0.010f) {
                                val pRightStart = Pair(x1 + tEnd * segDx, y1 + tEnd * segDy)
                                val newId = stroke.id + (tEnd * 1000).toLong() + 1
                                updatedStrokes.add(stroke.copy(id = newId, points = listOf(pRightStart, p2)))
                            }
                        } else {
                            updatedStrokes.add(stroke)
                        }
                    }
                } else {
                    // Multi-point Natural Highlighter
                    val resultFrags = filterPointsWithEraser(stroke.points, normX, normY, eraserRadius)
                    if (resultFrags != listOf(stroke.points)) {
                        hasChanges = true
                        resultFrags.forEachIndexed { idx, frag ->
                            val newId = if (idx == 0) stroke.id else stroke.id + (idx * 500L)
                            updatedStrokes.add(stroke.copy(id = newId, points = frag))
                        }
                    } else {
                        updatedStrokes.add(stroke)
                    }
                }
            }

            AnnotationType.PEN -> {
                // Freehand pen path: Erase touched points and split into surviving fragments
                val resultFrags = filterPointsWithEraser(stroke.points, normX, normY, eraserRadius)
                if (resultFrags != listOf(stroke.points)) {
                    hasChanges = true
                    resultFrags.forEachIndexed { idx, frag ->
                        val newId = if (idx == 0) stroke.id else stroke.id + (idx * 500L)
                        updatedStrokes.add(stroke.copy(id = newId, points = frag))
                    }
                } else {
                    updatedStrokes.add(stroke)
                }
            }
        }
    }

    return if (hasChanges) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        updatedStrokes
    } else {
        null
    }
}

/**
 * Splits a list of points into sub-segments by removing points within eraser radius
 */
private fun filterPointsWithEraser(
    points: List<Pair<Float, Float>>,
    normX: Float,
    normY: Float,
    radius: Float
): List<List<Pair<Float, Float>>> {
    val fragments = mutableListOf<List<Pair<Float, Float>>>()
    val currentFrag = mutableListOf<Pair<Float, Float>>()

    for (pt in points) {
        val dist = hypot(pt.first - normX, pt.second - normY)
        if (dist >= radius) {
            currentFrag.add(pt)
        } else {
            if (currentFrag.size >= 2) {
                fragments.add(currentFrag.toList())
            }
            currentFrag.clear()
        }
    }

    if (currentFrag.size >= 2) {
        fragments.add(currentFrag.toList())
    }

    return fragments
}

private fun DrawScope.drawSingleStroke(stroke: AnnotationStroke, w: Float, h: Float, zoomScale: Float = 1f) {
    if (stroke.points.size < 2) return

    val color = Color(stroke.colorArgb.toULong())
    val zoomFactor = sqrt(zoomScale.coerceAtLeast(1f))

    when (stroke.type) {
        AnnotationType.HIGHLIGHTER -> {
            // Regulate highlight height by zoom level so it always corresponds proportionally to printed text lines
            val highlightHeight = (stroke.strokeWidth * 3.4f / zoomFactor).coerceIn(16f, 44f)

            if (stroke.points.size == 2) {
                val p1 = stroke.points.first()
                val p2 = stroke.points.last()

                val startX = p1.first * w
                val startY = p1.second * h
                val endX = p2.first * w
                val endY = p2.second * h

                drawLine(
                    color = color.copy(alpha = 0.40f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = highlightHeight,
                    cap = StrokeCap.Round,
                    blendMode = BlendMode.Multiply
                )
            } else {
                // Natural organic highlighter curve with smooth quadratic bezier interpolation
                val path = Path().apply {
                    val first = stroke.points.first()
                    moveTo(first.first * w, first.second * h)
                    for (i in 1 until stroke.points.size - 1) {
                        val pCurrent = stroke.points[i]
                        val pNext = stroke.points[i + 1]
                        val midX = ((pCurrent.first + pNext.first) / 2f) * w
                        val midY = ((pCurrent.second + pNext.second) / 2f) * h
                        quadraticTo(pCurrent.first * w, pCurrent.second * h, midX, midY)
                    }
                    val last = stroke.points.last()
                    lineTo(last.first * w, last.second * h)
                }

                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.40f),
                    style = Stroke(
                        width = highlightHeight,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    ),
                    blendMode = BlendMode.Multiply
                )
            }
        }

        AnnotationType.STRIKE_THROUGH -> {
            val p1 = stroke.points.first()
            val p2 = stroke.points.last()

            val startX = p1.first * w
            val startY = p1.second * h
            val endX = p2.first * w
            val endY = p2.second * h

            val strikeWidth = ((stroke.strokeWidth * 1.5f) / zoomFactor).coerceIn(2.5f, 6f)

            drawLine(
                color = color.copy(alpha = 0.88f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = strikeWidth,
                cap = StrokeCap.Round
            )
        }

        AnnotationType.PEN -> {
            val penWidth = (stroke.strokeWidth / zoomFactor).coerceIn(1.8f, 10f)

            val path = Path().apply {
                val first = stroke.points.first()
                moveTo(first.first * w, first.second * h)

                if (stroke.points.size == 2) {
                    val second = stroke.points[1]
                    lineTo(second.first * w, second.second * h)
                } else {
                    for (i in 1 until stroke.points.size - 1) {
                        val pCurrent = stroke.points[i]
                        val pNext = stroke.points[i + 1]
                        val midX = ((pCurrent.first + pNext.first) / 2f) * w
                        val midY = ((pCurrent.second + pNext.second) / 2f) * h
                        quadraticTo(pCurrent.first * w, pCurrent.second * h, midX, midY)
                    }
                    val last = stroke.points.last()
                    lineTo(last.first * w, last.second * h)
                }
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = penWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

