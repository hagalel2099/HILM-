package com.nossouniverso.launcher.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
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
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
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

    fun bindIcon(drawable: Drawable?, contentDescriptionText: String) {
        contentDescription = contentDescriptionText
        if (drawable != null) {
            iconView.setImageDrawable(drawable)
            iconView.alpha = 1f
        } else {
            iconView.setImageResource(android.R.drawable.ic_input_add)
            iconView.alpha = 0.55f
        }
    }

    fun bindCenterButton(contentDescriptionText: String) {
        isCenterDrawerButton = true
        contentDescription = contentDescriptionText
        iconView.setImageResource(R.drawable.ic_launcher_foreground)
        iconView.alpha = 1f
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 3f * density
        if (r <= 0f) return

        val bgAlphaRatio = if (isCenterDrawerButton) {
            0.90f
        } else {
            (panelOpacityPercent / 100f).coerceAtLeast(0.55f)
        }
        bgPaint.alpha = (bgAlphaRatio * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, bgPaint)

        val outerRect = RectF(cx - r, cy - r, cx + r, cy + r)
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
