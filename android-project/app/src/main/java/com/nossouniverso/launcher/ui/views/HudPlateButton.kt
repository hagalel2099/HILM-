package com.nossouniverso.launcher.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nossouniverso.launcher.R

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

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B80A111B")
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

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setWillNotDraw(false)
        val padH = (14 * density).toInt()
        val padV = (8 * density).toInt()
        setPadding(padH, padV, padH, padV)

        iconView = ImageView(context).apply {
            val size = (20 * density).toInt()
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

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

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
}
