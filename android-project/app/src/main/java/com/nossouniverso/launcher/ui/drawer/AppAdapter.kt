package com.nossouniverso.launcher.ui.drawer

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nossouniverso.launcher.R
import com.nossouniverso.launcher.model.AppInfo
import com.nossouniverso.launcher.model.HilmTransitionConfig
import com.nossouniverso.launcher.ui.views.HilmTransitionAnimator

class AppAdapter(
    private val onAppClick: (AppInfo, View) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit,
    private val itemLayoutResId: Int = R.layout.item_app_drawer,
    private val transitionConfig: HilmTransitionConfig = HilmTransitionConfig()
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private val fullList = mutableListOf<AppInfo>()
    private val filteredList = mutableListOf<AppInfo>()
    private val animatedPositions = HashSet<Int>()

    fun submitList(apps: List<AppInfo>) {
        fullList.clear()
        fullList.addAll(apps)
        filteredList.clear()
        filteredList.addAll(apps)
        animatedPositions.clear()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val q = query.trim().lowercase()
        filteredList.clear()
        if (q.isEmpty()) {
            filteredList.addAll(fullList)
        } else {
            filteredList.addAll(fullList.filter { it.label.lowercase().contains(q) })
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(itemLayoutResId, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(filteredList[position], position)
    }

    override fun getItemCount(): Int = filteredList.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.imgAppIcon)
        private val labelView: TextView = itemView.findViewById(R.id.txtAppLabel)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(app: AppInfo, position: Int) {
            HilmTransitionAnimator.cancelRunning(iconView)
            iconView.clipBounds = null
            iconView.translationX = 0f
            iconView.translationY = 0f
            iconView.rotation = 0f
            iconView.rotationX = 0f
            iconView.rotationY = 0f
            iconView.scaleX = 1f
            iconView.scaleY = 1f
            iconView.alpha = 1f
            iconView.setImageDrawable(app.icon)
            labelView.text = app.label

            if (position < 20 && animatedPositions.add(position)) {
                HilmTransitionAnimator.animateIconEntrance(iconView, position, transitionConfig)
            }

            itemView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        HilmTransitionAnimator.cancelRunning(iconView)
                        iconView.animate()
                            .scaleX(0.88f)
                            .scaleY(0.88f)
                            .setDuration(85L)
                            .start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        iconView.animate().cancel()
                        iconView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(180L)
                            .setInterpolator(OvershootInterpolator(1.5f))
                            .start()
                    }
                }
                false
            }

            itemView.setOnClickListener {
                HilmTransitionAnimator.animateIconTapReaction(iconView, transitionConfig)
                onAppClick(app, iconView)
            }
            itemView.setOnLongClickListener {
                onAppLongClick(app)
                true
            }
        }
    }
}
