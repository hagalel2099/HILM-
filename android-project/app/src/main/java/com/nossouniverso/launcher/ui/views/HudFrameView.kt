package com.nossouniverso.launcher.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Draws the futuristic HUD structural frame from Reference 1:
 * - Top ornamental cyan/amber rail (large upper clock removed as specified)
 * - Left & right vertical HUD side rails
 * - Bottom vertical dotted matrix behind the 5-slot dock
 * The central HILM nucleus floats freely over the wallpaper without any enclosing ring or border.
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

    private val dotMatrixPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D64748B")
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        drawTopOrnamentalHeader(canvas, w)
        drawSideRails(canvas, w, h)
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
