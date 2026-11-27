package com.nossouniverso.launcher.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos

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

    var lunaThemeMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updatePulseAnimatorState()
                invalidate()
            }
        }

    var mayaThemeMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updatePulseAnimatorState()
                invalidate()
            }
        }

    private var pulsePhase: Float = 0f
    private var pulseAnimator: ValueAnimator? = null

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

    private val electricBluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#398BFF")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val mayaSignaturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE49E")
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.24f
    }

    private val dotMatrixPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D64748B")
        style = Paint.Style.FILL
    }

    private val glowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    private fun isReducedMotionEnabled(): Boolean {
        val animScale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        } catch (_: Exception) {
            1f
        }
        val sysEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            true
        }
        return animScale <= 0f || !sysEnabled
    }

    private fun updatePulseAnimatorState() {
        val shouldAnimate = (lunaThemeMode || mayaThemeMode) &&
            isAttachedToWindow &&
            windowVisibility == VISIBLE &&
            visibility == VISIBLE &&
            !isReducedMotionEnabled()

        if (!shouldAnimate) {
            pulseAnimator?.cancel()
            pulseAnimator = null
            pulsePhase = 0f
            return
        }

        val targetDuration = if (lunaThemeMode) 5400L else 4200L
        val existing = pulseAnimator
        if (existing != null && existing.duration == targetDuration) {
            if (!existing.isStarted) existing.start()
            return
        }

        existing?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = targetDuration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                pulsePhase = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePulseAnimatorState()
    }

    override fun onDetachedFromWindow() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updatePulseAnimatorState()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updatePulseAnimatorState()
    }

    private fun wave(offsetFraction: Float = 0f): Float {
        if (!lunaThemeMode && !mayaThemeMode) return 0f
        if (isReducedMotionEnabled()) return 0.5f
        val angle = ((pulsePhase + offsetFraction) % 1f) * (2f * PI.toFloat())
        return 0.5f - 0.5f * cos(angle)
    }

    private fun lerpColor(colorA: Int, colorB: Int, t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        val a = (Color.alpha(colorA) + (Color.alpha(colorB) - Color.alpha(colorA)) * c).toInt()
        val r = (Color.red(colorA) + (Color.red(colorB) - Color.red(colorA)) * c).toInt()
        val g = (Color.green(colorA) + (Color.green(colorB) - Color.green(colorA)) * c).toInt()
        val b = (Color.blue(colorA) + (Color.blue(colorB) - Color.blue(colorA)) * c).toInt()
        return Color.argb(a, r, g, b)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (lunaThemeMode) {
            drawLunaHoloFrame(canvas, w, h)
            return
        }
        if (mayaThemeMode) {
            drawMayaCeramicFrame(canvas, w, h)
            return
        }

        drawTopOrnamentalHeader(canvas, w)
        drawSideRails(canvas, w, h)
        drawBottomSpectrumMatrix(canvas, w, h)
    }

    private fun drawMayaCeramicFrame(canvas: Canvas, w: Float, h: Float) {
        val topY = paddingTop + 6f * density
        val margin = 16f * density
        val cx = w / 2f

        val topWave = wave(0f)
        val sideWave = wave(0.18f)
        val horizonWave = wave(0.32f)

        // Sculpted golden-honey upper rail split cleanly around signature with soft ceramic-gold pulse aura
        val gapHalf = 96f * density
        val leftTopPath = Path().apply {
            moveTo(margin, topY + 6f * density)
            lineTo(margin + 14f * density, topY)
            lineTo((cx - gapHalf).coerceAtLeast(margin + 24f * density), topY)
        }
        val rightTopPath = Path().apply {
            moveTo((cx + gapHalf).coerceAtMost(w - margin - 24f * density), topY)
            lineTo(w - margin - 14f * density, topY)
            lineTo(w - margin, topY + 6f * density)
        }
        glowStrokePaint.color = Color.parseColor("#F5B942")
        glowStrokePaint.strokeWidth = 4.4f * density
        glowStrokePaint.alpha = (22 + (46 * topWave).toInt()).coerceIn(0, 255)
        canvas.drawPath(leftTopPath, glowStrokePaint)
        canvas.drawPath(rightTopPath, glowStrokePaint)

        amberPaint.color = Color.parseColor("#F5B942")
        amberPaint.strokeWidth = 1.6f * density
        amberPaint.alpha = (168 + (80 * topWave).toInt()).coerceIn(0, 255)
        canvas.drawPath(leftTopPath, amberPaint)
        canvas.drawPath(rightTopPath, amberPaint)

        // Copper shoulder bevel ticks
        silverPaint.color = Color.parseColor("#C47A48")
        silverPaint.strokeWidth = 1.6f * density
        silverPaint.alpha = (158 + (78 * topWave).toInt()).coerceIn(0, 255)
        canvas.drawLine(margin + 22f * density, topY + 4.5f * density, margin + 44f * density, topY + 4.5f * density, silverPaint)
        canvas.drawLine(w - margin - 44f * density, topY + 4.5f * density, w - margin - 22f * density, topY + 4.5f * density, silverPaint)

        mayaSignaturePaint.textSize = 7.4f * density
        mayaSignaturePaint.alpha = (166 + (74 * topWave).toInt()).coerceIn(0, 255)
        canvas.drawText("MAYA — HOLOGRAPHIC PRESENCE", cx, topY + 4.5f * density, mayaSignaturePaint)

        // Electric blue center reticle chevron with subtle halo + 3 indicator dots
        val chevron = Path().apply {
            moveTo(cx - 14f * density, topY + 11f * density)
            lineTo(cx, topY + 18.5f * density)
            lineTo(cx + 14f * density, topY + 11f * density)
        }
        glowStrokePaint.color = Color.parseColor("#38BDF8")
        glowStrokePaint.strokeWidth = 3.8f * density
        glowStrokePaint.alpha = (24 + (48 * topWave).toInt()).coerceIn(0, 255)
        canvas.drawPath(chevron, glowStrokePaint)

        electricBluePaint.color = Color.parseColor("#38BDF8")
        electricBluePaint.strokeWidth = 1.5f * density
        electricBluePaint.alpha = (168 + (82 * topWave).toInt()).coerceIn(0, 255)
        canvas.drawPath(chevron, electricBluePaint)

        val dotR = 1.7f * density
        val dotGap = 6.5f * density
        val dotY = topY + 24.5f * density
        cyanFillPaint.color = Color.parseColor("#F5B942")
        cyanFillPaint.alpha = (165 + (75 * topWave).toInt()).coerceIn(0, 255)
        canvas.drawCircle(cx - dotGap, dotY, dotR, cyanFillPaint)
        canvas.drawCircle(cx + dotGap, dotY, dotR, cyanFillPaint)
        cyanFillPaint.color = Color.parseColor("#38BDF8")
        cyanFillPaint.alpha = (175 + (75 * topWave).toInt()).coerceIn(0, 255)
        canvas.drawCircle(cx, dotY, dotR, cyanFillPaint)

        // Ceramic side drawer rails (graphite copper + golden-honey + electric blue core)
        val top = h * 0.24f
        val bottom = h * 0.72f
        val mid = (top + bottom) / 2f
        val edgeInset = 6f * density
        val step = 10f * density

        silverPaint.alpha = (150 + (82 * sideWave).toInt()).coerceIn(0, 255)
        amberPaint.alpha = (162 + (84 * sideWave).toInt()).coerceIn(0, 255)
        electricBluePaint.alpha = (165 + (82 * sideWave).toInt()).coerceIn(0, 255)

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
        canvas.drawLine(edgeInset + 6f * density, mid + 12f * density, edgeInset + 6f * density, mid + 34f * density, electricBluePaint)

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
        canvas.drawLine(w - edgeInset - 6f * density, mid + 12f * density, w - edgeInset - 6f * density, mid + 34f * density, electricBluePaint)

        // Subtle warm golden bottom horizon line
        val bottomY = h - paddingBottom - 8f * density
        amberPaint.alpha = (75 + (78 * horizonWave).toInt()).coerceIn(0, 255)
        amberPaint.strokeWidth = 1.1f * density
        canvas.drawLine(margin + 24f * density, bottomY, w - margin - 24f * density, bottomY, amberPaint)
    }

    private fun drawLunaHoloFrame(canvas: Canvas, w: Float, h: Float) {
        val margin = 12f * density
        val topY = paddingTop + 5f * density
        val bottomY = h - paddingBottom - 8f * density
        val cornerLen = 22f * density
        val cx = w / 2f

        val cornerWave = wave(0f)
        val railWave = wave(0.24f)
        val horizonWave = wave(0.45f)

        val lunaCyan = Color.parseColor("#00E5FF")
        val lunaViolet = Color.parseColor("#8B5CF6")
        val lunaBlue = Color.parseColor("#398BFF")

        // Subtle holographic cyan-to-violet upper corner ticks & centered top-drawer V-indicator
        val cornerColor = lerpColor(lunaCyan, lunaViolet, cornerWave * 0.45f)
        glowStrokePaint.color = cornerColor
        glowStrokePaint.strokeWidth = 3.2f * density
        glowStrokePaint.alpha = (18 + (34 * cornerWave).toInt()).coerceIn(0, 255)
        canvas.drawLine(margin, topY + cornerLen * 0.45f, margin, topY, glowStrokePaint)
        canvas.drawLine(margin, topY, margin + cornerLen, topY, glowStrokePaint)
        canvas.drawLine(w - margin - cornerLen, topY, w - margin, topY, glowStrokePaint)
        canvas.drawLine(w - margin, topY, w - margin, topY + cornerLen * 0.45f, glowStrokePaint)

        val lunaChevron = Path().apply {
            moveTo(cx - 13f * density, topY + 6f * density)
            lineTo(cx, topY + 13.5f * density)
            lineTo(cx + 13f * density, topY + 6f * density)
        }
        canvas.drawPath(lunaChevron, glowStrokePaint)

        cyanPaint.color = cornerColor
        cyanPaint.strokeWidth = 1.2f * density
        cyanPaint.alpha = (128 + (84 * cornerWave).toInt()).coerceIn(0, 255)
        canvas.drawLine(margin, topY + cornerLen * 0.45f, margin, topY, cyanPaint)
        canvas.drawLine(margin, topY, margin + cornerLen, topY, cyanPaint)
        canvas.drawLine(w - margin - cornerLen, topY, w - margin, topY, cyanPaint)
        canvas.drawLine(w - margin, topY, w - margin, topY + cornerLen * 0.45f, cyanPaint)
        canvas.drawPath(lunaChevron, cyanPaint)

        val dotR = 1.6f * density
        val dotGap = 6.5f * density
        val dotY = topY + 20f * density
        cyanFillPaint.color = cornerColor
        cyanFillPaint.alpha = (130 + (82 * cornerWave).toInt()).coerceIn(0, 255)
        canvas.drawCircle(cx - dotGap, dotY, dotR, cyanFillPaint)
        canvas.drawCircle(cx + dotGap, dotY, dotR, cyanFillPaint)
        cyanFillPaint.color = lunaViolet
        cyanFillPaint.alpha = (145 + (80 * cornerWave).toInt()).coerceIn(0, 255)
        canvas.drawCircle(cx, dotY, dotR, cyanFillPaint)

        // Slim side-drawer handle indicators (subtle electric blue-violet & restrained copper glint)
        val midY = h * 0.40f
        val railSpan = 36f * density
        val railColor = lerpColor(lunaBlue, lunaViolet, railWave * 0.52f)
        glowStrokePaint.color = railColor
        glowStrokePaint.strokeWidth = 3.6f * density
        glowStrokePaint.alpha = (20 + (36 * railWave).toInt()).coerceIn(0, 255)
        canvas.drawLine(5f * density, midY - railSpan, 5f * density, midY + railSpan, glowStrokePaint)
        canvas.drawLine(w - 5f * density, midY - railSpan, w - 5f * density, midY + railSpan, glowStrokePaint)

        electricBluePaint.color = railColor
        electricBluePaint.strokeWidth = 1.5f * density
        electricBluePaint.alpha = (128 + (86 * railWave).toInt()).coerceIn(0, 255)
        canvas.drawLine(5f * density, midY - railSpan, 5f * density, midY + railSpan, electricBluePaint)
        canvas.drawLine(w - 5f * density, midY - railSpan, w - 5f * density, midY + railSpan, electricBluePaint)

        amberPaint.color = Color.parseColor("#C47A48")
        amberPaint.strokeWidth = 1.6f * density
        amberPaint.alpha = (145 + (68 * railWave).toInt()).coerceIn(0, 255)
        canvas.drawLine(8.5f * density, midY - 12f * density, 8.5f * density, midY - 2f * density, amberPaint)
        canvas.drawLine(w - 8.5f * density, midY + 2f * density, w - 8.5f * density, midY + 12f * density, amberPaint)

        // Discreet bottom horizon hairline
        cyanPaint.color = lerpColor(lunaCyan, lunaViolet, horizonWave * 0.4f)
        cyanPaint.alpha = (54 + (62 * horizonWave).toInt()).coerceIn(0, 255)
        cyanPaint.strokeWidth = 1.0f * density
        canvas.drawLine(margin + 18f * density, bottomY, w - margin - 18f * density, bottomY, cyanPaint)
    }

    private fun drawTopOrnamentalHeader(canvas: Canvas, w: Float) {
        val topY = paddingTop + 6f * density
        val margin = 18f * density

        cyanPaint.color = Color.parseColor("#22D3EE")
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
        amberPaint.color = Color.parseColor("#F59E0B")
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
        cyanFillPaint.color = Color.parseColor("#22D3EE")
        drawThreeDots(canvas, cx, topY + 21f * density)
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
        amberPaint.color = Color.parseColor("#F59E0B")
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
