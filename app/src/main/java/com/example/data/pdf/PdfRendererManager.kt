package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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

    companion object {
        @Volatile
        private var instance: PdfRendererManager? = null

        fun getInstance(context: Context): PdfRendererManager {
            return instance ?: synchronized(this) {
                instance ?: PdfRendererManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val mutex = Mutex()
    private var currentFilePath: String? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null

    // Allocate generous memory for cached pages (up to 96MB) for instant navigation
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 4).coerceIn(32 * 1024, 96 * 1024)

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

                renderer.pageCount
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

                // High-DPI Retina scaling: preserve aspect ratio and render with razor-sharp vector clarity
                val pageAspect = pageWidth.toFloat() / max(1, pageHeight).toFloat()
                val widthScale = targetWidth.toFloat() / max(1, pageWidth).toFloat()
                val heightScale = targetHeight.toFloat() / max(1, pageHeight).toFloat()
                val scale = maxOf(widthScale, heightScale, 2.2f).coerceIn(2.0f, 3.2f)

                var outWidth = (pageWidth * scale).toInt().coerceAtLeast(64)
                var outHeight = (pageHeight * scale).toInt().coerceAtLeast(64)

                // Cap maximum dimension to 2880px for pin-sharp text on Quad-HD and 4K displays
                val maxDimension = 2880
                if (outWidth > maxDimension || outHeight > maxDimension) {
                    if (outWidth >= outHeight) {
                        outWidth = maxDimension
                        outHeight = (maxDimension / pageAspect).toInt().coerceAtLeast(64)
                    } else {
                        outHeight = maxDimension
                        outWidth = (maxDimension * pageAspect).toInt().coerceAtLeast(64)
                    }
                }

                val bitmap = try {
                    Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    pageCache.evictAll()
                    System.gc()
                    try {
                        val halfW = (outWidth * 0.75f).toInt().coerceAtLeast(64)
                        val halfH = (outHeight * 0.75f).toInt().coerceAtLeast(64)
                        Bitmap.createBitmap(halfW, halfH, Bitmap.Config.ARGB_8888)
                    } catch (oom2: OutOfMemoryError) {
                        null
                    }
                } ?: return@withContext null

                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                val matrix = Matrix().apply {
                    postScale(bitmap.width.toFloat() / pageWidth.toFloat(), bitmap.height.toFloat() / pageHeight.toFloat())
                }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

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
        val bmp = renderCoverThumbnailBitmap(filePath, destPath)
        val file = File(destPath)
        bmp != null && file.exists() && file.length() > 0
    }

    suspend fun renderCoverThumbnailBitmap(filePath: String, destPath: String? = null): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || file.length() <= 0) return@withContext null

        mutex.withLock {
            var page: PdfRenderer.Page? = null
            var temporaryRenderer: PdfRenderer? = null
            var temporaryPfd: ParcelFileDescriptor? = null
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                temporaryPfd = pfd
                val renderer = PdfRenderer(pfd)
                temporaryRenderer = renderer

                if (renderer.pageCount <= 0) return@withContext null

                page = renderer.openPage(0)
                val pw = page.width
                val ph = page.height

                // Target standard book card thumbnail dimensions (aspect ratio ~0.72)
                // Crisp rendering at ~420x584 to cleanly cover the book card
                val scale = minOf(420f / pw.toFloat(), 584f / ph.toFloat()).coerceIn(0.15f, 3.0f)
                val outW = (pw * scale).toInt().coerceIn(160, 600)
                val outH = (ph * scale).toInt().coerceIn(240, 840)

                // Android's PdfRenderer strictly requires Bitmap.Config.ARGB_8888
                val bitmap = try {
                    Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    val halfW = (outW * 0.75f).toInt().coerceAtLeast(100)
                    val halfH = (outH * 0.75f).toInt().coerceAtLeast(150)
                    try {
                        Bitmap.createBitmap(halfW, halfH, Bitmap.Config.ARGB_8888)
                    } catch (_: OutOfMemoryError) {
                        null
                    }
                } ?: return@withContext null

                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val matrix = Matrix().apply {
                    postScale(bitmap.width.toFloat() / pw.toFloat(), bitmap.height.toFloat() / ph.toFloat())
                }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                if (!destPath.isNullOrBlank()) {
                    try {
                        val destFile = File(destPath)
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                bitmap
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            } finally {
                try { page?.close() } catch (_: Throwable) {}
                try { temporaryRenderer?.close() } catch (_: Throwable) {}
                try { temporaryPfd?.close() } catch (_: Throwable) {}
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
