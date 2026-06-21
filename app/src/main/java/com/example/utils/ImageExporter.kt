package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

object ImageExporter {

    /**
     * Loads a Bitmap using Coil. Ensures allowHardware(false) is set so that the returned
     * Bitmap is in software memory and can be drawn onto a Canvas.
     */
    suspend fun loadBitmap(context: Context, source: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                if (source.startsWith("/")) {
                    // It's a local file path
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeFile(source, options)
                } else if (source.startsWith("content://")) {
                    // Content URI from the Picker
                    val contentResolver = context.contentResolver
                    val inputStream: InputStream? = contentResolver.openInputStream(Uri.parse(source))
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bitmap
                } else {
                    // Image URL or online preset
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(source)
                        .allowHardware(false) // MANDATORY for drawing onto a Canvas
                        .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val drawable = result.drawable
                        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1000
                        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1000
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        drawable.setBounds(0, 0, width, height)
                        drawable.draw(canvas)
                        bitmap
                    } else null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Composites before/after/logo images into a single high-resolution branded comparison image.
     */
    suspend fun compositeHighResBeforeAfter(
        context: Context,
        beforeSource: String,
        afterSource: String,
        logoSource: String?,
        businessName: String,
        slogan: String,
        beforeLabel: String,
        afterLabel: String,
        category: String,
        layoutVersion: Int,
        bgColorInt: Int
    ): Uri? {
        return withContext(Dispatchers.IO) {
            // 1. Load the Before and After bitmaps
            val beforeBitmap = loadBitmap(context, beforeSource) ?: return@withContext null
            val afterBitmap = loadBitmap(context, afterSource) ?: return@withContext null
            val logoBitmap = logoSource?.let { loadBitmap(context, it) }

            // 2. Resolve colors corresponding to selected industry category
            val colors = getCategoryColors(category)
            val primaryColor = colors.primary
            val darkBgColor = colors.darkBg
            val cardBgColor = colors.cardBg

            // 3. Define canvas dimensions based on chosen layout version
            val width = when (layoutVersion) {
                0 -> 1600 // Style 1: Split landscape
                1 -> 1080 // Style 2: Vertical Stack (9:16 portrait)
                2 -> 1200 // Style 3: Spotlight square (1:1 style)
                else -> 1200 // Style 4: Modern Promo Layout (1:1 square)
            }
            val height = when (layoutVersion) {
                0 -> 1200
                1 -> 1920
                2 -> 1200
                else -> 1200
            }

            val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)
            canvas.drawColor(bgColorInt) // User chosen background

            // 4. Draw layout types
            when (layoutVersion) {
                0 -> drawClassicSplit(
                    canvas, width, height, beforeBitmap, afterBitmap, logoBitmap,
                    businessName, slogan, beforeLabel, afterLabel, primaryColor, bgColorInt, category
                )
                1 -> drawVerticalStackPort(
                    canvas, width, height, beforeBitmap, afterBitmap, logoBitmap,
                    businessName, slogan, beforeLabel, afterLabel, primaryColor, bgColorInt, category
                )
                2 -> drawSpotlightSquare(
                    canvas, width, height, beforeBitmap, afterBitmap, logoBitmap,
                    businessName, slogan, beforeLabel, afterLabel, primaryColor, bgColorInt, category
                )
                else -> drawPromoBannerSquare(
                    canvas, width, height, beforeBitmap, afterBitmap, logoBitmap,
                    businessName, slogan, beforeLabel, afterLabel, primaryColor, bgColorInt, category
                )
            }

            // 5. Save the composite image to the public MediaStore (Gallery)
            val exportTitle = "Before_After_${businessName.replace(" ", "_")}_${System.currentTimeMillis()}"
            val finalUri = saveBitmapToGallery(context, resultBitmap, exportTitle)
            resultBitmap.recycle()
            beforeBitmap.recycle()
            afterBitmap.recycle()
            logoBitmap?.recycle()

            finalUri
        }
    }

