package com.example.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object SampleBooksGenerator {

    data class BookCoverTheme(
        val primaryColor: String = "#0E2442",
        val spineColor: String = "#061224",
        val blueAccentColor: String = "#60A5FA",
        val whiteAccentColor: String = "#FFFFFF",
        val titleTextColor: String = "#FFFFFF",
        val subtitleTextColor: String = "#E0F2FE"
    )

    data class SampleBookInfo(
        val title: String,
        val author: String,
        val fileName: String,
        val pages: List<SamplePageContent>,
        val coverTheme: BookCoverTheme = BookCoverTheme()
    )

    data class SamplePageContent(
        val header: String,
        val chapterTitle: String?,
        val paragraphs: List<String>,
        val isCover: Boolean = false,
        val quote: String? = null
    )

    suspend fun createSampleBooksIfNotExist(context: Context): List<Pair<SampleBookInfo, File>> = withContext(Dispatchers.IO) {
        val booksDir = File(context.filesDir, "sample_books")
        if (!booksDir.exists()) booksDir.mkdirs()

        val versionMarker = File(booksDir, ".blue_white_book_v1")
        if (!versionMarker.exists()) {
            // Remove legacy sample books to re-render them with blue and white colors and book icon styling
            booksDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".pdf") || file.name.startsWith(".serif") || file.name.startsWith(".clean") || file.name.startsWith(".real")) {
                    file.delete()
                }
            }
            try {
                versionMarker.createNewFile()
            } catch (_: Exception) {}
        }

        val sampleBooks = listOf(
            createGatsbyBook(),
            createAliceBook(),
            createMeditationsBook(),
            createFrankensteinBook(),
            createPridePrejudiceBook(),
            createDorianGrayBook(),
            createDraculaBook(),
            createTimeMachineBook(),
            createMetamorphosisBook(),
            createSherlockHolmesBook(),
            createMobyDickBook(),
            createTaleOfTwoCitiesBook(),
            createArtOfWarBook()
        )

        sampleBooks.map { book ->
            val pdfFile = File(booksDir, book.fileName)
            if (!pdfFile.exists() || pdfFile.length() == 0L) {
                generatePdf(context, pdfFile, book)
            }
            Pair(book, pdfFile)
        }
    }

    fun purgeLegacySampleBooks(context: Context) {
        val booksDir = File(context.filesDir, "sample_books")
        if (booksDir.exists()) {
            booksDir.listFiles()?.forEach { it.delete() }
        }
        val coversDir = File(context.filesDir, "covers")
        if (coversDir.exists()) {
            coversDir.listFiles()?.forEach {
                it.delete()
            }
        }
    }

    private fun getBookTypeface(context: Context, isBold: Boolean = false, isItalic: Boolean = false): Typeface {
        val assetPath = if (isBold) "fonts/book_serif_bold.ttf" else "fonts/book_serif.ttf"
        val base = try {
            Typeface.createFromAsset(context.assets, assetPath)
        } catch (e: Exception) {
            Typeface.create(Typeface.SERIF, if (isBold) Typeface.BOLD else Typeface.NORMAL)
        }
        return if (isItalic) {
            Typeface.create(base, if (isBold) Typeface.BOLD_ITALIC else Typeface.ITALIC)
        } else {
            base
        }
    }

    private fun generatePdf(context: Context, destFile: File, book: SampleBookInfo) {
        val document = PdfDocument()
        val pageWidth = 595 // Standard A4 width in points
        val pageHeight = 842 // Standard A4 height in points

        val margin = 42f
        val contentWidth = (pageWidth - margin * 2).toInt()

        val serifRegular = getBookTypeface(context, isBold = false, isItalic = false)
        val serifBold = getBookTypeface(context, isBold = true, isItalic = false)
        val serifItalic = getBookTypeface(context, isBold = false, isItalic = true)

        book.pages.forEachIndexed { index, pageContent ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            if (pageContent.isCover) {
                renderCoverPage(canvas, pageWidth, pageHeight, book, serifBold, serifItalic)
            } else {
                // Natural clean crisp reading paper background
                val bgPaint = Paint().apply {
                    color = Color.parseColor("#FAF7F0")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)
                renderTextPage(canvas, pageWidth, pageHeight, pageContent, index + 1, book.pages.size, margin, contentWidth, serifRegular, serifBold, serifItalic)
            }

            document.finishPage(page)
        }

        try {
            FileOutputStream(destFile).use { out ->
                document.writeTo(out)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            try { destFile.delete() } catch (_: Throwable) {}
        } finally {
            try { document.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Draws the exact open book icon (matching Material AutoStories icon on the book cards thumbnail)
     * in blue and white with graceful curvature, page lines, and spine binding.
     */
    private fun drawBookCoverIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        whiteColor: Int,
        blueColor: Int
    ) {
        val halfW = size * 0.52f
        val halfH = size * 0.40f
        val gap = size * 0.05f

        val strokeWhite = Paint().apply {
            color = whiteColor
            strokeWidth = size * 0.065f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        val fillBlue = Paint().apply {
            color = (blueColor and 0x00FFFFFF) or 0x40000000 // 25% translucent blue fill
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val strokeBlue = Paint().apply {
            color = blueColor
            strokeWidth = size * 0.045f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        // Left open page outline
        val leftPath = android.graphics.Path().apply {
            moveTo(cx - gap, cy + halfH * 0.85f)
            cubicTo(
                cx - halfW * 0.45f, cy + halfH * 0.65f,
                cx - halfW * 0.80f, cy + halfH * 0.95f,
                cx - halfW, cy + halfH * 0.75f
            )
            lineTo(cx - halfW, cy - halfH * 0.75f)
            cubicTo(
                cx - halfW * 0.80f, cy - halfH * 0.95f,
                cx - halfW * 0.45f, cy - halfH * 0.65f,
                cx - gap, cy - halfH * 0.85f
            )
            close()
        }

        // Right open page outline
        val rightPath = android.graphics.Path().apply {
            moveTo(cx + gap, cy + halfH * 0.85f)
            cubicTo(
                cx + halfW * 0.45f, cy + halfH * 0.65f,
                cx + halfW * 0.80f, cy + halfH * 0.95f,
                cx + halfW, cy + halfH * 0.75f
            )
            lineTo(cx + halfW, cy - halfH * 0.75f)
            cubicTo(
                cx + halfW * 0.80f, cy - halfH * 0.95f,
                cx + halfW * 0.45f, cy - halfH * 0.65f,
                cx + gap, cy - halfH * 0.85f
            )
            close()
        }

        // Fill pages with soft blue tint
        canvas.drawPath(leftPath, fillBlue)
        canvas.drawPath(rightPath, fillBlue)

        // Draw page outlines in pure white
        canvas.drawPath(leftPath, strokeWhite)
        canvas.drawPath(rightPath, strokeWhite)

        // Inner page lines in bright blue
        val leftInnerPath = android.graphics.Path().apply {
            moveTo(cx - gap - size * 0.10f, cy - halfH * 0.45f)
            cubicTo(
                cx - halfW * 0.42f, cy - halfH * 0.32f,
                cx - halfW * 0.72f, cy - halfH * 0.55f,
                cx - halfW * 0.85f, cy - halfH * 0.42f
            )
        }
        val rightInnerPath = android.graphics.Path().apply {
            moveTo(cx + gap + size * 0.10f, cy - halfH * 0.45f)
            cubicTo(
                cx + halfW * 0.42f, cy - halfH * 0.32f,
                cx + halfW * 0.72f, cy - halfH * 0.55f,
                cx + halfW * 0.85f, cy - halfH * 0.42f
            )
        }
        canvas.drawPath(leftInnerPath, strokeBlue)
        canvas.drawPath(rightInnerPath, strokeBlue)

        // Central spine binding in white
        val spinePaint = Paint().apply {
            color = whiteColor
            strokeWidth = size * 0.07f
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawLine(cx, cy - halfH * 0.90f, cx, cy + halfH * 0.92f, spinePaint)
    }

    private fun renderCoverPage(
        canvas: Canvas,
        width: Int,
        height: Int,
        book: SampleBookInfo,
        serifBold: Typeface,
        serifItalic: Typeface
    ) {
        val theme = book.coverTheme
        val primaryCol = Color.parseColor(theme.primaryColor)
        val spineCol = Color.parseColor(theme.spineColor)
        val blueCol = Color.parseColor(theme.blueAccentColor)
        val whiteCol = Color.parseColor(theme.whiteAccentColor)
        val titleCol = Color.parseColor(theme.titleTextColor)
        val subCol = Color.parseColor(theme.subtitleTextColor)

        // 1. Rich Deep Hardcover Blue Gradient Background
        val bgShader = android.graphics.LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(spineCol, primaryCol, primaryCol, spineCol),
            floatArrayOf(0f, 0.25f, 0.85f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        val bgPaint = Paint().apply {
            shader = bgShader
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Realistic Spine Crease Shadow along the left edge
        val spineCreaseShader = android.graphics.LinearGradient(
            0f, 0f, 38f, 0f,
            intArrayOf(Color.parseColor("#77000000"), Color.parseColor("#22000000"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.6f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        val spineCreasePaint = Paint().apply {
            shader = spineCreaseShader
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, 38f, height.toFloat(), spineCreasePaint)

        // Spine Hinge Highlight in white
        val spineHingePaint = Paint().apply {
            color = Color.parseColor("#30FFFFFF")
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(38f, 0f, 38f, height.toFloat(), spineHingePaint)

        // 3. Double-Line Blue & White Border Frame
        val outerBorderInset = 28f
        val innerBorderInset = 36f

        val outerBorderPaint = Paint().apply {
            color = whiteCol
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawRect(outerBorderInset, outerBorderInset, width - outerBorderInset, height - outerBorderInset, outerBorderPaint)

        val innerBorderPaint = Paint().apply {
            color = blueCol
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawRect(innerBorderInset, innerBorderInset, width - innerBorderInset, height - innerBorderInset, innerBorderPaint)

        // Ornate Corner Diamonds in White & Blue
        val cornerDiamondPaint = Paint().apply {
            color = whiteCol
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cornerBlueDotPaint = Paint().apply {
            color = blueCol
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        fun drawCornerDiamond(cx: Float, cy: Float, size: Float) {
            val path = android.graphics.Path().apply {
                moveTo(cx, cy - size)
                lineTo(cx + size, cy)
                lineTo(cx, cy + size)
                lineTo(cx - size, cy)
                close()
            }
            canvas.drawPath(path, cornerDiamondPaint)
            canvas.drawCircle(cx, cy, size * 0.35f, cornerBlueDotPaint)
        }
        drawCornerDiamond(outerBorderInset, outerBorderInset, 5.5f)
        drawCornerDiamond(width - outerBorderInset, outerBorderInset, 5.5f)
        drawCornerDiamond(outerBorderInset, height - outerBorderInset, 5.5f)
        drawCornerDiamond(width - outerBorderInset, height - outerBorderInset, 5.5f)

        val contentLeft = 54f
        val contentRight = width - 54f
        val maxTitleWidth = (contentRight - contentLeft).toInt()

        // 4. Header Badge: "✦ LUMINA LITERARY CLASSICS ✦" in White & Blue
        val headerPaint = TextPaint().apply {
            color = whiteCol
            textSize = 10.5f
            typeface = serifBold
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.16f
        }
        canvas.drawText("✦  LUMINA LITERARY CLASSICS  ✦", width / 2f, 78f, headerPaint)

        // Top Blue Divider
        val topDividerPaint = Paint().apply {
            color = blueCol
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawLine(width / 2f - 95f, 92f, width / 2f + 95f, 92f, topDividerPaint)

        // 5. Open Book Icon Emblem (matching the icon on the book cards thumbnail)
        val iconCenterY = 142f
        val iconSize = 48f
        // Symmetrical accent wings beside the book icon
        val iconWingPaint = Paint().apply {
            color = blueCol
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawLine(width / 2f - 90f, iconCenterY, width / 2f - 38f, iconCenterY, iconWingPaint)
        canvas.drawLine(width / 2f + 38f, iconCenterY, width / 2f + 90f, iconCenterY, iconWingPaint)
        drawCornerDiamond(width / 2f - 38f, iconCenterY, 3.5f)
        drawCornerDiamond(width / 2f + 38f, iconCenterY, 3.5f)

        drawBookCoverIcon(canvas, width / 2f, iconCenterY, iconSize, whiteCol, blueCol)

        // 6. Ornate Embossed Pure White Title
        val titlePaint = TextPaint().apply {
            color = titleCol
            textSize = if (book.title.length > 25) 26f else 30f
            typeface = serifBold
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 1.5f, 2.5f, Color.parseColor("#AA000000"))
        }

        val titleLayout = StaticLayout.Builder.obtain(
            book.title,
            0,
            book.title.length,
            titlePaint,
            maxTitleWidth
        ).setAlignment(Layout.Alignment.ALIGN_CENTER)
         .setLineSpacing(5f, 1.15f)
         .build()

        val titleY = height * 0.33f
        canvas.save()
        canvas.translate(width / 2f, titleY)
        titleLayout.draw(canvas)
        canvas.restore()

        val afterTitleY = titleY + titleLayout.height + 22f

        // Center Blue & White Emblem / Flourish below Title
        val dividerPaint = Paint().apply {
            color = blueCol
            strokeWidth = 1.8f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawLine(width / 2f - 80f, afterTitleY, width / 2f - 16f, afterTitleY, dividerPaint)
        drawCornerDiamond(width / 2f, afterTitleY, 6f)
        canvas.drawLine(width / 2f + 16f, afterTitleY, width / 2f + 80f, afterTitleY, dividerPaint)

        // 7. Author in Elegant Light Ice Blue Serif Italic
        val authorPaint = TextPaint().apply {
            color = subCol
            textSize = 17.5f
            typeface = serifItalic
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(2f, 1f, 1.5f, Color.parseColor("#88000000"))
        }
        canvas.drawText("by ${book.author}", width / 2f, afterTitleY + 40f, authorPaint)

        // 8. Bottom Collector Seal & Imprint in White & Blue
        val sealPaint = TextPaint().apply {
            color = whiteCol
            textSize = 9.5f
            typeface = serifBold
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.14f
        }
        canvas.drawText("ARCHIVAL HARDCOVER EDITION", width / 2f, height - 76f, sealPaint)

        val bottomDividerPaint = Paint().apply {
            color = blueCol
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawLine(width / 2f - 65f, height - 64f, width / 2f + 65f, height - 64f, bottomDividerPaint)

        val imprintPaint = TextPaint().apply {
            color = subCol
            textSize = 8.5f
            typeface = serifBold
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.10f
        }
        canvas.drawText("COMPLETE & UNABRIDGED", width / 2f, height - 50f, imprintPaint)
    }

    private fun renderTextPage(
        canvas: Canvas,
        width: Int,
        height: Int,
        pageContent: SamplePageContent,
        pageNum: Int,
        totalPages: Int,
        margin: Float,
        contentWidth: Int,
        serifRegular: Typeface,
        serifBold: Typeface,
        serifItalic: Typeface
    ) {
        var currentY = 50f

        // Top Running Header: left-aligned section title, right-aligned page counter
        val headerPaint = TextPaint().apply {
            color = Color.parseColor("#475569")
            textSize = 10f
            typeface = serifRegular
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(pageContent.header.uppercase(), margin, currentY, headerPaint)

        val headerPagePaint = TextPaint().apply {
            color = Color.parseColor("#475569")
            textSize = 10f
            typeface = serifRegular
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Page $pageNum of $totalPages", width - margin, currentY, headerPagePaint)

        // Hairline rule under header
        currentY += 12f
        val rulePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }
        canvas.drawLine(margin, currentY, width - margin, currentY, rulePaint)
        currentY += 24f

        // Chapter Title if present
        if (!pageContent.chapterTitle.isNullOrBlank()) {
            val chapterPaint = TextPaint().apply {
                color = Color.parseColor("#0F172A")
                textSize = 18f
                typeface = serifBold
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText(pageContent.chapterTitle, margin, currentY, chapterPaint)
            currentY += 26f
        }

        // Quote Box if present - Clean blue and white theme
        if (!pageContent.quote.isNullOrBlank()) {
            val quotePaint = TextPaint().apply {
                color = Color.parseColor("#1E293B")
                textSize = 13.5f
                typeface = serifItalic
                isAntiAlias = true
            }
            val quoteLayout = StaticLayout.Builder.obtain(
                "“${pageContent.quote}”",
                0,
                pageContent.quote.length + 2,
                quotePaint,
                contentWidth - 28
            ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

            val quoteBg = Paint().apply {
                color = Color.parseColor("#F0F6FF")
                style = Paint.Style.FILL
            }
            canvas.drawRect(margin, currentY - 6f, width - margin, currentY + quoteLayout.height + 14f, quoteBg)

            val quoteBar = Paint().apply {
                color = Color.parseColor("#2563EB")
                style = Paint.Style.FILL
            }
            canvas.drawRect(margin, currentY - 6f, margin + 4.5f, currentY + quoteLayout.height + 14f, quoteBar)

            canvas.save()
            canvas.translate(margin + 16f, currentY + 4f)
            quoteLayout.draw(canvas)
            canvas.restore()

            currentY += quoteLayout.height + 28f
        }

        // Paragraphs
        val bodyPaint = TextPaint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 14f
            typeface = serifRegular
            isAntiAlias = true
        }

        pageContent.paragraphs.forEach { paragraph ->
            val layout = StaticLayout.Builder.obtain(
                paragraph,
                0,
                paragraph.length,
                bodyPaint,
                contentWidth
            ).setLineSpacing(5f, 1.22f)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            canvas.save()
            canvas.translate(margin, currentY)
            layout.draw(canvas)
            canvas.restore()

            currentY += layout.height + 18f
        }

        // Bottom Footer
        val footerRulePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.8f
        }
        val footerY = height - 34f
        canvas.drawLine(margin - 4f, footerY - 14f, width - margin, footerY - 14f, footerRulePaint)

        val footerPaint = TextPaint().apply {
            color = Color.parseColor("#475569")
            textSize = 9.5f
            typeface = serifRegular
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }

        val rawTitle = pageContent.header
        val maxTitleWidth = (width - margin * 2) * 0.62f
        val displayTitle = if (footerPaint.measureText(rawTitle) > maxTitleWidth) {
            val count = footerPaint.breakText(rawTitle, true, maxTitleWidth - 14f, null)
            rawTitle.substring(0, count).trimEnd() + "…"
        } else {
            rawTitle
        }

        canvas.drawText(displayTitle, margin - 4f, footerY, footerPaint)

        val footerPagePaint = TextPaint().apply {
            color = Color.parseColor("#475569")
            textSize = 9.5f
            typeface = serifRegular
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Page $pageNum of $totalPages", width - margin, footerY, footerPagePaint)
    }

    fun createGatsbyBook(): SampleBookInfo {
        return SampleBookInfo(
            title = "The Great Gatsby",
            author = "F. Scott Fitzgerald",
            fileName = "the_great_gatsby.pdf",
            coverTheme = BookCoverTheme(
                primaryColor = "#0D253F", // Midnight Jazz Navy Cloth
                spineColor = "#071424",
                blueAccentColor = "#60A5FA",
                whiteAccentColor = "#FFFFFF",
                titleTextColor = "#FFFFFF",
                subtitleTextColor = "#E0F2FE"
            ),
            pages = listOf(
                SamplePageContent("The Great Gatsby", null, emptyList(), isCover = true),
                SamplePageContent(
                    header = "The Great Gatsby",
                    chapterTitle = "Chapter I",
                    paragraphs = listOf(
                        "In my younger and more vulnerable years my father gave me some advice that I’ve been turning over in my mind ever since.",
                        "“Whenever you feel like criticizing any one,” he told me, “just remember that all the people in this world haven’t had the advantages that you’ve had.”",
                        "He didn’t say any more, but we’ve always been unusually communicative in a reserved way, and I understood that he meant a great deal more than that. In consequence, I’m inclined to reserve all judgments, a habit that has opened up many curious natures to me and also made me the victim of not a few veteran bores.",
                        "The abnormal mind is quick to detect and attach itself to this quality when it appears in a normal person, and so it came about that in college I was unjustly accused of being a politician, because I was privy to the secret griefs of wild, unknown men."
                    ),
                    quote = "Reserving judgments is a matter of infinite hope."
                ),
                SamplePageContent(
                    header = "The Great Gatsby",
                    chapterTitle = "The Green Light",
                    paragraphs = listOf(
                        "And as I sat there brooding on the old, unknown world, I thought of Gatsby’s wonder when he first picked out the green light at the end of Daisy’s dock.",
                        "He had come a long way to this blue lawn, and his dream must have seemed so close that he could hardly fail to grasp it. He did not know that it was already behind him, somewhere back in that vast obscurity beyond the city, where the dark fields of the republic rolled on under the night.",
                        "Gatsby believed in the green light, the orgastic future that year by year recedes before us. It eluded us then, but that’s no matter—tomorrow we will run faster, stretch out our arms farther. . . . And one fine morning——",
                        "So we beat on, boats against the current, borne back ceaselessly into the past."
                    ),
                    quote = "So we beat on, boats against the current, borne back ceaselessly into the past."
                ),
                SamplePageContent(
                    header = "The Great Gatsby",
                    chapterTitle = "Summer at West Egg",
                    paragraphs = listOf(
                        "There was music from my neighbor’s house through the summer nights. In his blue gardens men and girls came and went like moths among the whisperings and the champagne and the stars.",
                        "At high tide in the afternoon I watched his guests diving from the tower of his raft, or taking the sun on the hot sand of his beach while his two motor-boats slit the waters of the Sound, drawing aquaplanes over cataracts of foam.",
                        "On week-ends his Rolls-Royce became an omnibus, bearing parties to and from the city between nine in the morning and long past midnight, while his station wagon scampered like a brisk yellow bug to meet all trains.",
                        "And on Mondays eight servants, including an extra gardener, toiled all day with mops and scrubbing-brushes and hammers and shears, repairing the ravages of the night before."
                    )
                ),
                SamplePageContent(
                    header = "The Great Gatsby",
                    chapterTitle = "Daisy's Voice",
                    paragraphs = listOf(
                        "I looked at her again, and she had moved her eyes away from the green light and was looking down toward the harbor. She was lovely, in her white dress with her dark hair falling softly around her pale face.",
                        "“Her voice is full of money,” he said suddenly.",
                        "That was it. I’d never understood before. It was full of money—that was the inexhaustible charm that rose and fell in it, the jingle of it, the cymbals’ song of it. . . . High in a white palace the king’s daughter, the golden girl.",
                        "They were careless people, Tom and Daisy—they smashed up things and creatures and then retreated back into their money or their vast carelessness, or whatever it was that kept them together, and let other people clean up the mess they had made."
                    )
                ),
                SamplePageContent(
                    header = "The Great Gatsby",
                    chapterTitle = "Epilogue & Notes",
                    paragraphs = listOf(
                        "Published in 1925, The Great Gatsby stands as one of the quintessential masterpieces of American literature, exploring the disillusionment of the American Dream amidst the Jazz Age of the 1920s.",
                        "With its lyrical prose, meticulous structural symmetry, and profound emotional depth, Fitzgerald captured the radiant longing and tragic transience of human ambition.",
                        "Thank you for reading this Lumina Edition. Continue to flip pages seamlessly or import your own personal PDF library using the Upload button."
                    ),
                    quote = "There are only the pursued, the pursuing, the busy and the tired."
                )
            )
        )
    }

    fun createAliceBook(): SampleBookInfo {
        return SampleBookInfo(
            title = "Alice's Adventures in Wonderland",
            author = "Lewis Carroll",
            fileName = "alice_in_wonderland.pdf",
            coverTheme = BookCoverTheme(
                primaryColor = "#132D52", // Wonderland Royal Cobalt Cloth
                spineColor = "#09172B",
                blueAccentColor = "#38BDF8",
                whiteAccentColor = "#FFFFFF",
                titleTextColor = "#FFFFFF",
                subtitleTextColor = "#E0F2FE"
            ),
            pages = listOf(
                SamplePageContent("Alice's Adventures in Wonderland", null, emptyList(), isCover = true),
                SamplePageContent(
                    header = "Alice in Wonderland",
                    chapterTitle = "Chapter I: Down the Rabbit-Hole",
                    paragraphs = listOf(
                        "Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do: once or twice she had peeped into the book her sister was reading, but it had no pictures or conversations in it, “and what is the use of a book,” thought Alice “without pictures or conversations?”",
                        "So she was considering in her own mind (as well as she could, for the hot day made her feel very sleepy and stupid), whether the pleasure of making a daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly a White Rabbit with pink eyes ran close by her.",
                        "There was nothing so very remarkable in that; nor did Alice think it so very much out of the way to hear the Rabbit say to itself, “Oh dear! Oh dear! I shall be late!”"
                    ),
                    quote = "What is the use of a book without pictures or conversations?"
                ),
                SamplePageContent(
                    header = "Alice in Wonderland",
                    chapterTitle = "The Rabbit Hole",
                    paragraphs = listOf(
                        "In another moment down went Alice after it, never once considering how in the world she was to get out again.",
                        "The rabbit-hole went straight on like a tunnel for some way, and then dipped suddenly down, so suddenly that Alice had not a moment to think about stopping herself before she found herself falling down a very deep well.",
                        "Either the well was very deep, or she fell very slowly, for she had plenty of time as she went down to look about her and to wonder what was going to happen next. First, she tried to look down and make out what she was coming to, but it was too dark to see anything.",
                        "Down, down, down. Would the fall never come to an end! “I wonder how many miles I’ve fallen by this time?” she said aloud."
                    )
                ),
                SamplePageContent(
                    header = "Alice in Wonderland",
                    chapterTitle = "The Mad Tea-Party",
                    paragraphs = listOf(
                        "There was a table set out under a tree in front of the house, and the March Hare and the Hatter were having tea at it: a Dormouse was sitting between them, fast asleep, and the other two were using it as a cushion, resting their elbows on it, and talking over its head.",
                        "“Very uncomfortable for the Dormouse,” thought Alice; “only, as it’s asleep, I suppose it doesn’t mind.”",
                        "The table was a large one, but the three were all crowded together at one corner of it: “No room! No room!” they cried out when they saw Alice coming.",
                        "“There’s plenty of room!” said Alice indignantly, and she sat down in a large arm-chair at one end of the table."
                    ),
                    quote = "“Take some more tea,” the March Hare said to Alice, very earnestly."
                ),
                SamplePageContent(
                    header = "Alice in Wonderland",
                    chapterTitle = "Curiouser and Curiouser!",
                    paragraphs = listOf(
                        "“Curiouser and curiouser!” cried Alice (she was so much surprised, that for the moment she quite forgot how to speak good English); “now I’m opening out like the largest telescope that ever was! Good-bye, feet!”",
                        "“It’s no use going back to yesterday, because I was a different person then,” Alice remarked with a smile.",
                        "“Why, sometimes I’ve believed as many as six impossible things before breakfast,” said the Queen.",
                        "This concludes the Lumina Edition excerpt of Alice's Adventures in Wonderland."
                    )
                )
            )
        )
    }

    fun createMeditationsBook(): SampleBookInfo {
        return SampleBookInfo(
            title = "Meditations",
            author = "Marcus Aurelius",
            fileName = "meditations_marcus_aurelius.pdf",
            coverTheme = BookCoverTheme(
                primaryColor = "#0F2847", // Stoic Imperial Sapphire
                spineColor = "#081527",
                blueAccentColor = "#60A5FA",
                whiteAccentColor = "#FFFFFF",
                titleTextColor = "#FFFFFF",
                subtitleTextColor = "#E0F2FE"
            ),
            pages = listOf(
                SamplePageContent("Meditations", null, emptyList(), isCover = true),
                SamplePageContent(
                    header = "Meditations",
                    chapterTitle = "Book II: On the River Gran",
                    paragraphs = listOf(
                        "When you wake up in the morning, tell yourself: The people I deal with today will be meddling, ungrateful, arrogant, dishonest, jealous, and surly. They are like this because they cannot distinguish good from evil.",
                        "But I have seen the beauty of good, and the ugliness of evil, and have recognized that the wrongdoer has a nature related to my own—not of the same blood or birth, but the same mind, and possessing a share of the divine.",
                        "None of them can hurt me. No one can implicate me in ugliness. Nor can I feel angry at my kin, or hate him. We were born to work together like feet, hands, and eyes, like the two rows of teeth, upper and lower. To obstruct each other is unnatural."
                    ),
                    quote = "You have power over your mind - not outside events. Realize this, and you will find strength."
                ),
                SamplePageContent(
                    header = "Meditations",
                    chapterTitle = "Book IV: The Citadel of the Soul",
                    paragraphs = listOf(
                        "People look for retreats for themselves, in the country, by the coast, or in the hills. There is nowhere that a person can find a more peaceful and trouble-free retreat than in his own mind.",
                        "So constantly give yourself this retreat, and renew yourself. Let your basic principles be brief and fundamental, the kind that will at once wash away all sorrow and send you back without irritation to the life to which you must return.",
                        "Remember that the soul becomes dyed with the color of its thoughts. Steep it therefore in such a train of thoughts as this: Anywhere a man can live, he can live well.",
                        "Dwell on the beauty of life. Watch the stars, and see yourself running with them."
                    ),
                    quote = "The soul becomes dyed with the color of its thoughts."
                ),
                SamplePageContent(
                    header = "Meditations",
                    chapterTitle = "Book VII: Transient Nature",
                    paragraphs = listOf(
                        "Time is a river, a violent current of events, glimpsed once and already carried past us, and another follows and is gone.",
                        "Do not act as if you were going to live ten thousand years. Death hangs over you. While you live, while it is in your power, be good.",
                        "Accept the things to which fate binds you, and love the people with whom fate brings you together, but do so with all your heart.",
                        "Never let the future disturb you. You will meet it, if you have to, with the same weapons of reason which today arm you against the present."
                    ),
                    quote = "Waste no more time arguing about what a good man should be. Be one."
                ),
                SamplePageContent(
                    header = "Meditations",
                    chapterTitle = "Reflections & Closing",
                    paragraphs = listOf(
                        "Marcus Aurelius Antoninus Augustus reigned as Roman Emperor from 161 to 180 AD and was the last of the rulers known as the Five Good Emperors.",
                        "His personal writings, titled Meditations, were never meant for publication. They represent private exercises in Stoic philosophy, resilience, empathy, and duty.",
                        "Enjoy your focused reading journey with Lumina Reader."
                    )
                )
            )
        )
    }

    fun createFrankensteinBook(): SampleBookInfo {
        return SampleBookInfo(
            title = "Frankenstein",
            author = "Mary Shelley",
            fileName = "frankenstein_mary_shelley.pdf",
            coverTheme = BookCoverTheme(
                primaryColor = "#112239", // Gothic Arctic Midnight Cloth
                spineColor = "#09121F",
                blueAccentColor = "#38BDF8",
                whiteAccentColor = "#FFFFFF",
                titleTextColor = "#FFFFFF",
                subtitleTextColor = "#E0F2FE"
            ),
            pages = listOf(
                SamplePageContent("Frankenstein", null, emptyList(), isCover = true),
                SamplePageContent(
                    header = "Frankenstein",
                    chapterTitle = "Letter I: Walton to Mrs. Saville",
                    paragraphs = listOf(
                        "You will rejoice to hear that no disaster has accompanied the commencement of an enterprise which you have regarded with such evil forebodings.",
                        "I arrived here yesterday, and my first task is to assure my dear sister of my welfare and increasing confidence in the success of my undertaking.",
                        "I am already far north of London, and as I walk in the streets of Petersburgh, I feel a cold northern breeze play upon my cheeks, which braces my nerves and fills me with delight.",
                        "Do you understand this feeling? This breeze, which has travelled from the regions towards which I am advancing, gives me a foretaste of those icy climes."
                    ),
                    quote = "What can stop the determined heart and resolved will of man?"
                ),
                SamplePageContent(
                    header = "Frankenstein",
                    chapterTitle = "Chapter IV: The Spark of Being",
                    paragraphs = listOf(
                        "It was on a dreary night of November that I beheld the accomplishment of my toils.",
                        "With an anxiety that almost amounted to agony, I collected the instruments of life around me, that I might infuse a spark of being into the lifeless thing that lay at my feet.",
                        "It was already one in the morning; the rain pattered dismally against the panes, and my candle was nearly burnt out, when, by the glimmer of the half-extinguished light, I saw the dull yellow eye of the creature open; it breathed hard, and a convulsive motion agitated its limbs.",
                        "How can I describe my emotions at this catastrophe, or how delineate the wretch whom with such infinite pains and care I had endeavoured to form?"
                    ),
                    quote = "Beware; for I am fearless, and therefore powerful."
                ),
                SamplePageContent(
                    header = "Frankenstein",
                    chapterTitle = "Chapter X: Encounter on the Glacier",
                    paragraphs = listOf(
                        "I spent the following day traversing the valley. I stood beside the sources of the Arveiron, which take their rise in a glacier, that with slow pace is advancing down from the summit of the hills.",
                        "Suddenly I beheld the figure of a man, at some distance, advancing towards me with superhuman speed. He bounded over the crevices in the ice, among which I had walked with caution.",
                        "“Devil,” I exclaimed, “do you dare approach me? And do not you fear the fierce vengeance of my arm wreaked on your miserable head? Begone, vile insect! Or rather, stay, that I may trample you to dust!”",
                        "“I expected this reception,” said the daemon. “All men hate the wretched; how, then, must I be hated, who am miserable beyond all living things!”"
                    ),
                    quote = "Life, although it may only be an accumulation of anguish, is dear to me, and I will defend it."
                ),
                SamplePageContent(
                    header = "Frankenstein",
                    chapterTitle = "Epilogue & Literary Legacy",
                    paragraphs = listOf(
                        "Published in 1818, Mary Shelley's Frankenstein; or, The Modern Prometheus stands as one of the founding masterpieces of science fiction and gothic horror.",
                        "Written when Shelley was just eighteen years old, the novel explores profound themes of scientific responsibility, hubris, alienation, and empathy.",
                        "Thank you for reading this Lumina Literary Classics edition."
                    )
                )
            )
        )
    }

    fun createPridePrejudiceBook(): SampleBookInfo {
        return SampleBookInfo(
            title = "Pride and Prejudice",
            author = "Jane Austen",
            fileName = "pride_and_prejudice_austen.pdf",
            coverTheme = BookCoverTheme(
                primaryColor = "#142B55", // Regency Oxford Blue Cloth
                spineColor = "#0A172F",
                blueAccentColor = "#60A5FA",
                whiteAccentColor = "#FFFFFF",
                titleTextColor = "#FFFFFF",
                subtitleTextColor = "#E0F2FE"
            ),
            pages = listOf(
                SamplePageContent("Pride and Prejudice", null, emptyList(), isCover = true),
                SamplePageContent(
                    header = "Pride and Prejudice",
                    chapterTitle = "Chapter I: A Truth Universally Acknowledged",
                    paragraphs = listOf(
                        "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.",
                        "However little known the feelings or views of such a man may be on his first entering a neighbourhood, this truth is so well fixed in the minds of the surrounding families, that he is considered the rightful property of some one or other of their daughters.",
                        "“My dear Mr. Bennet,” said his lady to him one day, “have you heard that Netherfield Park is let at last?”",
                        "Mr. Bennet replied that he had not. “But it is,” returned she; “for Mrs. Long has just been here, and she told me all about it.”"
                    ),
                    quote = "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife."
                ),
                SamplePageContent(
                    header = "Pride and Prejudice",
                    chapterTitle = "Chapter III: The Assembly at Meryton",
                    paragraphs = listOf(
                        "Mr. Darcy danced only once with Mrs. Hurst and once with Miss Bingley, declined being introduced to any other lady, and spent the rest of the evening in walking about the room, speaking occasionally to one of his own party.",
                        "His character was decided. He was the proudest, most disagreeable man in the world, and everybody hoped that he would never come there again.",
                        "Elizabeth Bennet had been obliged, by the scarcity of gentlemen, to sit down for two dances; and during part of that time, Mr. Darcy had been standing near enough for her to overhear a conversation between him and Mr. Bingley.",
                        "“Which do you mean?” and turning round he looked for a moment at Elizabeth, till catching her eye, he withdrew his own and coldly said: “She is tolerable, but not handsome enough to tempt me.”"
                    ),
                    quote = "I could easily forgive his pride, if he had not mortified mine."
                ),
                SamplePageContent(
                    header = "Pride and Prejudice",
                    chapterTitle = "Chapter LVIII: The Declaration",
                    paragraphs = listOf(
                        "Elizabeth was too much embarrassed to say a word. After a short pause, her companion added: “You are too generous to trifle with me. If your feelings are still what they were last April, tell me so at once. My affections and wishes are unchanged, but one word from you will silence me on this subject for ever.”",
                        "Elizabeth, feeling all the more than common awkwardness and anxiety of his situation, now forced herself to speak; and immediately, though not very fluently, gave him to understand that her sentiments had undergone so material a change since the period to which he alluded.",
                        "The happiness which this reply produced was such as he had probably never felt before, and he expressed himself on the occasion as sensibly and warmly as a man violently in love can be supposed to do."
                    ),
                    quote = "You must allow me to tell you how ardently I admire and love you."
                ),
                SamplePageContent(
                    header = "Pride and Prejudice",
                    chapterTitle = "Epilogue & Historical Significance",
                    paragraphs = listOf(
                        "First published in 1813, Jane Austen's Pride and Prejudice is celebrated for its incisive wit, brilliant characterization, and timeless critique of 19th-century societal norms and romantic idealism.",
                        "Elizabeth Bennet and Fitzwilliam Darcy remain one of literature’s most enduring couples, exemplifying the triumph of genuine understanding over social pretense.",
                        "Thank you for reading this Lumina Literary Classics edition."
                    )
                )
            )
        )
    }

    fun createDorianGrayBook(): SampleBookInfo {
        return SampleBookInfo(
            title = "The Picture of Dorian Gray",
            author = "Oscar Wilde",
            fileName = "picture_of_dorian_gray_wilde.pdf",
            coverTheme = BookCoverTheme(
                primaryColor = "#172346", // Decadent Deep Indigo Velvet
                spineColor = "#0B1224",
                blueAccentColor = "#38BDF8",
                whiteAccentColor = "#FFFFFF",
                titleTextColor = "#FFFFFF",
                subtitleTextColor = "#E0F2FE"
            ),
            pages = listOf(
                SamplePageContent("The Picture of Dorian Gray", null, emptyList(), isCover = true),
                SamplePageContent(
                    header = "The Picture of Dorian Gray",
                    chapterTitle = "The Studio of Basil Hallward",
                    paragraphs = listOf(
                        "The studio was filled with the rich odour of roses, and when the light summer wind stirred amidst the trees of the garden, there came through the open door the heavy scent of the lilac, or the more delicate perfume of the pink-flowering thorn.",
                        "From the corner of the divan of Persian saddle-bags on which he was lying, smoking innumerable cigarettes, Lord Henry Wotton could just catch the gleam of the honey-sweet and honey-coloured blossoms of a laburnum.",
                        "In the centre of the room, clamped to an upright easel, stood the full-length portrait of a young man of extraordinary personal beauty, and in front of it, some little distance away, was sitting the artist himself, Basil Hallward."
                    ),
                    quote = "The only way to get rid of a temptation is to yield to it."
                ),
                SamplePageContent(
                    header = "The Picture of Dorian Gray",
                    chapterTitle = "The Wish of Youth",
                    paragraphs = listOf(
                        "“How sad it is!” murmured Dorian Gray with his eyes still fixed upon his own portrait. “How sad it is! I shall grow old, and horrible, and dreadful. But this picture will remain always young.”",
                        "“It will never be older than this particular day of June. . . . If it were only the other way! If it were I who was to be always young, and the picture that was to grow old!”",
                        "“For that—for that—I would give everything! Yes, there is nothing in the whole world I would not give! I would give my soul for that!”",
                        "Lord Henry laughed. “I don't think you would like that arrangement, Basil.”"
                    ),
                    quote = "Youth is the only thing worth having. When I find that I am growing old, I shall kill myself."
                ),
                SamplePageContent(
                    header = "The Picture of Dorian Gray",
                    chapterTitle = "Reflections & Tragedy",
                    paragraphs = listOf(
                        "Oscar Wilde's sole novel, published in 1890, explores aestheticism, vanity, hedonism, and moral degeneration.",
                        "The supernatural portrait serves as a visceral mirror of Dorian's decaying soul while his physical countenance remains radiantly pure.",
                        "Wilde’s sharp aphorisms and haunting philosophical inquiries make this one of the cornerstone classics of late Victorian literature."
                    )
                )
            )
        )
    }

    fun createDraculaBook(): SampleBookInfo {
        return SampleBookInfo(
            title = "Dracula",
            author = "Bram Stoker",
            fileName = "dracula_bram_stoker.pdf",
            coverTheme = BookCoverTheme(
                primaryColor = "#0C1F38", // Transylvanian Midnight Abyssal Blue
                spineColor = "#060F1C",
                blueAccentColor = "#60A5FA",
                whiteAccentColor = "#FFFFFF",
                titleTextColor = "#FFFFFF",
                subtitleTextColor = "#E0F2FE"
            ),
            pages = listOf(
                SamplePageContent("Dracula", null, emptyList(), isCover = true),
                SamplePageContent(
                    header = "Dracula",
                    chapterTitle = "Jonathan Harker's Journal",
                    paragraphs = listOf(
                        "3 May. Bistritz.—Left Munich at 8:35 P.M., on 1st May, arriving at Vienna early next morning; should have arrived at 6:46, but train was an hour late.",
                        "Buda-Pesth seems a wonderful place, from the glimpse which I got of it from the train and the little I could walk through the streets. I feared to go very far from the station, as we had arrived late and would start as near the correct time as possible.",
                        "The impression I had was that we were leaving the West and entering the East; the most western of splendid bridges over the Danube took us among the traditions of Turkish rule."
                    ),
                    quote = "Welcome to my house! Enter freely and of your own will!"
                ),
                SamplePageContent(
                    header = "Dracula",
                    chapterTitle = "The Castle in the Carpathians",
                    paragraphs = listOf(
                        "The castle is on the very edge of a terrible precipice. A stone falling from the window would fall a thousand feet without touching anything!",
                        "As far as the eye can reach is a sea of green tree tops, with occasionally a deep rift where there is a chasm.",
                        "Here and there are silver threads where the rivers wind in deep gorges through the forests.",
                        "Dracula turned to me with his strange, red-lit eyes and smiled: “We are in Transylvania; and Transylvania is not England. Our ways are not your ways, and there shall be to you many strange things.”"
                    ),
                    quote = "Listen to them, the children of the night. What music they make!"
                ),
                SamplePageContent(
                    header = "Dracula",
                    chapterTitle = "Gothic Legacy & Impact",
                    paragraphs = listOf(
                        "Published in 1897, Bram Stoker's Dracula redefined vampire folklore and shaped the gothic horror genre for generations.",
                        "Structured as an epistolary novel through diaries, letters, ship logs, and newspaper clippings, Stoker created a vividly modern suspense thriller."
                    )
                )
            )
        )
    }

    fun createTimeMachineBook(): SampleBookInfo {
        return SampleBookInfo(
            title = "The Time Machine",
            author = "H. G. Wells",
            fileName = "the_time_machine_wells.pdf",
            coverTheme = BookCoverTheme(
                primaryColor = "#102E4E", // Chrono Steampunk Navy
                spineColor = "#081829",
                blueAccentColor = "#38BDF8",
                whiteAccentColor = "#FFFFFF",
                titleTextColor = "#FFFFFF",
                subtitleTextColor = "#E0F2FE"
            ),
            pages = listOf(
                SamplePageContent("The Time Machine", null, emptyList(), isCover = true),
                SamplePageContent(
                    header = "The Time Machine",
                    chapterTitle = "Chapter I: The Fourth Dimension",
                    paragraphs = listOf(
                        "The Time Traveller (for so it will be convenient to speak of him) was expounding a recondite matter to us. His grey eyes shone and twinkled, and his usually pale face was flushed and animated.",
                        "The fire burnt brightly, and the soft radiance of the incandescent lights in the lilies of silver caught the bubbles that flashed and passed in our glasses.",
                        "“Clearly,” the Time Traveller proceeded, “any real body must have extension in four directions: it must have Length, Breadth, Thickness, and—Duration.”"
                    ),
                    quote = "There is no difference between Time and any of the three dimensions of Space except that our consciousness moves along it."
                ),
                SamplePageContent(
                    header = "The Time Machine",
                    chapterTitle = "The Golden Age of 802,701 AD",
                    paragraphs = listOf(
                        "I drew a long breath, clutched the lever with both hands, and went off with a thud. The laboratory grew faint and hazy. Night followed day like the flapping of a black wing.",
                        "As I put on pace, night and day flapped together in one continuous greyness; the sky took on a wonderful deepness of blue, a splendid luminous color like that of early twilight.",
                        "I stopped in the year Eight Hundred and Two Thousand, Seven Hundred and One AD, stepping out into a world of lush gardens, crystalline architecture, and the gentle Eloi."
                    ),
                    quote = "Face this world. Learn its ways, watch it, be careful of too hasty guesses."
                ),
                SamplePageContent(
                    header = "The Time Machine",
                    chapterTitle = "The Morlocks and the Future",
                    paragraphs = listOf(
                        "H. G. Wells' 1895 novella popularized the concept of time travel via purposeful mechanical apparatus.",
                        "Through the stark evolutionary divide between the surface-dwelling Eloi and the subterranean Morlocks, Wells delivered a penetrating critique of Victorian class stratification and dystopian destiny."
                    )
                )
            )
        )
    }

    fun createMetamorphosisBook(): SampleBookInfo {
        return SampleBookInfo(
            title = "The Metamorphosis",
            author = "Franz Kafka",
            fileName = "the_metamorphosis_kafka.pdf",
            coverTheme = BookCoverTheme(
                primaryColor = "#152C4B", // Modernist Prussian Deep Blue Cloth
                spineColor = "#0B1728",
                blueAccentColor = "#60A5FA",
                whiteAccentColor = "#FFFFFF",
                titleTextColor = "#FFFFFF",
                subtitleTextColor = "#E0F2FE"
            ),
            pages = listOf(
                SamplePageContent("The Metamorphosis", null, emptyList(), isCover = true),
                SamplePageContent(
                    header = "The Metamorphosis",
                    chapterTitle = "Chapter I: The Awakening",
                    paragraphs = listOf(
                        "One morning, when Gregor Samsa woke from troubled dreams, he found himself transformed in his bed into a horrible vermin.",
                        "He lay on his armour-like back, and if he lifted his head a little he could see his brown belly, slightly domed and divided by arches into stiff sections.",
                        "The bedding was hardly able to cover it and seemed ready to slide off any moment. His many legs, pitifully thin compared with the size of the rest of him, waved about helplessly as he looked.",
                        "“What's happened to me?” he thought. It wasn't a dream."
                    ),
                    quote = "One morning, when Gregor Samsa woke from troubled dreams, he found himself transformed in his bed into a horrible vermin."
                ),
                SamplePageContent(
                    header = "The Metamorphosis",
                    chapterTitle = "Isolation and Family",
                    paragraphs = listOf(
                        "Gregor's room was a proper human room, although a little too small, lying peacefully between its four familiar walls.",
                        "A collection of textile samples lay spread out on the table—Samsa was a travelling salesman—and above it there hung a picture that he had recently cut out of an illustrated magazine and housed in a nice, gilded frame.",
                        "Gregor then turned to look out the window at the dull weather. Drops of rain could be heard hitting the pane, which made him feel quite sad.",
                        "“How about if I sleep a little bit longer and forget all this nonsense,” he thought, but that was something he was unable to do because he was used to sleeping on his right, and in his present state couldn't get into that position."
                    ),
                    quote = "I cannot make you understand. I cannot make anyone understand what is happening inside me."
                ),
                SamplePageContent(
                    header = "The Metamorphosis",
                    chapterTitle = "Existential Masterpiece",
                    paragraphs = listOf(
                        "Published in 1915, Franz Kafka's Die Verwandlung remains one of the most celebrated and analyzed texts in modern world literature.",
                        "With tragic absurdity and compassionate precision, Kafka captures the burdens of familial obligation, bureaucratic alienation, and existential loneliness."
                    )
                )
            )
        )
    }

    fun createSherlockHolmesBook(): SampleBookInfo {
        return SampleBookInfo(
            title = "The Adventures of Sherlock Holmes",
            author = "Arthur Conan Doyle",
            fileName = "sherlock_holmes_doyle.pdf",
            coverTheme = BookCoverTheme(
                primaryColor = "#0E294A", // Baker Street Navy Blue Cloth
                spineColor = "#071629",
                blueAccentColor = "#38BDF8",
                whiteAccentColor = "#FFFFFF",
                titleTextColor = "#FFFFFF",
                subtitleTextColor = "#E0F2FE"
            ),
            pages = listOf(
                SamplePageContent("The Adventures of Sherlock Holmes", null, emptyList(), isCover = true),
                SamplePageContent(
                    header = "Sherlock Holmes",
                    chapterTitle = "A Scandal in Bohemia",
                    paragraphs = listOf(
                        "To Sherlock Holmes she is always THE woman. I have seldom heard him mention her under any other name. In his eyes she eclipses and predominates the whole of her sex.",
                        "It was not that he felt any emotion akin to love for Irene Adler. All emotions, and that one particularly, were abhorrent to his cold, precise but admirably balanced mind.",
                        "He was, I take it, the most perfect reasoning and observing machine that the world has seen, but as a lover he would have placed himself in a false position."
                    ),
                    quote = "It is a capital mistake to theorize before one has data. Insensibly one begins to twist facts to suit theories, instead of theories to suit facts."
                ),
                SamplePageContent(
                    header = "Sherlock Holmes",
                    chapterTitle = "The Science of Deduction",
                    paragraphs = listOf(
                        "“You see, but you do not observe. The distinction is clear. For example, you have frequently seen the steps which lead up from the hall to this room.”",
                        "“Frequently.”",
                        "“How often?”",
                        "“Well, some hundreds of times.”",
                        "“Then how many are there?”",
                        "“How many? I don't know.”",
                        "“Quite so! You have not observed. And yet you have seen. That is just my point. Now, I know that there are seventeen steps, because I have both seen and observed.”"
                    ),
                    quote = "You see, but you do not observe. The distinction is clear."
                ),
                SamplePageContent(
                    header = "Sherlock Holmes",
                    chapterTitle = "The Red-Headed League",
                    paragraphs = listOf(
                        "“My dear Watson,” said Holmes, as we sat on either side of the fire in Baker Street, “life is infinitely stranger than anything which the mind of man could invent.”",
                        "“We would not dare to conceive the things which are really mere commonplaces of existence.”",
                        "“If we could fly out of that window hand in hand, hover over this great city, gently remove the roofs, and peep in at the queer things which are going on, the strange coincidences, the plannings, the cross-purposes, it would make all fiction with its conventionalities and foreseen conclusions most stale and unprofitable.”"
                    ),
                    quote = "When you have eliminated the impossible, whatever remains, however improbable, must be the truth."
                ),
                SamplePageContent(
                    header = "Sherlock Holmes",
                    chapterTitle = "Detective Fiction Legacy",
                    paragraphs = listOf(
                        "First serialized in The Strand Magazine between 1891 and 1892, Sir Arthur Conan Doyle's stories created the definitive archetype of the brilliant consulting detective.",
                        "Sherlock Holmes and Dr. John H. Watson remain the most famous investigative duo in history."
                    )
                )
            )
        )
    }

    fun createMobyDickBook(): SampleBookInfo {
        return SampleBookInfo(
            title = "Moby Dick",
            author = "Herman Melville",
            fileName = "moby_dick_melville.pdf",
            coverTheme = BookCoverTheme(
                primaryColor = "#081E3B", // Oceanic Deep Abyssal Navy Cloth
                spineColor = "#040F20",
                blueAccentColor = "#60A5FA",
                whiteAccentColor = "#FFFFFF",
                titleTextColor = "#FFFFFF",
                subtitleTextColor = "#E0F2FE"
            ),
            pages = listOf(
                SamplePageContent("Moby Dick", null, emptyList(), isCover = true),
                SamplePageContent(
                    header = "Moby Dick",
                    chapterTitle = "Chapter I: Loomings",
                    paragraphs = listOf(
                        "Call me Ishmael. Some years ago—never mind how long precisely—having little or no money in my purse, and nothing particular to interest me on shore, I thought I would sail about a little and see the watery part of the world.",
                        "It is a way I have of driving off the spleen and regulating the circulation.",
                        "Whenever I find myself growing grim about the mouth; whenever it is a damp, drizzly November in my soul; whenever I find myself involuntarily pausing before coffin warehouses, and bringing up the rear of every funeral I meet; and especially whenever my hypos get such an upper hand of me, that it requires a strong moral principle to prevent me from deliberately stepping into the street, and methodically knocking people's hats off—then, I account it high time to get to sea as soon as I can."
                    ),
                    quote = "Call me Ishmael."
                ),
                SamplePageContent(
                    header = "Moby Dick",
                    chapterTitle = "The White Whale & Captain Ahab",
                    paragraphs = listOf(
                        "For it was the whiteness of the whale that above all things appalled me. But how can I hope to explain myself here; and yet, in some dim, random way, explain myself I must, else all these chapters might be naught.",
                        "Ahab stood before them with his ivory leg braced in an auger hole drilled into the quarter-deck. His face was bronze, carved by decades of relentless storms.",
                        "“Aye, aye! and I'll chase him round Good Hope, and round the Horn, and round the Norway Maelstrom, and round perdition's flames before I give him up!”"
                    ),
                    quote = "I know not all that may be coming, but be it what it will, I'll go to it laughing."
                ),
                SamplePageContent(
                    header = "Moby Dick",
                    chapterTitle = "American Epic Legacy",
                    paragraphs = listOf(
                        "Published in 1851, Herman Melville's Moby-Dick is recognized as one of the supreme epics of world literature.",
                        "Weaving whaling lore, Shakespearean monologue, biblical symbolism, and existential philosophy, Melville created an unforgettable meditation on obsession and humanity's confrontation with nature."
                    )
                )
            )
        )
    }

    fun createTaleOfTwoCitiesBook(): SampleBookInfo {
        return SampleBookInfo(
            title = "A Tale of Two Cities",
            author = "Charles Dickens",
            fileName = "a_tale_of_two_cities_dickens.pdf",
            coverTheme = BookCoverTheme(
                primaryColor = "#112648", // Revolutionary Cobalt French Navy Cloth
                spineColor = "#081325",
                blueAccentColor = "#38BDF8",
                whiteAccentColor = "#FFFFFF",
                titleTextColor = "#FFFFFF",
                subtitleTextColor = "#E0F2FE"
            ),
            pages = listOf(
                SamplePageContent("A Tale of Two Cities", null, emptyList(), isCover = true),
                SamplePageContent(
                    header = "A Tale of Two Cities",
                    chapterTitle = "Book I: The Period",
                    paragraphs = listOf(
                        "It was the best of times, it was the worst of times, it was the age of wisdom, it was the age of foolishness, it was the epoch of belief, it was the epoch of incredulity, it was the season of Light, it was the season of Darkness, it was the spring of hope, it was the winter of despair, we had everything before us, we had nothing before us, we were all going direct to Heaven, we were all going direct the other way.",
                        "In short, the period was so far like the present period, that some of its noisiest authorities insisted on its being received, for good or for evil, in the superlative degree of comparison only."
                    ),
                    quote = "It was the best of times, it was the worst of times."
                ),
                SamplePageContent(
                    header = "A Tale of Two Cities",
                    chapterTitle = "Sydney Carton's Sacrifice",
                    paragraphs = listOf(
                        "“It is a far, far better thing that I do, than I have ever done; it is a far, far better rest that I go to than I have ever known.”",
                        "Sydney Carton looked upon the tumbril and the roaring streets of revolutionary Paris. In his heart, he saw the lives he had redeemed with his own, thriving in peace under the calm English sky.",
                        "He saw a long line of the handsomest and most honorable descendants bearing his name, holding his memory sacred."
                    ),
                    quote = "It is a far, far better thing that I do, than I have ever done; it is a far, far better rest that I go to than I have ever known."
                ),
                SamplePageContent(
                    header = "A Tale of Two Cities",
                    chapterTitle = "Dickens' Historical Masterpiece",
                    paragraphs = listOf(
                        "Published in 1859, Charles Dickens' historical novel set in London and Paris before and during the French Revolution is one of the best-selling books in literary history.",
                        "Its themes of resurrection, social justice, tyranny, and unconditional love continue to inspire readers worldwide."
                    )
                )
            )
        )
    }

    fun createArtOfWarBook(): SampleBookInfo {
        return SampleBookInfo(
            title = "The Art of War",
            author = "Sun Tzu",
            fileName = "the_art_of_war_sun_tzu.pdf",
            coverTheme = BookCoverTheme(
                primaryColor = "#0B2240", // Ancient Imperial Deep Indigo Blue
                spineColor = "#051121",
                blueAccentColor = "#60A5FA",
                whiteAccentColor = "#FFFFFF",
                titleTextColor = "#FFFFFF",
                subtitleTextColor = "#E0F2FE"
            ),
            pages = listOf(
                SamplePageContent("The Art of War", null, emptyList(), isCover = true),
                SamplePageContent(
                    header = "The Art of War",
                    chapterTitle = "Chapter I: Laying Plans",
                    paragraphs = listOf(
                        "Sun Tzu said: The art of war is of vital importance to the State.",
                        "It is a matter of life and death, a road either to safety or to ruin. Hence it is a subject of inquiry which can on no account be neglected.",
                        "The art of war, then, is governed by five constant factors, to be taken into account in one's deliberations: (1) The Moral Law; (2) Heaven; (3) Earth; (4) The Commander; (5) Method and discipline.",
                        "All warfare is based on deception. Hence, when able to attack, we must seem unable; when using our forces, we must seem inactive; when we are near, we must make the enemy believe we are far away."
                    ),
                    quote = "All warfare is based on deception."
                ),
                SamplePageContent(
                    header = "The Art of War",
                    chapterTitle = "Chapter III: Attack by Stratagem",
                    paragraphs = listOf(
                        "In the practical art of war, the best thing of all is to take the enemy's country whole and intact; to shatter and destroy it is not so good.",
                        "Hence to fight and conquer in all your battles is not supreme excellence; supreme excellence consists in breaking the enemy's resistance without fighting.",
                        "Thus the highest form of generalship is to balk the enemy's plans; the next best is to prevent the junction of the enemy's forces; the next in order is to attack the enemy's army in the field; and the worst policy of all is to besiege walled cities."
                    ),
                    quote = "If you know the enemy and know yourself, you need not fear the result of a hundred battles."
                ),
                SamplePageContent(
                    header = "The Art of War",
                    chapterTitle = "Timeless Strategic Wisdom",
                    paragraphs = listOf(
                        "Written in ancient China over 2,500 years ago, Sun Tzu's The Art of War remains the world's most revered treatise on strategy, diplomacy, psychology, and leadership.",
                        "Its foundational principles emphasize foresight, psychological insight, restraint, and calculated harmony with nature."
                    )
                )
            )
        )
    }
}
