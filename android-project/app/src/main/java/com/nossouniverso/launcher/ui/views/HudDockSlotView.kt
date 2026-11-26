package com.nossouniverso.launcher.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.nossouniverso.launcher.R
import com.nossouniverso.launcher.model.HilmTransitionConfig
import kotlin.math.PI
import kotlin.math.cos
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
                updatePulseAnimatorState()
                invalidate()
            }
        }

    var mayaThemeMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateIconPadding()
                updatePulseAnimatorState()
                invalidate()
            }
        }

    var pulsePhaseOffsetFraction: Float = 0f

    private var pulsePhase: Float = 0f
    private var pulseAnimator: ValueAnimator? = null

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

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
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

    private fun currentPulseWave(): Float {
        if (!lunaThemeMode && !mayaThemeMode) return 0f
        if (isReducedMotionEnabled()) return 0.5f
        val angle = ((pulsePhase + pulsePhaseOffsetFraction) % 1f) * (2f * PI.toFloat())
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
            if (mayaThemeMode || lunaThemeMode) {
                val wave = currentPulseWave()
                val ringScale = 0.985f + 0.025f * wave
                val pulseR = r * ringScale
                val pulseRect = RectF(cx - pulseR, cy - pulseR, cx + pulseR, cy + pulseR)
                val themeColor = when {
                    isEditModeActive -> Color.argb(205, 245, 158, 11)
                    mayaThemeMode -> Color.argb((122 + (90 * wave).toInt()).coerceIn(0, 255), 245, 185, 66)
                    else -> lerpColor(
                        Color.argb(92, 0, 229, 255),
                        Color.argb(165, 139, 92, 246),
                        wave
                    )
                }
                haloPaint.color = themeColor
                haloPaint.strokeWidth = 3.0f * density
                haloPaint.alpha = (18 + (34 * wave).toInt()).coerceIn(0, 255)
                canvas.drawArc(pulseRect, 20f, 140f, false, haloPaint)
                canvas.drawArc(pulseRect, 200f, 140f, false, haloPaint)

                transparentRingPaint.color = themeColor
                canvas.drawArc(pulseRect, 20f, 140f, false, transparentRingPaint)
                canvas.drawArc(pulseRect, 200f, 140f, false, transparentRingPaint)
            } else {
                transparentRingPaint.color = if (isEditModeActive) {
                    Color.argb(190, 245, 158, 11)
                } else {
                    Color.argb(62, 255, 255, 255)
                }
                canvas.drawArc(outerRect, 20f, 140f, false, transparentRingPaint)
                canvas.drawArc(outerRect, 200f, 140f, false, transparentRingPaint)
            }
            super.onDraw(canvas)
            return
        }

        if (lunaThemeMode) {
            val wave = currentPulseWave()
            val pulseR = r * (0.988f + 0.024f * wave)
            val pulseRect = RectF(cx - pulseR, cy - pulseR, cx + pulseR, cy + pulseR)

            if (isCenterDrawerButton) {
                bgPaint.color = Color.rgb(10, 20, 36)
                bgPaint.alpha = (96 + (34 * wave).toInt()).coerceIn(0, 255)
                canvas.drawCircle(cx, cy, pulseR, bgPaint)
            }

            val lunaCyan = Color.parseColor("#00E5FF")
            val lunaViolet = Color.parseColor("#8B5CF6")
            val arcColor = when {
                isEditModeActive && !isCenterDrawerButton -> Color.parseColor("#FFB45C")
                isCenterDrawerButton -> lerpColor(
                    Color.argb(185, 0, 229, 255),
                    Color.argb(245, 167, 139, 250),
                    wave * 0.6f
                )
                else -> lerpColor(
                    Color.argb(105, 57, 139, 255),
                    Color.argb(195, 139, 92, 246),
                    wave * 0.72f
                )
            }

            haloPaint.color = lerpColor(lunaCyan, lunaViolet, wave * 0.6f)
            haloPaint.strokeWidth = 3.6f * density
            haloPaint.alpha = if (isCenterDrawerButton) {
                (28 + (48 * wave).toInt()).coerceIn(0, 255)
            } else {
                (16 + (34 * wave).toInt()).coerceIn(0, 255)
            }
            canvas.drawArc(pulseRect, 24f, 132f, false, haloPaint)
            canvas.drawArc(pulseRect, 204f, 132f, false, haloPaint)

            cyanPaint.strokeWidth = (if (isEditModeActive) 1.7f else 1.25f) * density
            cyanPaint.color = arcColor
            canvas.drawArc(pulseRect, 24f, 132f, false, cyanPaint)
            canvas.drawArc(pulseRect, 204f, 132f, false, cyanPaint)

            if (isCenterDrawerButton) {
                val innerR = pulseR * 0.84f
                val innerRect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
                amberPaint.color = lerpColor(
                    Color.argb(165, 255, 180, 92),
                    Color.argb(225, 167, 139, 250),
                    wave * 0.35f
                )
                amberPaint.strokeWidth = 1.4f * density
                canvas.drawArc(innerRect, 148f, 34f, false, amberPaint)
                canvas.drawArc(innerRect, 328f, 34f, false, amberPaint)
            }
            super.onDraw(canvas)
            return
        }

        if (mayaThemeMode) {
            val wave = currentPulseWave()
            val pulseR = r * (0.985f + 0.028f * wave)
            val pulseRect = RectF(cx - pulseR, cy - pulseR, cx + pulseR, cy + pulseR)

            val bgAlphaRatio = if (isCenterDrawerButton) 0.94f else (panelOpacityPercent / 100f).coerceAtLeast(0.72f)
            bgPaint.color = Color.rgb(19, 23, 32)
            bgPaint.alpha = (bgAlphaRatio * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, pulseR, bgPaint)

            haloPaint.color = lerpColor(Color.parseColor("#F5B942"), Color.parseColor("#38BDF8"), wave * 0.25f)
            haloPaint.strokeWidth = 4.0f * density
            haloPaint.alpha = (22 + (48 * wave).toInt()).coerceIn(0, 255)
            canvas.drawArc(pulseRect, 20f, 140f, false, haloPaint)
            canvas.drawArc(pulseRect, 200f, 140f, false, haloPaint)

            cyanPaint.strokeWidth = 1.75f * density
            cyanPaint.color = if (isEditModeActive && !isCenterDrawerButton) {
                Color.parseColor("#C47A48")
            } else {
                Color.argb((178 + (76 * wave).toInt()).coerceIn(0, 255), 245, 185, 66)
            }
            canvas.drawArc(pulseRect, 20f, 140f, false, cyanPaint)
            canvas.drawArc(pulseRect, 200f, 140f, false, cyanPaint)

            val innerR = pulseR * 0.84f
            val innerRect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
            amberPaint.color = if (isCenterDrawerButton) Color.parseColor("#C47A48") else Color.parseColor("#38BDF8")
            amberPaint.alpha = (165 + (82 * wave).toInt()).coerceIn(0, 255)
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
