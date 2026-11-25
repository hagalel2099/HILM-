package com.nossouniverso.launcher.ui.drawer

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nossouniverso.launcher.R
import com.nossouniverso.launcher.data.ShortcutStorage
import com.nossouniverso.launcher.model.AppInfo
import com.nossouniverso.launcher.model.HilmTransitionConfig
import com.nossouniverso.launcher.ui.views.HilmPortalOverlayView
import com.nossouniverso.launcher.ui.views.HilmTransitionAnimator

class AppDrawerBottomSheet(
    private val title: String,
    private val apps: List<AppInfo>,
    private val recentApps: List<AppInfo> = emptyList(),
    private val isLaunchMode: Boolean = false,
    private val transitionConfig: HilmTransitionConfig = HilmTransitionConfig(),
    private val onSelectApp: (AppInfo, View) -> Unit,
    private val onPinShortcut: ((AppInfo) -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var portalOverlay: HilmPortalOverlayView? = null
    private var isLaunching: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_app_drawer, container, false)
    }

    override fun onStart() {
        super.onStart()
        val bottomSheetDialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        val sheetHeight = (resources.displayMetrics.heightPixels * 0.90f).toInt()
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = sheetHeight
        }
        bottomSheet.requestLayout()

        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = sheetHeight
        behavior.isFitToContents = true
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        if (isLaunchMode && portalOverlay == null) {
            val decor = bottomSheetDialog.window?.decorView as? ViewGroup
            if (decor != null) {
                val isLuna = ShortcutStorage(requireContext()).getLauncherTheme() == ShortcutStorage.THEME_LUNA
                val overlay = HilmPortalOverlayView(requireContext()).apply {
                    elevation = 32f * resources.displayMetrics.density
                    lunaThemeMode = isLuna
                }
                decor.addView(
                    overlay,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                portalOverlay = overlay
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        HilmTransitionAnimator.animatePanelVisibility(
            panel = view,
            show = true,
            config = transitionConfig,
            fromTop = false
        )

        val titleView = view.findViewById<TextView>(R.id.txtDrawerTitle)
        val searchEdit = view.findViewById<EditText>(R.id.editSearchApps)
        val recentHeader = view.findViewById<TextView>(R.id.txtRecentHeader)
        val recentRecycler = view.findViewById<RecyclerView>(R.id.recyclerRecentApps)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerApps)

        val isLuna = ShortcutStorage(requireContext()).getLauncherTheme() == ShortcutStorage.THEME_LUNA
        if (isLuna) {
            view.setBackgroundColor(Color.parseColor("#F205070B"))
            titleView.setTextColor(ContextCompat.getColor(requireContext(), R.color.luna_white_blue))
            recentHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.luna_cyan))
            val strokePx = (resources.displayMetrics.density).toInt().coerceAtLeast(1)
            searchEdit.background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC0A1424"))
                cornerRadius = 10f * resources.displayMetrics.density
                setStroke(strokePx, Color.parseColor("#6600E5FF"))
            }
            searchEdit.setTextColor(ContextCompat.getColor(requireContext(), R.color.luna_white_blue))
            searchEdit.setHintTextColor(Color.parseColor("#88D9F7FF"))
        }

        titleView.text = title

        // Ensure BottomSheetBehavior binds vertical nested scroll exclusively to recyclerApps
        ViewCompat.setNestedScrollingEnabled(recentRecycler, false)
        ViewCompat.setNestedScrollingEnabled(recycler, true)

        val hasRecents = recentApps.isNotEmpty()
        recentHeader.visibility = if (hasRecents) View.VISIBLE else View.GONE
        recentRecycler.visibility = if (hasRecents) View.VISIBLE else View.GONE

        if (hasRecents) {
            val recentAdapter = AppAdapter(
                onAppClick = { app, anchorView -> handleAppSelection(app, anchorView) },
                onAppLongClick = { app -> showAppContextMenu(app) },
                itemLayoutResId = R.layout.item_app_drawer_recent,
                transitionConfig = transitionConfig
            )
            recentRecycler.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            recentRecycler.adapter = recentAdapter
            recentAdapter.submitList(recentApps)
        }

        val adapter = AppAdapter(
            onAppClick = { app, anchorView -> handleAppSelection(app, anchorView) },
            onAppLongClick = { app -> showAppContextMenu(app) },
            transitionConfig = transitionConfig
        )
        recycler.layoutManager = GridLayoutManager(requireContext(), 4)
        recycler.adapter = adapter
        adapter.submitList(apps)

        val baseBottomPadding = (64 * resources.displayMetrics.density).toInt()
        recycler.clipToPadding = false
        recycler.setPadding(
            recycler.paddingLeft,
            recycler.paddingTop,
            recycler.paddingRight,
            baseBottomPadding
        )
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val navAndImeBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime()
            ).bottom
            recycler.setPadding(
                recycler.paddingLeft,
                recycler.paddingTop,
                recycler.paddingRight,
                baseBottomPadding + navAndImeBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)

        var touchDownY = 0f
        recycler.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownY = event.y
                    if (v.canScrollVertically(-1) || v.canScrollVertically(1)) {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - touchDownY
                    val shouldRecyclerHandle =
                        v.canScrollVertically(-1) || (dy < 0f && v.canScrollVertically(1))
                    v.parent?.requestDisallowInterceptTouchEvent(shouldRecyclerHandle)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                adapter.filter(query)
                val showRecentsNow = hasRecents && query.isBlank()
                recentHeader.visibility = if (showRecentsNow) View.VISIBLE else View.GONE
                recentRecycler.visibility = if (showRecentsNow) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun handleAppSelection(app: AppInfo, anchorView: View) {
        if (!isLaunchMode) {
            onSelectApp(app, anchorView)
            dismiss()
            return
        }
        if (isLaunching) return
        isLaunching = true

        val overlay = portalOverlay
        val ctx = context
        if (overlay == null || ctx == null || !HilmTransitionAnimator.shouldAnimate(ctx, transitionConfig)) {
            onSelectApp(app, anchorView)
            dismissAllowingStateLoss()
            return
        }

        overlay.playLaunchTransition(
            originView = anchorView,
            iconDrawable = app.icon,
            config = transitionConfig,
            onLaunch = {
                if (isAdded) {
                    onSelectApp(app, anchorView)
                }
            },
            onFinished = {
                isLaunching = false
                if (isAdded) {
                    dismissAllowingStateLoss()
                }
            }
        )
    }

    override fun onDismiss(dialog: DialogInterface) {
        cleanupPortalOverlay()
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        cleanupPortalOverlay()
        super.onDestroyView()
    }

    private fun cleanupPortalOverlay() {
        isLaunching = false
        val overlay = portalOverlay ?: return
        portalOverlay = null
        overlay.cancelPortal()
        (overlay.parent as? ViewGroup)?.removeView(overlay)
    }

    private fun showAppContextMenu(app: AppInfo) {
        val options = mutableListOf<String>()
        if (onPinShortcut != null) {
            options.add("Fixar na tela inicial")
        }
        options.add("Informações do aplicativo")
        options.add("Desinstalar")

        AlertDialog.Builder(requireContext())
            .setTitle(app.label)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Fixar na tela inicial" -> {
                        onPinShortcut?.invoke(app)
                        dismiss()
                    }
                    "Informações do aplicativo" -> {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${app.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            dismiss()
                        } catch (_: Exception) {
                            Toast.makeText(
                                requireContext(),
                                "Não foi possível abrir as informações do aplicativo.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    "Desinstalar" -> {
                        try {
                            val intent = Intent(Intent.ACTION_DELETE).apply {
                                data = Uri.parse("package:${app.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            dismiss()
                        } catch (_: Exception) {
                            Toast.makeText(
                                requireContext(),
                                "Não foi possível iniciar a desinstalação.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .show()
    }
}
