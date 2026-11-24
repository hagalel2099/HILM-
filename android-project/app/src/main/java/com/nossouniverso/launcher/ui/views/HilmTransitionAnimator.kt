package com.nossouniverso.launcher.ui.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import com.nossouniverso.launcher.R
import com.nossouniverso.launcher.model.HilmTransitionConfig
import com.nossouniverso.launcher.model.HilmTransitionEffect
import java.util.WeakHashMap

/**
 * Shared single-shot transition helper for HILM launcher surfaces where HILM has real control:
 * side drawers, HUD popovers/banners, internal app drawer, and Activity-to-Activity navigation.
 */
object HilmTransitionAnimator {

    private val activeValueAnimators = WeakHashMap<View, ValueAnimator>()
    private val clipRect = Rect()

    private val quintOut = PathInterpolator(0.16f, 1f, 0.3f, 1f)
    private val smoothInOut = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    fun isSystemReducedMotionEnabled(context: Context): Boolean {
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

    fun shouldAnimate(context: Context, config: HilmTransitionConfig): Boolean {
        return config.enabled && !isSystemReducedMotionEnabled(context)
    }

    fun cancelRunning(view: View) {
        view.animate().cancel()
        activeValueAnimators.remove(view)?.cancel()
    }

    private fun resetVisualState(view: View) {
        view.clipBounds = null
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.rotationX = 0f
        view.rotationY = 0f
        view.alpha = 1f
    }

    fun animateSideDrawer(
        drawer: View,
        isLeft: Boolean,
        open: Boolean,
        config: HilmTransitionConfig
    ) {
        cancelRunning(drawer)
        val clean = config.sanitized()
        val density = drawer.resources.displayMetrics.density
        val slideOffset = (if (isLeft) -84f else 84f) * density

        if (!shouldAnimate(drawer.context, clean)) {
            resetVisualState(drawer)
            drawer.visibility = if (open) View.VISIBLE else View.GONE
            drawer.alpha = if (open) 1f else 0f
            return
        }

        val duration = clean.durationMs.toLong()
        val exitDuration = (clean.durationMs * 0.82f).toLong().coerceAtLeast(140L)

        if (open) {
            drawer.visibility = View.VISIBLE
            resetVisualState(drawer)
            drawer.cameraDistance = 14f * maxOf(drawer.width, (120 * density).toInt()) * density

            when (clean.effect) {
                HilmTransitionEffect.PORTAL -> {
                    drawer.alpha = 0f
                    drawer.translationX = slideOffset * 0.7f
                    drawer.scaleX = 0.86f
                    drawer.scaleY = 0.86f
                    drawer.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(duration)
                        .setInterpolator(OvershootInterpolator(0.85f))
                        .start()
                }
                HilmTransitionEffect.DIGITAL_DISINTEGRATION -> {
                    drawer.alpha = 0f
                    drawer.translationX = slideOffset * 0.25f
                    drawer.scaleX = 0.92f
                    drawer.scaleY = 1.04f
                    drawer.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(duration)
                        .setInterpolator(quintOut)
                        .start()
                }
                HilmTransitionEffect.LIGHT_SWEEP -> {
                    if (drawer.width > 0 && drawer.height > 0) {
                        val w = drawer.width
                        val h = drawer.height
                        drawer.alpha = 0.2f
                        drawer.translationX = slideOffset * 0.16f
                        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                            this.duration = duration
                            interpolator = quintOut
                            addUpdateListener { a ->
                                val t = a.animatedValue as Float
                                drawer.alpha = 0.2f + 0.8f * t
                                drawer.translationX = slideOffset * 0.16f * (1f - t)
                                val span = (w * t).toInt().coerceIn(1, w)
                                if (isLeft) {
                                    clipRect.set(0, 0, span, h)
                                } else {
                                    clipRect.set(w - span, 0, w, h)
                                }
                                drawer.clipBounds = clipRect
                            }
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    activeValueAnimators.remove(drawer)
                                    drawer.clipBounds = null
                                    drawer.alpha = 1f
                                    drawer.translationX = 0f
                                }

                                override fun onAnimationCancel(animation: Animator) {
                                    activeValueAnimators.remove(drawer)
                                    drawer.clipBounds = null
                                }
                            })
                            start()
                        }
                        activeValueAnimators[drawer] = anim
                    } else {
                        drawer.alpha = 0f
                        drawer.translationX = slideOffset * 0.65f
                        drawer.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(duration)
                            .setInterpolator(quintOut)
                            .start()
                    }
                }
                HilmTransitionEffect.DIMENSIONAL_SLIDE -> {
                    drawer.alpha = 0f
                    drawer.translationX = slideOffset * 1.15f
                    drawer.rotationY = if (isLeft) 28f else -28f
                    drawer.scaleX = 0.88f
                    drawer.scaleY = 0.92f
                    drawer.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .rotationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(duration)
                        .setInterpolator(quintOut)
                        .start()
                }
                HilmTransitionEffect.ENERGY_DISSOLVE -> {
                    drawer.alpha = 0f
                    drawer.scaleX = 0.95f
                    drawer.scaleY = 0.95f
                    drawer.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(duration)
                        .setInterpolator(smoothInOut)
                        .start()
                }
                HilmTransitionEffect.CLASSIC -> {
                    drawer.alpha = 0f
                    drawer.translationX = slideOffset * 0.55f
                    drawer.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(duration.coerceAtMost(260L))
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
        } else {
            if (drawer.visibility != View.VISIBLE) {
                resetVisualState(drawer)
                drawer.visibility = View.GONE
                return
            }
            drawer.cameraDistance = 14f * maxOf(drawer.width, (120 * density).toInt()) * density
            val onGone = {
                drawer.visibility = View.GONE
                resetVisualState(drawer)
            }

            when (clean.effect) {
                HilmTransitionEffect.PORTAL -> {
                    drawer.animate()
                        .alpha(0f)
                        .translationX(slideOffset * 0.65f)
                        .scaleX(0.84f)
                        .scaleY(0.84f)
                        .setDuration(exitDuration)
                        .setInterpolator(DecelerateInterpolator(1.3f))
                        .withEndAction(onGone)
                        .start()
                }
                HilmTransitionEffect.DIGITAL_DISINTEGRATION -> {
                    drawer.animate()
                        .alpha(0f)
                        .translationX(slideOffset * 0.2f)
                        .scaleX(0.92f)
                        .scaleY(1.06f)
                        .setDuration(exitDuration)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction(onGone)
                        .start()
                }
                HilmTransitionEffect.LIGHT_SWEEP -> {
                    drawer.animate()
                        .alpha(0f)
                        .translationX(slideOffset * 0.55f)
                        .setDuration(exitDuration)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction(onGone)
                        .start()
                }
                HilmTransitionEffect.DIMENSIONAL_SLIDE -> {
                    drawer.animate()
                        .alpha(0f)
                        .translationX(slideOffset * 1.1f)
                        .rotationY(if (isLeft) 26f else -26f)
                        .scaleX(0.88f)
                        .scaleY(0.92f)
                        .setDuration(exitDuration)
                        .setInterpolator(DecelerateInterpolator(1.3f))
                        .withEndAction(onGone)
                        .start()
                }
                HilmTransitionEffect.ENERGY_DISSOLVE -> {
                    drawer.animate()
                        .alpha(0f)
                        .scaleX(0.94f)
                        .scaleY(0.94f)
                        .setDuration(exitDuration)
                        .setInterpolator(smoothInOut)
                        .withEndAction(onGone)
                        .start()
                }
                HilmTransitionEffect.CLASSIC -> {
                    drawer.animate()
                        .alpha(0f)
                        .translationX(slideOffset * 0.5f)
                        .setDuration(exitDuration.coerceAtMost(200L))
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction(onGone)
                        .start()
                }
            }
        }
    }

    fun animatePanelVisibility(
        panel: View,
        show: Boolean,
        config: HilmTransitionConfig,
        fromTop: Boolean = false
    ) {
        cancelRunning(panel)
        val clean = config.sanitized()
        val density = panel.resources.displayMetrics.density
        val yShift = (if (fromTop) -22f else 26f) * density

        if (!shouldAnimate(panel.context, clean)) {
            resetVisualState(panel)
            panel.visibility = if (show) View.VISIBLE else View.GONE
            panel.alpha = if (show) 1f else 0f
            return
        }

        val enterDuration = clean.durationMs.toLong()
        val exitDuration = (clean.durationMs * 0.78f).toLong().coerceAtLeast(140L)

        if (show) {
            panel.visibility = View.VISIBLE
            resetVisualState(panel)
            panel.cameraDistance = 14f * maxOf(panel.width, (220 * density).toInt()) * density

            when (clean.effect) {
                HilmTransitionEffect.PORTAL -> {
                    panel.alpha = 0f
                    panel.scaleX = 0.88f
                    panel.scaleY = 0.88f
                    panel.translationY = yShift * 0.5f
                    panel.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setDuration(enterDuration)
                        .setInterpolator(OvershootInterpolator(0.75f))
                        .start()
                }
                HilmTransitionEffect.DIGITAL_DISINTEGRATION -> {
                    panel.alpha = 0f
                    panel.scaleX = 0.95f
                    panel.scaleY = 0.95f
                    panel.translationY = yShift * 0.35f
                    panel.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setDuration(enterDuration)
                        .setInterpolator(quintOut)
                        .start()
                }
                HilmTransitionEffect.LIGHT_SWEEP -> {
                    panel.alpha = 0f
                    panel.translationX = -26f * density
                    panel.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(enterDuration)
                        .setInterpolator(quintOut)
                        .start()
                }
                HilmTransitionEffect.DIMENSIONAL_SLIDE -> {
                    panel.alpha = 0f
                    panel.translationY = yShift
                    panel.rotationX = if (fromTop) -14f else 14f
                    panel.scaleX = 0.92f
                    panel.scaleY = 0.92f
                    panel.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .rotationX(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(enterDuration)
                        .setInterpolator(quintOut)
                        .start()
                }
                HilmTransitionEffect.ENERGY_DISSOLVE -> {
                    panel.alpha = 0f
                    panel.scaleX = 0.96f
                    panel.scaleY = 0.96f
                    panel.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(enterDuration)
                        .setInterpolator(smoothInOut)
                        .start()
                }
                HilmTransitionEffect.CLASSIC -> {
                    panel.alpha = 0f
                    panel.translationY = yShift * 0.4f
                    panel.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(enterDuration.coerceAtMost(240L))
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
        } else {
            if (panel.visibility != View.VISIBLE) {
                resetVisualState(panel)
                panel.visibility = View.GONE
                return
            }
            val onGone = {
                panel.visibility = View.GONE
                resetVisualState(panel)
            }
            panel.animate()
                .alpha(0f)
                .translationY(yShift * 0.45f)
                .scaleX(if (clean.effect == HilmTransitionEffect.PORTAL || clean.effect == HilmTransitionEffect.DIMENSIONAL_SLIDE) 0.92f else 1f)
                .scaleY(if (clean.effect == HilmTransitionEffect.PORTAL || clean.effect == HilmTransitionEffect.DIMENSIONAL_SLIDE) 0.92f else 1f)
                .setDuration(exitDuration)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction(onGone)
                .start()
        }
    }

    fun applyActivityEnterTransition(activity: Activity, config: HilmTransitionConfig) {
        if (!shouldAnimate(activity, config)) {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(R.anim.hilm_none, R.anim.hilm_none)
            return
        }
        val (enterAnim, exitAnim) = when (config.effect) {
            HilmTransitionEffect.PORTAL -> R.anim.hilm_enter_portal to R.anim.hilm_exit_portal
            HilmTransitionEffect.LIGHT_SWEEP -> R.anim.hilm_enter_sweep to R.anim.hilm_exit_sweep
            HilmTransitionEffect.DIMENSIONAL_SLIDE -> R.anim.hilm_enter_dimensional to R.anim.hilm_exit_dimensional
            HilmTransitionEffect.DIGITAL_DISINTEGRATION,
            HilmTransitionEffect.ENERGY_DISSOLVE,
            HilmTransitionEffect.CLASSIC -> R.anim.hilm_enter_classic to R.anim.hilm_exit_classic
        }
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(enterAnim, exitAnim)
    }

    fun applyActivityExitTransition(activity: Activity, config: HilmTransitionConfig) {
        applyActivityEnterTransition(activity, config)
    }
}
