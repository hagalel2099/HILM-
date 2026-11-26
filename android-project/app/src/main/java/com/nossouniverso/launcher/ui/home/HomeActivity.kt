package com.nossouniverso.launcher.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.nossouniverso.launcher.NossoUniversoApp
import com.nossouniverso.launcher.R
import com.nossouniverso.launcher.data.ShortcutStorage
import com.nossouniverso.launcher.model.AppInfo
import com.nossouniverso.launcher.model.HilmTransitionConfig
import com.nossouniverso.launcher.model.ShortcutSlot
import com.nossouniverso.launcher.model.SlotPosition
import com.nossouniverso.launcher.ui.drawer.AppDrawerBottomSheet
import com.nossouniverso.launcher.ui.settings.IconPackManagerActivity
import com.nossouniverso.launcher.ui.settings.LauncherSettingsActivity
import com.nossouniverso.launcher.ui.views.HilmCoreView
import com.nossouniverso.launcher.ui.views.HilmPortalOverlayView
import com.nossouniverso.launcher.ui.views.HilmTransitionAnimator
import com.nossouniverso.launcher.ui.views.HudDockSlotView
import com.nossouniverso.launcher.ui.views.HudFrameView
import com.nossouniverso.launcher.ui.views.HudPlateButton
import com.nossouniverso.launcher.updater.ApkDownloadResult
import com.nossouniverso.launcher.updater.LauncherUpdateManager
import com.nossouniverso.launcher.updater.UpdateCheckResult
import com.nossouniverso.launcher.updater.UpdateInstallReceiver
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max

class HomeActivity : AppCompatActivity() {

    private lateinit var app: NossoUniversoApp
    private lateinit var homeRoot: View
    private lateinit var imgThemeWallpaper: ImageView
    private lateinit var scrimLunaTop: View
    private lateinit var scrimLunaBottom: View
    private lateinit var btnLunaOrbitalCore: ImageButton
    private lateinit var hudFrameView: HudFrameView
    private lateinit var hilmCoreView: HilmCoreView
    private lateinit var editModeBar: LinearLayout
    private lateinit var btnFinishEdit: TextView
    private lateinit var btnHubControl: ImageButton

    private lateinit var btnLeftDrawerHandle: View
    private lateinit var btnRightDrawerHandle: View
    private lateinit var leftSideDrawer: LinearLayout
    private lateinit var rightSideDrawer: LinearLayout

    private lateinit var coreUpdatePopover: LinearLayout
    private lateinit var btnCloseUpdatePopover: TextView
    private lateinit var tvUpdateMessage: TextView
    private lateinit var progressUpdateDownload: ProgressBar
    private lateinit var btnUpdateAction: Button
    private lateinit var updateManager: LauncherUpdateManager
    private lateinit var hilmPortalOverlay: HilmPortalOverlayView

    private val plateViews = mutableMapOf<SlotPosition, HudPlateButton>()
    private val dockViews = mutableMapOf<SlotPosition, HudDockSlotView>()
    private lateinit var dockCenterDrawer: HudDockSlotView

    private var installedApps: List<AppInfo> = emptyList()
    private var slots: Map<SlotPosition, ShortcutSlot> = emptyMap()
    private var isEditMode = false
    private var isLeftDrawerOpen = false
    private var isRightDrawerOpen = false
    private var isPortalLaunching = false
    private var lastTransitionConfig: HilmTransitionConfig? = null
    private var pendingIconTransitionReplay = false
    private var pendingSwapSource: SlotPosition? = null
    private var pendingCustomIconTarget: SlotPosition? = null
    private var packageReceiver: BroadcastReceiver? = null
    private var installUiReceiver: BroadcastReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null

    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val reloadSeq = AtomicInteger(0)

    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gestureTriggered = false
    private var touchSlopPx = 24f

    private var updateState: UpdateUiState = UpdateUiState.IDLE
    private var availableUpdate: UpdateCheckResult.UpdateAvailable? = null
    private var downloadedApkFile: File? = null

    private enum class UpdateUiState {
        IDLE,
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        DOWNLOADING,
        PERMISSION_REQUIRED,
        READY_TO_INSTALL,
        INSTALLED_SUCCESS,
        ERROR
    }