    /**
     * Scale and center-crop a source bitmap into a target rect on the canvas.
     */
    private fun centerCropBitmap(canvas: Canvas, bitmap: Bitmap, destRect: Rect) {
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height
        val dstWidth = destRect.width()
        val dstHeight = destRect.height()

        val scaleX = dstWidth.toFloat() / srcWidth
        val scaleY = dstHeight.toFloat() / srcHeight
        val scale = maxOf(scaleX, scaleY)

        val w = (dstWidth / scale).toInt()
        val h = (dstHeight / scale).toInt()
        val x = (srcWidth - w) / 2
        val y = (srcHeight - h) / 2

        val srcRect = Rect(x, y, x + w, y + h)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bitmap, srcRect, destRect, paint)
    }

    /**
     * SCALE & CROP WITH ROUNDED CORNERS
     */
    private fun centerCropBitmapWithRoundRect(canvas: Canvas, bitmap: Bitmap, destRect: RectF, rx: Float, ry: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply {
            addRoundRect(destRect, rx, ry, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        
        val tempRect = Rect(destRect.left.toInt(), destRect.top.toInt(), destRect.right.toInt(), destRect.bottom.toInt())
        centerCropBitmap(canvas, bitmap, tempRect)
        
        canvas.restore()
    }

    // ==========================================
    // LAYOUT DRAWING ROUTINES (CANVAS HIGH-RES)
    // ==========================================

    private fun drawClassicSplit(
        canvas: Canvas, w: Int, h: Int, before: Bitmap, after: Bitmap, logo: Bitmap?,
        businessName: String, slogan: String, beforeLabel: String, afterLabel: String,
        primeColor: Int, bgColor: Int, category: String
    ) {
        val footerHeight = 180
        val imageAreaHeight = h - footerHeight

        // Before side-by-side rect
        val beforeRect = Rect(0, 0, w / 2, imageAreaHeight)
        centerCropBitmap(canvas, before, beforeRect)

        // After side-by-side rect
        val afterRect = Rect(w / 2, 0, w, imageAreaHeight)
        centerCropBitmap(canvas, after, afterRect)

        // Draw Vertical dividing line
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 6f
            style = Paint.Style.STROKE
        }
        canvas.drawLine((w / 2).toFloat(), 0f, (w / 2).toFloat(), imageAreaHeight.toFloat(), dividerPaint)

        // Footer block background
        val footerPaint = Paint().apply {
            color = bgColor
        }
        canvas.drawRect(0f, imageAreaHeight.toFloat(), w.toFloat(), h.toFloat(), footerPaint)

        // Accent divider line under images
        val linePaint = Paint().apply {
            color = primeColor
        }
        canvas.drawRect(0f, imageAreaHeight.toFloat(), w.toFloat(), (imageAreaHeight + 8).toFloat(), linePaint)

        // Draw Badges OVER the images
        drawBadge(canvas, 40f, 40f, beforeLabel, Color.parseColor("#D32F2F")) // Red for old
        drawBadge(canvas, (w / 2 + 40).toFloat(), 40f, afterLabel, primeColor) // Accent for new

        // Bottom Brand Text
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val isLight = Color.luminance(bgColor) > 0.5f
            color = if (isLight) Color.BLACK else Color.WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val sloganPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val isLight = Color.luminance(bgColor) > 0.5f
            color = if (isLight) Color.DKGRAY else Color.parseColor("#E0E0E0")
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        // Draw Logo or defaults on the left
        val logoSize = 80f
        val logoX = 60f
        val logoY = imageAreaHeight + (footerHeight - logoSize) / 2 + 10f

        if (logo != null) {
            val logoDst = RectF(logoX, logoY, logoX + logoSize, logoY + logoSize)
            canvas.drawBitmap(logo, null, logoDst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            drawDefaultSectorLogo(canvas, logoX + logoSize/2, logoY + logoSize/2, logoSize, category, primeColor)
        }

        // Draw Business Details
        canvas.drawText(businessName.uppercase(), logoX + logoSize + 30f, logoY + 35f, titlePaint)
        canvas.drawText(slogan, logoX + logoSize + 30f, logoY + 75f, sloganPaint)
    }

    private fun drawVerticalStackPort(
        canvas: Canvas, w: Int, h: Int, before: Bitmap, after: Bitmap, logo: Bitmap?,
        businessName: String, slogan: String, beforeLabel: String, afterLabel: String,
        primeColor: Int, bgColor: Int, category: String
    ) {
        val footerHeight = 220
        val imageAreaHeight = h - footerHeight
        val sectionHeight = imageAreaHeight / 2

        // Top Screen: Before
        val beforeRect = Rect(0, 0, w, sectionHeight)
        centerCropBitmap(canvas, before, beforeRect)

        // Bottom Screen: After
        val afterRect = Rect(0, sectionHeight, w, imageAreaHeight)
        centerCropBitmap(canvas, after, afterRect)

        // Draw Horizontal Bold Divider Line with Primary Color
        val divPaint = Paint().apply {
            color = primeColor
        }
        canvas.drawRect(0f, sectionHeight.toFloat() - 4f, w.toFloat(), sectionHeight.toFloat() + 4f, divPaint)

        // Double Circle Badge overlapping over the divider
        val centerBadgeX = w / 2f
        val centerBadgeY = sectionHeight.toFloat()
        
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primeColor
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val isLight = Color.luminance(bgColor) > 0.5f
        val textBadgeColor = if (isLight) Color.BLACK else Color.WHITE
        
        val textBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textBadgeColor
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        canvas.drawRoundRect(
            centerBadgeX - 160f, centerBadgeY - 35f,
            centerBadgeX + 160f, centerBadgeY + 35f,
            35f, 35f, badgeBgPaint
        )
        canvas.drawRoundRect(
            centerBadgeX - 160f, centerBadgeY - 35f,
            centerBadgeX + 160f, centerBadgeY + 35f,
            35f, 35f, borderPaint
        )
        canvas.drawText("$beforeLabel & $afterLabel", centerBadgeX, centerBadgeY + 10f, textBadgePaint)

        // Footer block
        val footerPaint = Paint().apply { color = bgColor }
        canvas.drawRect(0f, imageAreaHeight.toFloat(), w.toFloat(), h.toFloat(), footerPaint)

        // Clean subtle border line
        val accentBorder = Paint().apply { color = primeColor }
        canvas.drawRect(0f, imageAreaHeight.toFloat(), w.toFloat(), imageAreaHeight.toFloat() + 6f, accentBorder)

        // Footer Logo
        val logoSize = 90f
        val logoX = w / 2f - logoSize / 2f
        val logoY = imageAreaHeight + 25f

        if (logo != null) {
            val destLogo = RectF(logoX, logoY, logoX + logoSize, logoY + logoSize)
            canvas.drawBitmap(logo, null, destLogo, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            drawDefaultSectorLogo(canvas, w / 2f, logoY + logoSize / 2f, logoSize, category, primeColor)
        }

        // Center Aligned Text Display
        val businessTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLight) Color.BLACK else Color.WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val footerSloganPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLight) Color.DKGRAY else Color.parseColor("#E0E0E0")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(businessName.uppercase(), w / 2f, logoY + logoSize + 40f, businessTextPaint)
        canvas.drawText(slogan, w / 2f, logoY + logoSize + 75f, footerSloganPaint)
    }

    private fun drawSpotlightSquare(
        canvas: Canvas, w: Int, h: Int, before: Bitmap, after: Bitmap, logo: Bitmap?,
        businessName: String, slogan: String, beforeLabel: String, afterLabel: String,
        primeColor: Int, bgColor: Int, category: String
    ) {
        // Spotlight style: After image dominates the base canvas background
        val fullRect = Rect(0, 0, w, h)
        centerCropBitmap(canvas, after, fullRect)

        // Subtle radial dark gradient overlay to make things readable
        val gradientPaint = Paint().apply {
            shader = RadialGradient(
                w / 2f, h / 2f, w * 0.7f,
                Color.TRANSPARENT, Color.parseColor("#99000000"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gradientPaint)

        // Draw Inset floating "Before" Polaroid Card
        val cardWidth = 360f
        val cardHeight = 440f
        val cardX = 50f
        val cardY = 50f

        // Polaroid Frame Background
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(15f, 5f, 10f, Color.parseColor("#80000000"))
        }
        
        // Android requires physical software layer helper to display shadows securely
        val rectFrame = RectF(cardX, cardY, cardX + cardWidth, cardY + cardHeight)
        canvas.drawRoundRect(rectFrame, 20f, 20f, framePaint)

        // Small Before image centered inside frame
        val borderGap = 20f
        val innerBeforeRect = RectF(
            cardX + borderGap,
            cardY + borderGap,
            cardX + cardWidth - borderGap,
            cardY + cardHeight - 80f // leave space at bottom for text
        )
        // Draw the image cropped
        centerCropBitmapWithRoundRect(canvas, before, innerBeforeRect, 10f, 10f)

        // Text inside polaroid frame
        val polaroidTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#37474F")
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(beforeLabel, cardX + cardWidth / 2f, cardY + cardHeight - 30f, polaroidTextPaint)

        // Floating Minimal "AFTER" luxury label-tag on the main canvas
        val afterBadgeX = w - 240f
        val afterBadgeY = 60f
        drawBadge(canvas, afterBadgeX, afterBadgeY, afterLabel, primeColor)

        // Glassmorphic translucent branding banner at the very bottom
        val bannerHeight = 160f
        val bannerY = h - bannerHeight - 40f
        val bannerMargin = 40f
        
        val bannerRect = RectF(bannerMargin, bannerY, w - bannerMargin, h - bannerMargin)
        val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC0B0D10")
        }
        canvas.drawRoundRect(bannerRect, 30f, 30f, glassPaint)

        // Framed Gold Ring Border
        val glassBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primeColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(bannerRect, 30f, 30f, glassBorder)

        // Texts inside glass block
        val bNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val isLight = Color.luminance(bgColor) > 0.5f // Use bgColorInt
            // We force glassmorphic to be dark/light adapting
            color = Color.WHITE
            textSize = 38f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bSloganPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5F5F5")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val logoSize = 80f
        val logoX = bannerMargin + 30f
        val logoY = bannerY + (bannerHeight - logoSize) / 2f

        if (logo != null) {
            val dstLogo = RectF(logoX, logoY, logoX + logoSize, logoY + logoSize)
            canvas.drawBitmap(logo, null, dstLogo, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            drawDefaultSectorLogo(canvas, logoX + logoSize / 2f, logoY + logoSize / 2f, logoSize, category, primeColor)
        }

        canvas.drawText(businessName.uppercase(), logoX + logoSize + 30f, logoY + 32f, bNamePaint)
        canvas.drawText(slogan, logoX + logoSize + 30f, logoY + 68f, bSloganPaint)
    }

    private fun drawPromoBannerSquare(
        canvas: Canvas, w: Int, h: Int, before: Bitmap, after: Bitmap, logo: Bitmap?,
        businessName: String, slogan: String, beforeLabel: String, afterLabel: String,
        primeColor: Int, bgColor: Int, category: String
    ) {
        val outerGap = 35f
        val headerHeight = 100f
        val footerHeight = 220f
        
        // Paint outer styled professional matte framework background
        val bgPaint = Paint().apply { color = bgColor }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Golden details lines
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primeColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(outerGap, outerGap, w - outerGap, h - outerGap, linePaint)

        // Compute photo area rects
        val photoTop = outerGap + 30f
        val photoBottom = h - footerHeight - 20f
        val photoWidth = (w - (outerGap * 2) - 30f) / 2f

        val beforeBox = RectF(outerGap + 10f, photoTop, outerGap + 10f + photoWidth, photoBottom)
        val afterBox = RectF(w - outerGap - 10f - photoWidth, photoTop, w - outerGap - 10f, photoBottom)

        // Draw Images
        centerCropBitmapWithRoundRect(canvas, before, beforeBox, 16f, 16f)
        centerCropBitmapWithRoundRect(canvas, after, afterBox, 16f, 16f)

        // Add sleek circular corner badges
        drawBadgeOnBoxCorners(canvas, beforeBox, beforeLabel, Color.parseColor("#D32F2F"))
        drawBadgeOnBoxCorners(canvas, afterBox, afterLabel, primeColor)

        // Footer promo details layout
        val footY = h - footerHeight + 10f
        
        // Left Side: Brand Text
        val isLight = Color.luminance(bgColor) > 0.5f

        val promoBigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLight) Color.BLACK else Color.WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val promoSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLight) Color.DKGRAY else Color.parseColor("#E0E0E0")
            textSize = 25f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        // Center Brand Text at Footer
        canvas.drawText(businessName.uppercase(), w / 2f, footY + 45f, promoBigPaint)
        canvas.drawText(slogan, w / 2f, footY + 85f, promoSubPaint)
    }

    // ==========================================
    // DRAWING HELPERS
    // ==========================================

    private fun drawBadge(canvas: Canvas, x: Float, y: Float, text: String, badgeColor: Int) {
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badgeColor
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val textWidth = textPaint.measureText(text)
        val padX = 25f
        val padY = 14f
        val rHeight = 24f + padY * 2

        val rect = RectF(x, y, x + textWidth + padX * 2, y + rHeight)
        canvas.drawRoundRect(rect, rHeight / 2f, rHeight / 2f, badgePaint)
        canvas.drawText(text, x + padX, y + padY + 20f, textPaint)
    }

    private fun drawBadgeOnBoxCorners(canvas: Canvas, box: RectF, text: String, badgeColor: Int) {
        // Draw centered capsule overlay at bottom edge of current photo box
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val valX = textPaint.measureText(text)
        val widthBadge = valX + 40f
        val bannerH = 50f
        
        val bX = box.left + (box.width() - widthBadge)/2f
        val bY = box.bottom - 60f

        val container = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = badgeColor }
        canvas.drawRoundRect(bX, bY, bX + widthBadge, bY + bannerH, 25f, 25f, container)
        canvas.drawText(text, bX + 20f, bY + 34f, textPaint)
    }

    /**
     * Draws beautiful stylized vector emblems representing the category offline default logos.
     */
    fun drawDefaultSectorLogo(canvas: Canvas, cx: Float, cy: Float, size: Float, sector: String, colorInt: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            style = Paint.Style.FILL
            strokeWidth = 4f
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        when (sector) {
            "Interior Design" -> {
                // Drawing clean artistic chair symbol and modern circles
                canvas.drawCircle(cx - size/5f, cy, size/4f, strokePaint)
                canvas.drawCircle(cx + size/5f, cy, size/4f, strokePaint)
                val path = Path().apply {
                    moveTo(cx - 20f, cy - 10f)
                    lineTo(cx + 20f, cy - 10f)
                    lineTo(cx + 20f, cy + 20f)
                    lineTo(cx - 20f, cy + 20f)
                    close()
                    // Chair legs
                    moveTo(cx - 15f, cy + 20f)
                    lineTo(cx - 15f, cy + 35f)
                    moveTo(cx + 15f, cy + 20f)
                    lineTo(cx + 15f, cy + 35f)
                    // Chair backrest
                    moveTo(cx, cy - 10f)
                    lineTo(cx, cy - 30f)
                }
                canvas.drawPath(path, strokePaint)
            }
            "Construction & Roofing" -> {
                // Architectural house peak combined structure
                val p = Path().apply {
                    // Roof 1
                    moveTo(cx - size/2.5f, cy + size/6)
                    lineTo(cx, cy - size/3f)
                    lineTo(cx + size/2.5f, cy + size/6f)
                    // Foundation wall
                    moveTo(cx - size/3.2f, cy + size/8)
                    lineTo(cx - size/3.2f, cy + size/3f)
                    lineTo(cx + size/3.2f, cy + size/3f)
                    lineTo(cx + size/3.2f, cy + size/8f)
                    // Secondary peak
                    moveTo(cx - size/6f, cy - size/10)
                    lineTo(cx + size/4f, cy - size/4f)
                    lineTo(cx + size/2f, cy)
                }
                canvas.drawPath(p, strokePaint)
            }
            "Patio & Gardening" -> {
                // Curved twin organic leaves
                val p = Path().apply {
                    // Left Leaf
                    moveTo(cx, cy + size/3f)
                    cubicTo(cx - size/2f, cy, cx - size/3f, cy - size/3f, cx, cy - size/3f)
                    cubicTo(cx - size/15f, cy - size/10f, cx - size/8f, cy + size/6f, cx, cy + size/3f)
                    // Right Leaf
                    moveTo(cx, cy + size/3f)
                    cubicTo(cx + size/2f, cy, cx + size/3f, cy - size/3f, cx, cy - size/3f)
                    cubicTo(cx + size/15f, cy - size/10f, cx + size/8f, cy + size/6f, cx, cy + size/3f)
                }
                canvas.drawPath(p, paint)
            }
            else -> {
                // Cleaning: gleaming stars and a drop shield
                canvas.drawCircle(cx, cy, size/4f, strokePaint)
                // Draw 4 sparkling stars (+)
                canvas.drawLine(cx, cy - size/2.5f, cx, cy - size/5f, strokePaint)
                canvas.drawLine(cx - size/4f, cy - size/3f, cx + size/4f, cy - size/3f, strokePaint)
                canvas.drawLine(cx - 25f, cy + 25f, cx + 25f, cy + 25f, strokePaint)
                
                canvas.drawCircle(cx - 20f, cy - 20f, 6f, paint)
                canvas.drawCircle(cx + 25f, cy + 15f, 8f, paint)
            }
        }
    }

    /**
     * Helper to write raw Bitmap file to the MediaStore.
     */
    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BeforeAfterCreator")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            try {
                val outputStream: OutputStream? = resolver.openOutputStream(imageUri)
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    outputStream.close()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                return imageUri
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    // Colors data structure
    data class CategoryColorDetails(val primary: Int, val darkBg: Int, val cardBg: Int)

    fun getCategoryColors(category: String): CategoryColorDetails {
        return when (category) {
            "Interior Design" -> CategoryColorDetails(
                primary = Color.parseColor("#B8860B"), // DarkGoldenrod
                darkBg = Color.parseColor("#1C160F"), // Warm deep espresso brown
                cardBg = Color.parseColor("#FAF0E6")  // Linen background
            )
            "Construction & Roofing" -> CategoryColorDetails(
                primary = Color.parseColor("#E65100"), // Dark Orange
                darkBg = Color.parseColor("#13171B"),  // Concrete dark blue-slate
                cardBg = Color.parseColor("#ECEFF1")  // Blue Gray
            )
            "Patio & Gardening" -> CategoryColorDetails(
                primary = Color.parseColor("#2E7D32"), // Forest green
                darkBg = Color.parseColor("#0F1410"),  // Deep foliage black
                cardBg = Color.parseColor("#E8F5E9")  // Mint surface
            )
            else -> CategoryColorDetails( // Cleaning
                primary = Color.parseColor("#0097A7"), // Cyan
                darkBg = Color.parseColor("#091013"),  // Deep water obsidian
                cardBg = Color.parseColor("#E0F7FA")  // Light cool water
            )
        }
    }
}
