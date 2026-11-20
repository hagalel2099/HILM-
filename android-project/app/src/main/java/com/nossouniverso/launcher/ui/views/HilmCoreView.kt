package com.nossouniverso.launcher.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.nossouniverso.launcher.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders the fixed central HILM nucleus (R.drawable.nucleo_hilm_core) and animates
 * Íris (copper orange), Luna (electric violet), and Maya (golden amber with honey
 * highlights) on three distinct inclined 3D Keplerian elliptical orbits with
 * continuous curved luminous tails, depth-driven scale, and foreground/background occlusion.
 */
class HilmCoreView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class KeplerOrbSpec(
        val phaseOffset: Float,
        val semiMajorRatio: Float,
        val eccentricity: Float,
        val inclinationDeg: Float,
        val yawDeg: Float,
        val rollDeg: Float,
        val periodMs: Float,
        val coreColor: Int,
        val midColor: Int,
        val edgeColor: Int,
        val haloColor: Int,
        val trailR: Int,
        val trailG: Int,
        val trailB: Int,
        val trailCoreR: Int,
        val trailCoreG: Int,
        val trailCoreB: Int
    ) {
        val minorFactor: Float = sqrt(1f - eccentricity * eccentricity)
        val cosI: Float = cos(Math.toRadians(inclinationDeg.toDouble())).toFloat()
        val sinI: Float = sin(Math.toRadians(inclinationDeg.toDouble())).toFloat()
        val cosY: Float = cos(Math.toRadians(yawDeg.toDouble())).toFloat()
        val sinY: Float = sin(Math.toRadians(yawDeg.toDouble())).toFloat()
        val cosR: Float = cos(Math.toRadians(rollDeg.toDouble())).toFloat()
        val sinR: Float = sin(Math.toRadians(rollDeg.toDouble())).toFloat()
    }

    private class ProjectedPoint {
        var x: Float = 0f
        var y: Float = 0f
        var z: Float = 0f
        var scale: Float = 1f
    }

    private val orbSpecs = listOf(
        // Íris: laranja cobre
        KeplerOrbSpec(
            phaseOffset = 0.20f,
            semiMajorRatio = 0.341f,
            eccentricity = 0.23f,
            inclinationDeg = 68f,
            yawDeg = 18f,
            rollDeg = -34f,
            periodMs = 9200f,
            coreColor = Color.parseColor("#FFF3E0"),
            midColor = Color.parseColor("#FB923C"),
            edgeColor = Color.parseColor("#C2410C"),
            haloColor = Color.argb(173, 234, 88, 12),
            trailR = 234,
            trailG = 88,
            trailB = 12,
            trailCoreR = 253,
            trailCoreG = 186,
            trailCoreB = 116
        ),
        // Luna: violeta elétrico
        KeplerOrbSpec(
            phaseOffset = 2.35f,
            semiMajorRatio = 0.336f,
            eccentricity = 0.21f,
            inclinationDeg = 72f,
            yawDeg = -22f,
            rollDeg = 32f,
            periodMs = 11300f,
            coreColor = Color.parseColor("#F5F3FF"),
            midColor = Color.parseColor("#A855F7"),
            edgeColor = Color.parseColor("#6D28D9"),
            haloColor = Color.argb(178, 139, 92, 246),
            trailR = 139,
            trailG = 92,
            trailB = 246,
            trailCoreR = 221,
            trailCoreG = 214,
            trailCoreB = 254
        ),
        // Maya: âmbar dourado com reflexos de mel
        KeplerOrbSpec(
            phaseOffset = 4.35f,
            semiMajorRatio = 0.332f,
            eccentricity = 0.20f,
            inclinationDeg = 66f,
            yawDeg = 10f,
            rollDeg = 5f,
            periodMs = 13400f,
            coreColor = Color.parseColor("#FFFBEB"),
            midColor = Color.parseColor("#FBBF24"),
            edgeColor = Color.parseColor("#D97706"),
            haloColor = Color.argb(168, 245, 158, 11),
            trailR = 245,
            trailG = 158,
            trailB = 11,
            trailCoreR = 254,
            trailCoreG = 240,
            trailCoreB = 138
        )
    )

    private var coreSourceBitmap: Bitmap? = null
    private var coreScaledBitmap: Bitmap? = null
    private val orbBitmaps = arrayOfNulls<Bitmap>(3)
    private val coreRect = RectF()
    private val orbDestRect = RectF()

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val trailSegments = 42
    private val trailArcMeanRad = 1.22f
    private val trailBuffer = Array(trailSegments + 1) { ProjectedPoint() }
    private val headDepths = FloatArray(3)
    private val sortedIndices = intArrayOf(0, 1, 2)

    private var startTimeMs: Long = SystemClock.uptimeMillis()
    private var currentElapsedMs: Long = 0L

    private val frameTicker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 60000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            currentElapsedMs = SystemClock.uptimeMillis() - startTimeMs
            invalidate()
        }
    }

    init {
        coreSourceBitmap = BitmapFactory.decodeResource(resources, R.drawable.nucleo_hilm_core)
        orbSpecs.forEachIndexed { index, spec ->
            orbBitmaps[index] = createOrbBitmap(spec)
        }
    }

    private fun createOrbBitmap(spec: KeplerOrbSpec): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val center = size / 2f
        val haloRadius = size * 0.48f
        val bodyRadius = size * 0.185f

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                center,
                center,
                haloRadius,
                intArrayOf(spec.haloColor, Color.TRANSPARENT),
                floatArrayOf(0.12f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(center, center, haloRadius, haloPaint)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                center - bodyRadius * 0.28f,
                center - bodyRadius * 0.28f,
                bodyRadius,
                intArrayOf(spec.coreColor, spec.midColor, spec.edgeColor),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(center, center, bodyRadius, bodyPaint)
        return bmp
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTimeMs = SystemClock.uptimeMillis()
        currentElapsedMs = 0L
        if (!isReducedMotionEnabled()) {
            if (!frameTicker.isStarted) {
                frameTicker.start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        frameTicker.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val src = coreSourceBitmap ?: return
        val minDim = min(w, h)
        if (minDim <= 0) return

        val coreDrawSize = (minDim * 0.486f).toInt().coerceAtLeast(1)
        coreScaledBitmap = Bitmap.createScaledBitmap(src, coreDrawSize, coreDrawSize, true)

        val cx = w / 2f
        val cy = h / 2f
        val half = coreDrawSize / 2f
        coreRect.set(cx - half, cy - half, cx + half, cy + half)
    }

    private fun projectKeplerOrbit(
        meanAnomaly: Float,
        spec: KeplerOrbSpec,
        cx: Float,
        cy: Float,
        minDim: Float,
        out: ProjectedPoint
    ) {
        val e = spec.eccentricity
        var eccAnom = meanAnomaly
        for (step in 0 until 4) {
            val f = eccAnom - e * sin(eccAnom) - meanAnomaly
            val fp = 1f - e * cos(eccAnom)
            eccAnom -= f / fp
        }

        val semiMajor = minDim * spec.semiMajorRatio
        val xOrb = semiMajor * (cos(eccAnom) - e)
        val yOrb = semiMajor * spec.minorFactor * sin(eccAnom)

        val x1 = xOrb
        val y1 = yOrb * spec.cosI
        val z1 = yOrb * spec.sinI

        val x2 = x1 * spec.cosY + z1 * spec.sinY
        val y2 = y1
        val z2 = -x1 * spec.sinY + z1 * spec.cosY

        val x3 = x2 * spec.cosR - y2 * spec.sinR
        val y3 = x2 * spec.sinR + y2 * spec.cosR

        val cameraDist = minDim * 0.911f
        val perspective = cameraDist / (cameraDist - z2)

        out.x = cx + x3 * perspective
        out.y = cy + y3 * perspective
        out.z = z2
        out.scale = perspective
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val minDim = min(w, h)
        if (minDim <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val baseOrbRadius = minDim * 0.0318f
        val twoPi = (2.0 * PI).toFloat()

        for (i in 0..2) {
            val spec = orbSpecs[i]
            val meanAnomaly = (currentElapsedMs / spec.periodMs) * twoPi + spec.phaseOffset
            projectKeplerOrbit(meanAnomaly, spec, cx, cy, minDim, trailBuffer[0])
            headDepths[i] = trailBuffer[0].z
            sortedIndices[i] = i
        }
        for (i in 0 until 2) {
            for (j in i + 1 until 3) {
                if (headDepths[sortedIndices[j]] < headDepths[sortedIndices[i]]) {
                    val tmp = sortedIndices[i]
                    sortedIndices[i] = sortedIndices[j]
                    sortedIndices[j] = tmp
                }
            }
        }

        // Pass 1: Background hemisphere (z < 0) occluded behind the central sphere
        for (slot in 0..2) {
            val idx = sortedIndices[slot]
            val spec = orbSpecs[idx]
            val meanAnomaly = (currentElapsedMs / spec.periodMs) * twoPi + spec.phaseOffset
            drawOrbAndCurvedTrail(canvas, idx, meanAnomaly, cx, cy, minDim, baseOrbRadius, drawFront = false)
        }

        // Pass 2: Fixed & stable central HILM nucleus
        coreScaledBitmap?.let { bmp ->
            bitmapPaint.alpha = 255
            canvas.drawBitmap(bmp, null, coreRect, bitmapPaint)
        }

        // Pass 3: Foreground hemisphere (z >= 0) sweeping in front of the central sphere
        for (slot in 0..2) {
            val idx = sortedIndices[slot]
            val spec = orbSpecs[idx]
            val meanAnomaly = (currentElapsedMs / spec.periodMs) * twoPi + spec.phaseOffset
            drawOrbAndCurvedTrail(canvas, idx, meanAnomaly, cx, cy, minDim, baseOrbRadius, drawFront = true)
        }
    }

    private fun drawOrbAndCurvedTrail(
        canvas: Canvas,
        index: Int,
        meanAnomaly: Float,
        cx: Float,
        cy: Float,
        minDim: Float,
        baseOrbRadius: Float,
        drawFront: Boolean
    ) {
        val spec = orbSpecs[index]
        for (i in 0..trailSegments) {
            val frac = i.toFloat() / trailSegments
            projectKeplerOrbit(
                meanAnomaly - frac * trailArcMeanRad,
                spec,
                cx,
                cy,
                minDim,
                trailBuffer[i]
            )
        }

        for (i in trailSegments - 1 downTo 0) {
            val p0 = trailBuffer[i]
            val p1 = trailBuffer[i + 1]
            val midZ = (p0.z + p1.z) * 0.5f
            val isFront = midZ >= 0f
            if (isFront != drawFront) continue

            val frac = (i + 0.5f) / trailSegments
            val taper = (1f - frac).pow(1.4f)
            val alphaFade = (1f - frac).pow(2.0f)
            val depthDim = if (isFront) 1f else 0.52f
            val avgScale = (p0.scale + p1.scale) * 0.5f

            trailPaint.strokeWidth = max(1f, baseOrbRadius * 1.41f * avgScale * taper)
            trailPaint.color = Color.argb(
                (112f * alphaFade * depthDim).toInt().coerceIn(0, 255),
                spec.trailR,
                spec.trailG,
                spec.trailB
            )
            canvas.drawLine(p0.x, p0.y, p1.x, p1.y, trailPaint)

            trailPaint.strokeWidth = max(0.8f, baseOrbRadius * 0.56f * avgScale * taper)
            trailPaint.color = Color.argb(
                (204f * alphaFade * depthDim).toInt().coerceIn(0, 255),
                spec.trailCoreR,
                spec.trailCoreG,
                spec.trailCoreB
            )
            canvas.drawLine(p0.x, p0.y, p1.x, p1.y, trailPaint)
        }

        val head = trailBuffer[0]
        val isFront = head.z >= 0f
        if (isFront != drawFront) return

        val bmp = orbBitmaps[index] ?: return
        val halfDrawSize = baseOrbRadius * 2.55f * head.scale
        orbDestRect.set(
            head.x - halfDrawSize,
            head.y - halfDrawSize,
            head.x + halfDrawSize,
            head.y + halfDrawSize
        )
        bitmapPaint.alpha = if (isFront) 255 else 184
        canvas.drawBitmap(bmp, null, orbDestRect, bitmapPaint)
    }
}
