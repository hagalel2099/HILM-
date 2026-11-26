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
import com.nossouniverso.launcher.model.HilmTransitionConfig
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
            updateIconPadding()
            invalidate()
        }

    var lunaThemeMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateIconPadding()
                invalidate()
            }
        }

    var mayaThemeMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateIconPadding()
                invalidate()
            }
        }

    private fun updateIconPadding() {
        val padDp = when {
            transparentHudMode || (lunaThemeMode && !isCenterDrawerButton) -> 4.5f
            lunaThemeMode && isCenterDrawerButton -> 8.5f
            mayaThemeMode && !isCenterDrawerButton -> 6.0f
            else -> 9f
        }
        val pad = (padDp * density).toInt()
        iconView.setPadding(pad, pad, pad, pad)
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

    fun triggerPortalLaunchReaction(config: HilmTransitionConfig = HilmTransitionConfig()) {
        HilmTransitionAnimator.animateIconTapReaction(iconView, config)
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

        if (lunaThemeMode) {
            if (isCenterDrawerButton) {
                bgPaint.color = Color.rgb(10, 20, 36)
                bgPaint.alpha = 110
                canvas.drawCircle(cx, cy, r, bgPaint)
            }
            cyanPaint.strokeWidth = (if (isEditModeActive) 1.7f else 1.25f) * density
            cyanPaint.color = when {
                isEditModeActive && !isCenterDrawerButton -> Color.parseColor("#FFB45C")
                isCenterDrawerButton -> Color.argb(215, 0, 229, 255)
                transparentHudMode -> Color.argb(72, 0, 229, 255)
                else -> Color.argb(120, 57, 139, 255)
            }
            canvas.drawArc(outerRect, 24f, 132f, false, cyanPaint)
            canvas.drawArc(outerRect, 204f, 132f, false, cyanPaint)
            if (isCenterDrawerButton) {
                val innerR = r * 0.84f
                val innerRect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
                amberPaint.color = Color.argb(190, 255, 180, 92)
                amberPaint.strokeWidth = 1.4f * density
                canvas.drawArc(innerRect, 148f, 34f, false, amberPaint)
                canvas.drawArc(innerRect, 328f, 34f, false, amberPaint)
            }
            super.onDraw(canvas)
            return
        }

        if (mayaThemeMode) {
            val bgAlphaRatio = if (isCenterDrawerButton) 0.94f else (panelOpacityPercent / 100f).coerceAtLeast(0.72f)
            bgPaint.color = Color.rgb(19, 23, 32)
            bgPaint.alpha = (bgAlphaRatio * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r, bgPaint)

            cyanPaint.strokeWidth = 1.75f * density
            cyanPaint.color = if (isEditModeActive && !isCenterDrawerButton) {
                Color.parseColor("#C47A48")
            } else {
                Color.parseColor("#F5B942")
            }
            canvas.drawArc(outerRect, 20f, 140f, false, cyanPaint)
            canvas.drawArc(outerRect, 200f, 140f, false, cyanPaint)

            val innerR = r * 0.84f
            val innerRect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
            amberPaint.color = if (isCenterDrawerButton) Color.parseColor("#C47A48") else Color.parseColor("#38BDF8")
            amberPaint.strokeWidth = 1.45f * density
            canvas.drawArc(innerRect, 145f, 40f, false, amberPaint)
            canvas.drawArc(innerRect, 325f, 40f, false, amberPaint)

            super.onDraw(canvas)
            return
        }

        val bgAlphaRatio = if (isCenterDrawerButton) {
            0.90f
        } else {
            (panelOpacityPercent / 100f).coerceAtLeast(0.55f)
        }
        bgPaint.color = Color.rgb(8, 14, 23)
        bgPaint.alpha = (bgAlphaRatio * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, bgPaint)

        cyanPaint.strokeWidth = 1.8f * density
        cyanPaint.color = if (isEditModeActive && !isCenterDrawerButton) {
            Color.parseColor("#F59E0B")
        } else {
            Color.parseColor("#22D3EE")
        }
        canvas.drawArc(outerRect, 20f, 140f, false, cyanPaint)
        canvas.drawArc(outerRect, 200f, 140f, false, cyanPaint)

        val innerR = r * 0.84f
        val innerRect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
        amberPaint.color = innerAccentColor
        amberPaint.strokeWidth = 1.6f * density
        canvas.drawArc(innerRect, 145f, 40f, false, amberPaint)
        canvas.drawArc(innerRect, 325f, 40f, false, amberPaint)

        super.onDraw(canvas)
    }
}
