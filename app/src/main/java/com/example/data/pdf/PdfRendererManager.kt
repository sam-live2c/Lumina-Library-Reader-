package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class PdfRendererManager(private val context: Context) {

    private val mutex = Mutex()
    private var currentFilePath: String? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null

    // Allocate at most 1/8th of max heap (between 16MB and 48MB) for cached pages to prevent OutOfMemoryError
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 8).coerceIn(16 * 1024, 48 * 1024)

    private val pageCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return max(1, value.byteCount / 1024)
        }
    }

    // Cache page dimensions (pageWidth x pageHeight) for instant zero-jump layout sizing
    private val dimensionCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Int>>()

    fun getCachedPage(filePath: String, pageIndex: Int): Bitmap? {
        val cacheKey = "${filePath}_${pageIndex}"
        val cached = pageCache.get(cacheKey)
        return if (cached != null && !cached.isRecycled) cached else null
    }

    fun getPageDimensions(filePath: String, pageIndex: Int): Pair<Int, Int>? {
        val key = "${filePath}_${pageIndex}"
        return dimensionCache[key]
    }

    suspend fun openFile(filePath: String): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (currentFilePath == filePath && pdfRenderer != null) {
                    return@withContext pdfRenderer!!.pageCount
                }
                closeInternal()
                val file = File(filePath)
                if (!file.exists() || file.length() == 0L) {
                    return@withContext 0
                }
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                fileDescriptor = pfd
                pdfRenderer = renderer
                currentFilePath = filePath
                
                // Warm dimension cache for initial pages rapidly without bitmap overhead
                val pageCount = renderer.pageCount
                val scanLimit = minOf(pageCount, 50)
                for (i in 0 until scanLimit) {
                    val dimKey = "${filePath}_$i"
                    if (!dimensionCache.containsKey(dimKey)) {
                        try {
                            val p = renderer.openPage(i)
                            dimensionCache[dimKey] = Pair(p.width, p.height)
                            p.close()
                        } catch (_: Throwable) {}
                    }
                }

                pageCount
            } catch (t: Throwable) {
                t.printStackTrace()
                closeInternal()
                0
            }
        }
    }

    suspend fun getPageCount(filePath: String): Int = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext 0
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) {
            e.printStackTrace()
            0
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    suspend fun renderPage(
        filePath: String,
        pageIndex: Int,
        targetWidth: Int = 1080,
        targetHeight: Int = 1920
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${filePath}_${pageIndex}"
        val cached = pageCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return@withContext cached
        }

        mutex.withLock {
            val doubleCheck = pageCache.get(cacheKey)
            if (doubleCheck != null && !doubleCheck.isRecycled) {
                return@withContext doubleCheck
            }

            if (currentFilePath != filePath || pdfRenderer == null) {
                val file = File(filePath)
                if (!file.exists()) return@withContext null
                closeInternal()
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                fileDescriptor = pfd
                pdfRenderer = renderer
                currentFilePath = filePath
            }

            val renderer = pdfRenderer ?: return@withContext null
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

            var page: PdfRenderer.Page? = null
            try {
                page = renderer.openPage(pageIndex)
                val pageWidth = page.width
                val pageHeight = page.height

                dimensionCache[cacheKey] = Pair(pageWidth, pageHeight)

                // Calculate balanced resolution scale so text is crisp without causing OutOfMemory
                val scale = max(
                    targetWidth.toFloat() / max(1, pageWidth).toFloat(),
                    targetHeight.toFloat() / max(1, pageHeight).toFloat()
                ).coerceIn(1.2f, 2.2f)

                val maxDimW = 1440
                val maxDimH = 2560
                val outWidth = (pageWidth * scale).toInt().coerceIn(320, maxDimW)
                val outHeight = (pageHeight * scale).toInt().coerceIn(480, maxDimH)

                val bitmap = try {
                    Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    pageCache.evictAll()
                    System.gc()
                    try {
                        // Fallback to RGB_565 (2 bytes per pixel instead of 4) at lower resolution
                        val halfW = (outWidth * 0.7f).toInt().coerceAtLeast(320)
                        val halfH = (outHeight * 0.7f).toInt().coerceAtLeast(480)
                        Bitmap.createBitmap(halfW, halfH, Bitmap.Config.RGB_565)
                    } catch (oom2: OutOfMemoryError) {
                        null
                    }
                } ?: return@withContext null

                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                pageCache.put(cacheKey, bitmap)
                bitmap
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            } finally {
                try { page?.close() } catch (_: Throwable) {}
            }
        }
    }

    suspend fun prefetchWindow(
        filePath: String,
        centerPageIndex: Int,
        forwardCount: Int = 2,
        backwardCount: Int = 1,
        targetWidth: Int = 1080,
        targetHeight: Int = 1920
    ) = withContext(Dispatchers.IO) {
        try {
            // Collect page indices to prefetch (prioritize forward, then backward)
            val pagesToPrefetch = mutableListOf<Int>()
            for (i in 1..forwardCount) {
                val forwardIdx = centerPageIndex + i
                if (forwardIdx >= 0) pagesToPrefetch.add(forwardIdx)
            }
            for (i in 1..backwardCount) {
                val backwardIdx = centerPageIndex - i
                if (backwardIdx >= 0) pagesToPrefetch.add(backwardIdx)
            }

            for (pIdx in pagesToPrefetch) {
                val cacheKey = "${filePath}_${pIdx}"
                val existing = pageCache.get(cacheKey)
                if (existing == null || existing.isRecycled) {
                    renderPage(filePath, pIdx, targetWidth, targetHeight)
                }
            }
        } catch (_: Throwable) {}
    }

    suspend fun generateCoverThumbnail(filePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || file.length() <= 0) return@withContext false

        mutex.withLock {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            var page: PdfRenderer.Page? = null
            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                if (renderer.pageCount <= 0) return@withContext false

                page = renderer.openPage(0)
                val pw = page.width
                val ph = page.height

                // Target standard thumbnail dimensions ~360x520 without forcing high scale that causes OOM
                val scale = minOf(420f / pw.toFloat(), 640f / ph.toFloat(), 1.0f).coerceAtLeast(0.15f)
                val outW = (pw * scale).toInt().coerceIn(160, 480)
                val outH = (ph * scale).toInt().coerceIn(240, 720)

                val bitmap = try {
                    Bitmap.createBitmap(outW, outH, Bitmap.Config.RGB_565)
                } catch (oom: OutOfMemoryError) {
                    val halfW = (outW * 0.75f).toInt().coerceAtLeast(100)
                    val halfH = (outH * 0.75f).toInt().coerceAtLeast(150)
                    try {
                        Bitmap.createBitmap(halfW, halfH, Bitmap.Config.RGB_565)
                    } catch (_: OutOfMemoryError) {
                        null
                    }
                } ?: return@withContext false

                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val destFile = File(destPath)
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                try { bitmap.recycle() } catch (_: Throwable) {}
                destFile.exists() && destFile.length() > 0
            } catch (t: Throwable) {
                t.printStackTrace()
                false
            } finally {
                try { page?.close() } catch (_: Throwable) {}
                try { renderer?.close() } catch (_: Throwable) {}
                try { pfd?.close() } catch (_: Throwable) {}
            }
        }
    }

    private fun closeInternal() {
        try { pdfRenderer?.close() } catch (_: Throwable) {}
        try { fileDescriptor?.close() } catch (_: Throwable) {}
        pdfRenderer = null
        fileDescriptor = null
        currentFilePath = null
    }

    fun close() {
        try {
            pageCache.evictAll()
        } catch (_: Throwable) {}
        closeInternal()
    }
}
