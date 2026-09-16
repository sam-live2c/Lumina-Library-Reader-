package com.example.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.data.repository.AppSettingsManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ReaderScreen(
    bookId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val soundManager = remember { PageTurnSoundManager(context) }

    val activity = context as? Activity
    LaunchedEffect(uiState.isFullScreenModeEnabled, uiState.readerTheme) {
        AppSettingsManager.applySystemBars(
            activity = activity,
            isFullScreen = uiState.isFullScreenModeEnabled,
            isDarkTheme = uiState.readerTheme.isDark
        )
    }

    DisposableEffect(bookId) {
        viewModel.prepareForBook(bookId)
        viewModel.loadBook(bookId)
        onDispose {
            soundManager.release()
            viewModel.clearCurrentBook()
        }
    }

    BackHandler {
        if (uiState.isSearchOpen) {
            keyboardController?.hide()
            focusManager.clearFocus()
            viewModel.closeSearch()
        } else if (uiState.isAnnotationMode) {
            viewModel.toggleAnnotationMode()
        } else {
            keyboardController?.hide()
            focusManager.clearFocus()
            onNavigateBack()
        }
    }

    // Manage keyboard when search state changes
    LaunchedEffect(uiState.isSearchOpen) {
        if (!uiState.isSearchOpen) {
            keyboardController?.hide()
        }
    }

    var isDraggingPen by remember { mutableStateOf(false) }
    var isOverDismissTarget by remember { mutableStateOf(false) }

    val canvasInsetsModifier = if (uiState.isFullScreenModeEnabled) {
        Modifier
            .fillMaxSize()
            .padding(top = 5.dp)
    } else {
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(uiState.readerTheme.background)
            .testTag("reader_screen_container")
    ) {
        Box(
            modifier = canvasInsetsModifier
                .onGloballyPositioned { coordinates ->
                    viewModel.setCanvasDimensions(coordinates.size.width, coordinates.size.height)
                }
        ) {
            if (uiState.isContinuousScrollMode) {
                // Vertically Aligned Continuous Scroll / Book Overview Mode
                ContinuousVerticalReaderCanvas(
                    filePath = uiState.book?.filePath,
                    totalPages = uiState.totalPages,
                    currentPageIndex = uiState.currentPageIndex,
                    readerTheme = uiState.readerTheme,
                    pdfRendererManager = viewModel.pdfManager,
                    isFullScreen = uiState.isFullScreenModeEnabled,
                    isSmartMarginFit = uiState.isSmartMarginFitEnabled,
                    isAnnotationMode = uiState.isAnnotationMode,
                    activeTool = uiState.activeTool,
                    isEraserActive = uiState.isEraserActive,
                    activeColor = uiState.activeColor,
                    strokeWidth = uiState.strokeWidth,
                    isAnnotationsVisible = uiState.isAnnotationsVisible,
                    currentPageStrokes = uiState.currentPageStrokes,
                    onStrokeCompleted = { viewModel.addStroke(it) },
                    onEraseStroke = { viewModel.eraseStroke(it) },
                    onStrokesUpdated = { viewModel.updateStrokes(it) },
                    onPageChanged = { viewModel.goToPage(it) },
                    onToggleHud = { viewModel.toggleHud() },
                    onDoubleTap = { viewModel.summonPen() },
                    onPageClick = { targetPage ->
                        viewModel.switchToPageTurnMode(targetPage)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Single-Page 3D Realistic Turn / Slide Canvas with integrated zoom-locked annotation layer
                PhysicalBookReaderCanvas(
                    currentPageBitmap = uiState.currentPageBitmap,
                    nextPageBitmap = uiState.nextPageBitmap,
                    previousPageBitmap = uiState.previousPageBitmap,
                    currentPageIndex = uiState.currentPageIndex,
                    totalPages = uiState.totalPages,
                    isBookmarked = uiState.isBookmarked,
                    readerTheme = uiState.readerTheme,
                    isFullScreen = uiState.isFullScreenModeEnabled,
                    flipStyle = uiState.flipStyle,
                    isSmartMarginFit = uiState.isSmartMarginFitEnabled,
                    pageVerticalPosition = uiState.pageVerticalPosition,
                    isAnnotationMode = uiState.isAnnotationMode,
                    activeTool = uiState.activeTool,
                    isEraserActive = uiState.isEraserActive,
                    activeColor = uiState.activeColor,
                    strokeWidth = uiState.strokeWidth,
                    isAnnotationsVisible = uiState.isAnnotationsVisible,
                    currentPageStrokes = uiState.currentPageStrokes,
                    onStrokeCompleted = { viewModel.addStroke(it) },
                    onEraseStroke = { viewModel.eraseStroke(it) },
                    onStrokesUpdated = { viewModel.updateStrokes(it) },
                    onNextPage = {
                        soundManager.playPageTurnSound()
                        viewModel.nextPage()
                    },
                    onPreviousPage = {
                        soundManager.playPageTurnSound()
                        viewModel.previousPage()
                    },
                    onToggleHud = { viewModel.toggleHud() },
                    onDoubleTap = { viewModel.summonPen() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Fast Vertical Luxury Scrubber Bar along right edge
        if (!uiState.isAnnotationMode && !uiState.isSearchOpen && uiState.totalPages > 1) {
            FastVerticalScrollHandle(
                currentPage = uiState.currentPageIndex,
                totalPages = uiState.totalPages,
                readerTheme = uiState.readerTheme,
                isHudVisible = uiState.isHudVisible,
                onPageSelected = {
                    viewModel.goToPage(it)
                },
                onIndicatorClick = {
                    viewModel.openJumpDialog()
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
            )
        }

        // Clean PDF Open Loading Screen with a circle (guarantees zero flash of previous books)
        val isCurrentBookReady = !uiState.isLoading && uiState.book?.id == bookId && (uiState.currentPageBitmap != null || uiState.isContinuousScrollMode)
        if (!isCurrentBookReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(uiState.readerTheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = uiState.readerTheme.accentColor,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("reader_loading_spinner")
                )
            }
        }

        // Top Search Bar (When searching inside book)
        AnimatedVisibility(
            visible = uiState.isSearchOpen,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            InBookSearchBar(
                query = uiState.searchQuery,
                matches = uiState.searchResults,
                currentMatchIndex = uiState.currentSearchMatchIndex,
                readerTheme = uiState.readerTheme,
                isFullScreenModeEnabled = uiState.isFullScreenModeEnabled,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onNextMatch = { viewModel.nextSearchMatch() },
                onPreviousMatch = { viewModel.previousSearchMatch() },
                onSelectMatch = { viewModel.selectSearchMatch(it, closeSearchBar = true) },
                onSubmitSearch = { viewModel.submitSearch() },
                onClose = { viewModel.closeSearch() }
            )
        }

        // Floating Search Results Pill (After search is entered, search bar closes and user only sees search results)
        AnimatedVisibility(
            visible = !uiState.isSearchOpen && uiState.searchResults.isNotEmpty() && !uiState.isHudVisible && !uiState.isAnnotationMode,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(160)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(140)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            SearchResultsPill(
                query = uiState.searchQuery,
                currentMatchIndex = uiState.currentSearchMatchIndex,
                totalMatches = uiState.searchResults.size,
                readerTheme = uiState.readerTheme,
                isFullScreenModeEnabled = uiState.isFullScreenModeEnabled,
                onPreviousMatch = { viewModel.previousSearchMatch() },
                onNextMatch = { viewModel.nextSearchMatch() },
                onReopenSearch = { viewModel.openSearch() },
                onClearResults = { viewModel.clearSearchResults() }
            )
        }

        // Top Navigation Bar (Revealed on centre tap)
        AnimatedVisibility(
            visible = uiState.isHudVisible && !uiState.isSearchOpen,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                title = uiState.book?.title ?: "Document",
                currentPage = uiState.currentPageIndex,
                totalPages = uiState.totalPages,
                isBookmarked = uiState.isBookmarked,
                readerTheme = uiState.readerTheme,
                isFullScreenModeEnabled = uiState.isFullScreenModeEnabled,
                onBack = onNavigateBack,
                onToggleBookmark = { viewModel.toggleBookmark() },
                onOpenSearch = { viewModel.openSearch() },
                onOpenThemeDialog = { viewModel.openThemeDialog() }
            )
        }

        // Bottom Navigation Bar (Revealed on centre tap)
        AnimatedVisibility(
            visible = uiState.isHudVisible && !uiState.isSearchOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                currentPage = uiState.currentPageIndex,
                totalPages = uiState.totalPages,
                isOverviewMode = uiState.isContinuousScrollMode,
                readerTheme = uiState.readerTheme,
                onPreviousPage = {
                    soundManager.playPageTurnSound()
                    viewModel.previousPage()
                },
                onNextPage = {
                    soundManager.playPageTurnSound()
                    viewModel.nextPage()
                },
                onToggleOverviewMode = { viewModel.toggleContinuousScrollMode() },
                onOpenBookmarks = { viewModel.openTocSheet() },
                onOpenJumpDialog = { viewModel.openJumpDialog() }
            )
        }

        // Bottom-Center Dismiss Target for Floating Pen Drag
        FloatingPenDismissTarget(
            isVisible = isDraggingPen,
            isTargetActive = isOverDismissTarget,
            readerTheme = uiState.readerTheme,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        )

        // Annotation Floating Capsule Toolbar
        AnimatedVisibility(
            visible = uiState.isAnnotationMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            AnnotationCapsuleToolbar(
                activeTool = uiState.activeTool,
                isEraserActive = uiState.isEraserActive,
                activeColor = uiState.activeColor,
                isAnnotationsVisible = uiState.isAnnotationsVisible,
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                readerTheme = uiState.readerTheme,
                onSelectTool = { viewModel.setAnnotationTool(it) },
                onToggleEraser = { viewModel.toggleEraser() },
                onSelectColor = { viewModel.setAnnotationColor(it) },
                onToggleVisibility = { viewModel.toggleAnnotationVisibility() },
                onUndo = { viewModel.undoStroke() },
                onRedo = { viewModel.redoStroke() },
                onDone = { viewModel.toggleAnnotationMode() }
            )
        }

        // Annotation Quick Toggle FAB (Ergonomic thumb reach position on mobile)
        AnimatedVisibility(
            visible = !uiState.isAnnotationMode && !uiState.isSearchOpen && !uiState.isHudVisible && !uiState.isPenDismissed,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(180)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(150)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 24.dp)
        ) {
            AnnotationFloatingFab(
                isAnnotationMode = false,
                readerTheme = uiState.readerTheme,
                onClick = { viewModel.toggleAnnotationMode() },
                onDismiss = { viewModel.dismissPen() },
                onDragStateChanged = { dragging, overTarget ->
                    isDraggingPen = dragging
                    isOverDismissTarget = overTarget
                }
            )
        }

        // Appearance & Themes Dialog (Display, Themes, Page Transition, Vertical Position, Smart Fit, Contrast, Full Screen)
        if (uiState.isThemeDialogOpen) {
            ReaderThemeAppearanceDialog(
                currentTheme = uiState.readerTheme,
                currentFlipStyle = uiState.flipStyle,
                pageVerticalPosition = uiState.pageVerticalPosition,
                isSmartMarginFitEnabled = uiState.isSmartMarginFitEnabled,
                isEnhancedContrastEnabled = uiState.isEnhancedContrastEnabled,
                isFullScreenModeEnabled = uiState.isFullScreenModeEnabled,
                onSelectTheme = { viewModel.setReaderTheme(it) },
                onSelectFlipStyle = { viewModel.setFlipStyle(it) },
                onSelectPageVerticalPosition = { viewModel.setPageVerticalPosition(it) },
                onToggleSmartMarginFit = { viewModel.toggleSmartMarginFit() },
                onToggleEnhancedContrast = { viewModel.toggleEnhancedContrast() },
                onToggleFullScreenMode = { viewModel.toggleFullScreenMode() },
                onDismiss = { viewModel.closeThemeDialog() }
            )
        }

        // Bookmarks Modal Bottom Sheet
        if (uiState.isTocSheetOpen) {
            BookmarksBottomSheet(
                bookmarkedPages = uiState.bookmarkedPages,
                currentPage = uiState.currentPageIndex,
                totalPages = uiState.totalPages,
                readerTheme = uiState.readerTheme,
                isFullScreenModeEnabled = uiState.isFullScreenModeEnabled,
                onSelectPage = {
                    soundManager.playPageTurnSound()
                    viewModel.goToPage(it)
                },
                onToggleBookmark = { page ->
                    if (page == uiState.currentPageIndex) {
                        viewModel.toggleBookmark()
                    } else {
                        soundManager.playPageTurnSound()
                        viewModel.goToPage(page)
                    }
                },
                onDismiss = { viewModel.closeTocSheet() }
            )
        }

        // Jump To Page Dialog
        if (uiState.isJumpDialogOpen) {
            JumpToPageDialog(
                currentPage = uiState.currentPageIndex,
                totalPages = uiState.totalPages,
                readerTheme = uiState.readerTheme,
                isFullScreenModeEnabled = uiState.isFullScreenModeEnabled,
                onJump = {
                    soundManager.playPageTurnSound()
                    viewModel.goToPage(it)
                },
                onDismiss = { viewModel.closeJumpDialog() }
            )
        }
    }
}
