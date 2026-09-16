package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.ReaderThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

object AppSettingsManager {
    private const val PREFS_NAME = "lumina_settings_prefs"
    const val KEY_FULL_SCREEN_MODE = "pref_full_screen_mode"
    const val KEY_APP_THEME = "pref_reader_theme"

    private var sharedPrefs: SharedPreferences? = null
    private var currentActivity: WeakReference<Activity>? = null

    private val _isFullScreenModeEnabled = MutableStateFlow(false)
    val isFullScreenModeEnabled: StateFlow<Boolean> = _isFullScreenModeEnabled.asStateFlow()

    private val _currentAppTheme = MutableStateFlow(ReaderThemeMode.WHITE)
    val currentAppTheme: StateFlow<ReaderThemeMode> = _currentAppTheme.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == KEY_FULL_SCREEN_MODE) {
            val enabled = prefs.getBoolean(KEY_FULL_SCREEN_MODE, false)
            if (_isFullScreenModeEnabled.value != enabled) {
                _isFullScreenModeEnabled.value = enabled
                applySystemBars(currentActivity?.get(), enabled, _currentAppTheme.value.isDark)
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
                applySystemBars(currentActivity?.get(), _isFullScreenModeEnabled.value, theme.isDark)
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

    fun registerActivity(activity: Activity) {
        currentActivity = WeakReference(activity)
        applySystemBars(activity, _isFullScreenModeEnabled.value, _currentAppTheme.value.isDark)
    }

    fun unregisterActivity(activity: Activity) {
        if (currentActivity?.get() == activity) {
            currentActivity = null
        }
    }

    fun setFullScreenMode(enabled: Boolean) {
        _isFullScreenModeEnabled.value = enabled
        sharedPrefs?.edit()?.putBoolean(KEY_FULL_SCREEN_MODE, enabled)?.apply()
        applySystemBars(currentActivity?.get(), enabled, _currentAppTheme.value.isDark)
    }

    fun setAppTheme(theme: ReaderThemeMode) {
        _currentAppTheme.value = theme
        sharedPrefs?.edit()?.putString(KEY_APP_THEME, theme.name)?.apply()
        applySystemBars(currentActivity?.get(), _isFullScreenModeEnabled.value, theme.isDark)
    }

    fun applySystemBars(activity: Activity?, isFullScreen: Boolean, isDarkTheme: Boolean = false) {
        if (activity == null) return
        applyWindowSystemBars(activity.window, isFullScreen, isDarkTheme)
    }

    fun applyWindowSystemBars(window: Window?, isFullScreen: Boolean, isDarkTheme: Boolean = false) {
        if (window == null) return
        val block = Runnable {
            try {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
                insetsController.isAppearanceLightNavigationBars = !isDarkTheme
                if (isFullScreen) {
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            } catch (_: Throwable) {
                // Graceful fallback for various Android vendor custom decor views
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            block.run()
        } else {
            Handler(Looper.getMainLooper()).post(block)
        }
    }

    fun findWindow(view: View): Window? {
        var parent: ViewParent? = view.parent
        while (parent != null) {
            if (parent is DialogWindowProvider) {
                return parent.window
            }
            parent = parent.parent
        }
        val rootView = view.rootView
        if (rootView != null && rootView !== view) {
            if (rootView is DialogWindowProvider) {
                return rootView.window
            }
            try {
                val field = rootView.javaClass.getDeclaredField("mWindow")
                field.isAccessible = true
                val win = field.get(rootView) as? Window
                if (win != null) return win
            } catch (_: Throwable) {
                // Not a PhoneWindow DecorView
            }
        }
        var ctx: Context? = view.context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx.window
            }
            ctx = ctx.baseContext
        }
        return currentActivity?.get()?.window
    }
}

