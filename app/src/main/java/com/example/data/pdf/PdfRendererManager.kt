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

    // Cache up to 64 rendered high-res pages in memory to stabilize scrolling and reading
    private val pageCache = object : LruCache<String, Bitmap>(64) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
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
            if (currentFilePath == filePath && pdfRenderer != null) {
                return@withContext pdfRenderer!!.pageCount
            }
            closeInternal()
            val file = File(filePath)
            if (!file.exists()) {
                throw IllegalArgumentException("PDF file does not exist at $filePath")
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

                // Calculate high-resolution scale for razor sharp text rendering
                val scale = max(
                    targetWidth.toFloat() / pageWidth.toFloat(),
                    targetHeight.toFloat() / pageHeight.toFloat()
                ).coerceIn(1.5f, 2.5f)

                val outWidth = (pageWidth * scale).toInt()
                val outHeight = (pageHeight * scale).toInt()

                val bitmap = try {
                    Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    // Fallback to half resolution if device is low on memory
                    val halfScale = scale * 0.7f
                    Bitmap.createBitmap((pageWidth * halfScale).toInt(), (pageHeight * halfScale).toInt(), Bitmap.Config.ARGB_8888)
                }

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
        forwardCount: Int = 4,
        backwardCount: Int = 2,
        targetWidth: Int = 1080,
        targetHeight: Int = 1920
    ) = withContext(Dispatchers.IO) {
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
                val outW = (pw * scale).toInt().coerceIn(160, 600)
                val outH = (ph * scale).toInt().coerceIn(240, 900)

                val bitmap = try {
                    Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    val halfW = (outW * 0.75f).toInt().coerceAtLeast(100)
                    val halfH = (outH * 0.75f).toInt().coerceAtLeast(150)
                    Bitmap.createBitmap(halfW, halfH, Bitmap.Config.ARGB_8888)
                }

                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val destFile = File(destPath)
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
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
        try { pdfRenderer?.close() } catch (_: Exception) {}
        try { fileDescriptor?.close() } catch (_: Exception) {}
        pdfRenderer = null
        fileDescriptor = null
        currentFilePath = null
    }

    fun close() {
        closeInternal()
        pageCache.evictAll()
    }
}
