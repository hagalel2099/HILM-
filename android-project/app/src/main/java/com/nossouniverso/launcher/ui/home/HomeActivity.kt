package com.nossouniverso.launcher.ui.home

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.DragEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nossouniverso.launcher.NossoUniversoApp
import com.nossouniverso.launcher.R
import com.nossouniverso.launcher.model.AppInfo
import com.nossouniverso.launcher.model.ShortcutSlot
import com.nossouniverso.launcher.model.SlotPosition
import com.nossouniverso.launcher.ui.drawer.AppDrawerBottomSheet
import com.nossouniverso.launcher.ui.settings.IconPackManagerActivity
import com.nossouniverso.launcher.ui.settings.LauncherSettingsActivity
import com.nossouniverso.launcher.ui.views.HilmCoreView
import com.nossouniverso.launcher.ui.views.HudDockSlotView
import com.nossouniverso.launcher.ui.views.HudFrameView
import com.nossouniverso.launcher.ui.views.HudPlateButton
import com.nossouniverso.launcher.updater.ApkDownloadResult
import com.nossouniverso.launcher.updater.LauncherUpdateManager
import com.nossouniverso.launcher.updater.UpdateCheckResult
import com.nossouniverso.launcher.updater.UpdateInstallReceiver
import java.io.File

class HomeActivity : AppCompatActivity() {

    private lateinit var app: NossoUniversoApp
    private lateinit var hudFrameView: HudFrameView
    private lateinit var hilmCoreView: HilmCoreView
    private lateinit var editModeBar: LinearLayout
    private lateinit var btnFinishEdit: TextView
    private lateinit var btnHubControl: ImageButton

    private lateinit var coreUpdatePopover: LinearLayout
    private lateinit var btnCloseUpdatePopover: TextView
    private lateinit var tvUpdateMessage: TextView
    private lateinit var progressUpdateDownload: ProgressBar
    private lateinit var btnUpdateAction: Button
    private lateinit var updateManager: LauncherUpdateManager

    private val plateViews = mutableMapOf<SlotPosition, HudPlateButton>()
    private val dockViews = mutableMapOf<SlotPosition, HudDockSlotView>()
    private lateinit var dockCenterDrawer: HudDockSlotView

    private var installedApps: List<AppInfo> = emptyList()
    private var slots: Map<SlotPosition, ShortcutSlot> = emptyMap()
    private var isEditMode = false
    private var pendingSwapSource: SlotPosition? = null
    private var pendingCustomIconTarget: SlotPosition? = null
    private var packageReceiver: BroadcastReceiver? = null
    private var installUiReceiver: BroadcastReceiver? = null

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
        findViewById<View>(R.id.homeRoot).setBackgroundColor(Color.TRANSPARENT)

        app = application as NossoUniversoApp
        updateManager = LauncherUpdateManager(this)
        bindViews()
        setupListeners()
        preventHomeBackExit()
        registerInstallUiReceiver()
        handlePostUpdateRelaunchIntent(intent)

        packageReceiver = app.appRepository.registerPackageReceiver {
            runOnUiThread {
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
        val opacityPercent = app.shortcutStorage.getPanelOpacityPercent()
        hudFrameView.panelOpacityPercent = opacityPercent
        plateViews.values.forEach { it.panelOpacityPercent = opacityPercent }
        dockViews.values.forEach { it.panelOpacityPercent = opacityPercent }
        reloadAppsAndRender()
    }

    override fun onDestroy() {
        updateManager.shutdown()
        packageReceiver?.let { unregisterReceiver(it) }
        installUiReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    private fun bindViews() {
        hudFrameView = findViewById(R.id.hudFrameView)
        hilmCoreView = findViewById(R.id.hilmCoreView)
        editModeBar = findViewById(R.id.editModeBar)
        btnFinishEdit = findViewById(R.id.btnFinishEdit)
        btnHubControl = findViewById(R.id.btnHubControl)

        coreUpdatePopover = findViewById(R.id.coreUpdatePopover)
        btnCloseUpdatePopover = findViewById(R.id.btnCloseUpdatePopover)
        tvUpdateMessage = findViewById(R.id.tvUpdateMessage)
        progressUpdateDownload = findViewById(R.id.progressUpdateDownload)
        btnUpdateAction = findViewById(R.id.btnUpdateAction)

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
            hideUpdatePopover()
            openAppDrawer(targetSlot = null)
        }

        dockCenterDrawer.setOnLongClickListener {
            startActivity(Intent(this, LauncherSettingsActivity::class.java))
            true
        }

        btnHubControl.setOnClickListener {
            hideUpdatePopover()
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
                toggleCoreUpdatePopover()
            }
        }

        hilmCoreView.setOnLongClickListener {
            startActivity(Intent(this, LauncherSettingsActivity::class.java))
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
            if (isEditMode) "Sair do modo de edição" else "Reorganizar posições",
            "Gerenciar pacotes de ícones"
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
                    "Gerenciar pacotes de ícones" -> {
                        startActivity(Intent(this, IconPackManagerActivity::class.java))
                    }
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

        coreUpdatePopover.visibility = View.VISIBLE
    }

    private fun hideUpdatePopover() {
        coreUpdatePopover.visibility = View.GONE
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
            coreUpdatePopover.visibility = View.VISIBLE
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
                } else if (isEditMode) {
                    toggleEditMode(false)
                }
            }
        })
    }
}
