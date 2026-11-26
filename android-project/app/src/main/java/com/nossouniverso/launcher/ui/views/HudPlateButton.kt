package com.nossouniverso.launcher.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nossouniverso.launcher.R
import com.nossouniverso.launcher.model.HilmTransitionConfig
import kotlin.math.PI
import kotlin.math.cos

/**
 * Chamfered octagonal HUD shortcut plate matching the 4 lower plates in Reference 1.
 */
class HudPlateButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val iconView: ImageView
    private val labelView: TextView

    var isEditModeActive: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isSlotEmpty: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var panelOpacityPercent: Int = 78
        set(value) {
            field = value.coerceIn(15, 95)
            invalidate()
        }

    var lunaThemeMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateLabelThemeStyle()
                updatePulseAnimatorState()
                invalidate()
            }
        }

    var mayaThemeMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateLabelThemeStyle()
                updatePulseAnimatorState()
                invalidate()
            }
        }

    var pulsePhaseOffsetFraction: Float = 0f

    private var pulsePhase: Float = 0f
    private var pulseAnimator: ValueAnimator? = null

    private fun updateLabelThemeStyle() {
        val textColorRes = when {
            lunaThemeMode -> R.color.luna_white_blue
            mayaThemeMode -> R.color.maya_gold_light
            else -> R.color.hud_text_primary
        }
        labelView.setTextColor(ContextCompat.getColor(context, textColorRes))
        labelView.setShadowLayer(
            if (lunaThemeMode || mayaThemeMode) 5f else 0f,
            0f,
            1.5f,
            Color.parseColor("#E605070B")
        )
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(8, 14, 24)
        style = Paint.Style.FILL
    }

    private val cyanStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22D3EE")
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
    }

    private val silverStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        style = Paint.Style.STROKE
        strokeWidth = 1.1f * density
    }

    private val amberAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val haloStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
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

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setWillNotDraw(false)
        val padH = (14 * density).toInt()
        val padV = (8 * density).toInt()
        setPadding(padH, padV, padH, padV)

        iconView = ImageView(context).apply {
            val size = (24 * density).toInt()
            layoutParams = LayoutParams(size, size).apply {
                marginEnd = (8 * density).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        labelView = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.hud_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            isSingleLine = true
            letterSpacing = 0.08f
        }

        addView(iconView)
        addView(labelView)
    }

    fun getPortalOriginView(): View = if (iconView.visibility == VISIBLE) iconView else this

    fun getPortalIconDrawable(): Drawable? = if (iconView.visibility == VISIBLE) iconView.drawable else null

    fun triggerPortalLaunchReaction(config: HilmTransitionConfig = HilmTransitionConfig()) {
        HilmTransitionAnimator.animateIconTapReaction(iconView, config)
    }

    fun bindShortcut(label: String, icon: Drawable?, empty: Boolean) {
        isSlotEmpty = empty
        labelView.text = label.uppercase()
        if (icon != null && !empty) {
            iconView.visibility = VISIBLE
            iconView.setImageDrawable(icon)
            labelView.alpha = 1f
        } else {
            iconView.visibility = GONE
            labelView.alpha = 0.65f
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                animate().cancel()
                animate()
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(90L)
                    .start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animate().cancel()
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220L)
                    .setInterpolator(OvershootInterpolator(1.6f))
                    .start()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (lunaThemeMode) {
            drawLunaHoloPlate(canvas, w, h)
            super.onDraw(canvas)
            return
        }
        if (mayaThemeMode) {
            drawMayaCeramicPlate(canvas, w, h)
            super.onDraw(canvas)
            return
        }

        val inset = 3f * density
        val chamfer = 11f * density
        val path = Path().apply {
            moveTo(inset + chamfer, inset)
            lineTo(w - inset - chamfer, inset)
            lineTo(w - inset, inset + chamfer)
            lineTo(w - inset, h - inset - chamfer)
            lineTo(w - inset - chamfer, h - inset)
            lineTo(inset + chamfer, h - inset)
            lineTo(inset, h - inset - chamfer)
            lineTo(inset, inset + chamfer)
            close()
        }

        val fillAlphaRatio = if (isSlotEmpty) {
            ((panelOpacityPercent / 100f) * 0.23f).coerceIn(0.15f, 0.20f)
        } else {
            (panelOpacityPercent / 100f).coerceIn(0.18f, 0.90f)
        }
        fillPaint.alpha = (fillAlphaRatio * 255f).toInt().coerceIn(0, 255)
        canvas.drawPath(path, fillPaint)

        cyanStrokePaint.color = if (isEditModeActive) {
            Color.parseColor("#F59E0B")
        } else if (isSlotEmpty) {
            Color.parseColor("#6622D3EE")
        } else {
            Color.parseColor("#22D3EE")
        }
        canvas.drawPath(path, cyanStrokePaint)

        // Outer silver corner brackets
        val innerOffset = 1f * density
        canvas.drawLine(innerOffset, h * 0.32f, innerOffset, h * 0.68f, silverStrokePaint)
        canvas.drawLine(w - innerOffset, h * 0.32f, w - innerOffset, h * 0.68f, silverStrokePaint)

        // Amber top/bottom micro accents
        canvas.drawLine(w * 0.25f, inset, w * 0.38f, inset, amberAccentPaint)
        canvas.drawLine(w * 0.62f, h - inset, w * 0.75f, h - inset, amberAccentPaint)

        super.onDraw(canvas)
    }

    private fun drawLunaHoloPlate(canvas: Canvas, w: Float, h: Float) {
        val wave = currentPulseWave()
        val edge = 4f * density
        val cornerR = 10f * density
        val maxAlpha = if (isSlotEmpty) 42 else ((panelOpacityPercent / 100f) * 115f).toInt().coerceIn(28, 120)
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            h,
            Color.argb((maxAlpha * 0.28f).toInt(), 5, 7, 11),
            Color.argb(maxAlpha, 10, 20, 36),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(edge, 2f * density, w - edge, h - 3f * density, cornerR, cornerR, fillPaint)
        fillPaint.shader = null

        val lunaCyan = Color.parseColor("#00E5FF")
        val lunaViolet = Color.parseColor("#8B5CF6")
        val borderGlowColor = lerpColor(lunaCyan, lunaViolet, wave * 0.65f)

        if (!isEditModeActive) {
            haloStrokePaint.color = borderGlowColor
            haloStrokePaint.strokeWidth = 2.6f * density
            haloStrokePaint.alpha = if (isSlotEmpty) {
                (14 + (24 * wave).toInt()).coerceIn(0, 255)
            } else {
                (22 + (40 * wave).toInt()).coerceIn(0, 255)
            }
            canvas.drawRoundRect(edge, 2f * density, w - edge, h - 3f * density, cornerR, cornerR, haloStrokePaint)

            haloStrokePaint.strokeWidth = 1.0f * density
            haloStrokePaint.alpha = if (isSlotEmpty) {
                (38 + (38 * wave).toInt()).coerceIn(0, 255)
            } else {
                (64 + (62 * wave).toInt()).coerceIn(0, 255)
            }
            canvas.drawRoundRect(edge, 2f * density, w - edge, h - 3f * density, cornerR, cornerR, haloStrokePaint)
        }

        val lineY = h - 3f * density
        cyanStrokePaint.strokeWidth = (if (isEditModeActive) 1.7f else 1.25f) * density
        cyanStrokePaint.color = if (isEditModeActive) {
            Color.parseColor("#FFB45C")
        } else if (isSlotEmpty) {
            lerpColor(Color.argb(82, 0, 229, 255), Color.argb(128, 139, 92, 246), wave)
        } else {
            lerpColor(Color.argb(165, 0, 229, 255), Color.argb(235, 167, 139, 250), wave)
        }
        canvas.drawLine(edge + 6f * density, lineY, w - edge - 6f * density, lineY, cyanStrokePaint)

        // Delicate top-left blue-violet accent tick (no solid card box)
        silverStrokePaint.color = if (isSlotEmpty) {
            Color.argb((58 + (42 * wave).toInt()).coerceIn(0, 255), 57, 139, 255)
        } else {
            Color.argb((125 + (75 * wave).toInt()).coerceIn(0, 255), 139, 92, 246)
        }
        silverStrokePaint.strokeWidth = 1.0f * density
        canvas.drawLine(edge, 5f * density, edge + 10f * density, 5f * density, silverStrokePaint)
    }

    private fun drawMayaCeramicPlate(canvas: Canvas, w: Float, h: Float) {
        val wave = currentPulseWave()
        val inset = 3f * density
        val chamfer = 11f * density
        val path = Path().apply {
            moveTo(inset + chamfer, inset)
            lineTo(w - inset - chamfer, inset)
            lineTo(w - inset, inset + chamfer)
            lineTo(w - inset, h - inset - chamfer)
            lineTo(w - inset - chamfer, h - inset)
            lineTo(inset + chamfer, h - inset)
            lineTo(inset, h - inset - chamfer)
            lineTo(inset, inset + chamfer)
            close()
        }

        val alphaRatio = if (isSlotEmpty) {
            0.62f
        } else {
            (panelOpacityPercent / 100f).coerceIn(0.68f, 0.94f)
        }
        val baseAlpha = (alphaRatio * 255f).toInt().coerceIn(135, 245)
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            Color.argb(baseAlpha, 30, 36, 49),
            Color.argb(baseAlpha, 14, 18, 26),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, fillPaint)
        fillPaint.shader = null

        if (!isEditModeActive) {
            haloStrokePaint.color = Color.parseColor("#F5B942")
            haloStrokePaint.strokeWidth = 3.2f * density
            haloStrokePaint.alpha = if (isSlotEmpty) {
                (14 + (28 * wave).toInt()).coerceIn(0, 255)
            } else {
                (22 + (46 * wave).toInt()).coerceIn(0, 255)
            }
            canvas.drawPath(path, haloStrokePaint)
        }

        cyanStrokePaint.strokeWidth = (if (isEditModeActive) 1.8f else 1.45f) * density
        cyanStrokePaint.color = when {
            isEditModeActive -> Color.parseColor("#C47A48")
            isSlotEmpty -> Color.argb((118 + (65 * wave).toInt()).coerceIn(0, 255), 245, 185, 66)
            else -> Color.argb((178 + (74 * wave).toInt()).coerceIn(0, 255), 245, 185, 66)
        }
        canvas.drawPath(path, cyanStrokePaint)

        // Copper side chamfer brackets
        silverStrokePaint.color = Color.parseColor("#C47A48")
        silverStrokePaint.strokeWidth = 1.3f * density
        silverStrokePaint.alpha = (165 + (75 * wave).toInt()).coerceIn(0, 255)
        val innerOffset = 1f * density
        canvas.drawLine(innerOffset, h * 0.30f, innerOffset, h * 0.70f, silverStrokePaint)
        canvas.drawLine(w - innerOffset, h * 0.30f, w - innerOffset, h * 0.70f, silverStrokePaint)

        // Electric blue micro-circuit accent along lower edge (pulses softly in span + glow)
        val trimHalfSpan = w * (0.22f + 0.06f * wave)
        amberAccentPaint.color = lerpColor(Color.parseColor("#38BDF8"), Color.parseColor("#FFE49E"), wave * 0.35f)
        amberAccentPaint.strokeWidth = 1.6f * density
        amberAccentPaint.alpha = (145 + (100 * wave).toInt()).coerceIn(0, 255)
        canvas.drawLine(w * 0.5f - trimHalfSpan, h - inset - 1.2f * density, w * 0.5f + trimHalfSpan, h - inset - 1.2f * density, amberAccentPaint)
    }
}
