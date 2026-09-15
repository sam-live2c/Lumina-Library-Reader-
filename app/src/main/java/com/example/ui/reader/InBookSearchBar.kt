package com.example.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.SearchMatch
import com.example.ui.theme.LuminaAccentPrimary
import com.example.ui.theme.ReaderThemeMode

@Composable
fun InBookSearchBar(
    query: String,
    matches: List<SearchMatch>,
    currentMatchIndex: Int,
    readerTheme: ReaderThemeMode,
    isFullScreenModeEnabled: Boolean = false,
    onQueryChange: (String) -> Unit,
    onNextMatch: () -> Unit,
    onPreviousMatch: () -> Unit,
    onSelectMatch: (Int) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Suggestions list is open while user is actively typing or focused; collapses on selection or enter
    var isSuggestionsOpen by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    val barBg = readerTheme.background
    val barText = readerTheme.textColor
    val barSubtext = readerTheme.textSecondaryColor
    val cardBg = when (readerTheme) {
        ReaderThemeMode.WHITE -> Color(0xFFF1F5F9)
        ReaderThemeMode.CREAM -> Color(0xFFEBE2D3)
        ReaderThemeMode.SEPIA -> Color(0xFFE2D3BB)
        ReaderThemeMode.NIGHT -> Color(0xFF1E2634)
    }

    Surface(
        color = barBg,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("in_book_search_bar")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Close / Back button
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onClose()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("search_close_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Close search",
                        tint = barText,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Search Text Input
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .clickable {
                            isSuggestionsOpen = true
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search word or page (e.g. 5, Gatsby)...",
                            color = barSubtext,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            isSuggestionsOpen = true
                            onQueryChange(it)
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = barText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(readerTheme.accentColor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            // On Enter/Search: Close suggestions dropdown, dismiss keyboard, and stay on active match
                            isSuggestionsOpen = false
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (matches.isNotEmpty()) {
                                onSelectMatch(currentMatchIndex.coerceIn(0, matches.size - 1))
                            }
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && query.isNotEmpty()) {
                                    isSuggestionsOpen = true
                                }
                            }
                            .testTag("search_input_field")
                    )
                }

                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onQueryChange("")
                            isSuggestionsOpen = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear search",
                            tint = barSubtext,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Matches Counter & Navigation Chevrons
                if (matches.isNotEmpty()) {
                    Text(
                        text = "${currentMatchIndex + 1}/${matches.size}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = readerTheme.accentColor,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = {
                            isSuggestionsOpen = false
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onPreviousMatch()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("search_prev_match")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Previous match",
                            tint = barText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            isSuggestionsOpen = false
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onNextMatch()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("search_next_match")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Next match",
                            tint = barText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (query.isNotBlank()) {
                    Text(
                        text = "0 found",
                        fontSize = 12.sp,
                        color = barSubtext,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }

            // Results List Dropdown preview (Shows search results & page jump items)
            if (isSuggestionsOpen && matches.isNotEmpty() && query.isNotBlank()) {
                HorizontalDivider(color = barSubtext.copy(alpha = 0.2f), thickness = 0.8.dp)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 210.dp)
                        .testTag("search_results_list")
                ) {
                    itemsIndexed(matches) { index, match ->
                        val isSelected = index == currentMatchIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Standard UX: Click suggestion -> dropdown closes, keyboard hides, user gets search result on page
                                    isSuggestionsOpen = false
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onSelectMatch(index)
                                }
                                .background(
                                    if (isSelected) readerTheme.accentColor.copy(alpha = 0.12f) else Color.Transparent
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (match.isPageJump) Color(0xFF1E88E5) else if (isSelected) readerTheme.accentColor else cardBg,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (match.isPageJump) "PAGE ${match.pageIndex}" else "p. ${match.pageIndex}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (match.isPageJump || isSelected) Color.White else barText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Text(
                                text = match.snippet,
                                fontSize = 13.sp,
                                color = if (isSelected) barText else barSubtext,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
