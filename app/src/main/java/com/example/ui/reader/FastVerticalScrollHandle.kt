package com.example.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ReaderThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun FastVerticalScrollHandle(
    currentPage: Int,
    totalPages: Int,
    isHudVisible: Boolean,
    readerTheme: ReaderThemeMode,
    onPageSelected: (Int) -> Unit,
    onIndicatorClick: () -> Unit = {},
    onDragStarted: () -> Unit = {},
    onDraggingStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (totalPages <= 1) return

    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var isRecentActivity by remember { mutableStateOf(false) }
    var isHudRecentlyClosed by remember { mutableStateOf(false) }
    var lastScrubbedPage by remember { mutableIntStateOf(currentPage) }

    LaunchedEffect(isDragging) {
        onDraggingStateChanged(isDragging)
    }

    // Keep indicator alive after drag or page change
    LaunchedEffect(currentPage, isDragging) {
        if (isDragging) {
            isRecentActivity = true
        } else {
            isRecentActivity = true
            delay(2600)
            isRecentActivity = false
        }
    }

    // Keep indicator alive for some time after Header/Footer HUD closes
    LaunchedEffect(isHudVisible) {
        if (!isHudVisible) {
            isHudRecentlyClosed = true
            delay(2800)
            isHudRecentlyClosed = false
        } else {
            isHudRecentlyClosed = false
        }
    }

    val showIndicator = isHudVisible || isHudRecentlyClosed || isDragging || isRecentActivity

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .wrapContentWidth(Alignment.End)
            .testTag("fast_vertical_scrubber")
    ) {
        val availableHeight = maxHeight
        val density = LocalDensity.current
        
        // Smart track boundaries: top padding leaves room below top bar; bottom padding stops safely above the floating pen with generous breathing room
        val trackPaddingTop = (availableHeight * 0.18f).coerceIn(110.dp, 160.dp)
        val trackPaddingBottom = 135.dp // Increased spacing above the floating pen FAB for clean clearance
        val usableHeightDp = (availableHeight - trackPaddingTop - trackPaddingBottom).coerceAtLeast(100.dp)
        val usableHeightPx = with(density) { usableHeightDp.toPx() }
        val topPaddingPx = with(density) { trackPaddingTop.toPx() }
        val maxVisualYPx = topPaddingPx + usableHeightPx

        // Smart progression position calculation based on current reading progress
        val initialFraction = if (totalPages > 1) (currentPage - 1f) / (totalPages - 1f) else 0f
        val initialYPx = topPaddingPx + (usableHeightPx * initialFraction)
        val indicatorYAnim = remember { Animatable(initialYPx) }

        // Animate button position along with reading progress (Page 1 -> Total Pages up to nearest position above floating pen)
        LaunchedEffect(currentPage, totalPages, isDragging) {
            if (!isDragging && totalPages > 1) {
                val progressFraction = ((currentPage - 1f) / (totalPages - 1f)).coerceIn(0f, 1f)
                val smartTargetY = topPaddingPx + (usableHeightPx * progressFraction)
                indicatorYAnim.animateTo(
                    targetValue = smartTargetY,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }

        val currentYDp = with(density) { indicatorYAnim.value.toDp() }

        // Composite Handle: Popup Page Badge + Grip Thumb Pill in a single aligned Row
        // Gestures are attached directly to this handle and its immediate touch padding, leaving the rest of the screen 100% free for swipes and taps
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (currentYDp - 26.dp).coerceIn(trackPaddingTop - 26.dp, availableHeight - trackPaddingBottom))
                .pointerInput(totalPages) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        var isActivelyDragging = false
                        var totalMovementY = 0f
                        var lastDispatchedPage = currentPage

                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.firstOrNull { it.id == down.id && it.pressed }
                            if (pressed != null) {
                                val deltaY = pressed.positionChange().y
                                totalMovementY += deltaY

                                if (!isActivelyDragging && abs(totalMovementY) > 6f) {
                                    isActivelyDragging = true
                                    isDragging = true
                                    onDragStarted()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }

                                if (isActivelyDragging) {
                                    pressed.consume()
                                    val currentVal = indicatorYAnim.value
                                    val nextY = currentVal + deltaY

                                    // Visual button stops when getting to the nearest position above the floating pen
                                    val clampedVisualY = nextY.coerceIn(topPaddingPx, maxVisualYPx)
                                    coroutineScope.launch {
                                        indicatorYAnim.snapTo(clampedVisualY)
                                    }

                                    // Direct linear progression mapping across the usable track height
                                    // When the handle reaches the bottom visual position, it directly goes to the last page (totalPages)
                                    val fraction = if (usableHeightPx > 0f) {
                                        ((nextY - topPaddingPx) / usableHeightPx).coerceIn(0f, 1f)
                                    } else 0f
                                    val targetPage = (1 + fraction * (totalPages - 1)).roundToInt().coerceIn(1, totalPages)
                                    if (targetPage != lastDispatchedPage) {
                                        lastDispatchedPage = targetPage
                                        lastScrubbedPage = targetPage
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onPageSelected(targetPage)
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        val duration = System.currentTimeMillis() - downTime
                        if (isActivelyDragging) {
                            isDragging = false
                            // Smoothly settle at the smart position for the chosen page
                            val finalFraction = ((lastDispatchedPage - 1f) / (totalPages - 1f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val finalTargetY = topPaddingPx + usableHeightPx * finalFraction
                            coroutineScope.launch {
                                indicatorYAnim.animateTo(
                                    targetValue = finalTargetY,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        } else if (duration < 350 && abs(totalMovementY) < 14f) {
                            // Quick tap on indicator / page number badge opens Jump Dialog
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onIndicatorClick()
                        }
                    }
                }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                // Page Indicator Badge (`xx / xxx`)
                AnimatedVisibility(
                    visible = showIndicator,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    val badgeBg = when (readerTheme) {
                        ReaderThemeMode.WHITE -> Color(0xFFFFFFFF)
                        ReaderThemeMode.CREAM -> Color(0xFFFAF6EE)
                        ReaderThemeMode.SEPIA -> Color(0xFFF5EBD9)
                        ReaderThemeMode.NIGHT -> Color(0xFF161D28)
                    }
                    val badgeBorder = when (readerTheme) {
                        ReaderThemeMode.WHITE -> Color(0x18000000)
                        ReaderThemeMode.CREAM -> Color(0x288C5E2D)
                        ReaderThemeMode.SEPIA -> Color(0x307C4F22)
                        ReaderThemeMode.NIGHT -> Color(0x33FFFFFF)
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = badgeBg,
                        shadowElevation = 4.dp,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = badgeBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .testTag("page_indicator_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isDragging) "$lastScrubbedPage" else "$currentPage",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = readerTheme.accentColor,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "/",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = readerTheme.textSecondaryColor.copy(alpha = 0.6f),
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "$totalPages",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = readerTheme.textSecondaryColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // The Grip Thumb Pill on Right Edge
                AnimatedVisibility(
                    visible = showIndicator,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val defaultThumbBg = when (readerTheme) {
                        ReaderThemeMode.WHITE -> Color(0xFFFFFFFF)
                        ReaderThemeMode.CREAM -> Color(0xFFFAF6EE)
                        ReaderThemeMode.SEPIA -> Color(0xFFF5EBD9)
                        ReaderThemeMode.NIGHT -> Color(0xFF1E2634)
                    }
                    val thumbBorder = when (readerTheme) {
                        ReaderThemeMode.WHITE -> Color(0x18000000)
                        ReaderThemeMode.CREAM -> Color(0x288C5E2D)
                        ReaderThemeMode.SEPIA -> Color(0x307C4F22)
                        ReaderThemeMode.NIGHT -> Color(0x33FFFFFF)
                    }

                    Surface(
                        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                        color = if (isDragging) readerTheme.accentColor else defaultThumbBg,
                        shadowElevation = if (isDragging) 6.dp else 3.dp,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .width(16.dp)
                            .height(44.dp)
                            .border(
                                width = 1.dp,
                                color = if (isDragging) Color(0x55FFFFFF) else thumbBorder,
                                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                            )
                    ) {
                        // Subtle grip indicator
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val gripColor = when {
                                isDragging -> Color.White
                                readerTheme.isDark -> Color(0xAAFFFFFF)
                                readerTheme == ReaderThemeMode.WHITE -> Color(0x55000000)
                                else -> Color(0x668C5E2D)
                            }
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 2.dp)
                                        .size(width = 6.dp, height = 2.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(gripColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

