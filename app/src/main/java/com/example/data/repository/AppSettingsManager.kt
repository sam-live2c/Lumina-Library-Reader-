package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppSettingsManager {
    private const val PREFS_NAME = "lumina_settings_prefs"
    const val KEY_FULL_SCREEN_MODE = "pref_full_screen_mode"

    private var sharedPrefs: SharedPreferences? = null

    private val _isFullScreenModeEnabled = MutableStateFlow(false)
    val isFullScreenModeEnabled: StateFlow<Boolean> = _isFullScreenModeEnabled.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == KEY_FULL_SCREEN_MODE) {
            val enabled = prefs.getBoolean(KEY_FULL_SCREEN_MODE, false)
            if (_isFullScreenModeEnabled.value != enabled) {
                _isFullScreenModeEnabled.value = enabled
            }
        }
    }

    fun init(context: Context) {
        if (sharedPrefs == null) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs = prefs
            _isFullScreenModeEnabled.value = prefs.getBoolean(KEY_FULL_SCREEN_MODE, false)
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
        }
    }

    fun setFullScreenMode(enabled: Boolean) {
        _isFullScreenModeEnabled.value = enabled
        sharedPrefs?.edit()?.putBoolean(KEY_FULL_SCREEN_MODE, enabled)?.apply()
    }

    fun applySystemBars(activity: Activity?, isFullScreen: Boolean, isDarkTheme: Boolean = false) {
        if (activity == null) return
        val window = activity.window ?: return
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullScreen) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
        }
    }
}
