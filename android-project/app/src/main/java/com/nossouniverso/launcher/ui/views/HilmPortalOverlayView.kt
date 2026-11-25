package com.nossouniverso.launcher.ui.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import com.nossouniverso.launcher.model.HilmTransitionConfig
import com.nossouniverso.launcher.model.HilmTransitionEffect
import java.lang.ref.WeakReference
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders the single-shot HILM launch transition centered on the tapped icon coordinates,
 * connecting icon feedback to Android's native window reveal without continuous background loops.
 */
class HilmPortalOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class DigitalMote(
        val angleRad: Float,
        val distanceFactor: Float,
        val radiusDp: Float,
        val color: Int
    )

    private val density = resources.displayMetrics.density
    private val easeOutQuint = PathInterpolator(0.16f, 1f, 0.3f, 1f)

    private var animator: ValueAnimator? = null
    private var progress: Float = 0f
    private var activeEffect: HilmTransitionEffect = HilmTransitionEffect.PORTAL
    private var launchDispatched: Boolean = false
    private var wasCancelled: Boolean = false
    var lunaThemeMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                wellShader = null
                wellShaderRadius = -1f
                applyThemePaletteToPaints()
            }
        }

    private var onLaunchTrigger: (() -> Unit)? = null
    private var onFinishedCallback: (() -> Unit)? = null
    private var hiddenOriginRef: WeakReference<View>? = null

    private fun applyThemePaletteToPaints() {
        cyanRingPaint.color = Color.parseColor(if (lunaThemeMode) "#00E5FF" else "#22D3EE")
        cyanFinePaint.color = Color.parseColor(if (lunaThemeMode) "#D9F7FF" else "#A5F3FC")
        orangeRingPaint.color = Color.parseColor(if (lunaThemeMode) "#398BFF" else "#FB923C")
        amberTickPaint.color = Color.parseColor(if (lunaThemeMode) "#FFB45C" else "#F59E0B")
    }

    private var originX: Float = 0f
    private var originY: Float = 0f
    private var baseRadius: Float = 24f * density
    private var maxExpandRadius: Float = 240f * density

    private var iconBitmap: Bitmap? = null
    private var wellShader: RadialGradient? = null
    private var wellShaderRadius: Float = -1f

    // Pre-allocated single-shot particle descriptors (zero allocation in onDraw)
    private val digitalMotes = Array(22) { index ->
        val angle = ((index * 137.5f) % 360f) * (Math.PI.toFloat() / 180f)
        val dist = 0.38f + (index % 6) * 0.14f
        val radiusDp = 1.6f + (index % 3) * 0.75f
        val col = when (index % 3) {
            0 -> Color.parseColor("#22D3EE")
            1 -> Color.parseColor("#8B5CF6")
            else -> Color.parseColor("#FB923C")
        }
        DigitalMote(angle, dist, radiusDp, col)
    }

    private val wellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val cyanRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#22D3EE")
    }
    private val cyanFinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#A5F3FC")
    }
    private val orangeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FB923C")
    }
    private val amberTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#F59E0B")
    }
    private val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val motePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val arcRect = RectF()
    private val iconDestRect = RectF()
    private val iconSliceSrc = Rect()
    private val iconSliceDest = RectF()
    private val frameRect = RectF()
    private val selfLocation = IntArray(2)
    private val targetLocation = IntArray(2)
    private val shaderMatrix = Matrix()
    private val camera3d = Camera()
    private val matrix3d = Matrix()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    fun playPortalLaunch(
        originView: View?,
        iconDrawable: Drawable?,
        onLaunch: () -> Unit,
        onFinished: (() -> Unit)? = null
    ) {
        playLaunchTransition(
            originView = originView,
            iconDrawable = iconDrawable,
            config = HilmTransitionConfig(),
            onLaunch = onLaunch,
            onFinished = onFinished
        )
    }

    fun playLaunchTransition(
        originView: View?,
        iconDrawable: Drawable?,
        config: HilmTransitionConfig,
        onLaunch: () -> Unit,
        onFinished: (() -> Unit)? = null
    ) {
        cancelPortal()
        val clean = config.sanitized()
        activeEffect = clean.effect

        if (!HilmTransitionAnimator.shouldAnimate(context, clean) ||
            originView == null ||
            width <= 0 ||
            height <= 0
        ) {
            onLaunch()
            onFinished?.invoke()
            return
        }

        getLocationInWindow(selfLocation)
        originView.getLocationInWindow(targetLocation)

        val contentW = (originView.width - originView.paddingLeft - originView.paddingRight)
            .takeIf { it > 0 } ?: originView.width
        val contentH = (originView.height - originView.paddingTop - originView.paddingBottom)
            .takeIf { it > 0 } ?: originView.height
        val viewW = max(contentW, (28 * density).toInt()).toFloat()
        val viewH = max(contentH, (28 * density).toInt()).toFloat()
        originX = (targetLocation[0] - selfLocation[0]) +
            originView.paddingLeft + contentW / 2f
        originY = (targetLocation[1] - selfLocation[1]) +
            originView.paddingTop + contentH / 2f
        baseRadius = (min(viewW, viewH) * 0.56f).coerceIn(16f * density, 44f * density)

        val maxDistX = max(originX, width - originX)
        val maxDistY = max(originY, height - originY)
        maxExpandRadius = (hypot(maxDistX.toDouble(), maxDistY.toDouble()).toFloat() * 0.44f)
            .coerceIn(150f * density, 320f * density)

        iconBitmap = renderIconSnapshot(iconDrawable, (baseRadius * 2.4f).toInt().coerceAtLeast(72))
        if (iconBitmap != null) {
            hiddenOriginRef = WeakReference(originView)
            originView.alpha = 0f
        }
        wellShader = null
        wellShaderRadius = -1f

        launchDispatched = false
        wasCancelled = false
        progress = 0f
        onLaunchTrigger = onLaunch
        onFinishedCallback = onFinished
        visibility = VISIBLE
        bringToFront()

        val launchThreshold = when (clean.effect) {
            HilmTransitionEffect.CLASSIC -> 0.76f
            else -> 0.85f
        }
        val animDuration = if (clean.effect == HilmTransitionEffect.CLASSIC) {
            clean.durationMs.toLong().coerceIn(165L, 235L)
        } else {
            clean.durationMs.toLong()
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animDuration
            addUpdateListener { anim ->
                val raw = anim.animatedValue as Float
                progress = raw
                if (!wasCancelled && !launchDispatched && raw >= launchThreshold) {
                    launchDispatched = true
                    onLaunchTrigger?.invoke()
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (wasCancelled) {
                        cleanupVisuals()
                        return
                    }
                    if (!launchDispatched) {
                        launchDispatched = true
                        onLaunchTrigger?.invoke()
                    }
                    val finished = onFinishedCallback
                    cleanupVisuals()
                    finished?.invoke()
                }

                override fun onAnimationCancel(animation: Animator) {
                    wasCancelled = true
                    cleanupVisuals()
                }
            })
            start()
        }
    }

    fun cancelPortal() {
        wasCancelled = true
        val current = animator
        animator = null
        if (current != null && current.isRunning) {
            current.cancel()
        }
        cleanupVisuals()
    }

    private fun cleanupVisuals() {
        hiddenOriginRef?.get()?.let { origin ->
            origin.alpha = 1f
        }
        hiddenOriginRef = null
        onLaunchTrigger = null
        onFinishedCallback = null
        launchDispatched = false
        progress = 0f
        iconBitmap?.let { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        iconBitmap = null
        wellShader = null
        wellShaderRadius = -1f
        visibility = GONE
    }

    override fun onDetachedFromWindow() {
        cancelPortal()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (visibility != VISIBLE || progress <= 0f) return

        val eased = easeOutQuint.getInterpolation(progress.coerceIn(0f, 1f))
        val fadeOut = if (progress < 0.32f) {
            (progress / 0.14f).coerceIn(0.25f, 1f)
        } else {
            (1f - ((progress - 0.32f) / 0.68f)).coerceIn(0f, 1f)
        }

        when (activeEffect) {
            HilmTransitionEffect.PORTAL -> drawPortalEffect(canvas, eased, fadeOut)
            HilmTransitionEffect.DIGITAL_DISINTEGRATION -> drawDisintegrationEffect(canvas, eased, fadeOut)
            HilmTransitionEffect.LIGHT_SWEEP -> drawLightSweepEffect(canvas, eased, fadeOut)
            HilmTransitionEffect.DIMENSIONAL_SLIDE -> drawDimensionalEffect(canvas, eased, fadeOut)
            HilmTransitionEffect.ENERGY_DISSOLVE -> drawEnergyDissolveEffect(canvas, eased, fadeOut)
            HilmTransitionEffect.CLASSIC -> drawClassicIconEffect(canvas, eased)
        }
    }

    private fun drawPortalEffect(canvas: Canvas, eased: Float, fadeOut: Float) {
        val wellRadius = baseRadius * 0.85f + maxExpandRadius * 0.92f * eased
        drawWell(canvas, wellRadius, fadeOut)

        val rimRadius = baseRadius * 0.78f + maxExpandRadius * 0.78f * eased
        glowRimPaint.strokeWidth = (2.4f - 1.2f * eased) * density
        glowRimPaint.color = Color.argb((125 * fadeOut).toInt().coerceIn(0, 255), 34, 211, 238)
        canvas.drawCircle(originX, originY, rimRadius, glowRimPaint)

        val cyanR = baseRadius * 0.88f + maxExpandRadius * 0.86f * eased
        arcRect.set(originX - cyanR, originY - cyanR, originX + cyanR, originY + cyanR)
        cyanRingPaint.strokeWidth = (2.2f - 0.9f * eased) * density
        cyanRingPaint.alpha = (188 * fadeOut).toInt().coerceIn(0, 255)
        val cwAngle = -18f + 86f * eased
        canvas.drawArc(arcRect, cwAngle, 86f, false, cyanRingPaint)
        canvas.drawArc(arcRect, cwAngle + 128f, 74f, false, cyanRingPaint)
        canvas.drawArc(arcRect, cwAngle + 246f, 64f, false, cyanRingPaint)

        val cyanFineR = cyanR * 0.88f
        arcRect.set(originX - cyanFineR, originY - cyanFineR, originX + cyanFineR, originY + cyanFineR)
        cyanFinePaint.strokeWidth = 1.0f * density
        cyanFinePaint.alpha = (110 * fadeOut).toInt().coerceIn(0, 255)
        canvas.drawArc(arcRect, cwAngle + 40f, 44f, false, cyanFinePaint)
        canvas.drawArc(arcRect, cwAngle + 220f, 44f, false, cyanFinePaint)

        val orangeR = baseRadius * 0.72f + maxExpandRadius * 0.64f * eased
        arcRect.set(originX - orangeR, originY - orangeR, originX + orangeR, originY + orangeR)
        orangeRingPaint.strokeWidth = (2.0f - 0.7f * eased) * density
        orangeRingPaint.alpha = (178 * fadeOut).toInt().coerceIn(0, 255)
        val ccwAngle = 20f - 104f * eased
        canvas.drawArc(arcRect, ccwAngle, 72f, false, orangeRingPaint)
        canvas.drawArc(arcRect, ccwAngle + 180f, 72f, false, orangeRingPaint)

        val tickR = orangeR * 0.82f
        arcRect.set(originX - tickR, originY - tickR, originX + tickR, originY + tickR)
        amberTickPaint.strokeWidth = 1.15f * density
        amberTickPaint.alpha = (135 * fadeOut).toInt().coerceIn(0, 255)
        canvas.drawArc(arcRect, ccwAngle + 108f, 30f, false, amberTickPaint)
        canvas.drawArc(arcRect, ccwAngle + 288f, 30f, false, amberTickPaint)

        if (progress < 0.72f) {
            val flareFade = (1f - progress / 0.72f).coerceIn(0f, 1f)
            val span = baseRadius * (1.25f + 2.45f * eased)
            flarePaint.strokeWidth = 1.6f * density
            flarePaint.color = Color.argb((115 * flareFade).toInt().coerceIn(0, 255), 34, 211, 238)
            canvas.drawLine(originX - span, originY, originX, originY, flarePaint)
            flarePaint.color = Color.argb((115 * flareFade).toInt().coerceIn(0, 255), 251, 146, 60)
            canvas.drawLine(originX, originY, originX + span, originY, flarePaint)
        }

        drawSourceIcon(canvas, scaleBoost = 0.50f)
    }

    private fun drawDisintegrationEffect(canvas: Canvas, eased: Float, fadeOut: Float) {
        val wellRadius = baseRadius * 0.9f + maxExpandRadius * 0.58f * eased
        drawWell(canvas, wellRadius, fadeOut * 0.65f)

        val spread = baseRadius * 0.6f + maxExpandRadius * 0.72f * eased
        digitalMotes.forEachIndexed { idx, mote ->
            val r = spread * mote.distanceFactor
            val mx = originX + cos(mote.angleRad) * r
            val my = originY + sin(mote.angleRad) * r
            motePaint.color = if (lunaThemeMode) {
                when (idx % 4) {
                    0 -> Color.parseColor("#00E5FF")
                    1 -> Color.parseColor("#398BFF")
                    2 -> Color.parseColor("#D9F7FF")
                    else -> Color.parseColor("#FFB45C")
                }
            } else {
                mote.color
            }
            motePaint.alpha = (230 * fadeOut).toInt().coerceIn(0, 255)
            val dotR = mote.radiusDp * density * (1f - 0.35f * eased)
            canvas.drawCircle(mx, my, dotR, motePaint)
        }

        drawSourceIcon(canvas, scaleBoost = 0.28f)
    }

    private fun drawLightSweepEffect(canvas: Canvas, eased: Float, fadeOut: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val beamX = w * eased
        val beamW = 54f * density

        sweepPaint.shader = LinearGradient(
            beamX - beamW,
            0f,
            beamX + beamW,
            0f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb((165 * fadeOut).toInt().coerceIn(0, 255), 34, 211, 238),
                Color.argb((130 * fadeOut).toInt().coerceIn(0, 255), 251, 146, 60),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(beamX - beamW, 0f, beamX + beamW, h, sweepPaint)

        val span = baseRadius * (1.4f + 3.2f * eased)
        flarePaint.strokeWidth = 2.0f * density
        flarePaint.color = Color.argb((145 * fadeOut).toInt().coerceIn(0, 255), 34, 211, 238)
        canvas.drawLine(originX - span, originY, originX + span, originY, flarePaint)

        drawSourceIcon(canvas, scaleBoost = 0.24f)
    }

    private fun drawDimensionalEffect(canvas: Canvas, eased: Float, fadeOut: Float) {
        val halfW = baseRadius + (width * 0.36f) * eased
        val halfH = baseRadius + (height * 0.24f) * eased
        val tiltShift = (1f - eased) * 18f * density

        frameRect.set(
            originX - halfW + tiltShift,
            originY - halfH - tiltShift * 0.45f,
            originX + halfW + tiltShift,
            originY + halfH - tiltShift * 0.45f
        )
        framePaint.strokeWidth = 2.1f * density
        framePaint.color = Color.argb((190 * fadeOut).toInt().coerceIn(0, 255), 34, 211, 238)
        canvas.drawRoundRect(frameRect, 18f * density, 18f * density, framePaint)

        frameRect.inset(8f * density, 8f * density)
        framePaint.strokeWidth = 1.4f * density
        framePaint.color = Color.argb((150 * fadeOut).toInt().coerceIn(0, 255), 251, 146, 60)
        canvas.drawRoundRect(frameRect, 14f * density, 14f * density, framePaint)

        drawSourceIcon(canvas, scaleBoost = 0.44f)
    }

    private fun drawEnergyDissolveEffect(canvas: Canvas, eased: Float, fadeOut: Float) {
        val wellRadius = baseRadius * 0.95f + maxExpandRadius * 0.85f * eased
        drawWell(canvas, wellRadius, fadeOut * 0.85f)

        val waveR = baseRadius * 0.85f + maxExpandRadius * 0.82f * eased
        glowRimPaint.strokeWidth = 2.6f * density
        glowRimPaint.color = Color.argb((155 * fadeOut).toInt().coerceIn(0, 255), 139, 92, 246)
        canvas.drawCircle(originX, originY, waveR, glowRimPaint)

        glowRimPaint.strokeWidth = 1.6f * density
        glowRimPaint.color = Color.argb((150 * fadeOut).toInt().coerceIn(0, 255), 34, 211, 238)
        canvas.drawCircle(originX, originY, waveR * 0.74f, glowRimPaint)

        drawSourceIcon(canvas, scaleBoost = 0.34f)
    }

    private fun drawClassicIconEffect(canvas: Canvas, eased: Float) {
        glowRimPaint.strokeWidth = 1.4f * density
        glowRimPaint.color = Color.argb(((1f - eased) * 110f).toInt().coerceIn(0, 255), 34, 211, 238)
        canvas.drawCircle(originX, originY, baseRadius * (1.0f + 0.55f * eased), glowRimPaint)
        drawSourceIcon(canvas, scaleBoost = 0.28f)
    }

    private fun drawWell(canvas: Canvas, wellRadius: Float, fadeOut: Float) {
        if (wellRadius <= 2f) return
        ensureWellShader(wellRadius)
        wellShader?.let { shader ->
            shaderMatrix.reset()
            val scale = wellRadius / wellShaderRadius
            shaderMatrix.setScale(scale, scale)
            shaderMatrix.postTranslate(originX, originY)
            shader.setLocalMatrix(shaderMatrix)
            wellPaint.shader = shader
            wellPaint.alpha = (150 * fadeOut).toInt().coerceIn(0, 255)
            canvas.drawCircle(originX, originY, wellRadius, wellPaint)
        }
    }

    private fun drawSourceIcon(canvas: Canvas, scaleBoost: Float) {
        val bmp = iconBitmap ?: return
        if (bmp.isRecycled) return

        val maxProgress = when (activeEffect) {
            HilmTransitionEffect.CLASSIC -> 0.84f
            else -> 0.92f
        }
        if (progress >= maxProgress) return

        val iconT = (progress / maxProgress).coerceIn(0f, 1f)
        val iconEased = easeOutQuint.getInterpolation(iconT)
        val effectiveBoost = when (activeEffect) {
            HilmTransitionEffect.PORTAL -> max(scaleBoost, 0.85f)
            HilmTransitionEffect.DIMENSIONAL_SLIDE -> max(scaleBoost, 0.56f)
            HilmTransitionEffect.ENERGY_DISSOLVE -> max(scaleBoost, 0.55f)
            HilmTransitionEffect.DIGITAL_DISINTEGRATION -> max(scaleBoost, 0.36f)
            else -> scaleBoost
        }
        val iconScale = 1f + effectiveBoost * iconEased
        val iconAlpha = if (iconT < 0.42f) {
            1f
        } else {
            (1f - ((iconT - 0.42f) / 0.58f)).coerceIn(0f, 1f)
        }
        val half = baseRadius * 0.96f * iconScale
        val alphaInt = (255 * iconAlpha).toInt().coerceIn(0, 255)

        when (activeEffect) {
            HilmTransitionEffect.PORTAL -> {
                canvas.save()
                canvas.rotate(28f * iconEased, originX, originY)
                iconDestRect.set(originX - half, originY - half, originX + half, originY + half)
                iconPaint.alpha = alphaInt
                canvas.drawBitmap(bmp, null, iconDestRect, iconPaint)
                canvas.restore()
            }
            HilmTransitionEffect.DIGITAL_DISINTEGRATION -> {
                val sliceCount = 6
                val srcSliceH = max(1, bmp.height / sliceCount)
                val destSliceH = (half * 2f) / sliceCount
                iconPaint.alpha = alphaInt
                for (i in 0 until sliceCount) {
                    val srcTop = i * srcSliceH
                    val srcBottom = if (i == sliceCount - 1) bmp.height else (i + 1) * srcSliceH
                    val driftX = (if (i % 2 == 0) -1f else 1f) * (14f + i * 2.5f) * density * iconEased
                    val driftY = (i - (sliceCount - 1) / 2f) * 3.5f * density * iconEased
                    iconSliceSrc.set(0, srcTop, bmp.width, srcBottom)
                    iconSliceDest.set(
                        originX - half + driftX,
                        originY - half + i * destSliceH + driftY,
                        originX + half + driftX,
                        originY - half + (i + 1) * destSliceH + driftY
                    )
                    canvas.drawBitmap(bmp, iconSliceSrc, iconSliceDest, iconPaint)
                }
            }
            HilmTransitionEffect.LIGHT_SWEEP -> {
                val left = originX - half
                val right = originX + half
                val top = originY - half
                val bottom = originY + half
                val wipeX = left + (right - left) * (1f - iconEased * 0.94f)
                canvas.save()
                canvas.clipRect(left, top, wipeX, bottom)
                iconDestRect.set(left, top, right, bottom)
                iconPaint.alpha = alphaInt
                canvas.drawBitmap(bmp, null, iconDestRect, iconPaint)
                canvas.restore()

                flarePaint.strokeWidth = 3.0f * density
                flarePaint.color = Color.argb((245 * (1f - iconT * 0.7f)).toInt().coerceIn(0, 255), 165, 243, 252)
                canvas.drawLine(wipeX, top - 6f * density, wipeX, bottom + 6f * density, flarePaint)
                flarePaint.strokeWidth = 1.6f * density
                flarePaint.color = Color.argb((210 * (1f - iconT * 0.7f)).toInt().coerceIn(0, 255), 251, 146, 60)
                canvas.drawLine(wipeX + 2.5f * density, top - 4f * density, wipeX + 2.5f * density, bottom + 4f * density, flarePaint)
            }
            HilmTransitionEffect.DIMENSIONAL_SLIDE -> {
                canvas.save()
                camera3d.save()
                camera3d.rotateY(-46f * iconEased)
                camera3d.rotateX(22f * iconEased)
                camera3d.getMatrix(matrix3d)
                camera3d.restore()
                matrix3d.preTranslate(-originX, -originY)
                matrix3d.postTranslate(originX, originY - 16f * density * iconEased)
                canvas.concat(matrix3d)
                iconDestRect.set(originX - half, originY - half, originX + half, originY + half)
                iconPaint.alpha = alphaInt
                canvas.drawBitmap(bmp, null, iconDestRect, iconPaint)
                canvas.restore()
            }
            HilmTransitionEffect.ENERGY_DISSOLVE -> {
                glowRimPaint.strokeWidth = 3.4f * density
                glowRimPaint.color = Color.argb((220 * (1f - iconT * 0.65f)).toInt().coerceIn(0, 255), 34, 211, 238)
                canvas.drawCircle(originX, originY, half * 1.14f, glowRimPaint)
                glowRimPaint.strokeWidth = 2.2f * density
                glowRimPaint.color = Color.argb((195 * (1f - iconT * 0.65f)).toInt().coerceIn(0, 255), 168, 85, 247)
                canvas.drawCircle(originX, originY, half * 1.38f, glowRimPaint)
                iconDestRect.set(originX - half, originY - half, originX + half, originY + half)
                iconPaint.alpha = alphaInt
                canvas.drawBitmap(bmp, null, iconDestRect, iconPaint)
            }
            HilmTransitionEffect.CLASSIC -> {
                iconDestRect.set(originX - half, originY - half, originX + half, originY + half)
                iconPaint.alpha = alphaInt
                canvas.drawBitmap(bmp, null, iconDestRect, iconPaint)
            }
        }
    }

    private fun ensureWellShader(radius: Float) {
        if (wellShader != null && wellShaderRadius > 0f) return
        val unitR = max(radius, 80f * density)
        wellShaderRadius = unitR
        wellShader = if (lunaThemeMode) {
            RadialGradient(
                0f,
                0f,
                unitR,
                intArrayOf(
                    Color.argb(225, 5, 7, 11),
                    Color.argb(180, 10, 20, 36),
                    Color.argb(82, 0, 229, 255),
                    Color.argb(48, 57, 139, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.42f, 0.68f, 0.84f, 1f),
                Shader.TileMode.CLAMP
            )
        } else {
            RadialGradient(
                0f,
                0f,
                unitR,
                intArrayOf(
                    Color.argb(220, 3, 9, 20),
                    Color.argb(175, 7, 22, 38),
                    Color.argb(72, 34, 211, 238),
                    Color.argb(46, 251, 146, 60),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.42f, 0.68f, 0.84f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    private fun renderIconSnapshot(drawable: Drawable?, sizePx: Int): Bitmap? {
        if (drawable == null) return null
        return try {
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val oldBounds = android.graphics.Rect(drawable.bounds)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(c)
            drawable.bounds = oldBounds
            bmp
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun isReducedMotionEnabled(context: Context): Boolean {
            return HilmTransitionAnimator.isSystemReducedMotionEnabled(context)
        }

        fun buildClipRevealOptions(context: Context, originView: View?): Bundle? {
            if (isReducedMotionEnabled(context) || originView == null) return null
            if (originView.width <= 0 || originView.height <= 0) return null
            return try {
                ActivityOptions.makeClipRevealAnimation(
                    originView,
                    0,
                    0,
                    originView.width,
                    originView.height
                ).toBundle()
            } catch (_: Exception) {
                null
            }
        }

        fun buildLaunchActivityOptions(
            context: Context,
            originView: View?,
            config: HilmTransitionConfig
        ): Bundle? {
            if (!HilmTransitionAnimator.shouldAnimate(context, config) || originView == null) {
                return null
            }
            if (originView.width <= 0 || originView.height <= 0) return null
            return try {
                when (config.effect) {
                    HilmTransitionEffect.PORTAL,
                    HilmTransitionEffect.DIMENSIONAL_SLIDE,
                    HilmTransitionEffect.LIGHT_SWEEP -> {
                        ActivityOptions.makeClipRevealAnimation(
                            originView,
                            0,
                            0,
                            originView.width,
                            originView.height
                        ).toBundle()
                    }
                    HilmTransitionEffect.DIGITAL_DISINTEGRATION,
                    HilmTransitionEffect.ENERGY_DISSOLVE,
                    HilmTransitionEffect.CLASSIC -> {
                        ActivityOptions.makeScaleUpAnimation(
                            originView,
                            0,
                            0,
                            originView.width,
                            originView.height
                        ).toBundle()
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
