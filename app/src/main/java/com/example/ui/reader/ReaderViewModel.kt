package com.example.ui.reader

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.AnnotationType
import com.example.data.model.BookEntity
import com.example.data.pdf.PdfRendererManager
import com.example.data.repository.AnnotationRepository
import com.example.data.repository.AnnotationStroke
import com.example.data.repository.AppSettingsManager
import com.example.data.repository.BookRepository
import com.example.data.repository.BookSearchManager
import com.example.data.repository.SearchMatch
import com.example.ui.theme.ReaderThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val book: BookEntity? = null,
    val currentPageIndex: Int = 1, // 1-based index
    val totalPages: Int = 1,
    val isBookmarked: Boolean = false,
    val bookmarkedPages: List<Int> = emptyList(),
    val currentPageBitmap: Bitmap? = null,
    val nextPageBitmap: Bitmap? = null,
    val previousPageBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val isHudVisible: Boolean = false,
    val isThemeDialogOpen: Boolean = false,
    val isJumpDialogOpen: Boolean = false,
    val isTocSheetOpen: Boolean = false,
    val readerTheme: ReaderThemeMode = ReaderThemeMode.CREAM,
    val flipStyle: PageFlipStyle = PageFlipStyle.REALISTIC_CURL,
    val pageVerticalPosition: PageVerticalPosition = PageVerticalPosition.CENTER,
    val readingSessionMinutes: Int = 0,

    // Annotation Suite
    val isAnnotationMode: Boolean = false,
    val isPenDismissed: Boolean = false,
    val activeTool: AnnotationType = AnnotationType.HIGHLIGHTER,
    val isEraserActive: Boolean = false,
    val activeColor: Color = Color(0xFFFFD54F), // Radiant Amber Yellow
    val strokeWidth: Float = 4f,
    val isAnnotationsVisible: Boolean = true,
    val currentPageStrokes: List<AnnotationStroke> = emptyList(),
    val undoHistory: List<List<AnnotationStroke>> = emptyList(),
    val redoHistory: List<List<AnnotationStroke>> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,

    // In-Book Search
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchMatch> = emptyList(),
    val currentSearchMatchIndex: Int = 0,

    // Reading Mode & Smart Formatting
    val isContinuousScrollMode: Boolean = false,
    val isSmartMarginFitEnabled: Boolean = true, // Default optimal formatting so user never suffers from formatting
    val isEnhancedContrastEnabled: Boolean = true,
    val isFullScreenModeEnabled: Boolean = false
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val pdfRendererManager = PdfRendererManager(application)
    val pdfManager: PdfRendererManager get() = pdfRendererManager
    private val repository = BookRepository(application, db.bookDao(), pdfRendererManager, db.annotationDao())
    private val annotationRepository = AnnotationRepository(db.annotationDao())
    private val searchManager = BookSearchManager()

    private val prefs: SharedPreferences = application.getSharedPreferences("lumina_settings_prefs", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        ReaderUiState(
            readerTheme = AppSettingsManager.currentAppTheme.value,
            isFullScreenModeEnabled = prefs.getBoolean("pref_full_screen_mode", false),
            pageVerticalPosition = try {
                PageVerticalPosition.valueOf(
                    application.getSharedPreferences("lumina_settings_prefs", Context.MODE_PRIVATE)
                        .getString("pref_default_page_vertical_position", PageVerticalPosition.CENTER.name)
                        ?: PageVerticalPosition.CENTER.name
                )
            } catch (_: Throwable) {
                PageVerticalPosition.CENTER
            }
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        AppSettingsManager.init(application)
        viewModelScope.launch {
            AppSettingsManager.isFullScreenModeEnabled.collect { isFs ->
                _uiState.update { it.copy(isFullScreenModeEnabled = isFs) }
            }
        }
        viewModelScope.launch {
            AppSettingsManager.currentAppTheme.collect { theme ->
                _uiState.update { it.copy(readerTheme = theme) }
            }
        }
    }

    private var renderJob: Job? = null
    private var searchJob: Job? = null
    private var targetRenderWidth: Int = 1080
    private var targetRenderHeight: Int = 1920

    fun prepareForBook(bookId: Long) {
        renderJob?.cancel()
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                book = null,
                currentPageBitmap = null,
                nextPageBitmap = null,
                previousPageBitmap = null,
                currentPageStrokes = emptyList(),
                undoHistory = emptyList(),
                redoHistory = emptyList(),
                canUndo = false,
                canRedo = false,
                isLoading = true,
                isSearchOpen = false,
                searchQuery = "",
                searchResults = emptyList()
            )
        }
    }

    fun cancelPendingJobs() {
        renderJob?.cancel()
        searchJob?.cancel()
    }

    fun clearCurrentBook() {
        renderJob?.cancel()
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                book = null,
                currentPageBitmap = null,
                nextPageBitmap = null,
                previousPageBitmap = null,
                currentPageStrokes = emptyList(),
                undoHistory = emptyList(),
                redoHistory = emptyList(),
                canUndo = false,
                canRedo = false,
                isLoading = true,
                isSearchOpen = false,
                searchQuery = "",
                searchResults = emptyList()
            )
        }
    }

    fun loadBook(bookId: Long) {
        renderJob?.cancel()
        searchJob?.cancel()

        // 1. Immediately reset state so no stale pages from previous book are visible
        _uiState.update {
            it.copy(
                book = null,
                currentPageBitmap = null,
                nextPageBitmap = null,
                previousPageBitmap = null,
                currentPageStrokes = emptyList(),
                undoHistory = emptyList(),
                redoHistory = emptyList(),
                canUndo = false,
                canRedo = false,
                isLoading = true,
                isSearchOpen = false,
                searchQuery = "",
                searchResults = emptyList()
            )
        }

        viewModelScope.launch {
            val book = repository.getBookByIdSync(bookId)
            if (book != null) {
                // 1. Immediately store book metadata so title and author are stable and never flicker
                _uiState.update {
                    it.copy(
                        book = book,
                        currentPageIndex = book.currentPage.coerceAtLeast(1)
                    )
                }

                val totalPages = pdfRendererManager.openFile(book.filePath).coerceAtLeast(book.totalPages)
                val initialPage = book.currentPage.coerceIn(1, totalPages)
                val bookmarks = book.getBookmarkPages().toList().sorted()

                // Mark book as opened so read status and reading progress are activated and recorded
                repository.markBookOpened(book.id, initialPage, totalPages)

                // 2. Render initial page FIRST before dismissing loading screen to guarantee zero flash
                val page0 = initialPage - 1
                val currentBmp = withContext(Dispatchers.IO) {
                    pdfRendererManager.renderPage(
                        filePath = book.filePath,
                        pageIndex = page0,
                        targetWidth = targetRenderWidth,
                        targetHeight = targetRenderHeight
                    )
                }

                _uiState.update {
                    it.copy(
                        book = book.copy(hasBeenOpened = true, currentPage = initialPage, totalPages = totalPages),
                        currentPageIndex = initialPage,
                        totalPages = totalPages,
                        isBookmarked = bookmarks.contains(initialPage),
                        bookmarkedPages = bookmarks,
                        currentPageBitmap = currentBmp,
                        isLoading = false
                    )
                }

                loadAnnotationsForPage(book.id, initialPage)

                // 3. Pre-render adjacent pages asynchronously in background without delaying UI reveal
                viewModelScope.launch(Dispatchers.IO) {
                    val nextBmp = if (initialPage < totalPages) {
                        pdfRendererManager.renderPage(book.filePath, page0 + 1, targetRenderWidth, targetRenderHeight)
                    } else null

                    val prevBmp = if (initialPage > 1) {
                        pdfRendererManager.renderPage(book.filePath, page0 - 1, targetRenderWidth, targetRenderHeight)
                    } else null

                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                nextPageBitmap = nextBmp,
                                previousPageBitmap = prevBmp
                            )
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setCanvasDimensions(width: Int, height: Int) {
        if (width > 100 && height > 100) {
            val widthDiff = kotlin.math.abs(width - targetRenderWidth)
            val heightDiff = kotlin.math.abs(height - targetRenderHeight)
            if (widthDiff > 240 || heightDiff > 240) {
                targetRenderWidth = width
                targetRenderHeight = height
            }
        }
    }

    fun toggleHud() {
        _uiState.update {
            if (it.isAnnotationMode || it.isSearchOpen) it
            else it.copy(isHudVisible = !it.isHudVisible)
        }
    }

    fun setHudVisible(visible: Boolean) {
        _uiState.update { it.copy(isHudVisible = visible) }
    }

    fun hideHud() {
        _uiState.update { it.copy(isHudVisible = false) }
    }

    fun showHud() {
        _uiState.update { it.copy(isHudVisible = true) }
    }

    fun dismissPen() {
        _uiState.update { it.copy(isPenDismissed = true) }
    }

    fun summonPen() {
        _uiState.update { it.copy(isPenDismissed = false) }
    }

    fun nextPage() {
        val state = _uiState.value
        if (state.currentPageIndex < state.totalPages) {
            val nextIdx = state.currentPageIndex + 1
            val isBm = state.bookmarkedPages.contains(nextIdx)
            val book = state.book ?: return
            val cachedNext = pdfRendererManager.getCachedPage(book.filePath, nextIdx - 1)
            val promotedCurrentBmp = state.nextPageBitmap ?: cachedNext ?: state.currentPageBitmap

            _uiState.update {
                it.copy(
                    currentPageIndex = nextIdx,
                    currentPageBitmap = promotedCurrentBmp,
                    isBookmarked = isBm,
                    undoHistory = emptyList(),
                    redoHistory = emptyList(),
                    canUndo = false,
                    canRedo = false
                )
            }

            loadAnnotationsForPage(book.id, nextIdx)
            viewModelScope.launch {
                repository.updateProgress(book.id, nextIdx)
            }
            renderCurrentAndAdjacentPages(book.filePath, nextIdx, state.totalPages)
        }
    }

    fun previousPage() {
        val state = _uiState.value
        if (state.currentPageIndex > 1) {
            val prevIdx = state.currentPageIndex - 1
            val isBm = state.bookmarkedPages.contains(prevIdx)
            val book = state.book ?: return
            val cachedPrev = pdfRendererManager.getCachedPage(book.filePath, prevIdx - 1)
            val promotedCurrentBmp = state.previousPageBitmap ?: cachedPrev ?: state.currentPageBitmap

            _uiState.update {
                it.copy(
                    currentPageIndex = prevIdx,
                    currentPageBitmap = promotedCurrentBmp,
                    isBookmarked = isBm,
                    undoHistory = emptyList(),
                    redoHistory = emptyList(),
                    canUndo = false,
                    canRedo = false
                )
            }

            loadAnnotationsForPage(book.id, prevIdx)
            viewModelScope.launch {
                repository.updateProgress(book.id, prevIdx)
            }
            renderCurrentAndAdjacentPages(book.filePath, prevIdx, state.totalPages)
        }
    }

    fun goToPage(page: Int) {
        val state = _uiState.value
        val clampedPage = page.coerceIn(1, state.totalPages)
        if (clampedPage == state.currentPageIndex && state.currentPageBitmap != null) return

        val book = state.book ?: return
        val isBm = state.bookmarkedPages.contains(clampedPage)
        val cachedTarget = pdfRendererManager.getCachedPage(book.filePath, clampedPage - 1)

        _uiState.update {
            it.copy(
                currentPageIndex = clampedPage,
                currentPageBitmap = cachedTarget ?: it.currentPageBitmap,
                isBookmarked = isBm,
                undoHistory = emptyList(),
                redoHistory = emptyList(),
                canUndo = false,
                canRedo = false
            )
        }

        loadAnnotationsForPage(book.id, clampedPage)

        viewModelScope.launch {
            repository.updateProgress(book.id, clampedPage)
        }

        renderCurrentAndAdjacentPages(book.filePath, clampedPage, state.totalPages)
    }

    fun toggleBookmark() {
        val page = _uiState.value.currentPageIndex
        toggleBookmarkForPage(page)
    }

    fun toggleBookmarkForPage(page: Int) {
        val state = _uiState.value
        val book = state.book ?: return

        viewModelScope.launch {
            val isNowBm = repository.toggleBookmark(book.id, page)
            val updated = if (isNowBm) {
                (state.bookmarkedPages + page).distinct().sorted()
            } else {
                state.bookmarkedPages.filter { it != page }
            }
            _uiState.update {
                it.copy(
                    isBookmarked = if (state.currentPageIndex == page) isNowBm else it.isBookmarked,
                    bookmarkedPages = updated
                )
            }
        }
    }

    fun setReaderTheme(theme: ReaderThemeMode) {
        AppSettingsManager.setAppTheme(theme)
        _uiState.update { it.copy(readerTheme = theme) }
    }

    fun setFlipStyle(style: PageFlipStyle) {
        _uiState.update { it.copy(flipStyle = style) }
    }

    fun setPageVerticalPosition(position: PageVerticalPosition) {
        prefs.edit().putString("pref_default_page_vertical_position", position.name).apply()
        _uiState.update { it.copy(pageVerticalPosition = position) }
    }

    fun toggleContinuousScrollMode() {
        val nextMode = !_uiState.value.isContinuousScrollMode
        if (!nextMode) {
            switchToPageTurnMode()
        } else {
            _uiState.update { it.copy(isContinuousScrollMode = true) }
        }
    }

    fun switchToPageTurnMode(targetPage: Int? = null) {
        val state = _uiState.value
        val page = (targetPage ?: state.currentPageIndex).coerceIn(1, state.totalPages)
        val book = state.book ?: return
        _uiState.update {
            it.copy(
                isContinuousScrollMode = false,
                currentPageIndex = page,
                isBookmarked = it.bookmarkedPages.contains(page)
            )
        }
        loadAnnotationsForPage(book.id, page)
        renderCurrentAndAdjacentPages(book.filePath, page, state.totalPages)
    }

    fun toggleSmartMarginFit() {
        _uiState.update { it.copy(isSmartMarginFitEnabled = !it.isSmartMarginFitEnabled) }
    }

    fun toggleEnhancedContrast() {
        _uiState.update { it.copy(isEnhancedContrastEnabled = !it.isEnhancedContrastEnabled) }
    }

    fun toggleFullScreenMode() {
        val next = !_uiState.value.isFullScreenModeEnabled
        AppSettingsManager.setFullScreenMode(next)
        _uiState.update { it.copy(isFullScreenModeEnabled = next) }
    }

    fun openThemeDialog() {
        _uiState.update { it.copy(isThemeDialogOpen = true) }
    }

    fun closeThemeDialog() {
        _uiState.update { it.copy(isThemeDialogOpen = false) }
    }

    fun openJumpDialog() {
        _uiState.update { it.copy(isJumpDialogOpen = true) }
    }

    fun closeJumpDialog() {
        _uiState.update { it.copy(isJumpDialogOpen = false) }
    }

    fun openTocSheet() {
        _uiState.update { it.copy(isTocSheetOpen = true) }
    }

    fun closeTocSheet() {
        _uiState.update { it.copy(isTocSheetOpen = false) }
    }

    // ==========================================
    // ANNOTATION ENGINE
    // ==========================================

    fun toggleAnnotationMode() {
        _uiState.update {
            val nextMode = !it.isAnnotationMode
            it.copy(
                isAnnotationMode = nextMode,
                isHudVisible = false,
                isSearchOpen = false
            )
        }
    }

    fun setAnnotationTool(tool: AnnotationType) {
        _uiState.update {
            it.copy(
                activeTool = tool,
                isEraserActive = false,
                strokeWidth = when (tool) {
                    AnnotationType.PEN -> 3.5f
                    AnnotationType.HIGHLIGHTER -> 12f
                    AnnotationType.STRIKE_THROUGH -> 3.5f
                }
            )
        }
    }

    fun toggleEraser() {
        _uiState.update { it.copy(isEraserActive = !it.isEraserActive) }
    }

    fun setAnnotationColor(color: Color) {
        _uiState.update { it.copy(activeColor = color) }
    }

    fun toggleAnnotationVisibility() {
        _uiState.update { it.copy(isAnnotationsVisible = !it.isAnnotationsVisible) }
    }

    fun addStroke(stroke: AnnotationStroke) {
        val state = _uiState.value
        val book = state.book ?: return
        val strokeWithInfo = stroke.copy(
            bookId = book.id,
            pageIndex = state.currentPageIndex
        )

        val previousStrokes = state.currentPageStrokes
        val updatedStrokes = previousStrokes + strokeWithInfo
        val newUndoHistory = state.undoHistory + listOf(previousStrokes)

        _uiState.update {
            it.copy(
                currentPageStrokes = updatedStrokes,
                undoHistory = newUndoHistory,
                redoHistory = emptyList(),
                canUndo = true,
                canRedo = false
            )
        }

        viewModelScope.launch {
            annotationRepository.saveStroke(strokeWithInfo)
        }
    }

    fun updateStrokes(strokes: List<AnnotationStroke>, pushToHistory: Boolean = true, preActionState: List<AnnotationStroke>? = null) {
        val state = _uiState.value
        val book = state.book ?: return
        val pageIndex = state.currentPageIndex
        val updatedStrokes = strokes.map { it.copy(bookId = book.id, pageIndex = pageIndex) }

        val previousState = preActionState ?: state.currentPageStrokes
        val newUndoHistory = if (pushToHistory) state.undoHistory + listOf(previousState) else state.undoHistory

        _uiState.update {
            it.copy(
                currentPageStrokes = updatedStrokes,
                undoHistory = newUndoHistory,
                redoHistory = if (pushToHistory) emptyList() else it.redoHistory,
                canUndo = newUndoHistory.isNotEmpty() || updatedStrokes.isNotEmpty(),
                canRedo = if (pushToHistory) false else it.redoHistory.isNotEmpty()
            )
        }

        viewModelScope.launch {
            annotationRepository.clearPage(book.id, pageIndex)
            updatedStrokes.forEach { stroke ->
                annotationRepository.saveStroke(stroke)
            }
        }
    }

    fun eraseStroke(strokeId: Long) {
        val state = _uiState.value
        val book = state.book ?: return
        val pageIndex = state.currentPageIndex
        val previousStrokes = state.currentPageStrokes
        val updated = previousStrokes.filter { it.id != strokeId }
        val newUndoHistory = state.undoHistory + listOf(previousStrokes)

        _uiState.update {
            it.copy(
                currentPageStrokes = updated,
                undoHistory = newUndoHistory,
                redoHistory = emptyList(),
                canUndo = newUndoHistory.isNotEmpty() || updated.isNotEmpty(),
                canRedo = false
            )
        }
        viewModelScope.launch {
            annotationRepository.deleteStroke(strokeId)
        }
    }

    fun undoStroke() {
        val state = _uiState.value
        val book = state.book ?: return
        val pageIndex = state.currentPageIndex
        val currentStrokes = state.currentPageStrokes

        if (state.undoHistory.isNotEmpty()) {
            val previousStrokes = state.undoHistory.last()
            val newUndoHistory = state.undoHistory.dropLast(1)
            val newRedoHistory = state.redoHistory + listOf(currentStrokes)

            _uiState.update {
                it.copy(
                    currentPageStrokes = previousStrokes,
                    undoHistory = newUndoHistory,
                    redoHistory = newRedoHistory,
                    canUndo = newUndoHistory.isNotEmpty() || previousStrokes.isNotEmpty(),
                    canRedo = true
                )
            }

            viewModelScope.launch {
                annotationRepository.clearPage(book.id, pageIndex)
                previousStrokes.forEach { annotationRepository.saveStroke(it) }
            }
        } else if (currentStrokes.isNotEmpty()) {
            val newStrokes = currentStrokes.dropLast(1)
            val newRedoHistory = state.redoHistory + listOf(currentStrokes)

            _uiState.update {
                it.copy(
                    currentPageStrokes = newStrokes,
                    undoHistory = emptyList(),
                    redoHistory = newRedoHistory,
                    canUndo = newStrokes.isNotEmpty(),
                    canRedo = true
                )
            }

            viewModelScope.launch {
                annotationRepository.clearPage(book.id, pageIndex)
                newStrokes.forEach { annotationRepository.saveStroke(it) }
            }
        }
    }

    fun redoStroke() {
        val state = _uiState.value
        val book = state.book ?: return
        val pageIndex = state.currentPageIndex
        val currentStrokes = state.currentPageStrokes

        if (state.redoHistory.isNotEmpty()) {
            val nextStrokes = state.redoHistory.last()
            val newRedoHistory = state.redoHistory.dropLast(1)
            val newUndoHistory = state.undoHistory + listOf(currentStrokes)

            _uiState.update {
                it.copy(
                    currentPageStrokes = nextStrokes,
                    undoHistory = newUndoHistory,
                    redoHistory = newRedoHistory,
                    canUndo = true,
                    canRedo = newRedoHistory.isNotEmpty()
                )
            }

            viewModelScope.launch {
                annotationRepository.clearPage(book.id, pageIndex)
                nextStrokes.forEach { annotationRepository.saveStroke(it) }
            }
        }
    }

    private fun loadAnnotationsForPage(bookId: Long, pageIndex: Int) {
        viewModelScope.launch {
            val strokes = annotationRepository.getStrokesForPage(bookId, pageIndex)
            _uiState.update {
                it.copy(
                    currentPageStrokes = strokes,
                    undoHistory = emptyList(),
                    redoHistory = emptyList(),
                    canUndo = strokes.isNotEmpty(),
                    canRedo = false
                )
            }
        }
    }

    // ==========================================
    // IN-BOOK SEARCH ENGINE
    // ==========================================

    fun openSearch() {
        _uiState.update {
            it.copy(
                isSearchOpen = true,
                isHudVisible = false,
                isAnnotationMode = false
            )
        }
    }

    fun closeSearch() {
        _uiState.update {
            it.copy(
                isSearchOpen = false,
                searchQuery = "",
                searchResults = emptyList(),
                currentSearchMatchIndex = 0
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    currentSearchMatchIndex = 0
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            val state = _uiState.value
            val book = state.book ?: return@launch
            val results = searchManager.searchInBook(book.title, book.filePath, state.totalPages, query)
            _uiState.update {
                it.copy(
                    searchResults = results,
                    currentSearchMatchIndex = 0
                )
            }
            // Do not call goToPage while user is typing; results appear in suggestions
        }
    }

    fun submitSearch() {
        val state = _uiState.value
        val book = state.book ?: return
        val query = state.searchQuery.trim()
        if (query.isBlank()) {
            closeSearch()
            return
        }

        // Close search bar input so user only sees search results
        _uiState.update { it.copy(isSearchOpen = false) }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val results = if (state.searchResults.isNotEmpty()) {
                state.searchResults
            } else {
                searchManager.searchInBook(book.title, book.filePath, state.totalPages, query)
            }
            _uiState.update {
                it.copy(
                    searchResults = results,
                    currentSearchMatchIndex = 0,
                    isSearchOpen = false
                )
            }
            if (results.isNotEmpty()) {
                goToPage(results.first().pageIndex)
            }
        }
    }

    fun selectSearchMatch(index: Int, closeSearchBar: Boolean = false) {
        val state = _uiState.value
        if (index in state.searchResults.indices) {
            val match = state.searchResults[index]
            _uiState.update {
                it.copy(
                    currentSearchMatchIndex = index,
                    isSearchOpen = if (closeSearchBar) false else it.isSearchOpen
                )
            }
            goToPage(match.pageIndex)
        }
    }

    fun dismissSearchBarInput() {
        _uiState.update { it.copy(isSearchOpen = false) }
    }

    fun clearSearchResults() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                isSearchOpen = false,
                searchQuery = "",
                searchResults = emptyList(),
                currentSearchMatchIndex = 0
            )
        }
    }

    fun nextSearchMatch() {
        val state = _uiState.value
        if (state.searchResults.isNotEmpty()) {
            val nextIdx = (state.currentSearchMatchIndex + 1) % state.searchResults.size
            val match = state.searchResults[nextIdx]
            _uiState.update { it.copy(currentSearchMatchIndex = nextIdx) }
            goToPage(match.pageIndex)
        }
    }

    fun previousSearchMatch() {
        val state = _uiState.value
        if (state.searchResults.isNotEmpty()) {
            val prevIdx = if (state.currentSearchMatchIndex - 1 < 0) state.searchResults.size - 1 else state.currentSearchMatchIndex - 1
            val match = state.searchResults[prevIdx]
            _uiState.update { it.copy(currentSearchMatchIndex = prevIdx) }
            goToPage(match.pageIndex)
        }
    }

    // ==========================================
    // RENDERING ENGINE
    // ==========================================

    private fun renderCurrentAndAdjacentPages(filePath: String, page: Int, total: Int) {
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.IO) {
            val page0 = page - 1 // 0-based for PdfRenderer

            // 1. First render current page immediately for zero latency
            val currentBmp = pdfRendererManager.renderPage(filePath, page0, targetRenderWidth, targetRenderHeight)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(currentPageBitmap = currentBmp) }
            }

            // 2. Next page pre-render
            val nextBmp = if (page < total) {
                pdfRendererManager.renderPage(filePath, page0 + 1, targetRenderWidth, targetRenderHeight)
            } else null

            // 3. Previous page pre-render
            val prevBmp = if (page > 1) {
                pdfRendererManager.renderPage(filePath, page0 - 1, targetRenderWidth, targetRenderHeight)
            } else null

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        nextPageBitmap = nextBmp,
                        previousPageBitmap = prevBmp
                    )
                }
            }

            // 4. Background active prefetching to preserve scrolling smoothness and stability
            pdfRendererManager.prefetchWindow(
                filePath = filePath,
                centerPageIndex = page0,
                forwardCount = 4,
                backwardCount = 2,
                targetWidth = targetRenderWidth,
                targetHeight = targetRenderHeight
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfRendererManager.close()
    }
}
