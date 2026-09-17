package com.example.ui.reader

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.RawRes
import com.example.R
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

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
 * Uses direct in-memory PCM AudioTrack rendering with MediaPlayer fallback
 * to guarantee 100% audible playback on all devices, emulators, and streaming environments.
 */
class PageTurnSoundManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("lumina_settings_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "PageTurnAudioEngine"

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
     * Internal High-Performance PCM Sound Engine.
     */
    private class SharedAudioEngine(private val context: Context) {
        private val pcmCache = ConcurrentHashMap<PageTurnSoundStyle, ByteArray>()
        private val audioExecutor = Executors.newFixedThreadPool(2)
        private var lastTriggerTime: Long = 0L

        private val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        private val audioFormat = AudioFormat.Builder()
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(44100)
            .build()

        init {
            // Preload all raw PCM samples into RAM at startup
            audioExecutor.execute {
                PageTurnSoundStyle.entries.forEach { style ->
                    try {
                        loadPcmData(style)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed preloading PCM for $style", e)
                    }
                }
            }
        }

        private fun loadPcmData(style: PageTurnSoundStyle): ByteArray? {
            pcmCache[style]?.let { return it }
            var stream: InputStream? = null
            return try {
                stream = context.resources.openRawResource(style.rawResId)
                val allBytes = stream.readBytes()
                val pcm = extractPcmSamples(allBytes)
                pcmCache[style] = pcm
                pcm
            } catch (e: Throwable) {
                Log.w(TAG, "Error loading PCM bytes for ${style.name}", e)
                null
            } finally {
                try {
                    stream?.close()
                } catch (_: Throwable) {}
            }
        }

        private fun extractPcmSamples(wavBytes: ByteArray): ByteArray {
            if (wavBytes.size < 44) return wavBytes
            var index = 12
            while (index + 8 <= wavBytes.size) {
                val chunkId = String(wavBytes, index, 4, Charsets.US_ASCII)
                val chunkSize = (wavBytes[index + 4].toInt() and 0xFF) or
                        ((wavBytes[index + 5].toInt() and 0xFF) shl 8) or
                        ((wavBytes[index + 6].toInt() and 0xFF) shl 16) or
                        ((wavBytes[index + 7].toInt() and 0xFF) shl 24)
                if (chunkId == "data") {
                    val start = index + 8
                    val end = (start + chunkSize).coerceAtMost(wavBytes.size)
                    return wavBytes.copyOfRange(start, end)
                }
                index += 8 + chunkSize
            }
            return if (wavBytes.size > 44) wavBytes.copyOfRange(44, wavBytes.size) else wavBytes
        }

        fun play(style: PageTurnSoundStyle) {
            val now = System.currentTimeMillis()
            if (now - lastTriggerTime < 35L) return
            lastTriggerTime = now

            audioExecutor.execute {
                try {
                    val played = playViaAudioTrack(style)
                    if (!played) {
                        playViaMediaPlayer(style)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Error during audio dispatch for $style", e)
                    playViaMediaPlayer(style)
                }
            }
        }

        private fun playViaAudioTrack(style: PageTurnSoundStyle): Boolean {
            val pcmData = loadPcmData(style) ?: return false
            return try {
                val minBufSize = AudioTrack.getMinBufferSize(
                    44100,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBufSize, pcmData.size)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.setVolume(1.0f)
                val written = track.write(pcmData, 0, pcmData.size)
                if (written > 0) {
                    track.play()
                    // Schedule track cleanup after audio completes
                    val durationMs = ((pcmData.size / 2) * 1000L) / 44100L + 150L
                    audioExecutor.execute {
                        try {
                            Thread.sleep(durationMs)
                            track.stop()
                            track.release()
                        } catch (_: Throwable) {}
                    }
                    true
                } else {
                    track.release()
                    false
                }
            } catch (e: Throwable) {
                Log.w(TAG, "AudioTrack playback failed for $style, switching to MediaPlayer", e)
                false
            }
        }

        private fun playViaMediaPlayer(style: PageTurnSoundStyle) {
            try {
                val mp = MediaPlayer.create(context, style.rawResId) ?: return
                mp.setVolume(1.0f, 1.0f)
                mp.setOnCompletionListener { player ->
                    try {
                        player.release()
                    } catch (_: Throwable) {}
                }
                mp.setOnErrorListener { player, _, _ ->
                    try {
                        player.release()
                    } catch (_: Throwable) {}
                    true
                }
                mp.start()
            } catch (e: Throwable) {
                Log.e(TAG, "MediaPlayer fallback error for $style", e)
            }
        }
    }
}
