package com.nossouniverso.launcher.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.nossouniverso.launcher.R
import kotlin.math.min

/**
 * Renders the Núcleo HILM (Reference 2) centered inside the HUD aperture.
 * Applies a soft radial alpha feather so the pure-black outer space backdrop
 * blends smoothly into the HUD composition without adding a new sphere or sticker edge.
 */
class HilmCoreView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    private var featheredBitmap: Bitmap? = null
    private var pulseScale: Float = 1f
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val drawMatrix = Matrix()

    private val pulseAnimator = ValueAnimator.ofFloat(0.985f, 1.02f).apply {
        duration = 5500L
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            pulseScale = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        sourceBitmap = BitmapFactory.decodeResource(resources, R.drawable.nucleo_hilm)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!pulseAnimator.isStarted) {
            pulseAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildFeatheredBitmap(w, h)
    }

    private fun rebuildFeatheredBitmap(w: Int, h: Int) {
        val src = sourceBitmap ?: return
        val size = min(w, h)
        if (size <= 0) return

        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        canvas.drawBitmap(scaled, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        val radius = size * 0.5f
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            shader = RadialGradient(
                radius,
                radius,
                radius,
                intArrayOf(
                    Color.WHITE,
                    Color.WHITE,
                    Color.argb(185, 255, 255, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.54f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), maskPaint)

        featheredBitmap = output
        if (scaled != src) {
            scaled.recycle()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = featheredBitmap ?: return
        val cx = width / 2f
        val cy = height / 2f
        val bx = bmp.width / 2f
        val by = bmp.height / 2f

        drawMatrix.reset()
        drawMatrix.postTranslate(-bx, -by)
        drawMatrix.postScale(pulseScale, pulseScale)
        drawMatrix.postTranslate(cx, cy)

        canvas.drawBitmap(bmp, drawMatrix, bitmapPaint)
    }
}
