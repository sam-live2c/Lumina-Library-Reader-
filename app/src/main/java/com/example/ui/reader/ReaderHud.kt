package com.example.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LuminaLuxurySlider
import com.example.ui.theme.LuminaAccentPrimary
import com.example.ui.theme.ReaderThemeMode

import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    title: String,
    currentPage: Int,
    totalPages: Int,
    isBookmarked: Boolean,
    readerTheme: ReaderThemeMode,
    isFullScreenModeEnabled: Boolean = false,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenThemeDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barBg = readerTheme.background
    val barText = readerTheme.textColor
    val barSubtext = readerTheme.textSecondaryColor
    val accentColor = readerTheme.accentColor

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = barBg,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("reader_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back to Library",
                    tint = barText,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = barText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Page $currentPage of $totalPages",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = barSubtext,
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Search in Book
                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("reader_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search in book",
                        tint = barText,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("reader_bookmark_button")
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove Bookmark" else "Add Bookmark",
                        tint = if (isBookmarked) accentColor else barText,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Refined typographic style icon (AA) for Reading & Appearance settings
                IconButton(
                    onClick = onOpenThemeDialog,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("reader_theme_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FormatSize,
                        contentDescription = "Text and Theme Settings",
                        tint = barText,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReaderBottomBar(
    currentPage: Int,
    totalPages: Int,
    isOverviewMode: Boolean,
    readerTheme: ReaderThemeMode,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onToggleOverviewMode: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenJumpDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barBg = readerTheme.background

    // Theme-tailored container colors for footer action pills
    val pillBgInactive = when (readerTheme) {
        ReaderThemeMode.WHITE -> Color(0xFFF1F5F9)
        ReaderThemeMode.CREAM -> Color(0xFFEBE2D3)
        ReaderThemeMode.SEPIA -> Color(0xFFE2D3BB)
        ReaderThemeMode.NIGHT -> Color(0xFF1E2634)
    }
    val pillBgActive = when (readerTheme) {
        ReaderThemeMode.WHITE -> Color(0xFF2563EB)
        ReaderThemeMode.CREAM -> Color(0xFF8C5E2D)
        ReaderThemeMode.SEPIA -> Color(0xFF7C4F22)
        ReaderThemeMode.NIGHT -> Color(0xFFDFAB72)
    }

    val barText = readerTheme.textColor
    val barSubtext = readerTheme.textSecondaryColor
    val activeTextColor = if (readerTheme == ReaderThemeMode.NIGHT) Color(0xFF0F141C) else Color.White

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = barBg,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Previous Page Button
                IconButton(
                    onClick = onPreviousPage,
                    enabled = currentPage > 1,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("reader_prev_page_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronLeft,
                        contentDescription = "Previous Page",
                        modifier = Modifier.size(24.dp),
                        tint = if (currentPage > 1) barText else barSubtext.copy(alpha = 0.35f)
                    )
                }

                // Middle Action Options Row - Free, unconstrained option labels
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 1. Overview Mode Toggle Option
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isOverviewMode) pillBgActive.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onToggleOverviewMode() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("reader_overview_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoStories,
                                contentDescription = "Overview Mode",
                                modifier = Modifier.size(20.dp),
                                tint = if (isOverviewMode) readerTheme.accentColor else barText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pages",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = if (isOverviewMode) FontWeight.Bold else FontWeight.Medium
                                ),
                                maxLines = 1,
                                softWrap = false,
                                color = if (isOverviewMode) readerTheme.accentColor else barText
                            )
                        }
                    }

                    // 2. Bookmarks / Table of Contents Option
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Transparent)
                            .clickable { onOpenBookmarks() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("reader_bookmarks_toc_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.List,
                                contentDescription = "Bookmarks",
                                modifier = Modifier.size(20.dp),
                                tint = barText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Marks",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                softWrap = false,
                                color = barText
                            )
                        }
                    }

                    // 3. Jump to Page / Progression Option
                    val percent = if (totalPages > 0) ((currentPage.toFloat() / totalPages.toFloat()) * 100).toInt() else 0
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Transparent)
                            .clickable { onOpenJumpDialog() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("reader_jump_dialog_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FindInPage,
                                contentDescription = "Jump to Page",
                                modifier = Modifier.size(20.dp),
                                tint = readerTheme.accentColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val label = "$percent%"
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                softWrap = false,
                                color = barText
                            )
                        }
                    }
                }

                // Next Page Button
                IconButton(
                    onClick = onNextPage,
                    enabled = currentPage < totalPages,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("reader_next_page_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Next Page",
                        modifier = Modifier.size(24.dp),
                        tint = if (currentPage < totalPages) barText else barSubtext.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingPageIndicator(
    isVisible: Boolean,
    currentPage: Int,
    totalPages: Int,
    readerTheme: ReaderThemeMode,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && totalPages > 0,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(180)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(150)),
        modifier = modifier
    ) {
        val percent = if (totalPages > 0) ((currentPage.toFloat() / totalPages.toFloat()) * 100).toInt() else 0
        val pillShape = RoundedCornerShape(24.dp)
        val pillBg = when (readerTheme) {
            ReaderThemeMode.WHITE -> Color(0xFFFFFFFF)
            ReaderThemeMode.CREAM -> Color(0xFFEFE8DB)
            ReaderThemeMode.SEPIA -> Color(0xFFE3D6BE)
            ReaderThemeMode.NIGHT -> Color(0xFF161D28)
        }
        val pillBorder = when (readerTheme) {
            ReaderThemeMode.WHITE -> Color(0x1F000000)
            ReaderThemeMode.CREAM -> Color(0x338C5E2D)
            ReaderThemeMode.SEPIA -> Color(0x387C4F22)
            ReaderThemeMode.NIGHT -> Color(0x33FFFFFF)
        }

        Box(
            modifier = Modifier
                .clip(pillShape)
                .background(pillBg)
                .clickable { onClick() }
                .testTag("reader_floating_page_indicator")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "$currentPage / $totalPages",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.3.sp
                    ),
                    color = readerTheme.textColor
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = readerTheme.textColor.copy(alpha = 0.6f)
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    ),
                    color = readerTheme.accentColor
                )
            }
        }
    }
}
