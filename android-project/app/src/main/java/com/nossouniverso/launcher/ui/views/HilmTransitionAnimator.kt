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
        view.rotation = 0f
        view.rotationX = 0f
        view.rotationY = 0f
        view.alpha = 1f
    }

    fun animateIconEntrance(
        iconTarget: View,
        index: Int,
        config: HilmTransitionConfig
    ) {
        cancelRunning(iconTarget)
        val clean = config.sanitized()
        if (!shouldAnimate(iconTarget.context, clean)) {
            resetVisualState(iconTarget)
            return
        }

        val density = iconTarget.resources.displayMetrics.density
        val startDelayMs = minOf(index, 11) * 18L
        val duration = clean.durationMs.toLong()
        resetVisualState(iconTarget)
        iconTarget.cameraDistance = 14f * maxOf(iconTarget.width, (48 * density).toInt()) * density

        when (clean.effect) {
            HilmTransitionEffect.PORTAL -> {
                iconTarget.alpha = 0f
                iconTarget.scaleX = 0.36f
                iconTarget.scaleY = 0.36f
                iconTarget.rotation = -16f
                iconTarget.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotation(0f)
                    .setStartDelay(startDelayMs)
                    .setDuration(duration)
                    .setInterpolator(OvershootInterpolator(1.05f))
                    .start()
            }
            HilmTransitionEffect.DIGITAL_DISINTEGRATION -> {
                iconTarget.alpha = 0f
                iconTarget.translationX = (if (index % 2 == 0) -14f else 14f) * density
                iconTarget.translationY = 10f * density
                iconTarget.scaleX = 0.68f
                iconTarget.scaleY = 0.68f
                iconTarget.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(startDelayMs)
                    .setDuration(duration)
                    .setInterpolator(quintOut)
                    .start()
            }
            HilmTransitionEffect.LIGHT_SWEEP -> {
                if (iconTarget.width > 0 && iconTarget.height > 0) {
                    val w = iconTarget.width
                    val h = iconTarget.height
                    val iconClip = Rect()
                    iconTarget.alpha = 0.25f
                    iconTarget.translationX = -16f * density
                    val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                        this.startDelay = startDelayMs
                        this.duration = duration
                        interpolator = quintOut
                        addUpdateListener { a ->
                            val t = a.animatedValue as Float
                            iconTarget.alpha = 0.25f + 0.75f * t
                            iconTarget.translationX = -16f * density * (1f - t)
                            val right = (w * t).toInt().coerceIn(1, w)
                            iconClip.set(0, 0, right, h)
                            iconTarget.clipBounds = iconClip
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                activeValueAnimators.remove(iconTarget)
                                resetVisualState(iconTarget)
                            }

                            override fun onAnimationCancel(animation: Animator) {
                                activeValueAnimators.remove(iconTarget)
                                resetVisualState(iconTarget)
                            }
                        })
                        start()
                    }
                    activeValueAnimators[iconTarget] = anim
                } else {
                    iconTarget.alpha = 0f
                    iconTarget.translationX = -16f * density
                    iconTarget.scaleX = 0.82f
                    iconTarget.scaleY = 0.82f
                    iconTarget.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setStartDelay(startDelayMs)
                        .setDuration(duration)
                        .setInterpolator(quintOut)
                        .start()
                }
            }
            HilmTransitionEffect.DIMENSIONAL_SLIDE -> {
                iconTarget.alpha = 0f
                iconTarget.rotationY = -42f
                iconTarget.rotationX = 18f
                iconTarget.translationY = 16f * density
                iconTarget.scaleX = 0.74f
                iconTarget.scaleY = 0.74f
                iconTarget.animate()
                    .alpha(1f)
                    .rotationY(0f)
                    .rotationX(0f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(startDelayMs)
                    .setDuration(duration)
                    .setInterpolator(quintOut)
                    .start()
            }
            HilmTransitionEffect.ENERGY_DISSOLVE -> {
                iconTarget.alpha = 0f
                iconTarget.scaleX = 0.74f
                iconTarget.scaleY = 0.74f
                iconTarget.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(startDelayMs)
                    .setDuration(duration)
                    .setInterpolator(smoothInOut)
                    .start()
            }
            HilmTransitionEffect.CLASSIC -> {
                iconTarget.alpha = 0f
                iconTarget.translationY = 12f * density
                iconTarget.scaleX = 0.88f
                iconTarget.scaleY = 0.88f
                iconTarget.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(minOf(index, 9) * 12L)
                    .setDuration(duration.coerceAtMost(220L))
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    fun animateIconTapReaction(
        iconTarget: View,
        config: HilmTransitionConfig
    ) {
        cancelRunning(iconTarget)
        val clean = config.sanitized()
        if (!shouldAnimate(iconTarget.context, clean)) {
            resetVisualState(iconTarget)
            return
        }

        val density = iconTarget.resources.displayMetrics.density
        resetVisualState(iconTarget)
        iconTarget.cameraDistance = 14f * maxOf(iconTarget.width, (48 * density).toInt()) * density

        val outDuration = (clean.durationMs * 0.38f).toLong().coerceIn(85L, 160L)
        val backDuration = (clean.durationMs * 0.50f).toLong().coerceIn(115L, 210L)

        val settle = Runnable {
            iconTarget.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .rotation(0f)
                .rotationX(0f)
                .rotationY(0f)
                .alpha(1f)
                .setDuration(backDuration)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        when (clean.effect) {
            HilmTransitionEffect.PORTAL -> {
                iconTarget.animate()
                    .scaleX(1.30f)
                    .scaleY(1.30f)
                    .rotation(12f)
                    .setDuration(outDuration)
                    .setInterpolator(quintOut)
                    .withEndAction(settle)
                    .start()
            }
            HilmTransitionEffect.DIGITAL_DISINTEGRATION -> {
                iconTarget.animate()
                    .scaleX(1.20f)
                    .scaleY(0.84f)
                    .alpha(0.62f)
                    .setDuration(outDuration)
                    .setInterpolator(quintOut)
                    .withEndAction(settle)
                    .start()
            }
            HilmTransitionEffect.LIGHT_SWEEP -> {
                iconTarget.animate()
                    .translationX(8f * density)
                    .scaleX(1.16f)
                    .scaleY(1.16f)
                    .setDuration(outDuration)
                    .setInterpolator(quintOut)
                    .withEndAction(settle)
                    .start()
            }
            HilmTransitionEffect.DIMENSIONAL_SLIDE -> {
                iconTarget.animate()
                    .rotationY(-28f)
                    .rotationX(14f)
                    .scaleX(1.18f)
                    .scaleY(1.18f)
                    .setDuration(outDuration)
                    .setInterpolator(quintOut)
                    .withEndAction(settle)
                    .start()
            }
            HilmTransitionEffect.ENERGY_DISSOLVE -> {
                iconTarget.animate()
                    .scaleX(1.22f)
                    .scaleY(1.22f)
                    .alpha(0.70f)
                    .setDuration(outDuration)
                    .setInterpolator(smoothInOut)
                    .withEndAction(settle)
                    .start()
            }
            HilmTransitionEffect.CLASSIC -> {
                iconTarget.animate()
                    .scaleX(1.16f)
                    .scaleY(1.16f)
                    .setDuration(85L)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction(settle)
                    .start()
            }
        }
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
        val slideOffset = (if (isLeft) -98f else 98f) * density

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
