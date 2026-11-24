package com.nossouniverso.launcher.ui.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import com.nossouniverso.launcher.model.HilmTransitionConfig
import com.nossouniverso.launcher.model.HilmTransitionEffect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compact interactive Canvas preview stage inside HILM Settings.
 * Plays a single-shot transition demonstration on demand without any continuous background loops.
 */
class TransitionPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class PreviewMote(
        val angleRad: Float,
        val distanceFactor: Float,
        val radiusDp: Float,
        val color: Int
    )

    private val density = resources.displayMetrics.density
    private val easeOutQuint = PathInterpolator(0.16f, 1f, 0.3f, 1f)

    private var activeConfig: HilmTransitionConfig = HilmTransitionConfig()
    private var animator: ValueAnimator? = null
    private var progress: Float = 1f

    private val motes = Array(18) { index ->
        val angle = ((index * 137.5f) % 360f) * (Math.PI.toFloat() / 180f)
        val dist = 0.25f + (index % 5) * 0.16f
        val r = 1.4f + (index % 3) * 0.7f
        val col = when (index % 3) {
            0 -> Color.parseColor("#22D3EE")
            1 -> Color.parseColor("#8B5CF6")
            else -> Color.parseColor("#FB923C")
        }
        PreviewMote(angle, dist, r, col)
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A121F")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = Color.argb(110, 34, 211, 238)
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111D30")
    }
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
        color = Color.argb(170, 34, 211, 238)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22D3EE")
    }
    private val orangeAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FB923C")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        textSize = 11f * density
        textAlign = Paint.Align.LEFT
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val motePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    private val cardRect = RectF()
    private val clipPath = Path()

    fun playPreview(config: HilmTransitionConfig) {
        stopPreview()
        activeConfig = config.sanitized()
        if (!HilmTransitionAnimator.shouldAnimate(context, activeConfig)) {
            progress = 1f
            invalidate()
            return
        }

        progress = 0f
        invalidate()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = activeConfig.durationMs.toLong()
            addUpdateListener { anim ->
                progress = anim.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    progress = 1f
                    animator = null
                    invalidate()
                }

                override fun onAnimationCancel(animation: Animator) {
                    animator = null
                }
            })
            start()
        }
    }

    fun stopPreview() {
        val current = animator
        animator = null
        if (current != null && current.isRunning) {
            current.cancel()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) {
            stopPreview()
        }
    }

    override fun onDetachedFromWindow() {
        stopPreview()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val corner = 14f * density
        rectF.set(1f * density, 1f * density, w - 1f * density, h - 1f * density)
        canvas.drawRoundRect(rectF, corner, corner, bgPaint)
        canvas.drawRoundRect(rectF, corner, corner, borderPaint)

        val t = easeOutQuint.getInterpolation(progress.coerceIn(0f, 1f))
        val cx = w / 2f
        val cy = h / 2f

        val baseCardW = w * 0.68f
        val baseCardH = h * 0.62f
        var scaleX = 1f
        var scaleY = 1f
        var translateX = 0f
        var translateY = 0f
        var alpha = 1f

        if (HilmTransitionAnimator.shouldAnimate(context, activeConfig)) {
            when (activeConfig.effect) {
                HilmTransitionEffect.PORTAL -> {
                    scaleX = 0.72f + 0.28f * t
                    scaleY = 0.72f + 0.28f * t
                    alpha = t
                }
                HilmTransitionEffect.DIGITAL_DISINTEGRATION -> {
                    scaleX = 0.88f + 0.12f * t
                    scaleY = 0.94f + 0.06f * t
                    alpha = t
                }
                HilmTransitionEffect.LIGHT_SWEEP -> {
                    translateX = (1f - t) * -18f * density
                    alpha = 0.25f + 0.75f * t
                }
                HilmTransitionEffect.DIMENSIONAL_SLIDE -> {
                    scaleX = 0.82f + 0.18f * t
                    scaleY = 0.90f + 0.10f * t
                    translateX = (1f - t) * 44f * density
                    translateY = (1f - t) * 14f * density
                    alpha = t
                }
                HilmTransitionEffect.ENERGY_DISSOLVE -> {
                    scaleX = 0.94f + 0.06f * t
                    scaleY = 0.94f + 0.06f * t
                    alpha = t
                }
                HilmTransitionEffect.CLASSIC -> {
                    translateY = (1f - t) * 16f * density
                    alpha = t
                }
            }
        }

        canvas.save()
        if (activeConfig.effect == HilmTransitionEffect.LIGHT_SWEEP &&
            HilmTransitionAnimator.shouldAnimate(context, activeConfig) &&
            progress < 1f
        ) {
            val revealRight = w * t
            clipPath.reset()
            clipPath.addRect(0f, 0f, revealRight, h, Path.Direction.CW)
            canvas.clipPath(clipPath)
        }

        canvas.translate(cx + translateX, cy + translateY)
        canvas.scale(scaleX, scaleY)

        cardRect.set(-baseCardW / 2f, -baseCardH / 2f, baseCardW / 2f, baseCardH / 2f)
        cardPaint.alpha = (235 * alpha).toInt().coerceIn(0, 255)
        cardStrokePaint.alpha = (185 * alpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(cardRect, 11f * density, 11f * density, cardPaint)
        canvas.drawRoundRect(cardRect, 11f * density, 11f * density, cardStrokePaint)

        // Inner HUD mockup bars & emblem
        accentPaint.alpha = (220 * alpha).toInt().coerceIn(0, 255)
        orangeAccentPaint.alpha = (210 * alpha).toInt().coerceIn(0, 255)
        val iconCx = cardRect.left + 26f * density
        val iconCy = cardRect.centerY()
        canvas.drawCircle(iconCx, iconCy, 13f * density, accentPaint)
        canvas.drawCircle(iconCx, iconCy, 6f * density, orangeAccentPaint)

        textPaint.alpha = (245 * alpha).toInt().coerceIn(0, 255)
        val label = if (!activeConfig.enabled) {
            "Efeitos desativados (instantâneo)"
        } else {
            "${activeConfig.effect.title} · ${activeConfig.durationMs} ms"
        }
        canvas.drawText(label, iconCx + 22f * density, iconCy - 3f * density, textPaint)

        val barLeft = iconCx + 22f * density
        val barTop = iconCy + 6f * density
        canvas.drawRoundRect(
            barLeft,
            barTop,
            cardRect.right - 16f * density,
            barTop + 4f * density,
            2f * density,
            2f * density,
            accentPaint
        )
        canvas.restore()

        // Effect-specific overlay accents during single-shot playback
        if (progress in 0.01f..0.98f && HilmTransitionAnimator.shouldAnimate(context, activeConfig)) {
            val fade = (1f - progress).coerceIn(0f, 1f)
            when (activeConfig.effect) {
                HilmTransitionEffect.PORTAL -> {
                    val r1 = 22f * density + 58f * density * t
                    ringPaint.strokeWidth = 2f * density
                    ringPaint.color = Color.argb((190 * fade).toInt(), 34, 211, 238)
                    canvas.drawCircle(cx, cy, r1, ringPaint)
                    ringPaint.color = Color.argb((170 * fade).toInt(), 251, 146, 60)
                    canvas.drawCircle(cx, cy, r1 * 0.72f, ringPaint)
                }
                HilmTransitionEffect.DIGITAL_DISINTEGRATION -> {
                    val spread = 56f * density * (1f - t)
                    motes.forEach { m ->
                        val mx = cx + cos(m.angleRad) * (spread * m.distanceFactor + 28f * density)
                        val my = cy + sin(m.angleRad) * (spread * 0.55f * m.distanceFactor + 14f * density)
                        motePaint.color = m.color
                        motePaint.alpha = (220 * fade).toInt().coerceIn(0, 255)
                        canvas.drawCircle(mx, my, m.radiusDp * density, motePaint)
                    }
                }
                HilmTransitionEffect.LIGHT_SWEEP -> {
                    val beamX = w * t
                    val beamW = 34f * density
                    sweepPaint.shader = LinearGradient(
                        beamX - beamW,
                        0f,
                        beamX + beamW,
                        0f,
                        intArrayOf(
                            Color.TRANSPARENT,
                            Color.argb((180 * fade).toInt(), 34, 211, 238),
                            Color.argb((140 * fade).toInt(), 251, 146, 60),
                            Color.TRANSPARENT
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(beamX - beamW, 0f, beamX + beamW, h, sweepPaint)
                }
                HilmTransitionEffect.DIMENSIONAL_SLIDE -> {
                    ringPaint.strokeWidth = 1.6f * density
                    ringPaint.color = Color.argb((145 * fade).toInt(), 34, 211, 238)
                    val dx = (1f - t) * 28f * density
                    canvas.drawRoundRect(
                        cx - baseCardW * 0.5f + dx,
                        cy - baseCardH * 0.5f,
                        cx + baseCardW * 0.5f + dx,
                        cy + baseCardH * 0.5f,
                        11f * density,
                        11f * density,
                        ringPaint
                    )
                }
                HilmTransitionEffect.ENERGY_DISSOLVE -> {
                    ringPaint.strokeWidth = 2.4f * density
                    ringPaint.color = Color.argb((150 * fade).toInt(), 139, 92, 246)
                    canvas.drawCircle(cx, cy, 18f * density + 62f * density * t, ringPaint)
                }
                HilmTransitionEffect.CLASSIC -> Unit
            }
        }
    }
}