    private val requestInstallPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apkFile = downloadedApkFile
        if (apkFile != null && apkFile.exists()) {
            if (updateManager.canInstallPackages()) {
                launchApkInstaller(apkFile)
            } else {
                updateState = UpdateUiState.PERMISSION_REQUIRED
                renderUpdatePopover(
                    message = "Permissão de instalação ainda não autorizada no Android.",
                    actionLabel = getString(R.string.update_action_authorize),
                    actionEnabled = true
                )
            }
        }
    }

    private val installApkLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                updateState = UpdateUiState.INSTALLED_SUCCESS
                renderUpdatePopover(
                    message = "Atualização instalada com sucesso.",
                    actionLabel = "Fechar",
                    actionEnabled = true
                )
            }
            Activity.RESULT_CANCELED -> {
                updateState = UpdateUiState.READY_TO_INSTALL
                renderUpdatePopover(
                    message = "Instalação cancelada pelo usuário. Você pode tentar novamente.",
                    actionLabel = "Instalar agora",
                    actionEnabled = true
                )
            }
            else -> {
                updateState = UpdateUiState.READY_TO_INSTALL
                renderUpdatePopover(
                    message = "Não foi possível concluir a instalação no Android.",
                    actionLabel = "Tentar instalar novamente",
                    actionEnabled = true
                )
            }
        }
    }

    private val pickCustomIconLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = pendingCustomIconTarget ?: return@registerForActivityResult
        pendingCustomIconTarget = null
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Provider may not grant persistable permission
            }
            val current = slots[target] ?: return@registerForActivityResult
            if (!current.isEmpty && current.packageName != null && current.activityName != null) {
                slots = app.shortcutStorage.assignSlot(
                    position = target,
                    packageName = current.packageName,
                    activityName = current.activityName,
                    customLabel = current.customLabel,
                    customIconUri = uri.toString()
                )
                renderAllSlots()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0f }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        setContentView(R.layout.activity_home)
        homeRoot = findViewById(R.id.homeRoot)
        homeRoot.setBackgroundColor(Color.TRANSPARENT)

        touchSlopPx = ViewConfiguration.get(this).scaledTouchSlop.toFloat()

        app = application as NossoUniversoApp
        updateManager = LauncherUpdateManager(this)
        bindViews()
        setupListeners()
        preventHomeBackExit()
        registerInstallUiReceiver()
        registerBatteryReceiver()
        handlePostUpdateRelaunchIntent(intent)

        packageReceiver = app.appRepository.registerPackageReceiver {
            runOnUiThread {
                app.iconPackManager.clearCache()
                reloadAppsAndRender()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePostUpdateRelaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        isPortalLaunching = false
        if (::hilmPortalOverlay.isInitialized) {
            hilmPortalOverlay.cancelPortal()
        }
        val currentTransitionConfig = app.shortcutStorage.getTransitionConfig()
        if (lastTransitionConfig != null && lastTransitionConfig != currentTransitionConfig) {
            pendingIconTransitionReplay = true
        }
        lastTransitionConfig = currentTransitionConfig

        val opacityPercent = app.shortcutStorage.getPanelOpacityPercent()
        hudFrameView.panelOpacityPercent = opacityPercent
        plateViews.values.forEach { it.panelOpacityPercent = opacityPercent }
        dockViews.values.forEach { it.panelOpacityPercent = opacityPercent }
        applyLauncherTheme()
        reloadAppsAndRender()
    }

    override fun onStop() {
        isPortalLaunching = false
        if (::hilmPortalOverlay.isInitialized) {
            hilmPortalOverlay.cancelPortal()
        }
        if (::leftSideDrawer.isInitialized) HilmTransitionAnimator.cancelRunning(leftSideDrawer)
        if (::rightSideDrawer.isInitialized) HilmTransitionAnimator.cancelRunning(rightSideDrawer)
        if (::coreUpdatePopover.isInitialized) HilmTransitionAnimator.cancelRunning(coreUpdatePopover)
        if (::editModeBar.isInitialized) HilmTransitionAnimator.cancelRunning(editModeBar)
        super.onStop()
    }

    override fun onDestroy() {
        reloadSeq.incrementAndGet()
        bgExecutor.shutdownNow()
        updateManager.shutdown()
        packageReceiver?.let { unregisterReceiver(it) }
        installUiReceiver?.let { unregisterReceiver(it) }
        batteryReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureStartX = ev.rawX
                gestureStartY = ev.rawY
                gestureTriggered = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isEditMode && !gestureTriggered && ev.pointerCount == 1 &&
                    coreUpdatePopover.visibility != View.VISIBLE
                ) {
                    val dx = ev.rawX - gestureStartX
                    val dy = ev.rawY - gestureStartY
                    val absDx = abs(dx)
                    val absDy = abs(dy)
                    val density = resources.displayMetrics.density
                    val rootW = max(homeRoot.width, resources.displayMetrics.widthPixels).toFloat()
                    val rootH = max(homeRoot.height, resources.displayMetrics.heightPixels).toFloat()
                    val edgeZone = 56f * density
                    val horizThresh = max(touchSlopPx * 2.4f, 42f * density)
                    val vertThresh = max(touchSlopPx * 3.0f, 68f * density)

                    val fired = when {
                        gestureStartX <= edgeZone && dx > horizThresh && absDx > absDy * 1.3f && !isLeftDrawerOpen -> {
                            setSideDrawerOpen(isLeft = true, open = true)
                            true
                        }
                        isLeftDrawerOpen && gestureStartX <= 160f * density && dx < -horizThresh && absDx > absDy * 1.3f -> {
                            setSideDrawerOpen(isLeft = true, open = false)
                            true
                        }
                        gestureStartX >= rootW - edgeZone && dx < -horizThresh && absDx > absDy * 1.3f && !isRightDrawerOpen -> {
                            setSideDrawerOpen(isLeft = false, open = true)
                            true
                        }
                        isRightDrawerOpen && gestureStartX >= rootW - 160f * density && dx > horizThresh && absDx > absDy * 1.3f -> {
                            setSideDrawerOpen(isLeft = false, open = false)
                            true
                        }
                        dy < -vertThresh && absDy > absDx * 1.45f && gestureStartY < rootH * 0.90f -> {
                            openAppDrawer(targetSlot = null)
                            true
                        }
                        dy > vertThresh && absDy > absDx * 1.45f && gestureStartY > 36f * density -> {
                            expandNotificationShade()
                            true
                        }
                        else -> false
                    }

                    if (fired) {
                        gestureTriggered = true
                        val cancel = MotionEvent.obtain(ev).apply {
                            action = MotionEvent.ACTION_CANCEL
                        }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gestureTriggered) {
                    gestureTriggered = false
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun bindViews() {
        imgThemeWallpaper = findViewById(R.id.imgThemeWallpaper)
        scrimLunaTop = findViewById(R.id.scrimLunaTop)
        scrimLunaBottom = findViewById(R.id.scrimLunaBottom)
        btnLunaOrbitalCore = findViewById(R.id.btnLunaOrbitalCore)

        scrimLunaTop.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#9905070B"), Color.parseColor("#3305070B"), Color.TRANSPARENT)
        )
        scrimLunaBottom.background = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(Color.parseColor("#EB05070B"), Color.parseColor("#990A1424"), Color.TRANSPARENT)
        )
        val density = resources.displayMetrics.density
        btnLunaOrbitalCore.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#990A1424"))
            setStroke((1.2f * density).toInt().coerceAtLeast(1), Color.parseColor("#8800E5FF"))
        }

        hudFrameView = findViewById(R.id.hudFrameView)
        hilmCoreView = findViewById(R.id.hilmCoreView)
        editModeBar = findViewById(R.id.editModeBar)
        btnFinishEdit = findViewById(R.id.btnFinishEdit)
        btnHubControl = findViewById(R.id.btnHubControl)

        btnLeftDrawerHandle = findViewById(R.id.btnLeftDrawerHandle)
        btnRightDrawerHandle = findViewById(R.id.btnRightDrawerHandle)
        leftSideDrawer = findViewById(R.id.leftSideDrawer)
        rightSideDrawer = findViewById(R.id.rightSideDrawer)

        coreUpdatePopover = findViewById(R.id.coreUpdatePopover)
        btnCloseUpdatePopover = findViewById(R.id.btnCloseUpdatePopover)
        tvUpdateMessage = findViewById(R.id.tvUpdateMessage)
        progressUpdateDownload = findViewById(R.id.progressUpdateDownload)
        btnUpdateAction = findViewById(R.id.btnUpdateAction)
        hilmPortalOverlay = findViewById(R.id.hilmPortalOverlay)

        plateViews[SlotPosition.PLATE_TOP_LEFT] = findViewById(R.id.plateTopLeft)
        plateViews[SlotPosition.PLATE_TOP_RIGHT] = findViewById(R.id.plateTopRight)
        plateViews[SlotPosition.PLATE_BOTTOM_LEFT] = findViewById(R.id.plateBottomLeft)
        plateViews[SlotPosition.PLATE_BOTTOM_RIGHT] = findViewById(R.id.plateBottomRight)

        dockViews[SlotPosition.DOCK_1] = findViewById(R.id.dock1)
        dockViews[SlotPosition.DOCK_2] = findViewById(R.id.dock2)
        dockCenterDrawer = findViewById(R.id.dockCenterDrawer)
        dockViews[SlotPosition.DOCK_4] = findViewById(R.id.dock4)
        dockViews[SlotPosition.DOCK_5] = findViewById(R.id.dock5)

        val leftWingSlots = listOf(
            SlotPosition.LEFT_1 to R.id.leftSlot1,
            SlotPosition.LEFT_2 to R.id.leftSlot2,
            SlotPosition.LEFT_3 to R.id.leftSlot3,
            SlotPosition.LEFT_4 to R.id.leftSlot4,
            SlotPosition.LEFT_5 to R.id.leftSlot5
        )
        leftWingSlots.forEach { (pos, viewId) ->
            val slotView = findViewById<HudDockSlotView>(viewId)
            slotView.transparentHudMode = true
            dockViews[pos] = slotView
        }

        val rightWingSlots = listOf(
            SlotPosition.RIGHT_1 to R.id.rightSlot1,
            SlotPosition.RIGHT_2 to R.id.rightSlot2,
            SlotPosition.RIGHT_3 to R.id.rightSlot3,
            SlotPosition.RIGHT_4 to R.id.rightSlot4,
            SlotPosition.RIGHT_5 to R.id.rightSlot5
        )
        rightWingSlots.forEach { (pos, viewId) ->
            val slotView = findViewById<HudDockSlotView>(viewId)
            slotView.transparentHudMode = true
            dockViews[pos] = slotView
        }

        dockCenterDrawer.bindCenterButton("Abrir gaveta de aplicativos")
    }

    private fun setupListeners() {
        btnLeftDrawerHandle.setOnClickListener {
            hideUpdatePopover()
            setSideDrawerOpen(isLeft = true, open = !isLeftDrawerOpen)
        }

        btnRightDrawerHandle.setOnClickListener {
            hideUpdatePopover()
            setSideDrawerOpen(isLeft = false, open = !isRightDrawerOpen)
        }

        dockCenterDrawer.setOnClickListener {
            hideUpdatePopover()
            openAppDrawer(targetSlot = null)
        }

        dockCenterDrawer.setOnLongClickListener {
            openSettingsScreen()
            true
        }

        btnHubControl.setOnClickListener {
            hideUpdatePopover()
            toggleEditMode(!isEditMode)
        }

        btnHubControl.setOnLongClickListener {
            openSettingsScreen()
            true
        }

        btnFinishEdit.setOnClickListener {
            toggleEditMode(false)
        }

        hilmCoreView.setOnClickListener {
            if (isEditMode) {
                toggleEditMode(false)
            } else {
                toggleCoreUpdatePopover()
            }
        }

        hilmCoreView.setOnLongClickListener {
            openSettingsScreen()
            true
        }

        btnLunaOrbitalCore.setOnClickListener {
            if (isEditMode) {
                toggleEditMode(false)
            } else {
                toggleCoreUpdatePopover()
            }
        }

        btnLunaOrbitalCore.setOnLongClickListener {
            openSettingsScreen()
            true
        }

        btnCloseUpdatePopover.setOnClickListener {
            hideUpdatePopover()
        }

        btnUpdateAction.setOnClickListener {
            handleUpdateActionTap()
        }

        val allSlotViews: Map<SlotPosition, View> = plateViews + dockViews
        allSlotViews.forEach { (position, view) ->
            view.setOnClickListener { handleSlotTap(position) }
            view.setOnLongClickListener {
                if (isEditMode) {
                    val clip = ClipData.newPlainText("slot", position.id)
                    view.startDragAndDrop(clip, View.DragShadowBuilder(view), position, 0)
                } else {
                    showSlotOptionsDialog(position)
                }
                true
            }
            view.setOnDragListener { _, event -> handleSlotDrag(position, event) }
        }
    }

    private fun applyLauncherTheme() {
        val activeTheme = app.shortcutStorage.getLauncherTheme()
        val isLuna = activeTheme == ShortcutStorage.THEME_LUNA
        val isMaya = activeTheme == ShortcutStorage.THEME_MAYA

        homeRoot.setBackgroundColor(
            when {
                isLuna -> Color.parseColor("#05070B")
                isMaya -> Color.parseColor("#0B0E14")
                else -> Color.TRANSPARENT
            }
        )
        if (isLuna || isMaya) {
            imgThemeWallpaper.setImageResource(
                if (isMaya) {
                    R.drawable.wallpaper_maya_holographic_presence
                } else {
                    R.drawable.wallpaper_luna_hilm
                }
            )
            imgThemeWallpaper.visibility = View.VISIBLE
            scrimLunaTop.visibility = View.VISIBLE
            scrimLunaBottom.visibility = View.VISIBLE
        } else {
            imgThemeWallpaper.visibility = View.GONE
            scrimLunaTop.visibility = View.GONE
            scrimLunaBottom.visibility = View.GONE
        }
        btnLunaOrbitalCore.visibility = if (isLuna) View.VISIBLE else View.GONE

        // Keep hilmCoreView INVISIBLE in Luna mode (side-drawer anchor) and VISIBLE in Maya/Default
        hilmCoreView.visibility = if (isLuna) View.INVISIBLE else View.VISIBLE
        hilmCoreView.isClickable = !isLuna
        hilmCoreView.isFocusable = !isLuna
        hilmCoreView.mayaThemeMode = isMaya

        hudFrameView.lunaThemeMode = isLuna
        hudFrameView.mayaThemeMode = isMaya
        hilmPortalOverlay.lunaThemeMode = isLuna
        hilmPortalOverlay.mayaThemeMode = isMaya
        plateViews.values.forEach {
            it.lunaThemeMode = isLuna
            it.mayaThemeMode = isMaya
        }
        dockViews.values.forEach {
            it.lunaThemeMode = isLuna
            it.mayaThemeMode = isMaya
        }
        dockCenterDrawer.lunaThemeMode = isLuna
        dockCenterDrawer.mayaThemeMode = isMaya

        when {
            isLuna -> {
                btnLunaOrbitalCore.setColorFilter(Color.parseColor("#00E5FF"))
                btnHubControl.setColorFilter(Color.parseColor("#00E5FF"))
            }
            isMaya -> {
                btnHubControl.setColorFilter(Color.parseColor("#F5B942"))
            }
            else -> {
                btnHubControl.clearColorFilter()
            }
        }

        val rootLayout = homeRoot as? ConstraintLayout ?: return
        val marginPx = (if (isLuna) 14f else 4f) * resources.displayMetrics.density
        val constraints = ConstraintSet()
        constraints.clone(rootLayout)
        constraints.clear(R.id.coreUpdatePopover, ConstraintSet.TOP)
        constraints.clear(R.id.coreUpdatePopover, ConstraintSet.BOTTOM)
        if (isLuna) {
            constraints.connect(
                R.id.coreUpdatePopover,
                ConstraintSet.BOTTOM,
                R.id.platesContainer,
                ConstraintSet.TOP,
                marginPx.toInt()
            )
        } else {
            constraints.connect(
                R.id.coreUpdatePopover,
                ConstraintSet.TOP,
                R.id.hilmCoreView,
                ConstraintSet.BOTTOM,
                marginPx.toInt()
            )
        }
        constraints.applyTo(rootLayout)
    }

    private fun openSettingsScreen() {
        startActivity(Intent(this, LauncherSettingsActivity::class.java))
        HilmTransitionAnimator.applyActivityEnterTransition(
            this,
            app.shortcutStorage.getTransitionConfig()
        )
    }

    private fun setSideDrawerOpen(isLeft: Boolean, open: Boolean, animate: Boolean = true) {
        if (isLeft) isLeftDrawerOpen = open else isRightDrawerOpen = open
        val drawer = if (isLeft) leftSideDrawer else rightSideDrawer
        val baseConfig = app.shortcutStorage.getTransitionConfig()
        val effectiveConfig = if (animate) baseConfig else baseConfig.copy(enabled = false)
        HilmTransitionAnimator.animateSideDrawer(
            drawer = drawer,
            isLeft = isLeft,
            open = open,
            config = effectiveConfig
        )
        if (open && animate) {
            val wingPositions = if (isLeft) {
                listOf(
                    SlotPosition.LEFT_1,
                    SlotPosition.LEFT_2,
                    SlotPosition.LEFT_3,
                    SlotPosition.LEFT_4,
                    SlotPosition.LEFT_5
                )
            } else {
                listOf(
                    SlotPosition.RIGHT_1,
                    SlotPosition.RIGHT_2,
                    SlotPosition.RIGHT_3,
                    SlotPosition.RIGHT_4,
                    SlotPosition.RIGHT_5
                )
            }
            wingPositions.forEachIndexed { idx, pos ->
                dockViews[pos]?.getPortalOriginView()?.let { iconView ->
                    HilmTransitionAnimator.animateIconEntrance(iconView, idx, effectiveConfig)
                }
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun expandNotificationShade() {
        try {
            val statusBarService = getSystemService("statusbar") ?: return
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val method = statusBarManager.getMethod("expandNotificationsPanel")
            method.invoke(statusBarService)
        } catch (_: Exception) {
            // Some OEMs restrict programmatic expansion on non-system signatures
        }
    }

    private fun handleSlotDrag(targetPosition: SlotPosition, event: DragEvent): Boolean {
        return when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> event.localState is SlotPosition
            DragEvent.ACTION_DROP -> {
                val sourcePosition = event.localState as? SlotPosition ?: return false
                if (sourcePosition != targetPosition) {
                    slots = app.shortcutStorage.swapSlots(sourcePosition, targetPosition)
                    renderAllSlots()
                }
                true
            }
            else -> true
        }
    }

    private fun handleSlotTap(position: SlotPosition) {
        if (isEditMode) {
            val pending = pendingSwapSource
            if (pending == null) {
                pendingSwapSource = position
                Toast.makeText(
                    this,
                    "Toque em outro espaço para trocar de posição ou segure para arrastar.",
                    Toast.LENGTH_SHORT
                ).show()
            } else if (pending == position) {
                pendingSwapSource = null
                showSlotOptionsDialog(position)
            } else {
                slots = app.shortcutStorage.swapSlots(pending, position)
                pendingSwapSource = null
                renderAllSlots()
            }
            return
        }

        val slot = slots[position]
        if (slot == null || slot.isEmpty || slot.packageName == null) {
            openAppDrawer(targetSlot = position)
            return
        }

        if (isPortalLaunching) return
        isPortalLaunching = true

        val plateView = plateViews[position]
        val dockView = dockViews[position]
        val originView: View? = plateView?.getPortalOriginView() ?: dockView?.getPortalOriginView()
        val originDrawable = plateView?.getPortalIconDrawable()
            ?: dockView?.getPortalIconDrawable()
            ?: app.iconPackManager.resolveCustomUriDrawable(slot.customIconUri)

        val transitionConfig = app.shortcutStorage.getTransitionConfig()
        plateView?.triggerPortalLaunchReaction(transitionConfig)
        dockView?.triggerPortalLaunchReaction(transitionConfig)
        hilmPortalOverlay.playLaunchTransition(
            originView = originView,
            iconDrawable = originDrawable,
            config = transitionConfig,
            onLaunch = {
                val options = HilmPortalOverlayView.buildLaunchActivityOptions(
                    this,
                    originView,
                    transitionConfig
                )
                val launched = app.appRepository.launchApp(
                    packageName = slot.packageName,
                    activityName = slot.activityName,
                    launchOptions = options
                )
                if (launched) {
                    app.shortcutStorage.recordRecentPackage(slot.packageName)
                } else {
                    isPortalLaunching = false
                    Toast.makeText(this, "Aplicativo não encontrado.", Toast.LENGTH_SHORT).show()
                }
            },
            onFinished = {
                isPortalLaunching = false
            }
        )
    }

    private fun showSlotOptionsDialog(position: SlotPosition) {
        val slot = slots[position]
        val hasApp = slot != null && !slot.isEmpty
        val options = mutableListOf(
            "Selecionar aplicativo",
            if (isEditMode) "Sair do modo de edição" else "Reorganizar posições",
            "Gerenciar pacotes de ícones"
        )
        if (hasApp) {
            options.add("Renomear atalho")
            options.add("Escolher imagem própria para ícone")
            options.add("Remover atalho da tela inicial")
        }

        AlertDialog.Builder(this)
            .setTitle("Gerenciar atalho")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Selecionar aplicativo" -> openAppDrawer(targetSlot = position)
                    "Reorganizar posições" -> toggleEditMode(true)
                    "Sair do modo de edição" -> toggleEditMode(false)
                    "Gerenciar pacotes de ícones" -> {
                        startActivity(Intent(this, IconPackManagerActivity::class.java))
                        HilmTransitionAnimator.applyActivityEnterTransition(
                            this,
                            app.shortcutStorage.getTransitionConfig()
                        )
                    }
                    "Renomear atalho" -> showRenameSlotDialog(position, slot?.customLabel.orEmpty())
                    "Escolher imagem própria para ícone" -> {
                        pendingCustomIconTarget = position
                        pickCustomIconLauncher.launch(arrayOf("image/*"))
                    }
                    "Remover atalho da tela inicial" -> {
                        slots = app.shortcutStorage.clearSlot(position)
                        renderAllSlots()
                    }
                }
            }
            .show()
    }

    private fun showRenameSlotDialog(position: SlotPosition, currentLabel: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            hint = "Nome personalizado (deixe vazio para padrão)"
            setText(currentLabel)
            setSelection(text.length)
            val pad = (18 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        AlertDialog.Builder(this)
            .setTitle("Renomear atalho")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                slots = app.shortcutStorage.renameSlot(position, input.text.toString())
                renderAllSlots()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openAppDrawer(targetSlot: SlotPosition?) {
        val title = if (targetSlot != null) {
            "Escolher aplicativo para o atalho"
        } else {
            "Aplicativos Instalados"
        }

        val byPkg = installedApps.associateBy { it.packageName }
        val recentApps = app.shortcutStorage.getRecentPackages().mapNotNull { byPkg[it] }

        val transitionConfig = app.shortcutStorage.getTransitionConfig()
        val sheet = AppDrawerBottomSheet(
            title = title,
            apps = installedApps,
            recentApps = recentApps,
            isLaunchMode = (targetSlot == null),
            transitionConfig = transitionConfig,
            onSelectApp = { selectedApp, anchorView ->
                if (targetSlot != null) {
                    slots = app.shortcutStorage.assignSlot(
                        position = targetSlot,
                        packageName = selectedApp.packageName,
                        activityName = selectedApp.activityName
                    )
                    renderAllSlots()
                } else {
                    val options = HilmPortalOverlayView.buildLaunchActivityOptions(
                        this,
                        anchorView,
                        transitionConfig
                    )
                    if (app.appRepository.launchApp(
                            packageName = selectedApp.packageName,
                            activityName = selectedApp.activityName,
                            launchOptions = options
                        )
                    ) {
                        app.shortcutStorage.recordRecentPackage(selectedApp.packageName)
                    } else {
                        Toast.makeText(this, "Aplicativo não encontrado.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onPinShortcut = { selectedApp ->
                val firstEmpty = SlotPosition.values().firstOrNull { pos ->
                    slots[pos]?.isEmpty != false
                }
                if (firstEmpty != null) {
                    slots = app.shortcutStorage.assignSlot(
                        position = firstEmpty,
                        packageName = selectedApp.packageName,
                        activityName = selectedApp.activityName
                    )
                    renderAllSlots()
                    if (firstEmpty.isLeftDrawer) setSideDrawerOpen(isLeft = true, open = true)
                    if (firstEmpty.isRightDrawer) setSideDrawerOpen(isLeft = false, open = true)
                } else {
                    Toast.makeText(
                        this,
                        "Todos os 18 atalhos estão ocupados. Segure um espaço para substituir.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        sheet.show(supportFragmentManager, "AppDrawerBottomSheet")
    }

    private fun toggleEditMode(active: Boolean) {
        isEditMode = active
        pendingSwapSource = null
        HilmTransitionAnimator.animatePanelVisibility(
            panel = editModeBar,
            show = active,
            config = app.shortcutStorage.getTransitionConfig(),
            fromTop = true
        )
        plateViews.values.forEach { it.isEditModeActive = active }
        dockViews.values.forEach { it.isEditModeActive = active }
    }

    private fun reloadAppsAndRender() {
        val seq = reloadSeq.incrementAndGet()
        bgExecutor.execute {
            val loadedApps = app.appRepository.loadInstalledApps()
            val loadedSlots = app.shortcutStorage.loadSlots()
            runOnUiThread {
                if (isFinishing || isDestroyed || seq != reloadSeq.get()) return@runOnUiThread
                installedApps = loadedApps
                slots = loadedSlots
                renderAllSlots()
                if (pendingIconTransitionReplay) {
                    pendingIconTransitionReplay = false
                    animateHomeIcons(app.shortcutStorage.getTransitionConfig())
                }
            }
        }
    }

    private fun animateHomeIcons(config: HilmTransitionConfig) {
        var index = 0
        plateViews.values.forEach { plate ->
            HilmTransitionAnimator.animateIconEntrance(plate.getPortalOriginView(), index++, config)
        }
        listOf(
            SlotPosition.DOCK_1,
            SlotPosition.DOCK_2,
            SlotPosition.DOCK_4,
            SlotPosition.DOCK_5
        ).forEach { pos ->
            dockViews[pos]?.getPortalOriginView()?.let { iconView ->
                HilmTransitionAnimator.animateIconEntrance(iconView, index++, config)
            }
        }
        HilmTransitionAnimator.animateIconEntrance(dockCenterDrawer.getPortalOriginView(), index, config)
    }

    private fun renderAllSlots() {
        val appByComponent = installedApps.associateBy { "${it.packageName}/${it.activityName}" }
        val appByPackage = installedApps.associateBy { it.packageName }

        plateViews.forEach { (position, view) ->
            val slot = slots[position]
            val resolved = resolveAppForSlot(slot, appByComponent, appByPackage)
            if (slot != null && resolved != null) {
                val customDrawable = app.iconPackManager.resolveCustomUriDrawable(slot.customIconUri)
                view.bindShortcut(
                    label = slot.customLabel ?: resolved.label,
                    icon = customDrawable ?: resolved.icon,
                    empty = false
                )
            } else {
                view.bindShortcut(label = getString(R.string.slot_empty), icon = null, empty = true)
            }
        }

        dockViews.forEach { (position, view) ->
            val slot = slots[position]
            val resolved = resolveAppForSlot(slot, appByComponent, appByPackage)
            if (slot != null && resolved != null) {
                val customDrawable = app.iconPackManager.resolveCustomUriDrawable(slot.customIconUri)
                val displayLabel = slot.customLabel ?: resolved.label
                view.bindIcon(customDrawable ?: resolved.icon, displayLabel)
                TooltipCompat.setTooltipText(view, displayLabel)
            } else {
                val emptyText = getString(R.string.slot_empty)
                view.bindIcon(null, emptyText)
                TooltipCompat.setTooltipText(view, emptyText)
            }
        }
    }

    private fun resolveAppForSlot(
        slot: ShortcutSlot?,
        byComponent: Map<String, AppInfo>,
        byPackage: Map<String, AppInfo>
    ): AppInfo? {
        if (slot == null || slot.packageName.isNullOrBlank()) return null
        val key = "${slot.packageName}/${slot.activityName.orEmpty()}"
        return byComponent[key] ?: byPackage[slot.packageName]
    }

    private fun registerBatteryReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                applyBatteryIntent(intent)
            }
        }
        batteryReceiver = receiver
        val sticky = registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) {
            applyBatteryIntent(sticky)
        }
    }

    private fun applyBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else 100
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        hilmCoreView.updateBatteryTelemetry(pct, charging)
    }

    private fun toggleCoreUpdatePopover() {
        if (coreUpdatePopover.visibility == View.VISIBLE) {
            if (updateState != UpdateUiState.CHECKING && updateState != UpdateUiState.DOWNLOADING) {
                hideUpdatePopover()
            }
            return
        }

        if (updateState == UpdateUiState.UP_TO_DATE ||
            updateState == UpdateUiState.ERROR ||
            updateState == UpdateUiState.INSTALLED_SUCCESS
        ) {
            updateState = UpdateUiState.IDLE
        }

        when (updateState) {
            UpdateUiState.IDLE -> {
                val (installedVersion, _) = updateManager.getInstalledVersionInfo()
                renderUpdatePopover(
                    message = "Versão instalada: v$installedVersion",
                    actionLabel = getString(R.string.update_action_check),
                    actionEnabled = true
                )
            }
            UpdateUiState.UPDATE_AVAILABLE -> {
                val update = availableUpdate
                if (update != null) {
                    renderUpdatePopover(
                        message = "Instalada: v${update.installedVersion} · Disponível: v${update.latestVersion}",
                        actionLabel = getString(R.string.update_action_install_now),
                        actionEnabled = true
                    )
                } else {
                    updateState = UpdateUiState.IDLE
                    val (installedVersion, _) = updateManager.getInstalledVersionInfo()
                    renderUpdatePopover(
                        message = "Versão instalada: v$installedVersion",
                        actionLabel = getString(R.string.update_action_check),
                        actionEnabled = true
                    )
                }
            }
            else -> {
                // Keep active state visible as-is
            }
        }

        HilmTransitionAnimator.animatePanelVisibility(
            panel = coreUpdatePopover,
            show = true,
            config = app.shortcutStorage.getTransitionConfig(),
            fromTop = true
        )
    }

    private fun hideUpdatePopover() {
        HilmTransitionAnimator.animatePanelVisibility(
            panel = coreUpdatePopover,
            show = false,
            config = app.shortcutStorage.getTransitionConfig(),
            fromTop = true
        )
    }

    private fun handleUpdateActionTap() {
        when (updateState) {
            UpdateUiState.IDLE,
            UpdateUiState.UP_TO_DATE,
            UpdateUiState.ERROR -> startUpdateCheck()
            UpdateUiState.UPDATE_AVAILABLE -> startUpdateDownload()
            UpdateUiState.PERMISSION_REQUIRED -> {
                try {
                    requestInstallPermissionLauncher.launch(updateManager.createInstallPermissionIntent())
                } catch (_: Exception) {
                    updateState = UpdateUiState.ERROR
                    renderUpdatePopover(
                        message = "Não foi possível abrir as permissões de instalação do Android.",
                        actionLabel = "Tentar novamente",
                        actionEnabled = true
                    )
                }
            }
            UpdateUiState.READY_TO_INSTALL -> {
                val file = downloadedApkFile
                if (file != null && file.exists()) {
                    proceedToInstallApk(file)
                } else {
                    updateState = UpdateUiState.IDLE
                    startUpdateCheck()
                }
            }
            UpdateUiState.INSTALLED_SUCCESS -> hideUpdatePopover()
            UpdateUiState.CHECKING,
            UpdateUiState.DOWNLOADING -> Unit
        }
    }

    private fun startUpdateCheck() {
        updateState = UpdateUiState.CHECKING
        availableUpdate = null
        downloadedApkFile = null
        renderUpdatePopover(
            message = getString(R.string.update_checking),
            actionLabel = "Verificando…",
            actionEnabled = false
        )

        updateManager.checkForUpdates { result ->
            when (result) {
                is UpdateCheckResult.UpToDate -> {
                    updateState = UpdateUiState.UP_TO_DATE
                    renderUpdatePopover(
                        message = "O aplicativo já está atualizado (v${result.installedVersion}).",
                        actionLabel = "Verificar novamente",
                        actionEnabled = true
                    )
                }
                is UpdateCheckResult.UpdateAvailable -> {
                    updateState = UpdateUiState.UPDATE_AVAILABLE
                    availableUpdate = result
                    renderUpdatePopover(
                        message = "Versão instalada: v${result.installedVersion}\nVersão disponível: v${result.latestVersion}",
                        actionLabel = getString(R.string.update_action_install_now),
                        actionEnabled = true
                    )
                }
                is UpdateCheckResult.NoApkAsset -> {
                    updateState = UpdateUiState.ERROR
                    renderUpdatePopover(
                        message = "Nova versão v${result.latestVersion} encontrada, mas sem pacote .apk anexado.",
                        actionLabel = "Tentar novamente",
                        actionEnabled = true
                    )
                }
                is UpdateCheckResult.Failure -> {
                    updateState = UpdateUiState.ERROR
                    renderUpdatePopover(
                        message = result.message,
                        actionLabel = "Tentar novamente",
                        actionEnabled = true
                    )
                }
            }
        }
    }

    private fun startUpdateDownload() {
        val update = availableUpdate ?: return
        updateState = UpdateUiState.DOWNLOADING
        renderUpdatePopover(
            message = "Baixando atualização v${update.latestVersion}…",
            actionLabel = "Baixando…",
            actionEnabled = false,
            showProgress = true,
            progressPercent = 0
        )

        updateManager.downloadApk(
            update = update,
            onProgress = { downloaded, total ->
                val percent = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                val mbDone = String.format(java.util.Locale.US, "%.1f", downloaded / (1024f * 1024f))
                val msg = if (total > 0L) {
                    "Baixando pacote v${update.latestVersion}… $percent% ($mbDone MB)"
                } else {
                    "Baixando pacote v${update.latestVersion}… ($mbDone MB)"
                }
                renderUpdatePopover(
                    message = msg,
                    actionLabel = "Baixando…",
                    actionEnabled = false,
                    showProgress = true,
                    progressPercent = percent
                )
            },
            onComplete = { downloadResult ->
                when (downloadResult) {
                    is ApkDownloadResult.Success -> {
                        downloadedApkFile = downloadResult.apkFile
                        proceedToInstallApk(downloadResult.apkFile)
                    }
                    is ApkDownloadResult.Failure -> {
                        updateState = UpdateUiState.ERROR
                        renderUpdatePopover(
                            message = downloadResult.message,
                            actionLabel = "Tentar novamente",
                            actionEnabled = true
                        )
                    }
                }
            }
        )
    }

    private fun proceedToInstallApk(apkFile: File) {
        if (!updateManager.canInstallPackages()) {
            updateState = UpdateUiState.PERMISSION_REQUIRED
            renderUpdatePopover(
                message = "Autorize este aplicativo a instalar pacotes de atualização no Android.",
                actionLabel = getString(R.string.update_action_authorize),
                actionEnabled = true
            )
            try {
                requestInstallPermissionLauncher.launch(updateManager.createInstallPermissionIntent())
            } catch (_: Exception) {
                // Usuário pode acionar pelo botão do popover
            }
            return
        }
        launchApkInstaller(apkFile)
    }

    private fun launchApkInstaller(apkFile: File) {
        updateState = UpdateUiState.READY_TO_INSTALL
        renderUpdatePopover(
            message = "Abrindo confirmação de instalação do Android…",
            actionLabel = "Instalar agora",
            actionEnabled = true
        )
        updateManager.installApkWithPackageInstaller(
            apkFile = apkFile,
            onFallbackLegacyIntent = { legacyIntent ->
                try {
                    installApkLauncher.launch(legacyIntent)
                } catch (e: Exception) {
                    UpdateInstallReceiver.markPendingUpdateRelaunch(this, false)
                    updateState = UpdateUiState.ERROR
                    renderUpdatePopover(
                        message = "Falha ao abrir o instalador do Android: ${e.localizedMessage ?: "erro desconhecido"}",
                        actionLabel = "Tentar novamente",
                        actionEnabled = true
                    )
                }
            },
            onSessionError = { errorMsg ->
                UpdateInstallReceiver.markPendingUpdateRelaunch(this, false)
                updateState = UpdateUiState.ERROR
                renderUpdatePopover(
                    message = errorMsg,
                    actionLabel = "Tentar novamente",
                    actionEnabled = true
                )
            }
        )
    }

    private fun registerInstallUiReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != UpdateInstallReceiver.ACTION_INSTALL_UI_RESULT) return
                val status = intent.getIntExtra(
                    UpdateInstallReceiver.EXTRA_INSTALL_STATUS,
                    PackageInstaller.STATUS_FAILURE
                )
                val message = intent.getStringExtra(UpdateInstallReceiver.EXTRA_INSTALL_MESSAGE)
                    .orEmpty()

                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> {
                        updateState = UpdateUiState.INSTALLED_SUCCESS
                        renderUpdatePopover(
                            message = message.ifBlank { "Atualização instalada com sucesso." },
                            actionLabel = "Fechar",
                            actionEnabled = true
                        )
                    }
                    PackageInstaller.STATUS_FAILURE_ABORTED -> {
                        updateState = UpdateUiState.READY_TO_INSTALL
                        renderUpdatePopover(
                            message = message.ifBlank { "Instalação cancelada pelo usuário. Você pode tentar novamente." },
                            actionLabel = "Instalar agora",
                            actionEnabled = true
                        )
                    }
                    else -> {
                        updateState = UpdateUiState.READY_TO_INSTALL
                        renderUpdatePopover(
                            message = message.ifBlank { "Não foi possível concluir a instalação no Android." },
                            actionLabel = "Tentar instalar novamente",
                            actionEnabled = true
                        )
                    }
                }
            }
        }
        installUiReceiver = receiver
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(UpdateInstallReceiver.ACTION_INSTALL_UI_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun handlePostUpdateRelaunchIntent(incomingIntent: Intent?) {
        if (incomingIntent?.getBooleanExtra(UpdateInstallReceiver.EXTRA_RELAUNCHED_AFTER_UPDATE, false) == true) {
            incomingIntent.removeExtra(UpdateInstallReceiver.EXTRA_RELAUNCHED_AFTER_UPDATE)
            val (installedVersion, _) = updateManager.getInstalledVersionInfo()
            updateState = UpdateUiState.INSTALLED_SUCCESS
            availableUpdate = null
            downloadedApkFile = null
            renderUpdatePopover(
                message = "Atualização concluída com sucesso (v$installedVersion).",
                actionLabel = "Fechar",
                actionEnabled = true
            )
            HilmTransitionAnimator.animatePanelVisibility(
                panel = coreUpdatePopover,
                show = true,
                config = app.shortcutStorage.getTransitionConfig(),
                fromTop = true
            )
        }
    }

    private fun renderUpdatePopover(
        message: String,
        actionLabel: String,
        actionEnabled: Boolean,
        showProgress: Boolean = false,
        progressPercent: Int = 0
    ) {
        tvUpdateMessage.text = message
        btnUpdateAction.text = actionLabel
        btnUpdateAction.isEnabled = actionEnabled
        btnUpdateAction.alpha = if (actionEnabled) 1f else 0.55f
        progressUpdateDownload.visibility = if (showProgress) View.VISIBLE else View.GONE
        if (showProgress) {
            progressUpdateDownload.isIndeterminate = progressPercent <= 0
            progressUpdateDownload.progress = progressPercent
        }
    }

    private fun preventHomeBackExit() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (coreUpdatePopover.visibility == View.VISIBLE &&
                    updateState != UpdateUiState.CHECKING &&
                    updateState != UpdateUiState.DOWNLOADING
                ) {
                    hideUpdatePopover()
                } else if (isLeftDrawerOpen || isRightDrawerOpen) {
                    if (isLeftDrawerOpen) setSideDrawerOpen(isLeft = true, open = false)
                    if (isRightDrawerOpen) setSideDrawerOpen(isLeft = false, open = false)
                } else if (isEditMode) {
                    toggleEditMode(false)
                }
            }
        })
    }
}
