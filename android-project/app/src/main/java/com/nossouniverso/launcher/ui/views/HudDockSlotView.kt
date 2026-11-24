package com.nossouniverso.launcher.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.nossouniverso.launcher.R
import kotlin.math.min

/**
 * Circular segmented HUD dock slot for the 4 side dock shortcuts and central App Drawer ring.
 */
class HudDockSlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val iconView: ImageView

    var isCenterDrawerButton: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isEditModeActive: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var panelOpacityPercent: Int = 78
        set(value) {
            field = value.coerceIn(25, 95)
            invalidate()
        }

    var transparentHudMode: Boolean = false
        set(value) {
            field = value
            val pad = ((if (value) 4.5f else 9f) * density).toInt()
            iconView.setPadding(pad, pad, pad, pad)
            invalidate()
        }

    var innerAccentColor: Int = Color.parseColor("#F59E0B")
        set(value) {
            field = value
            amberPaint.color = value
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(8, 14, 23)
        style = Paint.Style.FILL
    }

    private val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22D3EE")
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
    }

    private val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = innerAccentColor
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
    }

    private val transparentRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(64, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }

    init {
        setWillNotDraw(false)
        iconView = ImageView(context).apply {
            val pad = (9 * density).toInt()
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER
            }
            setPadding(pad, pad, pad, pad)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addView(iconView)
    }

    fun getPortalOriginView(): View = iconView

    fun getPortalIconDrawable(): Drawable? = iconView.drawable

    fun triggerPortalLaunchReaction() {
        if (HilmPortalOverlayView.isReducedMotionEnabled(context)) return
        iconView.animate().cancel()
        iconView.animate()
            .scaleX(1.22f)
            .scaleY(1.22f)
            .setDuration(110L)
            .withEndAction {
                iconView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160L)
                    .start()
            }
            .start()
    }

    fun bindIcon(drawable: Drawable?, contentDescriptionText: String) {
        contentDescription = contentDescriptionText
        if (drawable != null) {
            iconView.setImageDrawable(drawable)
            iconView.alpha = 1f
        } else {
            iconView.setImageResource(android.R.drawable.ic_input_add)
            iconView.alpha = if (transparentHudMode) 0.40f else 0.55f
        }
    }

    fun bindCenterButton(contentDescriptionText: String) {
        isCenterDrawerButton = true
        contentDescription = contentDescriptionText
        iconView.setImageResource(R.drawable.ic_launcher_foreground)
        iconView.alpha = 1f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                animate().cancel()
                animate()
                    .scaleX(0.88f)
                    .scaleY(0.88f)
                    .setDuration(90L)
                    .start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animate().cancel()
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(230L)
                    .setInterpolator(OvershootInterpolator(1.7f))
                    .start()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 3f * density
        if (r <= 0f) return

        val outerRect = RectF(cx - r, cy - r, cx + r, cy + r)

        if (transparentHudMode) {
            transparentRingPaint.color = if (isEditModeActive) {
                Color.argb(190, 245, 158, 11)
            } else {
                Color.argb(62, 255, 255, 255)
            }
            canvas.drawArc(outerRect, 20f, 140f, false, transparentRingPaint)
            canvas.drawArc(outerRect, 200f, 140f, false, transparentRingPaint)
            super.onDraw(canvas)
            return
        }

        val bgAlphaRatio = if (isCenterDrawerButton) {
            0.90f
        } else {
            (panelOpacityPercent / 100f).coerceAtLeast(0.55f)
        }
        bgPaint.alpha = (bgAlphaRatio * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, bgPaint)

        cyanPaint.color = if (isEditModeActive && !isCenterDrawerButton) {
            Color.parseColor("#F59E0B")
        } else {
            Color.parseColor("#22D3EE")
        }
        canvas.drawArc(outerRect, 20f, 140f, false, cyanPaint)
        canvas.drawArc(outerRect, 200f, 140f, false, cyanPaint)

        val innerR = r * 0.84f
        val innerRect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
        canvas.drawArc(innerRect, 145f, 40f, false, amberPaint)
        canvas.drawArc(innerRect, 325f, 40f, false, amberPaint)

        super.onDraw(canvas)
    }
}
