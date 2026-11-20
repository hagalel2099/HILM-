package com.nossouniverso.launcher.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the futuristic HUD structural frame from Reference 1:
 * - Top ornamental cyan/amber rail (large upper clock removed as specified)
 * - Left & right vertical HUD side rails
 * - Concentric outer segmented rings, 4 corner brackets, radial ticks & 3-dot indicators
 * - Bottom vertical dotted matrix behind the 5-slot dock
 * Transparent areas allow the Android wallpaper to show through.
 */
class HudFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var panelOpacityPercent: Int = 78
        set(value) {
            field = value.coerceIn(25, 95)
            invalidate()
        }

    var ringCenterYRatio: Float = 0.40f
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density

    private val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22D3EE")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }

    private val cyanFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22D3EE")
        style = Paint.Style.FILL
    }

    private val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }

    private val silverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }

    private val darkTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0060B12")
        style = Paint.Style.STROKE
    }

    private val dotMatrixPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D64748B")
        style = Paint.Style.FILL
    }

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Subtle atmospheric scrim preserving wallpaper visibility
        val scrimAlpha = (panelOpacityPercent * 1.45f).toInt().coerceIn(30, 145)
        scrimPaint.color = Color.argb(scrimAlpha, 5, 8, 13)
        canvas.drawRect(0f, 0f, w, h, scrimPaint)

        drawTopOrnamentalHeader(canvas, w)
        drawSideRails(canvas, w, h)
        drawCentralHudRing(canvas, w, h)
        drawBottomSpectrumMatrix(canvas, w, h)
    }

    private fun drawTopOrnamentalHeader(canvas: Canvas, w: Float) {
        val topY = paddingTop + 14f * density
        val margin = 18f * density

        cyanPaint.strokeWidth = 1.5f * density
        cyanPaint.alpha = 200

        val path = Path().apply {
            moveTo(margin, topY + 6f * density)
            lineTo(margin + 18f * density, topY)
            lineTo(w - margin - 18f * density, topY)
            lineTo(w - margin, topY + 6f * density)
        }
        canvas.drawPath(path, cyanPaint)

        // Amber edge ticks
        amberPaint.strokeWidth = 1.5f * density
        amberPaint.alpha = 210
        canvas.drawLine(margin + 30f * density, topY + 4f * density, margin + 52f * density, topY + 4f * density, amberPaint)
        canvas.drawLine(w - margin - 52f * density, topY + 4f * density, w - margin - 30f * density, topY + 4f * density, amberPaint)

        // Center downward chevron
        val cx = w / 2f
        val chevron = Path().apply {
            moveTo(cx - 14f * density, topY + 6f * density)
            lineTo(cx, topY + 14f * density)
            lineTo(cx + 14f * density, topY + 6f * density)
        }
        canvas.drawPath(chevron, cyanPaint)

        // Three centered indicator dots
        drawThreeDots(canvas, cx, topY + 23f * density)
    }

    private fun drawSideRails(canvas: Canvas, w: Float, h: Float) {
        val top = h * 0.24f
        val bottom = h * 0.72f
        val mid = (top + bottom) / 2f
        val edgeInset = 6f * density
        val step = 10f * density

        silverPaint.strokeWidth = 1.8f * density
        silverPaint.alpha = 185
        cyanPaint.strokeWidth = 1.8f * density
        cyanPaint.alpha = 210
        amberPaint.strokeWidth = 2f * density
        amberPaint.alpha = 220

        // Left rail
        val leftPath = Path().apply {
            moveTo(edgeInset, top)
            lineTo(edgeInset + step, top + step)
            lineTo(edgeInset + step, mid - 28f * density)
            lineTo(edgeInset + 3f * density, mid - 20f * density)
            lineTo(edgeInset + 3f * density, mid + 20f * density)
            lineTo(edgeInset + step, mid + 28f * density)
            lineTo(edgeInset + step, bottom - step)
            lineTo(edgeInset, bottom)
        }
        canvas.drawPath(leftPath, silverPaint)
        canvas.drawLine(edgeInset + 6f * density, mid - 34f * density, edgeInset + 6f * density, mid - 12f * density, amberPaint)
        canvas.drawLine(edgeInset + 6f * density, mid + 12f * density, edgeInset + 6f * density, mid + 34f * density, cyanPaint)

        // Right rail (mirrored)
        val rightPath = Path().apply {
            moveTo(w - edgeInset, top)
            lineTo(w - edgeInset - step, top + step)
            lineTo(w - edgeInset - step, mid - 28f * density)
            lineTo(w - edgeInset - 3f * density, mid - 20f * density)
            lineTo(w - edgeInset - 3f * density, mid + 20f * density)
            lineTo(w - edgeInset - step, mid + 28f * density)
            lineTo(w - edgeInset - step, bottom - step)
            lineTo(w - edgeInset, bottom)
        }
        canvas.drawPath(rightPath, silverPaint)
        canvas.drawLine(w - edgeInset - 6f * density, mid - 34f * density, w - edgeInset - 6f * density, mid - 12f * density, amberPaint)
        canvas.drawLine(w - edgeInset - 6f * density, mid + 12f * density, w - edgeInset - 6f * density, mid + 34f * density, cyanPaint)
    }

    private fun drawCentralHudRing(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h * ringCenterYRatio
        val outerR = min(w * 0.40f, h * 0.22f)
        val midR = outerR * 0.87f
        val innerR = outerR * 0.72f

        // Dark outer structural ring track
        darkTrackPaint.strokeWidth = outerR * 0.16f
        canvas.drawCircle(cx, cy, (outerR + innerR) / 2f, darkTrackPaint)

        // Radial tick marks around the outer circumference
        silverPaint.strokeWidth = 1.2f * density
        silverPaint.alpha = 140
        for (deg in 0 until 360 step 6) {
            val rad = Math.toRadians(deg.toDouble())
            val innerTick = if (deg % 30 == 0) outerR * 0.90f else outerR * 0.94f
            val outerTick = outerR * 0.99f
            canvas.drawLine(
                cx + (cos(rad) * innerTick).toFloat(),
                cy + (sin(rad) * innerTick).toFloat(),
                cx + (cos(rad) * outerTick).toFloat(),
                cy + (sin(rad) * outerTick).toFloat(),
                silverPaint
            )
        }

        // Cyan segmented arcs (matching Ref 1)
        val outerRect = RectF(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
        cyanPaint.strokeWidth = 2.4f * density
        cyanPaint.alpha = 230
        canvas.drawArc(outerRect, 200f, 52f, false, cyanPaint)
        canvas.drawArc(outerRect, 290f, 65f, false, cyanPaint)
        canvas.drawArc(outerRect, 22f, 58f, false, cyanPaint)
        canvas.drawArc(outerRect, 112f, 52f, false, cyanPaint)

        // Amber segmented outer arcs
        val midRect = RectF(cx - midR, cy - midR, cx + midR, cy + midR)
        amberPaint.strokeWidth = 2.2f * density
        amberPaint.alpha = 225
        canvas.drawArc(midRect, 258f, 24f, false, amberPaint)
        canvas.drawArc(midRect, 82f, 24f, false, amberPaint)
        canvas.drawArc(midRect, 168f, 20f, false, amberPaint)
        canvas.drawArc(midRect, 350f, 20f, false, amberPaint)

        // Inner circular HUD border around HILM core aperture
        val innerRect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
        cyanPaint.strokeWidth = 1.8f * density
        cyanPaint.alpha = 195
        canvas.drawArc(innerRect, 15f, 130f, false, cyanPaint)
        canvas.drawArc(innerRect, 195f, 130f, false, cyanPaint)

        // Left & right outer caliper brackets
        val caliperR = outerR * 1.11f
        val caliperRect = RectF(cx - caliperR, cy - caliperR, cx + caliperR, cy + caliperR)
        silverPaint.strokeWidth = 2.5f * density
        silverPaint.alpha = 215
        canvas.drawArc(caliperRect, 162f, 36f, false, silverPaint)
        canvas.drawArc(caliperRect, 342f, 36f, false, silverPaint)

        // Four corner brackets around the circular HUD
        drawCornerBrackets(canvas, cx, cy, outerR * 1.15f)

        // Subtle amber calibration hairlines crossing at ~78 deg & ~258 deg
        amberPaint.strokeWidth = 1f * density
        amberPaint.alpha = 150
        for (angle in listOf(-76.0, 104.0)) {
            val rad = Math.toRadians(angle)
            canvas.drawLine(
                cx + (cos(rad) * innerR * 0.96f).toFloat(),
                cy + (sin(rad) * innerR * 0.96f).toFloat(),
                cx + (cos(rad) * outerR * 1.07f).toFloat(),
                cy + (sin(rad) * outerR * 1.07f).toFloat(),
                amberPaint
            )
        }

        // Top & bottom 3-dot indicators
        drawThreeDots(canvas, cx, cy - outerR * 1.12f)
        drawThreeDots(canvas, cx, cy + outerR * 1.12f)
    }

    private fun drawCornerBrackets(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val len = 18f * density
        silverPaint.strokeWidth = 2f * density
        silverPaint.alpha = 200
        cyanPaint.strokeWidth = 1.4f * density
        cyanPaint.alpha = 180

        val corners = listOf(
            Pair(-1f, -1f),
            Pair(1f, -1f),
            Pair(-1f, 1f),
            Pair(1f, 1f)
        )
        corners.forEach { (sx, sy) ->
            val x = cx + sx * r * 0.78f
            val y = cy + sy * r * 0.78f
            val path = Path().apply {
                moveTo(x - sx * len, y)
                lineTo(x, y)
                lineTo(x, y - sy * len)
            }
            canvas.drawPath(path, silverPaint)
        }
    }

    private fun drawBottomSpectrumMatrix(canvas: Canvas, w: Float, h: Float) {
        val bottomY = h - paddingBottom - 6f * density
        val cols = 38
        val colSpacing = w / cols
        val dotSize = 2.6f * density
        val dotStep = 5.5f * density

        for (c in 0 until cols) {
            val x = c * colSpacing + colSpacing * 0.5f
            val norm = (c.toFloat() / (cols - 1)) * 2f - 1f
            val envelope = (0.35f + 0.65f * kotlin.math.abs(norm)) * (if (c % 3 == 0) 1.0f else 0.7f)
            val rows = (4 + (envelope * 11)).toInt()
            for (r in 0 until rows) {
                val y = bottomY - r * dotStep
                dotMatrixPaint.alpha = (115 - r * 7).coerceAtLeast(20)
                canvas.drawRect(x, y - dotSize, x + dotSize, y, dotMatrixPaint)
            }
        }
    }

    private fun drawThreeDots(canvas: Canvas, cx: Float, cy: Float) {
        val r = 2f * density
        val gap = 7f * density
        cyanFillPaint.alpha = 190
        canvas.drawCircle(cx - gap, cy, r, cyanFillPaint)
        canvas.drawCircle(cx, cy, r, cyanFillPaint)
        canvas.drawCircle(cx + gap, cy, r, cyanFillPaint)
    }
}
