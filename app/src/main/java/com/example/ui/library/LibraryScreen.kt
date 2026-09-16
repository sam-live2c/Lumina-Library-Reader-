package com.example.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.BookEntity
import com.example.data.model.CustomFilter
import com.example.data.repository.AppSettingsManager
import com.example.ui.reader.PdfShareHelper
import com.example.ui.settings.LibrarySortOrder
import com.example.ui.settings.LibraryViewMode
import com.example.ui.settings.SettingsSubpage
import com.example.ui.theme.LuminaAccentPrimary
import com.example.ui.theme.LuminaAccentSubtle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    onOpenSettings: (SettingsSubpage?) -> Unit,
    viewModel: LibraryViewModel = viewModel(),
    isFullScreenModeEnabled: Boolean = AppSettingsManager.isFullScreenModeEnabled.collectAsStateWithLifecycle().value,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var searchFieldCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    BackHandler(enabled = isSearchFocused) {
        focusManager.clearFocus()
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2200)
            toastMessage = null
        }
    }

    val multiPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importPdfs(uris) { bookId ->
                onOpenBook(bookId)
            }
        }
    }

    LaunchedEffect(uiState.importToastMessage) {
        uiState.importToastMessage?.let { msg ->
            toastMessage = msg
            viewModel.clearImportToast()
        }
    }

    LaunchedEffect(uiState.importError) {
        uiState.importError?.let { err ->
            toastMessage = err
            viewModel.clearImportError()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootCoordinates = it }
            .pointerInput(isSearchFocused) {
                if (!isSearchFocused) return@pointerInput
                detectTapGestures(
                    onTap = { position ->
                        val root = rootCoordinates
                        val search = searchFieldCoordinates
                        if (root != null && search != null && root.isAttached && search.isAttached) {
                            val searchBounds = root.localBoundingBoxOf(search)
                            if (!searchBounds.contains(position)) {
                                focusManager.clearFocus()
                            }
                        } else {
                            focusManager.clearFocus()
                        }
                    }
                )
            }
    ) {
        Scaffold(
            contentWindowInsets = if (isFullScreenModeEnabled) WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        multiPdfPickerLauncher.launch(arrayOf("application/pdf"))
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("upload_pdf_fab")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Upload PDF",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.viewMode == LibraryViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 152.dp),
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            top = if (isFullScreenModeEnabled) 12.dp else 4.dp,
                            bottom = 100.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("books_grid")
                    ) {
                        // 1. All top page components scroll together with the grid
                        item(span = { GridItemSpan(maxLineSpan) }, key = "library_header_section") {
                            LibraryHeaderSection(
                                uiState = uiState,
                                viewModel = viewModel,
                                focusManager = focusManager,
                                keyboardController = keyboardController,
                                isSearchFocused = isSearchFocused,
                                onSearchFocusChange = { isSearchFocused = it },
                                onSearchCoordinatesPositioned = { searchFieldCoordinates = it },
                                onOpenBook = onOpenBook,
                                onOpenSettings = onOpenSettings,
                                onToast = { toastMessage = it }
                            )
                        }

                        if (uiState.filteredBooks.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "empty_state") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyLibraryState(
                                        onUpload = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            multiPdfPickerLauncher.launch(arrayOf("application/pdf"))
                                        }
                                    )
                                }
                            }
                        } else {
                            gridItems(uiState.filteredBooks, key = { it.id }) { book ->
                                BookCardItem(
                                    book = book,
                                    currentFilterId = uiState.activeFilterId,
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        onOpenBook(book.id)
                                    },
                                    onTogglePin = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        val msg = if (!book.isPinned) "Pinned \"${book.title}\" to top" else "Unpinned \"${book.title}\""
                                        toastMessage = msg
                                        viewModel.togglePinBook(book)
                                    },
                                    onAssignToList = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.openAssignBookToListDialog(book)
                                    },
                                    onRemoveFromCurrentFilter = if (uiState.activeFilterId != "ALL") {
                                        {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            viewModel.removeBookFromCurrentFilter(book)
                                            toastMessage = "Removed \"${book.title}\" from current list"
                                        }
                                    } else null,
                                    onCopy = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.initiateCopyBook(book)
                                    },
                                    onDelete = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.confirmDeleteBook(book)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            top = if (isFullScreenModeEnabled) 12.dp else 4.dp,
                            bottom = 100.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("books_list")
                    ) {
                        // 1. All top page components scroll together with the list
                        item(key = "library_header_section") {
                            LibraryHeaderSection(
                                uiState = uiState,
                                viewModel = viewModel,
                                focusManager = focusManager,
                                keyboardController = keyboardController,
                                isSearchFocused = isSearchFocused,
                                onSearchFocusChange = { isSearchFocused = it },
                                onSearchCoordinatesPositioned = { searchFieldCoordinates = it },
                                onOpenBook = onOpenBook,
                                onOpenSettings = onOpenSettings,
                                onToast = { toastMessage = it }
                            )
                        }

                        if (uiState.filteredBooks.isEmpty()) {
                            item(key = "empty_state") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyLibraryState(
                                        onUpload = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            multiPdfPickerLauncher.launch(arrayOf("application/pdf"))
                                        }
                                    )
                                }
                            }
                        } else {
                            items(uiState.filteredBooks, key = { it.id }) { book ->
                                BookListItem(
                                    book = book,
                                    currentFilterId = uiState.activeFilterId,
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        onOpenBook(book.id)
                                    },
                                    onTogglePin = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        val msg = if (!book.isPinned) "Pinned \"${book.title}\" to top" else "Unpinned \"${book.title}\""
                                        toastMessage = msg
                                        viewModel.togglePinBook(book)
                                    },
                                    onAssignToList = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.openAssignBookToListDialog(book)
                                    },
                                    onRemoveFromCurrentFilter = if (uiState.activeFilterId != "ALL") {
                                        {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            viewModel.removeBookFromCurrentFilter(book)
                                            toastMessage = "Removed \"${book.title}\" from current list"
                                        }
                                    } else null,
                                    onCopy = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.initiateCopyBook(book)
                                    },
                                    onDelete = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.confirmDeleteBook(book)
                                    }
                                )
                            }
                        }
                    }
                }

            // Loading overlay when importing
            if (uiState.isImporting) {
                val queueProgress = uiState.importQueueProgress
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .testTag("import_loading_overlay"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .padding(28.dp)
                            .fillMaxWidth(0.88f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (queueProgress != null && queueProgress.second > 1) {
                                val current = queueProgress.first
                                val total = queueProgress.second
                                val fraction = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                                Text(
                                    text = "Uploading PDFs ($current of $total)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                uiState.importQueueCurrentName?.let { currentDoc ->
                                    Text(
                                        text = currentDoc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Preparing your book...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Extracting pages and building crisp rendering cache",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Copy / Rename PDF Confirmation Dialog
            uiState.selectedBookToCopy?.let { bookToCopy ->
                var copyTitle by remember(bookToCopy.id) {
                    mutableStateOf("${bookToCopy.title} (Copy)")
                }

                AlertDialog(
                    onDismissRequest = { viewModel.cancelCopyBook() },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 560.dp),
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Copy Book",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Creates an independent duplicate copy. All existing bookmarks, highlights, and annotations will be preserved, and future edits will remain separate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = copyTitle,
                                onValueChange = { copyTitle = it },
                                placeholder = {
                                    Text(
                                        text = "Book Title / PDF Name",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent,
                                    errorBorderColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("copy_book_rename_input")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.executeCopyBook(copyTitle.trim())
                            },
                            enabled = copyTitle.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("confirm_copy_button")
                        ) {
                            Text("Copy Book")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.cancelCopyBook() },
                            modifier = Modifier.testTag("cancel_copy_button")
                        ) {
                            Text("Cancel")
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                )
            }

            // Delete Confirmation Dialog
            uiState.selectedBookToDelete?.let { bookToDelete ->
                AlertDialog(
                    onDismissRequest = { viewModel.cancelDeleteBook() },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 560.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(20.dp),
                    title = {
                        Text(
                            text = "Remove Book?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "Are you sure you want to remove \"${bookToDelete.title}\" from your library? Reading progress and bookmarks will be deleted.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                toastMessage = "Removed \"${bookToDelete.title}\" from library"
                                viewModel.executeDeleteBook()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("confirm_delete_btn")
                        ) {
                            Text("Remove", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.cancelDeleteBook() },
                            modifier = Modifier.testTag("cancel_delete_btn")
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Custom Filter Creation Dialog
            if (uiState.isCreateFilterDialogOpen) {
                CreateCustomFilterDialog(
                    allBooks = uiState.books,
                    onDismiss = { viewModel.closeCreateFilterDialog() },
                    onCreate = { name, selectedBookIds ->
                        viewModel.createCustomFilter(name, selectedBookIds.toSet())
                    }
                )
            }

            // Custom Filter Edit Dialog
            uiState.editingCustomFilter?.let { filterToEdit ->
                EditCustomFilterDialog(
                    customFilter = filterToEdit,
                    allBooks = uiState.books,
                    onDismiss = { viewModel.closeEditFilterDialog() },
                    onSave = { name, selectedBookIds ->
                        viewModel.updateCustomFilter(filterToEdit.id, name, selectedBookIds.toSet())
                    },
                    onDelete = {
                        viewModel.deleteCustomFilter(filterToEdit.id)
                    }
                )
            }

            // Custom Filter Rename Dialog
            uiState.renamingCustomFilter?.let { filterToRename ->
                RenameCustomFilterDialog(
                    customFilter = filterToRename,
                    onDismiss = { viewModel.closeRenameFilterDialog() },
                    onRename = { newName ->
                        viewModel.renameCustomFilter(filterToRename.id, newName)
                    }
                )
            }

            // Assign Book to Custom Filter Lists Dialog
            uiState.bookForListAssignment?.let { bookToAssign ->
                AssignBookToListDialog(
                    book = bookToAssign,
                    customFilters = uiState.customFilters,
                    onDismiss = { viewModel.closeAssignBookToListDialog() },
                    onSave = { assignedFilterIds ->
                        viewModel.saveBookCustomFilterAssignments(bookToAssign.id, assignedFilterIds.toSet())
                    },
                    onCreateNewFilter = {
                        viewModel.openCreateFilterDialog()
                    }
                )
            }
        }
    }

    // Feedback toasts for actions: positioned above all UI in z-index,
    // rendered after Scaffold so it is drawn on top of the entire screen including the upload PDF button (FAB),
    // and vertically positioned safely above the upload PDF button.
    AnimatedVisibility(
        visible = toastMessage != null,
        enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .zIndex(9999f)
            .navigationBarsPadding()
            .padding(bottom = 96.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .testTag("library_theme_toast")
        ) {
            Text(
                text = toastMessage ?: "",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp)
            )
        }
    }
}
}

@Composable
private fun LibraryHeaderSection(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    isSearchFocused: Boolean,
    onSearchFocusChange: (Boolean) -> Unit,
    onSearchCoordinatesPositioned: (LayoutCoordinates) -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenSettings: (SettingsSubpage?) -> Unit,
    onToast: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Library Header with 3 Dots Menu Button
        LibraryHeader(
            viewMode = uiState.viewMode,
            sortOrder = uiState.sortOrder,
            onToggleViewMode = {
                val next = if (uiState.viewMode == LibraryViewMode.GRID) "List view" else "Grid view"
                onToast("Switched to $next")
                viewModel.toggleViewMode()
            },
            onSelectSortOrder = { order ->
                onToast("Sorted by ${order.title}")
                viewModel.setSortOrder(order)
            },
            onOpenSettings = {
                onOpenSettings(null)
            },
            onOpenHelpAndSupport = {
                onOpenSettings(SettingsSubpage.HELP_AND_SUPPORT)
            }
        )

        // 2. Search Field with standard UX (No border)
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = {
                Text(
                    text = "Search books & documents...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotBlank()) {
                    IconButton(onClick = {
                        viewModel.setSearchQuery("")
                        focusManager.clearFocus()
                        onToast("Search cleared")
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                }
            ),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_field")
                .onFocusChanged { onSearchFocusChange(it.isFocused) }
                .onGloballyPositioned { onSearchCoordinatesPositioned(it) }
        )

        // 3. Continue Reading Hero Card (Up to 5 books left to read)
        if (uiState.searchQuery.isBlank() && uiState.booksLeftToRead.isNotEmpty()) {
            ContinueReadingHero(
                books = uiState.booksLeftToRead,
                onRead = { bookId ->
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onOpenBook(bookId)
                }
            )
        }

        // 4. WhatsApp-Style Filter Chips & Custom Lists
        FilterChipsRow(
            activeFilterId = uiState.activeFilterId,
            filterChips = uiState.filterChipItems,
            onFilterSelected = { filterId ->
                focusManager.clearFocus()
                keyboardController?.hide()
                viewModel.setFilterById(filterId)
            },
            onAddNewFilter = {
                focusManager.clearFocus()
                keyboardController?.hide()
                viewModel.openCreateFilterDialog()
            },
            onRenameCustomFilter = { customFilter ->
                focusManager.clearFocus()
                keyboardController?.hide()
                viewModel.openRenameFilterDialog(customFilter)
            },
            onEditCustomFilter = { customFilter ->
                focusManager.clearFocus()
                keyboardController?.hide()
                viewModel.openEditFilterDialog(customFilter)
            },
            onDeleteCustomFilter = { filterId ->
                focusManager.clearFocus()
                keyboardController?.hide()
                viewModel.deleteCustomFilter(filterId)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryHeader(
    viewMode: LibraryViewMode,
    sortOrder: LibrarySortOrder,
    onToggleViewMode: () -> Unit,
    onSelectSortOrder: (LibrarySortOrder) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelpAndSupport: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showSortPopup by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "LUMINA",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Library",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 3 Dots Menu Button at Top-Right
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .size(42.dp)
                    .testTag("library_overflow_menu_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Library Options",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.clip(RoundedCornerShape(16.dp))
            ) {
                // 1. Grid/List Toggle
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (viewMode == LibraryViewMode.GRID) "View as List" else "View as Grid",
                            fontWeight = FontWeight.Medium
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleViewMode()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (viewMode == LibraryViewMode.GRID) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("menu_item_toggle_view")
                )

                // 2. Sort Options (from main menu)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Sort by", fontWeight = FontWeight.Medium)
                            Text(
                                text = sortOrder.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        showMenu = false
                        showSortPopup = true
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Sort,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    },
                    modifier = Modifier.testTag("menu_item_sort")
                )

                // 3. Settings Page
                DropdownMenuItem(
                    text = { Text("Settings", fontWeight = FontWeight.Medium) },
                    onClick = {
                        showMenu = false
                        onOpenSettings()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("menu_item_settings")
                )

                // 4. Help & Support
                DropdownMenuItem(
                    text = { Text("Help & Support", fontWeight = FontWeight.Medium) },
                    onClick = {
                        showMenu = false
                        onOpenHelpAndSupport()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("menu_item_help")
                )
            }
        }
    }

    // Popup Dialog: Sort Order Picker
    if (showSortPopup) {
        AlertDialog(
            onDismissRequest = { showSortPopup = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFF9100),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Sort,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Sort Books By",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Organize your reading library",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LibrarySortOrder.values().forEach { order ->
                        val isSelected = sortOrder == order
                        val subtitle = when (order) {
                            LibrarySortOrder.RECENT -> "Most recently opened & read books first"
                            LibrarySortOrder.DATE_ADDED -> "Newly added & imported books first"
                            LibrarySortOrder.TITLE_ASC -> "Alphabetical order from A to Z"
                            LibrarySortOrder.TITLE_DESC -> "Reverse alphabetical order Z to A"
                            LibrarySortOrder.AUTHOR_ASC -> "Alphabetical by author name"
                            LibrarySortOrder.PROGRESS -> "Highest reading progress first"
                            LibrarySortOrder.PAGE_COUNT_DESC -> "Thickest books with highest page count"
                            LibrarySortOrder.PAGE_COUNT_ASC -> "Quick reads with shortest page count"
                        }

                        Surface(
                            onClick = {
                                onSelectSortOrder(order)
                                showSortPopup = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .testTag("menu_sort_${order.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = order.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showSortPopup = false }
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContinueReadingHero(
    books: List<BookEntity>,
    onRead: (Long) -> Unit
) {
    if (books.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("continue_reading_hero_section")
    ) {
        val pagerState = rememberPagerState(pageCount = { books.size })

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { pageIndex ->
            val book = books[pageIndex]
            Card(
                onClick = { onRead(book.id) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("continue_reading_hero_${book.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Book Cover Thumbnail
                    BookCoverThumbnail(
                        coverImagePath = book.coverImagePath,
                        filePath = book.filePath,
                        title = book.title,
                        modifier = Modifier
                            .width(72.dp)
                            .aspectRatio(0.7f)
                            .clip(RoundedCornerShape(8.dp))
                            .shadow(3.dp, RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (books.size > 1) "CONTINUE READING (${pageIndex + 1}/${books.size})" else "CONTINUE READING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (book.isPinned) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Pinned",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val displayAuthor = book.author.takeIf { it.isNotBlank() && !it.equals("Imported Document", ignoreCase = true) }
                        if (displayAuthor != null) {
                            Text(
                                text = displayAuthor,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress Bar & Stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Page ${book.currentPage} of ${book.totalPages}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(book.progressPercent * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        LinearProgressIndicator(
                            progress = { book.progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        if (books.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(books.size) { iteration ->
                    val isSelected = pagerState.currentPage == iteration
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FilterChipsRow(
    activeFilterId: String,
    filterChips: List<FilterChipItem>,
    onFilterSelected: (String) -> Unit,
    onAddNewFilter: () -> Unit,
    onRenameCustomFilter: (CustomFilter) -> Unit,
    onEditCustomFilter: (CustomFilter) -> Unit,
    onDeleteCustomFilter: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Render each filter chip
        filterChips.forEach { chip ->
            val isSelected = chip.id == activeFilterId
            var showMenu by remember { mutableStateOf(false) }

            Box {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .combinedClickable(
                            onClick = {
                                if (isSelected && chip.isCustom && chip.customFilter != null) {
                                    showMenu = true
                                } else {
                                    onFilterSelected(chip.id)
                                }
                            },
                            onLongClick = {
                                if (chip.isCustom && chip.customFilter != null) {
                                    showMenu = true
                                }
                            }
                        )
                        .testTag("filter_chip_${chip.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = chip.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        if (chip.count > 0 || isSelected) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Text(
                                    text = "${chip.count}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Dropdown menu for custom lists (Rename / Edit / Delete)
                if (chip.isCustom && chip.customFilter != null) {
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename List", fontWeight = FontWeight.Medium) },
                            onClick = {
                                showMenu = false
                                onRenameCustomFilter(chip.customFilter)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("menu_rename_filter_${chip.id}")
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Books", fontWeight = FontWeight.Medium) },
                            onClick = {
                                showMenu = false
                                onEditCustomFilter(chip.customFilter)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.List,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("menu_edit_filter_${chip.id}")
                        )
                        DropdownMenuItem(
                            text = { Text("Delete List", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDeleteCustomFilter(chip.id)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            modifier = Modifier.testTag("menu_delete_filter_${chip.id}")
                        )
                    }
                }
            }
        }

        // "+ New List" button (without border)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            contentColor = MaterialTheme.colorScheme.primary,
            border = null,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onAddNewFilter() }
                .testTag("add_custom_filter_chip")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New List",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "New List",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCardItem(
    book: BookEntity,
    currentFilterId: String = "ALL",
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onAssignToList: () -> Unit,
    onRemoveFromCurrentFilter: (() -> Unit)? = null,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .testTag("book_card_${book.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                BookCoverThumbnail(
                    coverImagePath = book.coverImagePath,
                    filePath = book.filePath,
                    title = book.title,
                    modifier = Modifier.fillMaxSize()
                )

                // Spine Shadow simulation on card
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0x28000000), Color.Transparent)
                            )
                        )
                )

                // Top badges (Pin & Bookmark)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (book.isPinned) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(12.dp)
                            )
                        }
                    }

                    val bookmarkCount = book.getBookmarkPages().size
                    if (bookmarkCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bookmark,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "$bookmarkCount",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Options Menu Button with theme-adapted backdrop (No shadow)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 0.dp,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.size(30.dp)
                    ) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("book_card_options_${book.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (book.isPinned) "Unpin Book" else "Pin Book",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showMenu = false
                                onTogglePin()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (book.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("menu_pin_card_${book.id}")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Add to List...",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showMenu = false
                                onAssignToList()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.PlaylistAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("menu_assign_card_${book.id}")
                        )
                        if (onRemoveFromCurrentFilter != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Remove from List",
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onRemoveFromCurrentFilter()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                modifier = Modifier.testTag("menu_remove_from_list_card_${book.id}")
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Share PDF",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showMenu = false
                                PdfShareHelper.sharePdf(context, book)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("menu_share_card_${book.id}")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Copy Book",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showMenu = false
                                onCopy()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("menu_copy_card_${book.id}")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Delete Book",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            modifier = Modifier.testTag("menu_delete_card_${book.id}")
                        )
                    }
                }
            }

            // Info Section with fixed deterministic dimensions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    ),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (book.hasBeenOpened) "p. ${book.currentPage}/${book.totalPages}" else "${book.totalPages} pages",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (book.hasBeenOpened) "${(book.progressPercent * 100).toInt()}%" else "Unread",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = if (book.hasBeenOpened) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (book.hasBeenOpened) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { if (book.hasBeenOpened) book.progressPercent else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = if (book.hasBeenOpened) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListItem(
    book: BookEntity,
    currentFilterId: String = "ALL",
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onAssignToList: () -> Unit,
    onRemoveFromCurrentFilter: (() -> Unit)? = null,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .testTag("book_list_item_${book.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Book Cover Thumbnail with Pin badge
            Box {
                BookCoverThumbnail(
                    coverImagePath = book.coverImagePath,
                    filePath = book.filePath,
                    title = book.title,
                    modifier = Modifier
                        .width(52.dp)
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(6.dp))
                        .shadow(2.dp, RoundedCornerShape(6.dp))
                )
                if (book.isPinned) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(2.dp)
                                .size(10.dp)
                        )
                    }
                }
            }

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (book.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                val displayAuthor = book.author.takeIf { it.isNotBlank() && !it.equals("Imported Document", ignoreCase = true) }
                if (displayAuthor != null) {
                    Text(
                        text = displayAuthor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (book.hasBeenOpened) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Page ${book.currentPage} of ${book.totalPages}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(book.progressPercent * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { book.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${book.totalPages} pages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Unread",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // More Options
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("book_list_options_${book.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (book.isPinned) "Unpin Book" else "Pin Book",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (book.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.testTag("menu_pin_list_${book.id}")
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Add to List...",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            showMenu = false
                            onAssignToList()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.PlaylistAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.testTag("menu_assign_list_${book.id}")
                    )
                    if (onRemoveFromCurrentFilter != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Remove from List",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onRemoveFromCurrentFilter()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            modifier = Modifier.testTag("menu_remove_from_list_item_${book.id}")
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Share PDF",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            showMenu = false
                            PdfShareHelper.sharePdf(context, book)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.testTag("menu_share_list_${book.id}")
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Copy Book",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            showMenu = false
                            onCopy()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.testTag("menu_copy_list_${book.id}")
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Delete Book",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.testTag("menu_delete_list_${book.id}")
                    )
                }
            }
        }
    }
}

/**
 * WhatsApp-style Create Custom Filter List Dialog
 */
@Composable
private fun CreateCustomFilterDialog(
    allBooks: List<BookEntity>,
    onDismiss: () -> Unit,
    onCreate: (name: String, selectedBookIds: List<Long>) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var filterName by remember { mutableStateOf("") }
    var bookSearchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }

    val filteredList = remember(allBooks, bookSearchQuery) {
        if (bookSearchQuery.isBlank()) {
            allBooks
        } else {
            allBooks.filter {
                it.title.contains(bookSearchQuery, ignoreCase = true) ||
                (it.author?.contains(bookSearchQuery, ignoreCase = true) == true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            focusManager.clearFocus()
            keyboardController?.hide()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 560.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "New List",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // List Name Input
                OutlinedTextField(
                    value = filterName,
                    onValueChange = { filterName = it },
                    placeholder = {
                        Text(
                            text = "List name",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_filter_name_input")
                )

                // Select Books Header & Search
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Included Books (${selectedIds.size} selected)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (allBooks.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                if (selectedIds.size == allBooks.size) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    selectedIds.addAll(allBooks.map { it.id })
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedIds.size == allBooks.size) "Deselect All" else "Select All",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (allBooks.size > 5) {
                    OutlinedTextField(
                        value = bookSearchQuery,
                        onValueChange = { bookSearchQuery = it },
                        placeholder = {
                            Text(
                                text = "Filter books below...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (bookSearchQuery.isNotBlank()) {
                                IconButton(onClick = { bookSearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            errorBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Books Selection Checklist
                if (allBooks.isEmpty()) {
                    Text(
                        text = "No books in library yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredList, key = { it.id }) { book ->
                            val isChecked = selectedIds.contains(book.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        if (isChecked) selectedIds.remove(book.id) else selectedIds.add(book.id)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        if (checked) selectedIds.add(book.id) else selectedIds.remove(book.id)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!book.author.isNullOrBlank()) {
                                        Text(
                                            text = book.author,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    if (filterName.isNotBlank()) {
                        onCreate(filterName.trim(), selectedIds.toList())
                    }
                },
                enabled = filterName.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("confirm_create_filter_btn")
            ) {
                Text("Create List")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

/**
 * WhatsApp-style Edit / Manage Custom Filter List Dialog
 */
@Composable
private fun EditCustomFilterDialog(
    customFilter: CustomFilter,
    allBooks: List<BookEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, selectedBookIds: List<Long>) -> Unit,
    onDelete: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var filterName by remember(customFilter.id) { mutableStateOf(customFilter.name) }
    var bookSearchQuery by remember { mutableStateOf("") }
    val selectedIds = remember(customFilter.id) {
        mutableStateListOf<Long>().apply { addAll(customFilter.bookIds) }
    }

    val filteredList = remember(allBooks, bookSearchQuery) {
        if (bookSearchQuery.isBlank()) {
            allBooks
        } else {
            allBooks.filter {
                it.title.contains(bookSearchQuery, ignoreCase = true) ||
                (it.author?.contains(bookSearchQuery, ignoreCase = true) == true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            focusManager.clearFocus()
            keyboardController?.hide()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 560.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit List",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onDelete()
                }) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete list",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = filterName,
                    onValueChange = { filterName = it },
                    placeholder = {
                        Text(
                            text = "List name",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_filter_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Included Books (${selectedIds.size} selected)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (allBooks.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                if (selectedIds.size == allBooks.size) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    selectedIds.addAll(allBooks.map { it.id })
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedIds.size == allBooks.size) "Deselect All" else "Select All",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (allBooks.size > 5) {
                    OutlinedTextField(
                        value = bookSearchQuery,
                        onValueChange = { bookSearchQuery = it },
                        placeholder = {
                            Text(
                                text = "Filter books below...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (bookSearchQuery.isNotBlank()) {
                                IconButton(onClick = { bookSearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            errorBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (allBooks.isEmpty()) {
                    Text(
                        text = "No books in library yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredList, key = { it.id }) { book ->
                            val isChecked = selectedIds.contains(book.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        if (isChecked) selectedIds.remove(book.id) else selectedIds.add(book.id)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        if (checked) selectedIds.add(book.id) else selectedIds.remove(book.id)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!book.author.isNullOrBlank()) {
                                        Text(
                                            text = book.author,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    if (filterName.isNotBlank()) {
                        onSave(filterName.trim(), selectedIds.toList())
                    }
                },
                enabled = filterName.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("save_edit_filter_btn")
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Rename Custom Filter List Dialog
 */
@Composable
private fun RenameCustomFilterDialog(
    customFilter: CustomFilter,
    onDismiss: () -> Unit,
    onRename: (newName: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var filterName by remember { mutableStateOf(customFilter.name) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = {
            focusManager.clearFocus()
            keyboardController?.hide()
            onDismiss()
        },
        title = {
            Text(
                text = "Rename List",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Enter a new name for this reading list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = filterName,
                    onValueChange = { filterName = it },
                    placeholder = {
                        Text(
                            text = "List name (e.g., Favorites, Research, Sci-Fi)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            if (filterName.isNotBlank()) {
                                onRename(filterName.trim())
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("rename_filter_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    if (filterName.isNotBlank()) {
                        onRename(filterName.trim())
                    }
                },
                enabled = filterName.isNotBlank() && filterName.trim() != customFilter.name,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("confirm_rename_filter_btn")
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onDismiss()
                },
                modifier = Modifier.testTag("cancel_rename_filter_btn")
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Assign Book to Custom Filter Lists Dialog
 */
@Composable
private fun AssignBookToListDialog(
    book: BookEntity,
    customFilters: List<CustomFilter>,
    onDismiss: () -> Unit,
    onSave: (assignedFilterIds: List<String>) -> Unit,
    onCreateNewFilter: () -> Unit
) {
    val initialAssigned = remember(book.id, customFilters) {
        customFilters.filter { it.bookIds.contains(book.id) }.map { it.id }
    }
    val assignedFilterIds = remember(book.id) {
        mutableStateListOf<String>().apply { addAll(initialAssigned) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 560.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Column {
                Text(
                    text = "Add to Filter Lists",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "\"${book.title}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (customFilters.isEmpty()) {
                    Text(
                        text = "No custom filter lists created yet. Create a list below to easily group and sort your books.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(customFilters, key = { it.id }) { filter ->
                            val isAssigned = assignedFilterIds.contains(filter.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isAssigned) assignedFilterIds.remove(filter.id) else assignedFilterIds.add(filter.id)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = isAssigned,
                                    onCheckedChange = { checked ->
                                        if (checked) assignedFilterIds.add(filter.id) else assignedFilterIds.remove(filter.id)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = filter.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${filter.bookIds.size} books",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick button to make a new list
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onDismiss()
                            onCreateNewFilter()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Create New Filter List",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(assignedFilterIds.toList())
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("save_book_assignment_btn")
            ) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * In-memory LRU cache to prevent repeated decoding of cover images on scroll and eliminate flicker
 */
private object CoverMemoryCache {
    private val maxMem = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMem / 16).coerceIn(4096, 24576) // 4MB - 24MB
    val lru = object : android.util.LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }
}

@Composable
private fun BookCoverThumbnail(
    coverImagePath: String?,
    filePath: String? = null,
    title: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cacheKey = coverImagePath ?: filePath ?: title
    val initialCached = remember(cacheKey) { CoverMemoryCache.lru.get(cacheKey) }
    var bitmapLoaded by remember(cacheKey) {
        mutableStateOf(initialCached)
    }

    if (initialCached == null) {
        LaunchedEffect(cacheKey) {
            val inCache = CoverMemoryCache.lru.get(cacheKey)
            if (inCache != null) {
                bitmapLoaded = inCache
                return@LaunchedEffect
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var loaded: Bitmap? = null

                // 1. Try loading cached cover image file if available and valid
                if (!coverImagePath.isNullOrBlank()) {
                    val file = File(coverImagePath)
                    if (file.exists() && file.length() > 0) {
                        try {
                            val opts = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.RGB_565
                            }
                            loaded = BitmapFactory.decodeFile(file.absolutePath, opts)
                        } catch (_: Throwable) {}
                    }
                }

                // 2. If cover file is missing or unreadable, dynamically render page 0 directly from the PDF file
                if (loaded == null && !filePath.isNullOrBlank()) {
                    val pdfFile = File(filePath)
                    if (pdfFile.exists() && pdfFile.length() > 0) {
                        var pfd: ParcelFileDescriptor? = null
                        var renderer: PdfRenderer? = null
                        var page: PdfRenderer.Page? = null
                        try {
                            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                            renderer = PdfRenderer(pfd)
                            if (renderer.pageCount > 0) {
                                page = renderer.openPage(0)
                                val pw = page.width
                                val ph = page.height
                                val scale = minOf(360f / pw.toFloat(), 520f / ph.toFloat(), 1.0f).coerceAtLeast(0.15f)
                                val outW = (pw * scale).toInt().coerceIn(120, 480)
                                val outH = (ph * scale).toInt().coerceIn(180, 720)

                                val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.RGB_565)
                                val canvas = Canvas(bmp)
                                canvas.drawColor(AndroidColor.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                loaded = bmp

                                // Persist to covers directory so subsequent loads are immediate
                                val coversDir = File(context.filesDir, "covers")
                                if (!coversDir.exists()) coversDir.mkdirs()
                                val cachedCover = File(coversDir, "${pdfFile.nameWithoutExtension}_cover.jpg")
                                java.io.FileOutputStream(cachedCover).use { out ->
                                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                }
                            }
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        } finally {
                            try { page?.close() } catch (_: Throwable) {}
                            try { renderer?.close() } catch (_: Throwable) {}
                            try { pfd?.close() } catch (_: Throwable) {}
                        }
                    }
                }

                if (loaded != null) {
                    CoverMemoryCache.lru.put(cacheKey, loaded)
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        bitmapLoaded = loaded
                    }
                }
            }
        }
    }

    val currentBmp = bitmapLoaded?.takeIf { !it.isRecycled }
    val imgBitmap = currentBmp?.let { bmp ->
        try { bmp.asImageBitmap() } catch (_: Throwable) { null }
    }

    if (imgBitmap != null) {
        Image(
            bitmap = imgBitmap,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        // Aesthetic Fallback Book Cover (or decoding placeholder)
        Box(
            modifier = modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(onUpload: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Text(
            text = "No Books Found",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Text(
            text = "Upload PDF documents or books to begin reading with realistic physical page flipping animations.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onUpload,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("empty_state_upload_button")
        ) {
            Icon(imageVector = Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Upload First Book")
        }
    }
}
