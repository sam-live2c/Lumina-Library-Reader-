package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.AppSettingsManager
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.reader.ReaderScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsSubpage
import com.example.ui.settings.SettingsViewModel
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.LuminaTheme
import kotlinx.coroutines.delay

sealed interface AppDestination {
    data object Library : AppDestination
    data class Reader(val bookId: Long) : AppDestination
    data class Settings(val initialSubpage: SettingsSubpage? = null) : AppDestination
}

val AppDestinationSaver = Saver<AppDestination, String>(
    save = { dest ->
        when (dest) {
            is AppDestination.Library -> "library"
            is AppDestination.Reader -> "reader:${dest.bookId}"
            is AppDestination.Settings -> "settings:${dest.initialSubpage?.name ?: ""}"
        }
    },
    restore = { str ->
        val parts = str.split(":")
        when (parts[0]) {
            "library" -> AppDestination.Library
            "reader" -> {
                val id = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                AppDestination.Reader(id)
            }
            "settings" -> {
                val subpageName = parts.getOrNull(1)
                val subpage = if (!subpageName.isNullOrEmpty()) {
                    try { SettingsSubpage.valueOf(subpageName) } catch (_: Exception) { null }
                } else null
                AppDestination.Settings(subpage)
            }
            else -> AppDestination.Library
        }
    }
)

class MainActivity : ComponentActivity() {

    private val incomingPdfUriState = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettingsManager.init(this)
        val initialFullScreen = AppSettingsManager.isFullScreenModeEnabled.value
        if (initialFullScreen) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettingsManager.registerActivity(this)
        AppSettingsManager.applyWindowSystemBars(
            window,
            AppSettingsManager.isFullScreenModeEnabled.value,
            AppSettingsManager.currentAppTheme.value.isDark
        )
        extractPdfUriFromIntent(intent)

        setContent {
            LuminaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LuminaApp(
                        incomingUri = incomingPdfUriState.value,
                        onConsumeIncomingUri = { incomingPdfUriState.value = null }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppSettingsManager.unregisterActivity(this)
    }

    override fun onResume() {
        super.onResume()
        applyFullScreenPreference()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFullScreenPreference()
        }
    }

    private fun applyFullScreenPreference() {
        AppSettingsManager.applySystemBars(
            this,
            AppSettingsManager.isFullScreenModeEnabled.value,
            AppSettingsManager.currentAppTheme.value.isDark
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractPdfUriFromIntent(intent)
    }

    private fun extractPdfUriFromIntent(intent: Intent?) {
        if (intent == null) return
        try {
            val action = intent.action
            val uri: Uri? = when {
                action == Intent.ACTION_VIEW -> {
                    intent.data ?: intent.clipData?.getItemAt(0)?.uri
                }
                action == Intent.ACTION_SEND -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    } ?: intent.clipData?.getItemAt(0)?.uri ?: intent.data
                }
                else -> intent.data ?: intent.clipData?.getItemAt(0)?.uri
            }

            if (uri != null) {
                incomingPdfUriState.value = uri
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}

@Composable
fun LuminaApp(
    incomingUri: Uri? = null,
    onConsumeIncomingUri: () -> Unit = {}
) {
    val libraryViewModel: LibraryViewModel = viewModel()
    val libraryUiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val isFullScreen by AppSettingsManager.isFullScreenModeEnabled.collectAsStateWithLifecycle()
    val currentTheme by AppSettingsManager.currentAppTheme.collectAsStateWithLifecycle()
    var isSplashActive by rememberSaveable { mutableStateOf(true) }
    var currentDestination by rememberSaveable(stateSaver = AppDestinationSaver) { mutableStateOf<AppDestination>(AppDestination.Library) }

    // Dynamic full-screen mode listener: toggles status bar immediately when preference changes
    LaunchedEffect(isFullScreen, currentTheme) {
        AppSettingsManager.applySystemBars(activity, isFullScreen, isDarkTheme = currentTheme.isDark)
    }

    // Handle opening PDF directly from other applications ("Open with..." or "Share to")
    LaunchedEffect(incomingUri, isSplashActive) {
        if (!isSplashActive && incomingUri != null) {
            libraryViewModel.importPdf(incomingUri) { importedBookId ->
                currentDestination = AppDestination.Reader(importedBookId)
            }
            onConsumeIncomingUri()
        }
    }

    // Ensure all books are organized and sorted before ending the loading screen
    LaunchedEffect(libraryUiState.isOrganizedAndReady) {
        if (libraryUiState.isOrganizedAndReady) {
            // Keep brand splash visible for a brief moment (600ms)
            // guaranteeing that books are fully organized and sorted before dismissing splash
            delay(600)
            isSplashActive = false
        }
    }

    // Safety fallback timeout to guarantee splash screen dismisses
    LaunchedEffect(Unit) {
        delay(2000)
        isSplashActive = false
    }

    if (isSplashActive) {
        SplashScreen(isFullScreen = isFullScreen)
    } else {
        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = {
                androidx.compose.animation.EnterTransition.None.togetherWith(
                    androidx.compose.animation.ExitTransition.None
                )
            },
            label = "AppScreenNavigation"
        ) { destination ->
            when (destination) {
                is AppDestination.Library -> {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        isFullScreenModeEnabled = isFullScreen,
                        onOpenBook = { bookId ->
                            currentDestination = AppDestination.Reader(bookId)
                        },
                        onOpenSettings = { subpage ->
                            settingsViewModel.navigateTo(subpage ?: SettingsSubpage.MAIN)
                            currentDestination = AppDestination.Settings(subpage)
                        }
                    )
                }
                is AppDestination.Reader -> {
                    ReaderScreen(
                        bookId = destination.bookId,
                        onNavigateBack = {
                            currentDestination = AppDestination.Library
                        }
                    )
                }
                is AppDestination.Settings -> {
                    SettingsScreen(
                        initialSubpage = destination.initialSubpage,
                        viewModel = settingsViewModel,
                        onNavigateBack = {
                            settingsViewModel.navigateTo(SettingsSubpage.MAIN)
                            currentDestination = AppDestination.Library
                        }
                    )
                }
            }
        }
    }
}
