package com.nossouniverso.launcher.ui.home

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.DragEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nossouniverso.launcher.NossoUniversoApp
import com.nossouniverso.launcher.R
import com.nossouniverso.launcher.model.AppInfo
import com.nossouniverso.launcher.model.ShortcutSlot
import com.nossouniverso.launcher.model.SlotPosition
import com.nossouniverso.launcher.ui.drawer.AppDrawerBottomSheet
import com.nossouniverso.launcher.ui.settings.LauncherSettingsActivity
import com.nossouniverso.launcher.ui.views.HilmCoreView
import com.nossouniverso.launcher.ui.views.HudDockSlotView
import com.nossouniverso.launcher.ui.views.HudFrameView
import com.nossouniverso.launcher.ui.views.HudPlateButton

class HomeActivity : AppCompatActivity() {

    private lateinit var app: NossoUniversoApp
    private lateinit var hudFrameView: HudFrameView
    private lateinit var hilmCoreView: HilmCoreView
    private lateinit var editModeBar: LinearLayout
    private lateinit var btnFinishEdit: TextView
    private lateinit var btnHubControl: ImageButton

    private val plateViews = mutableMapOf<SlotPosition, HudPlateButton>()
    private val dockViews = mutableMapOf<SlotPosition, HudDockSlotView>()
    private lateinit var dockCenterDrawer: HudDockSlotView

    private var installedApps: List<AppInfo> = emptyList()
    private var slots: Map<SlotPosition, ShortcutSlot> = emptyMap()
    private var isEditMode = false
    private var pendingSwapSource: SlotPosition? = null
    private var pendingCustomIconTarget: SlotPosition? = null
    private var packageReceiver: BroadcastReceiver? = null

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
        setContentView(R.layout.activity_home)

        app = application as NossoUniversoApp
        bindViews()
        setupListeners()
        preventHomeBackExit()

        packageReceiver = app.appRepository.registerPackageReceiver {
            runOnUiThread {
                reloadAppsAndRender()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hudFrameView.panelOpacityPercent = app.shortcutStorage.getPanelOpacityPercent()
        reloadAppsAndRender()
    }

    override fun onDestroy() {
        packageReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    private fun bindViews() {
        hudFrameView = findViewById(R.id.hudFrameView)
        hilmCoreView = findViewById(R.id.hilmCoreView)
        editModeBar = findViewById(R.id.editModeBar)
        btnFinishEdit = findViewById(R.id.btnFinishEdit)
        btnHubControl = findViewById(R.id.btnHubControl)

        plateViews[SlotPosition.PLATE_TOP_LEFT] = findViewById(R.id.plateTopLeft)
        plateViews[SlotPosition.PLATE_TOP_RIGHT] = findViewById(R.id.plateTopRight)
        plateViews[SlotPosition.PLATE_BOTTOM_LEFT] = findViewById(R.id.plateBottomLeft)
        plateViews[SlotPosition.PLATE_BOTTOM_RIGHT] = findViewById(R.id.plateBottomRight)

        dockViews[SlotPosition.DOCK_1] = findViewById(R.id.dock1)
        dockViews[SlotPosition.DOCK_2] = findViewById(R.id.dock2)
        dockCenterDrawer = findViewById(R.id.dockCenterDrawer)
        dockViews[SlotPosition.DOCK_4] = findViewById(R.id.dock4)
        dockViews[SlotPosition.DOCK_5] = findViewById(R.id.dock5)

        dockCenterDrawer.bindCenterButton("Abrir gaveta de aplicativos")
    }

    private fun setupListeners() {
        dockCenterDrawer.setOnClickListener {
            openAppDrawer(targetSlot = null)
        }

        dockCenterDrawer.setOnLongClickListener {
            startActivity(Intent(this, LauncherSettingsActivity::class.java))
            true
        }

        btnHubControl.setOnClickListener {
            toggleEditMode(!isEditMode)
        }

        btnHubControl.setOnLongClickListener {
            startActivity(Intent(this, LauncherSettingsActivity::class.java))
            true
        }

        btnFinishEdit.setOnClickListener {
            toggleEditMode(false)
        }

        hilmCoreView.setOnClickListener {
            if (isEditMode) {
                toggleEditMode(false)
            } else {
                openAppDrawer(targetSlot = null)
            }
        }

        hilmCoreView.setOnLongClickListener {
            startActivity(Intent(this, LauncherSettingsActivity::class.java))
            true
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

        val launched = app.appRepository.launchApp(slot.packageName, slot.activityName)
        if (!launched) {
            Toast.makeText(this, "Aplicativo não encontrado.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSlotOptionsDialog(position: SlotPosition) {
        val slot = slots[position]
        val hasApp = slot != null && !slot.isEmpty
        val options = mutableListOf(
            "Selecionar aplicativo",
            if (isEditMode) "Sair do modo de edição" else "Reorganizar posições"
        )
        if (hasApp) {
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

    private fun openAppDrawer(targetSlot: SlotPosition?) {
        val title = if (targetSlot != null) {
            "Escolher aplicativo para o atalho"
        } else {
            "Aplicativos Instalados"
        }

        val sheet = AppDrawerBottomSheet(
            title = title,
            apps = installedApps,
            onSelectApp = { selectedApp ->
                if (targetSlot != null) {
                    slots = app.shortcutStorage.assignSlot(
                        position = targetSlot,
                        packageName = selectedApp.packageName,
                        activityName = selectedApp.activityName
                    )
                    renderAllSlots()
                } else {
                    app.appRepository.launchApp(selectedApp.packageName, selectedApp.activityName)
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
                } else {
                    Toast.makeText(
                        this,
                        "Todos os atalhos estão ocupados. Segure um espaço para substituir.",
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
        editModeBar.visibility = if (active) View.VISIBLE else View.GONE
        plateViews.values.forEach { it.isEditModeActive = active }
        dockViews.values.forEach { it.isEditModeActive = active }
    }

    private fun reloadAppsAndRender() {
        installedApps = app.appRepository.loadInstalledApps()
        slots = app.shortcutStorage.loadSlots()
        renderAllSlots()
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
                view.bindIcon(customDrawable ?: resolved.icon, resolved.label)
            } else {
                view.bindIcon(null, getString(R.string.slot_empty))
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

    private fun preventHomeBackExit() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isEditMode) {
                    toggleEditMode(false)
                }
            }
        })
    }
}
