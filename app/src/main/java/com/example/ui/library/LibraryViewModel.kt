package com.example.ui.library

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.BookEntity
import com.example.data.model.CustomFilter
import com.example.data.pdf.PdfRendererManager
import com.example.data.repository.BookRepository
import com.example.ui.settings.LibrarySortOrder
import com.example.ui.settings.LibraryViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class LibraryFilter {
    ALL,
    UNREAD,
    READING,
    COMPLETED,
    PINNED,
    BOOKMARKED,
    CUSTOM
}

data class FilterChipItem(
    val id: String, // "ALL", "UNREAD", "READING", "COMPLETED", "PINNED", "BOOKMARKED", or custom UUID
    val label: String,
    val count: Int,
    val isCustom: Boolean = false,
    val customFilter: CustomFilter? = null
)

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val filteredBooks: List<BookEntity> = emptyList(),
    val booksLeftToRead: List<BookEntity> = emptyList(),
    val currentFilter: LibraryFilter = LibraryFilter.ALL,
    val activeFilterId: String = "ALL",
    val customFilters: List<CustomFilter> = emptyList(),
    val filterChipItems: List<FilterChipItem> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.DATE_ADDED,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val isImporting: Boolean = false,
    val importQueueProgress: Pair<Int, Int>? = null, // current to total
    val importQueueCurrentName: String? = null,
    val importError: String? = null,
    val importToastMessage: String? = null,
    val selectedBookToDelete: BookEntity? = null,
    val selectedBookToCopy: BookEntity? = null,
    val selectedBookToRename: BookEntity? = null,
    val isCreateFilterDialogOpen: Boolean = false,
    val editingCustomFilter: CustomFilter? = null,
    val renamingCustomFilter: CustomFilter? = null,
    val bookForListAssignment: BookEntity? = null,
    val isOrganizedAndReady: Boolean = false,
    val hasLoadedFromDatabase: Boolean = false
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences("lumina_settings_prefs", Context.MODE_PRIVATE)
    private val db = AppDatabase.getInstance(application)
    private val pdfRendererManager = PdfRendererManager.getInstance(application)
    private val repository = BookRepository(application, db.bookDao(), pdfRendererManager, db.annotationDao())

    private val _filterState = MutableStateFlow(LibraryFilter.ALL)
    private val _activeFilterIdState = MutableStateFlow("ALL")
    private val _customFiltersState = MutableStateFlow<List<CustomFilter>>(loadCustomFilters())
    private val _searchQueryState = MutableStateFlow("")
    private val _sortOrderState = MutableStateFlow(
        LibrarySortOrder.valueOf(
            prefs.getString("pref_sort_order", LibrarySortOrder.DATE_ADDED.name) ?: LibrarySortOrder.DATE_ADDED.name
        )
    )
    private val _viewModeState = MutableStateFlow(
        LibraryViewMode.valueOf(
            prefs.getString("pref_view_mode", LibraryViewMode.GRID.name) ?: LibraryViewMode.GRID.name
        )
    )
    private val _isImportingState = MutableStateFlow(false)
    private val _importQueueProgressState = MutableStateFlow<Pair<Int, Int>?>(null)
    private val _importQueueCurrentNameState = MutableStateFlow<String?>(null)
    private val _importErrorState = MutableStateFlow<String?>(null)
    private val _importToastMessageState = MutableStateFlow<String?>(null)
    private val _bookToDeleteState = MutableStateFlow<BookEntity?>(null)
    private val _bookToCopyState = MutableStateFlow<BookEntity?>(null)
    private val _bookToRenameState = MutableStateFlow<BookEntity?>(null)
    private val _isCreateFilterDialogOpenState = MutableStateFlow(false)
    private val _editingCustomFilterState = MutableStateFlow<CustomFilter?>(null)
    private val _renamingCustomFilterState = MutableStateFlow<CustomFilter?>(null)
    private val _bookForListAssignmentState = MutableStateFlow<BookEntity?>(null)
    private val _isOrganizedAndReadyState = MutableStateFlow(false)

    // Scroll state preservation across screens and back-navigation
    var gridScrollIndex: Int = 0
    var gridScrollOffset: Int = 0
    var listScrollIndex: Int = 0
    var listScrollOffset: Int = 0

    // Combine primary data flows cleanly
    private val _filteredBooksFlow = combine(
        repository.allBooks,
        _activeFilterIdState,
        _customFiltersState,
        _searchQueryState,
        _sortOrderState
    ) { books, activeFilterId, customFilters, query, sortOrder ->
        val filtered = books.filter { book ->
            val matchesQuery = query.isBlank() ||
                    book.title.contains(query, ignoreCase = true) ||
                    book.author.contains(query, ignoreCase = true)

            val matchesFilter = when (activeFilterId) {
                "ALL" -> true
                "UNREAD" -> !book.hasBeenOpened || (book.currentPage <= 1 && book.progressPercent == 0f)
                "READING" -> book.hasBeenOpened && book.currentPage < book.totalPages && (book.progressPercent in 0.001f..0.999f || book.currentPage > 1)
                "COMPLETED" -> book.hasBeenOpened && (book.currentPage >= book.totalPages || book.progressPercent >= 0.99f)
                "PINNED" -> book.isPinned
                "BOOKMARKED" -> book.getBookmarkPages().isNotEmpty()
                else -> {
                    val custom = customFilters.find { it.id == activeFilterId }
                    custom?.bookIds?.contains(book.id) ?: true
                }
            }
            matchesQuery && matchesFilter
        }

        val sorted = when (sortOrder) {
            LibrarySortOrder.DATE_ADDED -> filtered.sortedWith(
                compareByDescending<BookEntity> { it.isPinned }
                    .thenByDescending { it.dateAddedTimestamp }
                    .thenByDescending { it.id }
            )
            LibrarySortOrder.RECENT -> filtered.sortedWith(
                compareByDescending<BookEntity> { it.isPinned }
                    .thenByDescending { if (it.hasBeenOpened) it.lastReadTimestamp else 0L }
                    .thenByDescending { it.dateAddedTimestamp }
                    .thenByDescending { it.id }
            )
            LibrarySortOrder.TITLE_ASC -> filtered.sortedWith(
                compareByDescending<BookEntity> { it.isPinned }
                    .thenBy { it.title.lowercase() }
                    .thenByDescending { it.id }
            )
            LibrarySortOrder.TITLE_DESC -> filtered.sortedWith(
                compareByDescending<BookEntity> { it.isPinned }
                    .thenByDescending { it.title.lowercase() }
                    .thenByDescending { it.id }
            )
            LibrarySortOrder.AUTHOR_ASC -> filtered.sortedWith(
                compareByDescending<BookEntity> { it.isPinned }
                    .thenBy { it.author.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenByDescending { it.id }
            )
            LibrarySortOrder.PROGRESS -> filtered.sortedWith(
                compareByDescending<BookEntity> { it.isPinned }
                    .thenByDescending { if (it.hasBeenOpened) it.progressPercent else -1f }
                    .thenByDescending { if (it.hasBeenOpened) it.lastReadTimestamp else 0L }
                    .thenByDescending { it.id }
            )
            LibrarySortOrder.PAGE_COUNT_DESC -> filtered.sortedWith(
                compareByDescending<BookEntity> { it.isPinned }
                    .thenByDescending { it.totalPages }
                    .thenByDescending { it.id }
            )
            LibrarySortOrder.PAGE_COUNT_ASC -> filtered.sortedWith(
                compareByDescending<BookEntity> { it.isPinned }
                    .thenBy { it.totalPages }
                    .thenByDescending { it.id }
            )
        }

        // Recent read carousel pdfs
        val recentReadBooks = books
            .filter { it.hasBeenOpened && it.lastReadTimestamp > 0L }
            .sortedWith(
                compareByDescending<BookEntity> { it.isPinned }
                    .thenByDescending { it.lastReadTimestamp }
                    .thenByDescending { it.id }
            )
            .take(5)

        // Compute filter chips with real-time counts
        val bookIdSet = books.map { it.id }.toSet()
        val allCount = books.size
        val unreadCount = books.count { !it.hasBeenOpened || (it.currentPage <= 1 && it.progressPercent == 0f) }
        val readingCount = books.count { it.hasBeenOpened && it.currentPage < it.totalPages && (it.progressPercent in 0.001f..0.999f || it.currentPage > 1) }
        val completedCount = books.count { it.hasBeenOpened && (it.currentPage >= it.totalPages || it.progressPercent >= 0.99f) }
        val pinnedCount = books.count { it.isPinned }
        val bookmarkedCount = books.count { it.getBookmarkPages().isNotEmpty() }

        val chips = mutableListOf<FilterChipItem>()
        chips.add(FilterChipItem("ALL", "All", allCount))
        if (unreadCount > 0) {
            chips.add(FilterChipItem("UNREAD", "Unread", unreadCount))
        }
        chips.add(FilterChipItem("READING", "Reading", readingCount))
        if (bookmarkedCount > 0) {
            chips.add(FilterChipItem("BOOKMARKED", "Bookmarked", bookmarkedCount))
        }
        if (completedCount > 0) {
            chips.add(FilterChipItem("COMPLETED", "Completed", completedCount))
        }
        if (pinnedCount > 0) {
            chips.add(FilterChipItem("PINNED", "Favorites", pinnedCount))
        }

        customFilters.forEach { custom ->
            val count = custom.bookIds.count { bookIdSet.contains(it) }
            chips.add(
                FilterChipItem(
                    id = custom.id,
                    label = custom.name,
                    count = count,
                    isCustom = true,
                    customFilter = custom
                )
            )
        }

        Triple(books, sorted, recentReadBooks) to chips
    }

    private data class ImportStatus(
        val isImporting: Boolean = false,
        val queueProgress: Pair<Int, Int>? = null,
        val queueCurrentName: String? = null,
        val importError: String? = null,
        val importToast: String? = null
    )

    private val _importStatusFlow = combine(
        _isImportingState,
        _importQueueProgressState,
        _importQueueCurrentNameState,
        _importErrorState,
        _importToastMessageState
    ) { isImporting, queueProgress, queueCurrentName, importError, importToast ->
        ImportStatus(isImporting, queueProgress, queueCurrentName, importError, importToast)
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    private data class DialogState(
        val isCreateFilterDialogOpen: Boolean,
        val editingCustomFilter: CustomFilter?,
        val renamingCustomFilter: CustomFilter?,
        val bookForListAssignment: BookEntity?,
        val bookToDelete: BookEntity?,
        val bookToCopy: BookEntity?,
        val bookToRename: BookEntity?
    )

    private val _dialogsFlow = combine(
        _isCreateFilterDialogOpenState,
        _editingCustomFilterState,
        _renamingCustomFilterState,
        _bookForListAssignmentState,
        combine(_bookToDeleteState, _bookToCopyState, _bookToRenameState) { del, cpy, ren -> Triple(del, cpy, ren) }
    ) { isCreateOpen, editFilter, renameFilter, bookForList, delCpyRen ->
        DialogState(
            isCreateFilterDialogOpen = isCreateOpen,
            editingCustomFilter = editFilter,
            renamingCustomFilter = renameFilter,
            bookForListAssignment = bookForList,
            bookToDelete = delCpyRen.first,
            bookToCopy = delCpyRen.second,
            bookToRename = delCpyRen.third
        )
    }

    private val _controlsFlow = combine(
        _viewModeState,
        _filterState,
        _activeFilterIdState,
        _searchQueryState,
        _sortOrderState
    ) { viewMode, filter, activeFilterId, query, sortOrder ->
        Tuple5(viewMode, filter, activeFilterId, query, sortOrder)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        _filteredBooksFlow,
        _importStatusFlow,
        _dialogsFlow,
        _controlsFlow,
        combine(_customFiltersState, _isOrganizedAndReadyState) { cf, org -> Pair(cf, org) }
    ) { booksAndChips, importStatus, dialogs, controls, cfAndOrg ->
        val booksInfo = booksAndChips.first
        val chips = booksAndChips.second
        val books = booksInfo.first
        val filtered = booksInfo.second
        val leftToRead = booksInfo.third

        LibraryUiState(
            books = books,
            filteredBooks = filtered,
            booksLeftToRead = leftToRead,
            currentFilter = controls.b,
            activeFilterId = controls.c,
            customFilters = cfAndOrg.first,
            filterChipItems = chips,
            searchQuery = controls.d,
            sortOrder = controls.e,
            viewMode = controls.a,
            isImporting = importStatus.isImporting,
            importQueueProgress = importStatus.queueProgress,
            importQueueCurrentName = importStatus.queueCurrentName,
            importError = importStatus.importError,
            importToastMessage = importStatus.importToast,
            selectedBookToDelete = dialogs.bookToDelete,
            selectedBookToCopy = dialogs.bookToCopy,
            selectedBookToRename = dialogs.bookToRename,
            isCreateFilterDialogOpen = dialogs.isCreateFilterDialogOpen,
            editingCustomFilter = dialogs.editingCustomFilter,
            renamingCustomFilter = dialogs.renamingCustomFilter,
            bookForListAssignment = dialogs.bookForListAssignment,
            isOrganizedAndReady = cfAndOrg.second,
            hasLoadedFromDatabase = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = run {
            val cached = BookRepository.cachedBooks
            if (cached != null) {
                val sorted = when (_sortOrderState.value) {
                    LibrarySortOrder.DATE_ADDED -> cached.sortedWith(
                        compareByDescending<BookEntity> { it.isPinned }
                            .thenByDescending { it.dateAddedTimestamp }
                            .thenByDescending { it.id }
                    )
                    LibrarySortOrder.RECENT -> cached.sortedWith(
                        compareByDescending<BookEntity> { it.isPinned }
                            .thenByDescending { if (it.hasBeenOpened) it.lastReadTimestamp else 0L }
                            .thenByDescending { it.dateAddedTimestamp }
                            .thenByDescending { it.id }
                    )
                    LibrarySortOrder.TITLE_ASC -> cached.sortedWith(
                        compareByDescending<BookEntity> { it.isPinned }
                            .thenBy { it.title.lowercase() }
                            .thenByDescending { it.id }
                    )
                    LibrarySortOrder.TITLE_DESC -> cached.sortedWith(
                        compareByDescending<BookEntity> { it.isPinned }
                            .thenByDescending { it.title.lowercase() }
                            .thenByDescending { it.id }
                    )
                    LibrarySortOrder.AUTHOR_ASC -> cached.sortedWith(
                        compareByDescending<BookEntity> { it.isPinned }
                            .thenBy { it.author.lowercase() }
                            .thenBy { it.title.lowercase() }
                            .thenByDescending { it.id }
                    )
                    LibrarySortOrder.PROGRESS -> cached.sortedWith(
                        compareByDescending<BookEntity> { it.isPinned }
                            .thenByDescending { if (it.hasBeenOpened) it.progressPercent else -1f }
                            .thenByDescending { if (it.hasBeenOpened) it.lastReadTimestamp else 0L }
                            .thenByDescending { it.id }
                    )
                    LibrarySortOrder.PAGE_COUNT_DESC -> cached.sortedWith(
                        compareByDescending<BookEntity> { it.isPinned }
                            .thenByDescending { it.totalPages }
                            .thenByDescending { it.id }
                    )
                    LibrarySortOrder.PAGE_COUNT_ASC -> cached.sortedWith(
                        compareByDescending<BookEntity> { it.isPinned }
                            .thenBy { it.totalPages }
                            .thenByDescending { it.id }
                    )
                }
                val recentReadBooks = cached
                    .filter { it.hasBeenOpened && it.lastReadTimestamp > 0L }
                    .sortedWith(
                        compareByDescending<BookEntity> { it.isPinned }
                            .thenByDescending { it.lastReadTimestamp }
                            .thenByDescending { it.id }
                    )
                    .take(5)
                val chips = mutableListOf<FilterChipItem>()
                chips.add(FilterChipItem("ALL", "All", cached.size))
                LibraryUiState(
                    books = cached,
                    filteredBooks = sorted,
                    booksLeftToRead = recentReadBooks,
                    sortOrder = _sortOrderState.value,
                    viewMode = _viewModeState.value,
                    filterChipItems = chips,
                    hasLoadedFromDatabase = true,
                    isOrganizedAndReady = true
                )
            } else {
                LibraryUiState(
                    sortOrder = _sortOrderState.value,
                    viewMode = _viewModeState.value,
                    hasLoadedFromDatabase = false,
                    isOrganizedAndReady = false
                )
            }
        }
    )

    init {
        viewModelScope.launch {
            repository.initializeDefaultsIfNeeded()
            repository.ensureAllCoversGenerated()
            try {
                repository.allBooks.first()
            } catch (_: Exception) {
            }
            _isOrganizedAndReadyState.value = true
        }
    }

    // Custom Filters Storage
    private fun loadCustomFilters(): List<CustomFilter> {
        val json = prefs.getString("pref_custom_filters_json", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<CustomFilter>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val bookIdsArray = obj.optJSONArray("bookIds")
                val bookIds = mutableSetOf<Long>()
                if (bookIdsArray != null) {
                    for (j in 0 until bookIdsArray.length()) {
                        bookIds.add(bookIdsArray.getLong(j))
                    }
                }
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                list.add(CustomFilter(id = id, name = name, bookIds = bookIds, createdAt = createdAt))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCustomFilters(filters: List<CustomFilter>) {
        try {
            val array = JSONArray()
            for (f in filters) {
                val obj = JSONObject()
                obj.put("id", f.id)
                obj.put("name", f.name)
                val bookIdsArray = JSONArray()
                f.bookIds.forEach { bookIdsArray.put(it) }
                obj.put("bookIds", bookIdsArray)
                obj.put("createdAt", f.createdAt)
                array.put(obj)
            }
            prefs.edit().putString("pref_custom_filters_json", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setFilter(filter: LibraryFilter) {
        _filterState.value = filter
        _activeFilterIdState.value = when (filter) {
            LibraryFilter.ALL -> "ALL"
            LibraryFilter.UNREAD -> "UNREAD"
            LibraryFilter.READING -> "READING"
            LibraryFilter.COMPLETED -> "COMPLETED"
            LibraryFilter.PINNED -> "PINNED"
            LibraryFilter.BOOKMARKED -> "BOOKMARKED"
            LibraryFilter.CUSTOM -> _activeFilterIdState.value
        }
    }

    fun setFilterById(filterId: String) {
        val targetId = if (_activeFilterIdState.value == filterId && filterId != "ALL") "ALL" else filterId
        _activeFilterIdState.value = targetId
        _filterState.value = when (targetId) {
            "ALL" -> LibraryFilter.ALL
            "UNREAD" -> LibraryFilter.UNREAD
            "READING" -> LibraryFilter.READING
            "COMPLETED" -> LibraryFilter.COMPLETED
            "PINNED" -> LibraryFilter.PINNED
            "BOOKMARKED" -> LibraryFilter.BOOKMARKED
            else -> LibraryFilter.CUSTOM
        }
    }

    fun openCreateFilterDialog() {
        _isCreateFilterDialogOpenState.value = true
    }

    fun closeCreateFilterDialog() {
        _isCreateFilterDialogOpenState.value = false
    }

    fun createCustomFilter(name: String, bookIds: Set<Long>) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val newFilter = CustomFilter(
            id = java.util.UUID.randomUUID().toString(),
            name = trimmed,
            bookIds = bookIds
        )
        val updated = _customFiltersState.value + newFilter
        _customFiltersState.value = updated
        saveCustomFilters(updated)
        setFilterById(newFilter.id)
        _isCreateFilterDialogOpenState.value = false
        _importToastMessageState.value = "Created list \"$trimmed\""
    }

    fun openEditFilterDialog(customFilter: CustomFilter) {
        _editingCustomFilterState.value = customFilter
    }

    fun closeEditFilterDialog() {
        _editingCustomFilterState.value = null
    }

    fun openRenameFilterDialog(customFilter: CustomFilter) {
        _renamingCustomFilterState.value = customFilter
    }

    fun closeRenameFilterDialog() {
        _renamingCustomFilterState.value = null
    }

    fun renameCustomFilter(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val updated = _customFiltersState.value.map {
            if (it.id == id) it.copy(name = trimmed) else it
        }
        _customFiltersState.value = updated
        saveCustomFilters(updated)
        _renamingCustomFilterState.value = null
        _importToastMessageState.value = "Renamed list to \"$trimmed\""
    }

    fun updateCustomFilter(id: String, name: String, bookIds: Set<Long>) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val updated = _customFiltersState.value.map {
            if (it.id == id) it.copy(name = trimmed, bookIds = bookIds) else it
        }
        _customFiltersState.value = updated
        saveCustomFilters(updated)
        _editingCustomFilterState.value = null
        _importToastMessageState.value = "Updated list \"$trimmed\""
    }

    fun deleteCustomFilter(id: String) {
        val target = _customFiltersState.value.find { it.id == id }
        val updated = _customFiltersState.value.filter { it.id != id }
        _customFiltersState.value = updated
        saveCustomFilters(updated)
        if (_activeFilterIdState.value == id) {
            setFilterById("ALL")
        }
        _editingCustomFilterState.value = null
        _renamingCustomFilterState.value = null
        _importToastMessageState.value = "Deleted list \"${target?.name ?: ""}\""
    }

    fun removeBookFromCustomFilter(bookId: Long, filterId: String) {
        val targetList = _customFiltersState.value.find { it.id == filterId }
        val updated = _customFiltersState.value.map { filter ->
            if (filter.id == filterId) {
                filter.copy(bookIds = filter.bookIds - bookId)
            } else {
                filter
            }
        }
        _customFiltersState.value = updated
        saveCustomFilters(updated)
        _importToastMessageState.value = "Removed from \"${targetList?.name ?: "list"}\""
    }

    fun removeBookFromCurrentFilter(book: BookEntity) {
        val activeId = _activeFilterIdState.value
        when (activeId) {
            "ALL" -> {
                // In ALL, confirm delete book entirely from library
                confirmDeleteBook(book)
            }
            "PINNED" -> {
                togglePinBook(book)
                _importToastMessageState.value = "Removed from Favorites"
            }
            "BOOKMARKED" -> {
                clearBookBookmarks(book)
            }
            "READING" -> {
                markBookAsUnread(book)
            }
            "COMPLETED" -> {
                markBookAsUnread(book)
            }
            "UNREAD" -> {
                markBookAsCompleted(book)
            }
            else -> {
                removeBookFromCustomFilter(book.id, activeId)
            }
        }
    }

    fun markBookAsUnread(book: BookEntity) {
        viewModelScope.launch {
            repository.markBookUnread(book.id)
            _importToastMessageState.value = "Marked \"${book.title}\" as unread"
        }
    }

    fun markBookAsCompleted(book: BookEntity) {
        viewModelScope.launch {
            repository.markBookCompleted(book.id)
            _importToastMessageState.value = "Marked \"${book.title}\" as completed"
        }
    }

    fun clearBookBookmarks(book: BookEntity) {
        viewModelScope.launch {
            repository.clearBookmarks(book.id)
            _importToastMessageState.value = "Cleared bookmarks for \"${book.title}\""
        }
    }

    fun openAssignBookToListDialog(book: BookEntity) {
        _bookForListAssignmentState.value = book
    }

    fun closeAssignBookToListDialog() {
        _bookForListAssignmentState.value = null
    }

    fun saveBookCustomFilterAssignments(bookId: Long, filterIds: Set<String>) {
        val updated = _customFiltersState.value.map { filter ->
            val newBookIds = if (filterIds.contains(filter.id)) {
                filter.bookIds + bookId
            } else {
                filter.bookIds - bookId
            }
            filter.copy(bookIds = newBookIds)
        }
        _customFiltersState.value = updated
        saveCustomFilters(updated)
        _bookForListAssignmentState.value = null
        _importToastMessageState.value = "Updated lists for book"
    }

    fun setSearchQuery(query: String) {
        _searchQueryState.value = query
    }

    fun setSortOrder(order: LibrarySortOrder) {
        prefs.edit().putString("pref_sort_order", order.name).apply()
        _sortOrderState.value = order
    }

    fun setViewMode(mode: LibraryViewMode) {
        prefs.edit().putString("pref_view_mode", mode.name).apply()
        _viewModeState.value = mode
    }

    fun toggleViewMode() {
        val next = if (_viewModeState.value == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID
        setViewMode(next)
    }

    fun importPdfs(uris: List<Uri>, onSingleSuccess: ((Long) -> Unit)? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImportingState.value = true
            _importErrorState.value = null
            _importQueueProgressState.value = Pair(1, uris.size)
            _importQueueCurrentNameState.value = "Starting queue..."

            val result = repository.importPdfs(uris) { current, total, name ->
                _importQueueProgressState.value = Pair(current, total)
                _importQueueCurrentNameState.value = name
            }

            _isImportingState.value = false
            _importQueueProgressState.value = null
            _importQueueCurrentNameState.value = null

            if (result.importedCount > 0) {
                val msg = if (result.skippedCount > 0) {
                    "Imported ${result.importedCount} PDF${if (result.importedCount > 1) "s" else ""} (${result.skippedCount} invalid file${if (result.skippedCount > 1) "s" else ""} skipped)"
                } else {
                    "Imported ${result.importedCount} PDF${if (result.importedCount > 1) "s" else ""} successfully"
                }
                _importToastMessageState.value = msg
                if (uris.size == 1 && result.lastImportedBookId != null) {
                    onSingleSuccess?.invoke(result.lastImportedBookId)
                }
            } else if (result.skippedCount > 0) {
                _importErrorState.value = "Could not read the selected PDF file${if (result.skippedCount > 1) "s" else ""}"
            } else {
                _importErrorState.value = "Could not import the selected PDF documents"
            }
        }
    }

    fun importPdf(uri: Uri, onBookImported: (Long) -> Unit) {
        importPdfs(listOf(uri), onBookImported)
    }

    fun clearImportError() {
        _importErrorState.value = null
    }

    fun clearImportToast() {
        _importToastMessageState.value = null
    }

    fun togglePinBook(book: BookEntity) {
        viewModelScope.launch {
            repository.togglePin(book.id)
        }
    }

    fun confirmDeleteBook(book: BookEntity) {
        _bookToDeleteState.value = book
    }

    fun cancelDeleteBook() {
        _bookToDeleteState.value = null
    }

    fun executeDeleteBook() {
        val book = _bookToDeleteState.value ?: return
        viewModelScope.launch {
            repository.deleteBook(book.id)
            _bookToDeleteState.value = null
        }
    }

    // Copy / Duplicate Book
    fun initiateCopyBook(book: BookEntity) {
        _bookToCopyState.value = book
    }

    fun requestCopyBook(book: BookEntity) {
        _bookToCopyState.value = book
    }

    fun cancelCopyBook() {
        _bookToCopyState.value = null
    }

    fun executeCopyBook(newTitle: String, onCopied: ((Long) -> Unit)? = null) {
        val originalBook = _bookToCopyState.value ?: return
        viewModelScope.launch {
            val result = repository.copyBook(originalBook.id, newTitle)
            _bookToCopyState.value = null
            result.onSuccess { newId ->
                _importToastMessageState.value = "Created copy: \"$newTitle\""
                onCopied?.invoke(newId)
            }.onFailure { err ->
                _importErrorState.value = "Failed to copy book: ${err.message}"
            }
        }
    }

    // Rename Book / PDF
    fun initiateRenameBook(book: BookEntity) {
        _bookToRenameState.value = book
    }

    fun cancelRenameBook() {
        _bookToRenameState.value = null
    }

    fun executeRenameBook(newTitle: String) {
        val book = _bookToRenameState.value ?: return
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val result = repository.renameBook(book.id, trimmed)
            _bookToRenameState.value = null
            result.onSuccess {
                _importToastMessageState.value = "Renamed to \"$trimmed\""
            }.onFailure { err ->
                _importErrorState.value = "Failed to rename: ${err.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfRendererManager.close()
    }
}
