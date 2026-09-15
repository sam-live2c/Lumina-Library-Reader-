package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.ReaderThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppSettingsManager {
    private const val PREFS_NAME = "lumina_settings_prefs"
    const val KEY_FULL_SCREEN_MODE = "pref_full_screen_mode"
    const val KEY_APP_THEME = "pref_reader_theme"

    private var sharedPrefs: SharedPreferences? = null

    private val _isFullScreenModeEnabled = MutableStateFlow(false)
    val isFullScreenModeEnabled: StateFlow<Boolean> = _isFullScreenModeEnabled.asStateFlow()

    private val _currentAppTheme = MutableStateFlow(ReaderThemeMode.WHITE)
    val currentAppTheme: StateFlow<ReaderThemeMode> = _currentAppTheme.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == KEY_FULL_SCREEN_MODE) {
            val enabled = prefs.getBoolean(KEY_FULL_SCREEN_MODE, false)
            if (_isFullScreenModeEnabled.value != enabled) {
                _isFullScreenModeEnabled.value = enabled
            }
        } else if (key == KEY_APP_THEME) {
            val themeName = prefs.getString(KEY_APP_THEME, ReaderThemeMode.WHITE.name) ?: ReaderThemeMode.WHITE.name
            val theme = try {
                ReaderThemeMode.valueOf(themeName)
            } catch (_: Exception) {
                ReaderThemeMode.WHITE
            }
            if (_currentAppTheme.value != theme) {
                _currentAppTheme.value = theme
            }
        }
    }

    fun init(context: Context) {
        if (sharedPrefs == null) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs = prefs
            _isFullScreenModeEnabled.value = prefs.getBoolean(KEY_FULL_SCREEN_MODE, false)
            val themeName = prefs.getString(KEY_APP_THEME, ReaderThemeMode.WHITE.name) ?: ReaderThemeMode.WHITE.name
            _currentAppTheme.value = try {
                ReaderThemeMode.valueOf(themeName)
            } catch (_: Exception) {
                ReaderThemeMode.WHITE
            }
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
        }
    }

    fun setFullScreenMode(enabled: Boolean) {
        _isFullScreenModeEnabled.value = enabled
        sharedPrefs?.edit()?.putBoolean(KEY_FULL_SCREEN_MODE, enabled)?.apply()
    }

    fun setAppTheme(theme: ReaderThemeMode) {
        _currentAppTheme.value = theme
        sharedPrefs?.edit()?.putString(KEY_APP_THEME, theme.name)?.apply()
    }

    fun applySystemBars(activity: Activity?, isFullScreen: Boolean, isDarkTheme: Boolean = false) {
        if (activity == null) return
        val window = activity.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullScreen) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}

