package com.example.ui.reader

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.annotation.RawRes
import com.example.R
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

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
 * Uses native static PCM AudioTracks with USAGE_ASSISTANCE_SONIFICATION.
 * Completely bypasses MediaCodec and Codec2 hardware extraction queries.
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
     * Internal Shared Static PCM AudioTrack Engine.
     * Streams directly to AudioFlinger HAL without touching media decoders or codec services.
     */
    private class SharedAudioEngine(private val context: Context) {
        private val tracks = ConcurrentHashMap<PageTurnSoundStyle, AudioTrack>()
        private var lastTriggerTime: Long = 0L

        private val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        private val audioFormat = AudioFormat.Builder()
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(44100)
            .build()

        init {
            // Preload PCM tracks in background thread to guarantee instantaneous play
            Thread {
                PageTurnSoundStyle.entries.forEach { style ->
                    try {
                        getOrCreateTrack(style)
                    } catch (e: Throwable) {
                        Log.w(TAG, "AudioTrack preload failed for $style", e)
                    }
                }
            }.start()
        }

        private fun getOrCreateTrack(style: PageTurnSoundStyle): AudioTrack? {
            tracks[style]?.let { return it }
            synchronized(this) {
                tracks[style]?.let { return it }
                return try {
                    val pcmBytes = loadPcmData(style.rawResId) ?: return null
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(audioAttributes)
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(pcmBytes.size)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()

                    track.write(pcmBytes, 0, pcmBytes.size)
                    tracks[style] = track
                    track
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed creating AudioTrack for $style", e)
                    null
                }
            }
        }

        private fun loadPcmData(rawResId: Int): ByteArray? {
            var stream: InputStream? = null
            return try {
                stream = context.resources.openRawResource(rawResId)
                val allBytes = stream.readBytes()
                // Parse standard RIFF/WAV data chunk
                extractPcmSamples(allBytes)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed reading raw PCM data for res $rawResId", e)
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
            // Standard 44-byte WAV header fallback
            return if (wavBytes.size > 44) wavBytes.copyOfRange(44, wavBytes.size) else wavBytes
        }

        fun play(style: PageTurnSoundStyle) {
            val now = System.currentTimeMillis()
            if (now - lastTriggerTime < 35L) return
            lastTriggerTime = now

            val track = getOrCreateTrack(style) ?: return
            synchronized(track) {
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                    track.reloadStaticData()
                    track.play()
                } catch (e: Throwable) {
                    Log.w(TAG, "AudioTrack play invocation error for $style", e)
                }
            }
        }
    }
}

