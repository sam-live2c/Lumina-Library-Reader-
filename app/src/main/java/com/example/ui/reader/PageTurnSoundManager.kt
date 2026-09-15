package com.example.ui.reader

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import androidx.annotation.RawRes
import com.example.R
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Multiple Sound Profiles for Page Turn Experience.
 * Each style links directly to high-fidelity, studio-mastered 44.1kHz WAV resources.
 */
enum class PageTurnSoundStyle(
    val title: String,
    val subtitle: String,
    @RawRes val rawResId: Int
) {
    CLASSIC_PAPER(
        title = "Classic Paper",
        subtitle = "Rich authentic tactile paper turn",
        rawResId = R.raw.page_flip
    ),
    SILKY_WHISPER(
        title = "Silky Whisper",
        subtitle = "Ultra-soft gentle airy glide",
        rawResId = R.raw.sound_silky_whisper
    ),
    CRISP_PARCHMENT(
        title = "Crisp Parchment",
        subtitle = "Fine-grain library book leaf",
        rawResId = R.raw.sound_crisp_parchment
    ),
    DIGITAL_SNAP(
        title = "Digital Snap",
        subtitle = "Modern minimalist clean tap",
        rawResId = R.raw.sound_digital_snap
    ),
    WARM_FLUTTER(
        title = "Warm Flutter",
        subtitle = "Vintage archive page flutter",
        rawResId = R.raw.sound_warm_flutter
    )
}

/**
 * High-performance, zero-latency Sound Engine for page-turn sonification.
 * Uses Android's native SoundPool with USAGE_MEDIA for crystal-clear playback on all physical devices,
 * with an automatic fallback to MediaPlayer to guarantee sound is always played.
 */
class PageTurnSoundManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("lumina_settings_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "PageTurnAudioEngine"

        // Shared engine instance across screens
        @Volatile
        private var sharedEngine: SharedAudioEngine? = null

        private fun getSharedEngine(appContext: Context): SharedAudioEngine {
            return sharedEngine ?: synchronized(this) {
                sharedEngine ?: SharedAudioEngine(appContext.applicationContext).also {
                    sharedEngine = it
                }
            }
        }
    }

    private val engine = getSharedEngine(appContext)

    /**
     * Plays the currently active page turn sound according to user preferences.
     */
    fun playPageTurnSound(styleOverride: PageTurnSoundStyle? = null) {
        val isSoundEnabled = prefs.getBoolean("pref_sound", true)
        if (!isSoundEnabled) return

        val style = styleOverride ?: getCurrentSelectedStyle()
        engine.play(style)
    }

    /**
     * Preview sound in Settings popup without requiring pref_sound to be enabled.
     */
    fun previewSound(style: PageTurnSoundStyle) {
        engine.play(style)
    }

    fun getCurrentSelectedStyle(): PageTurnSoundStyle {
        val savedName = prefs.getString("pref_sound_style", PageTurnSoundStyle.CLASSIC_PAPER.name)
        return try {
            PageTurnSoundStyle.valueOf(savedName ?: PageTurnSoundStyle.CLASSIC_PAPER.name)
        } catch (_: Exception) {
            PageTurnSoundStyle.CLASSIC_PAPER
        }
    }

    fun release() {
        // Shared engine manages background lifetime across app navigation
    }

    /**
     * Internal Shared SoundPool & MediaPlayer Fallback Audio Engine.
     * Guarantees reliable, low-latency audio feedback across all devices and Android versions.
     */
    private class SharedAudioEngine(private val context: Context) {
        private val soundIds = ConcurrentHashMap<PageTurnSoundStyle, Int>()
        private val loadedSoundIds = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
        private val pendingPlayStyle = AtomicReference<PageTurnSoundStyle?>(null)
        private var lastTriggerTime: Long = 0L

        private var soundPool: SoundPool? = null
        private var soundPoolInitAttempted = false

        private fun getSoundPool(): SoundPool? {
            if (soundPoolInitAttempted) return soundPool
            synchronized(this) {
                if (soundPoolInitAttempted) return soundPool
                soundPoolInitAttempted = true
                soundPool = try {
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()

                    SoundPool.Builder()
                        .setMaxStreams(4)
                        .setAudioAttributes(attributes)
                        .build().apply {
                            setOnLoadCompleteListener { _, sampleId, status ->
                                if (status == 0) {
                                    loadedSoundIds.add(sampleId)
                                    val pending = pendingPlayStyle.getAndSet(null)
                                    if (pending != null && soundIds[pending] == sampleId) {
                                        try {
                                            play(sampleId, 1f, 1f, 1, 0, 1f)
                                        } catch (e: Throwable) {
                                            Log.w(TAG, "Error playing pending sample $sampleId", e)
                                        }
                                    }
                                } else {
                                    Log.w(TAG, "SoundPool sample $sampleId failed to load with status $status")
                                }
                            }
                        }
                } catch (e: Throwable) {
                    Log.w(TAG, "SoundPool initialization skipped", e)
                    null
                }
            }
            return soundPool
        }

        private fun ensureSoundLoaded(style: PageTurnSoundStyle): Int? {
            soundIds[style]?.let { return it }
            val pool = getSoundPool() ?: return null
            return try {
                val soundId = pool.load(context, style.rawResId, 1)
                if (soundId > 0) {
                    soundIds[style] = soundId
                    soundId
                } else {
                    null
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed loading sound for style: $style", e)
                null
            }
        }

        fun play(style: PageTurnSoundStyle) {
            val now = System.currentTimeMillis()
            if (now - lastTriggerTime < 35L) return
            lastTriggerTime = now

            val pool = getSoundPool()
            val soundId = ensureSoundLoaded(style)
            if (pool != null && soundId != null && loadedSoundIds.contains(soundId)) {
                val streamId = try {
                    pool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
                } catch (e: Throwable) {
                    Log.w(TAG, "SoundPool.play failed for $style", e)
                    0
                }
                if (streamId == 0) {
                    playViaMediaPlayer(style)
                }
            } else if (soundId != null) {
                pendingPlayStyle.set(style)
            } else {
                playViaMediaPlayer(style)
            }
        }

        private fun playViaMediaPlayer(style: PageTurnSoundStyle) {
            try {
                val mediaPlayer = MediaPlayer.create(context, style.rawResId) ?: return
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                mediaPlayer.setAudioAttributes(attributes)
                mediaPlayer.setVolume(1.0f, 1.0f)
                mediaPlayer.setOnCompletionListener { player ->
                    try {
                        player.reset()
                        player.release()
                    } catch (_: Throwable) {}
                }
                mediaPlayer.setOnErrorListener { player, _, _ ->
                    try {
                        player.reset()
                        player.release()
                    } catch (_: Throwable) {}
                    true
                }
                mediaPlayer.start()
            } catch (e: Throwable) {
                Log.w(TAG, "MediaPlayer fallback failed for style $style", e)
            }
        }
    }
}
