package com.nossouniverso.launcher.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.nossouniverso.launcher.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders the fixed, unaltered central HILM nucleus (R.drawable.nucleo_hilm_core) floating
 * freely over the wallpaper and animates Íris (copper orange), Luna (electric violet), and
 * Maya (golden amber with honey highlights) on independent, compact, inclined 3D elliptical
 * orbits with smooth occasional crossings, perspective depth, and curved luminous trails.
 */
class HilmCoreView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class IndependentOrbSpec(
        val periodMs: Float,
        val basePhase: Float,
        val semiMajorRatio: Float,
        val eccentricity: Float,
        val argPeriapsisRad: Float,
        val inclinationDeg: Float,
        val yawDeg: Float,
        val rollDeg: Float,
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
        val cosW: Float = cos(argPeriapsisRad)
        val sinW: Float = sin(argPeriapsisRad)
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
        // Íris: cobre luminoso
        IndependentOrbSpec(
            periodMs = 4900f,
            basePhase = 0.45f,
            semiMajorRatio = 53.5f / 214f,
            eccentricity = 0.12f,
            argPeriapsisRad = 1.11f,
            inclinationDeg = 61.5f,
            yawDeg = 9.9f,
            rollDeg = -28.0f,
            coreColor = Color.parseColor("#FFF3E0"),
            midColor = Color.parseColor("#FB923C"),
            edgeColor = Color.parseColor("#C2410C"),
            haloColor = Color.argb(178, 234, 88, 12),
            trailR = 234,
            trailG = 88,
            trailB = 12,
            trailCoreR = 253,
            trailCoreG = 186,
            trailCoreB = 116
        ),
        // Luna: violeta elétrico
        IndependentOrbSpec(
            periodMs = 6300f,
            basePhase = 2.55f,
            semiMajorRatio = 57.5f / 214f,
            eccentricity = 0.14f,
            argPeriapsisRad = 1.66f,
            inclinationDeg = -63.1f,
            yawDeg = 3.9f,
            rollDeg = 26.0f,
            coreColor = Color.parseColor("#F5F3FF"),
            midColor = Color.parseColor("#A855F7"),
            edgeColor = Color.parseColor("#6D28D9"),
            haloColor = Color.argb(184, 139, 92, 246),
            trailR = 139,
            trailG = 92,
            trailB = 246,
            trailCoreR = 221,
            trailCoreG = 214,
            trailCoreB = 254
        ),
        // Maya: dourado com reflexos de mel
        IndependentOrbSpec(
            periodMs = 7900f,
            basePhase = 4.65f,
            semiMajorRatio = 61.5f / 214f,
            eccentricity = 0.12f,
            argPeriapsisRad = 2.19f,
            inclinationDeg = 67.0f,
            yawDeg = -2.5f,
            rollDeg = 72.0f,
            coreColor = Color.parseColor("#FFFDF2"),
            midColor = Color.parseColor("#FBBF24"),
            edgeColor = Color.parseColor("#B45309"),
            haloColor = Color.argb(178, 245, 158, 11),
            trailR = 217,
            trailG = 119,
            trailB = 6,
            trailCoreR = 253,
            trailCoreG = 224,
            trailCoreB = 71
        )
    )

    private var coreSourceBitmap: Bitmap? = null
    private var coreScaledBitmap: Bitmap? = null
    private var coreGlowBitmap: Bitmap? = null
    private val orbBitmaps = arrayOfNulls<Bitmap>(3)
    private val trailPuffBitmaps = arrayOfNulls<Bitmap>(3)
    private val coreRect = RectF()
    private val orbDestRect = RectF()
    private val puffDestRect = RectF()

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val coreGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pulsePeriodMs = 4600f
    private val trailSegments = 126
    private val trailArcRad = 2.65f
    private val filamentLimit = (trailSegments * 0.68f).toInt()
    private val trailBuffers = Array(3) { Array(trailSegments + 1) { ProjectedPoint() } }
    private val plasmaColorTable = Array(3) { IntArray(trailSegments) }
    private val filamentColorTable = Array(3) { IntArray(trailSegments) }
    private val workAdjX = FloatArray(3)
    private val workAdjY = FloatArray(3)
    private val workAdjZ = FloatArray(3)
    private val headDepths = FloatArray(3)
    private val sortedIndices = intArrayOf(0, 1, 2)

    private var lastFrameTimeMs: Double = -1.0
    private var simulationTimeMs: Double = 0.0
    private var isLoopRunning: Boolean = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isLoopRunning) return
            val nowMs = frameTimeNanos / 1_000_000.0
            if (lastFrameTimeMs >= 0.0) {
                val deltaMs = (nowMs - lastFrameTimeMs).coerceIn(0.0, 100.0)
                simulationTimeMs += deltaMs
            }
            lastFrameTimeMs = nowMs
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        coreSourceBitmap = BitmapFactory.decodeResource(resources, R.drawable.nucleo_hilm_core)
        orbSpecs.forEachIndexed { index, spec ->
            orbBitmaps[index] = createOrbBitmap(spec)
            trailPuffBitmaps[index] = createTrailPuffBitmap(spec)
            precomputeTrailColors(index, spec)
        }
    }

    private fun lerpRgbInt(c0: Int, c1: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val r0 = Color.red(c0)
        val g0 = Color.green(c0)
        val b0 = Color.blue(c0)
        val r1 = Color.red(c1)
        val g1 = Color.green(c1)
        val b1 = Color.blue(c1)
        return Color.rgb(
            (r0 + (r1 - r0) * f).toInt(),
            (g0 + (g1 - g0) * f).toInt(),
            (b0 + (b1 - b0) * f).toInt()
        )
    }

    private fun precomputeTrailColors(index: Int, spec: IndependentOrbSpec) {
        val tailColor = Color.rgb(spec.trailR, spec.trailG, spec.trailB)
        val plasma = plasmaColorTable[index]
        val filament = filamentColorTable[index]
        for (i in 0 until trailSegments) {
            val frac = (i + 0.5f) / trailSegments
            plasma[i] = when {
                frac < 0.24f -> lerpRgbInt(spec.coreColor, spec.midColor, frac / 0.24f)
                frac < 0.65f -> lerpRgbInt(spec.midColor, tailColor, (frac - 0.24f) / 0.41f)
                else -> tailColor
            }
            val innerFrac = (frac / 0.68f).coerceIn(0f, 1f)
            filament[i] = lerpRgbInt(spec.coreColor, spec.midColor, innerFrac)
        }
    }

    private fun createTrailPuffBitmap(spec: IndependentOrbSpec): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val center = size / 2f
        val puffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                center,
                center,
                center,
                intArrayOf(
                    Color.argb(133, spec.trailCoreR, spec.trailCoreG, spec.trailCoreB),
                    Color.argb(71, spec.trailR, spec.trailG, spec.trailB),
                    Color.argb(20, spec.trailR, spec.trailG, spec.trailB),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.38f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(center, center, center, puffPaint)
        return bmp
    }

    private fun createOrbBitmap(spec: IndependentOrbSpec): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val center = size / 2f
        val haloRadius = size * 0.48f
        val bodyRadius = size * 0.19f

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

    private fun startOrbitLoop() {
        if (isLoopRunning || isReducedMotionEnabled()) return
        isLoopRunning = true
        lastFrameTimeMs = -1.0
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopOrbitLoop() {
        if (!isLoopRunning) return
        isLoopRunning = false
        lastFrameTimeMs = -1.0
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (windowVisibility == VISIBLE) {
            startOrbitLoop()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            startOrbitLoop()
        } else {
            stopOrbitLoop()
        }
    }

    override fun onDetachedFromWindow() {
        stopOrbitLoop()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val src = coreSourceBitmap ?: return
        val minDim = min(w, h)
        if (minDim <= 0) return

        val coreDrawSize = (minDim * 0.505f).toInt().coerceAtLeast(1)
        coreScaledBitmap = Bitmap.createScaledBitmap(src, coreDrawSize, coreDrawSize, true)

        val softMiniSize = (coreDrawSize / 4).coerceAtLeast(16)
        val softMini = Bitmap.createScaledBitmap(src, softMiniSize, softMiniSize, true)
        coreGlowBitmap = Bitmap.createScaledBitmap(softMini, coreDrawSize, coreDrawSize, true)
        if (softMini !== src) {
            softMini.recycle()
        }

        val cx = w / 2f
        val cy = h / 2f
        val half = coreDrawSize / 2f
        coreRect.set(cx - half, cy - half, cx + half, cy + half)
    }

    private fun computeSingleBaseOrb3D(
        spec: IndependentOrbSpec,
        elapsedMs: Float,
        minDim: Float,
        idx: Int
    ) {
        val twoPi = (2.0 * PI).toFloat()
        val meanAnomaly = (elapsedMs / spec.periodMs) * twoPi + spec.basePhase
        val e = spec.eccentricity
        var eccAnom = meanAnomaly
        for (step in 0 until 4) {
            eccAnom -= (eccAnom - e * sin(eccAnom) - meanAnomaly) / (1f - e * cos(eccAnom))
        }

        val semiMajor = minDim * spec.semiMajorRatio
        val xOrb = semiMajor * (cos(eccAnom) - e)
        val yOrb = semiMajor * spec.minorFactor * sin(eccAnom)

        val xp = xOrb * spec.cosW - yOrb * spec.sinW
        val yp = xOrb * spec.sinW + yOrb * spec.cosW

        val x1 = xp
        val y1 = yp * spec.cosI
        val z1 = yp * spec.sinI

        val x2 = x1 * spec.cosY + z1 * spec.sinY
        val y2 = y1
        val z2 = -x1 * spec.sinY + z1 * spec.cosY

        workAdjX[idx] = x2 * spec.cosR - y2 * spec.sinR
        workAdjY[idx] = x2 * spec.sinR + y2 * spec.cosR
        workAdjZ[idx] = z2
    }

    private fun sampleOrbAtTime(
        targetIdx: Int,
        elapsedMs: Float,
        cx: Float,
        cy: Float,
        minDim: Float,
        out: ProjectedPoint
    ) {
        for (i in 0..2) {
            computeSingleBaseOrb3D(orbSpecs[i], elapsedMs, minDim, i)
        }

        val scaleUnit = minDim / 214f
        val sigma = 18.0f * scaleUnit
        val pushAmp = 5.5f * scaleUnit

        for (i in 0..2) {
            for (j in i + 1..2) {
                val dx = workAdjX[j] - workAdjX[i]
                val dy = workAdjY[j] - workAdjY[i]
                val dz = workAdjZ[j] - workAdjZ[i]
                val d3 = sqrt(dx * dx + dy * dy + dz * dz + 1e-4f)
                val ratio = d3 / sigma
                val w3 = exp(-ratio * ratio)
                val step3 = pushAmp * w3
                val ux = dx / d3
                val uy = dy / d3
                val uz = dz / d3
                workAdjX[j] += ux * step3
                workAdjY[j] += uy * step3
                workAdjZ[j] += uz * step3
                workAdjX[i] -= ux * step3
                workAdjY[i] -= uy * step3
                workAdjZ[i] -= uz * step3
            }
        }

        val cameraDist = minDim * (240f / 214f)
        val pz = workAdjZ[targetIdx]
        val perspective = cameraDist / (cameraDist - pz)
        out.x = cx + workAdjX[targetIdx] * perspective
        out.y = cy + workAdjY[targetIdx] * perspective
        out.z = pz
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
        val baseOrbRadius = minDim * (6.0f / 214f)
        val elapsedF = if (isReducedMotionEnabled()) 0f else simulationTimeMs.toFloat()
        val twoPi = (2.0 * PI).toFloat()

        for (idx in 0..2) {
            val spec = orbSpecs[idx]
            val trailWindowMs = (trailArcRad / twoPi) * spec.periodMs
            val buffer = trailBuffers[idx]
            for (s in 0..trailSegments) {
                val frac = s.toFloat() / trailSegments
                sampleOrbAtTime(idx, elapsedF - frac * trailWindowMs, cx, cy, minDim, buffer[s])
            }
            headDepths[idx] = buffer[0].z
            sortedIndices[idx] = idx
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

        // Pass 1: Back hemisphere (z < 0) behind central sphere
        for (slot in 0..2) {
            val idx = sortedIndices[slot]
            drawOrbAndCurvedTrail(canvas, idx, minDim, baseOrbRadius, drawFront = false)
        }

        // Pass 2: Central HILM nucleus with smooth self-luminance breathing pulse
        coreScaledBitmap?.let { bmp ->
            bitmapPaint.alpha = 255
            canvas.drawBitmap(bmp, null, coreRect, bitmapPaint)

            val pulse = if (isReducedMotionEnabled()) {
                0f
            } else {
                val wave = 0.5f - 0.5f * cos((elapsedF / pulsePeriodMs) * twoPi)
                wave.pow(1.15f)
            }

            coreGlowPaint.alpha = ((0.03f + 0.34f * pulse) * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(bmp, null, coreRect, coreGlowPaint)

            coreGlowBitmap?.let { glowBmp ->
                coreGlowPaint.alpha = ((0.02f + 0.16f * pulse) * 255f).toInt().coerceIn(0, 255)
                canvas.drawBitmap(glowBmp, null, coreRect, coreGlowPaint)
            }
        }

        // Pass 3: Front hemisphere (z >= 0) in front of central sphere
        for (slot in 0..2) {
            val idx = sortedIndices[slot]
            drawOrbAndCurvedTrail(canvas, idx, minDim, baseOrbRadius, drawFront = true)
        }
    }

    private fun drawOrbAndCurvedTrail(
        canvas: Canvas,
        index: Int,
        minDim: Float,
        baseOrbRadius: Float,
        drawFront: Boolean
    ) {
        val buffer = trailBuffers[index]
        val depthNormSpan = 45f * (minDim / 214f)
        val scaleUnit = minDim / 214f
        val head = buffer[0]
        val headDepthMix = ((head.z + depthNormSpan) / (2f * depthNormSpan)).coerceIn(0f, 1f)
        val headDepthDim = 0.38f + 0.62f * headDepthMix
        val comaSpan = 26f * scaleUnit

        // Camada 1: Névoa luminosa contínua ao longo de toda a cauda elíptica
        val puffBmp = trailPuffBitmaps[index]
        if (puffBmp != null) {
            for (i in trailSegments downTo 1) {
                val p = buffer[i]
                val isFront = p.z >= 0f
                if (isFront != drawFront) continue

                val frac = i.toFloat() / trailSegments
                val mistFade = (1f - frac).pow(1.28f)
                val depthMix = ((p.z + depthNormSpan) / (2f * depthNormSpan)).coerceIn(0f, 1f)
                val depthDim = 0.36f + 0.64f * depthMix
                val dx = p.x - head.x
                val dy = p.y - head.y
                val distFromHead = sqrt(dx * dx + dy * dy)
                val comaBoost = 0.72f + 0.28f * exp(-distFromHead / comaSpan)
                val puffAlpha = 0.145f * mistFade * depthDim * comaBoost
                if (puffAlpha <= 0.003f) continue

                val puffRadius = (10.2f * (1f - 0.54f * frac) + 2.2f) * scaleUnit * p.scale
                puffDestRect.set(
                    p.x - puffRadius,
                    p.y - puffRadius,
                    p.x + puffRadius,
                    p.y + puffRadius
                )
                bitmapPaint.alpha = (puffAlpha * 255f).toInt().coerceIn(0, 255)
                canvas.drawBitmap(puffBmp, null, puffDestRect, bitmapPaint)
            }
        }

        // Camada 2: Cauda principal contínua com dissipação progressiva ao longo do arco
        val plasmaColors = plasmaColorTable[index]
        for (i in trailSegments - 1 downTo 0) {
            val p0 = buffer[i]
            val p1 = buffer[i + 1]
            val midZ = (p0.z + p1.z) * 0.5f
            val isFront = midZ >= 0f
            if (isFront != drawFront) continue

            val frac = (i + 0.5f) / trailSegments
            val taper = (1f - frac).pow(1.15f)
            val depthMix = ((midZ + depthNormSpan) / (2f * depthNormSpan)).coerceIn(0f, 1f)
            val segAlpha = 0.35f * (1f - frac).pow(1.24f) * (0.38f + 0.62f * depthMix) * (0.68f + 0.32f * headDepthDim)
            if (segAlpha <= 0.004f) continue

            val avgScale = (p0.scale + p1.scale) * 0.5f
            trailPaint.color = plasmaColors[i]
            trailPaint.strokeWidth = max(0.45f * scaleUnit, baseOrbRadius * 0.833f * avgScale * taper)
            trailPaint.alpha = (segAlpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawLine(p0.x, p0.y, p1.x, p1.y, trailPaint)
        }

        // Camada 3: Filamento luminoso interno próximo ao ponto (efeito núcleo de cometa)
        val filamentColors = filamentColorTable[index]
        for (i in filamentLimit - 1 downTo 0) {
            val p0 = buffer[i]
            val p1 = buffer[i + 1]
            val midZ = (p0.z + p1.z) * 0.5f
            val isFront = midZ >= 0f
            if (isFront != drawFront) continue

            val innerFrac = (i + 0.5f) / filamentLimit
            val depthMix = ((midZ + depthNormSpan) / (2f * depthNormSpan)).coerceIn(0f, 1f)
            val innerAlpha = 0.44f * (1f - innerFrac).pow(1.38f) * (0.4f + 0.6f * depthMix) * (0.7f + 0.3f * headDepthDim)
            if (innerAlpha <= 0.005f) continue

            val avgScale = (p0.scale + p1.scale) * 0.5f
            val innerTaper = (1f - innerFrac).pow(1.25f)
            trailPaint.color = filamentColors[i]
            trailPaint.strokeWidth = max(0.35f * scaleUnit, baseOrbRadius * 0.367f * avgScale * innerTaper)
            trailPaint.alpha = (innerAlpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawLine(p0.x, p0.y, p1.x, p1.y, trailPaint)
        }

        val isFront = head.z >= 0f
        if (isFront != drawFront) return

        val bmp = orbBitmaps[index] ?: return
        val halfDrawSize = baseOrbRadius * 2.5f * head.scale
        orbDestRect.set(
            head.x - halfDrawSize,
            head.y - halfDrawSize,
            head.x + halfDrawSize,
            head.y + halfDrawSize
        )
        bitmapPaint.alpha = ((0.56f + 0.44f * headDepthMix) * 255f).toInt().coerceIn(0, 255)
        canvas.drawBitmap(bmp, null, orbDestRect, bitmapPaint)
    }
}
