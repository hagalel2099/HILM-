package com.nossouniverso.launcher.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.nossouniverso.launcher.NossoUniversoApp
import com.nossouniverso.launcher.R
import com.nossouniverso.launcher.model.HilmTransitionConfig
import com.nossouniverso.launcher.model.HilmTransitionEffect
import com.nossouniverso.launcher.ui.views.HilmTransitionAnimator
import com.nossouniverso.launcher.ui.views.TransitionPreviewView
import java.nio.charset.StandardCharsets

class LauncherSettingsActivity : AppCompatActivity() {

    private lateinit var app: NossoUniversoApp
    private lateinit var txtDefaultStatus: TextView
    private lateinit var btnSetDefaultLauncher: Button
    private lateinit var txtSettingsActivePack: TextView
    private lateinit var btnOpenIconPackManager: Button
    private lateinit var radioIconPacks: RadioGroup
    private lateinit var seekOpacity: SeekBar

    private lateinit var txtTransitionAccessibilityNote: TextView
    private lateinit var switchTransitionsEnabled: SwitchCompat
    private lateinit var radioTransitionEffects: RadioGroup
    private lateinit var txtTransitionDurationValue: TextView
    private lateinit var seekTransitionDuration: SeekBar
    private lateinit var transitionPreviewStage: TransitionPreviewView
    private lateinit var txtTransitionPreviewStatus: TextView
    private lateinit var btnPreviewTransition: Button
    private lateinit var btnApplyTransition: Button

    private var savedTransitionConfig: HilmTransitionConfig = HilmTransitionConfig()
    private var draftTransitionConfig: HilmTransitionConfig = HilmTransitionConfig()
    private var isUpdatingTransitionUi = false

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val payload = app.shortcutStorage.exportBackupJson()
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.toByteArray(StandardCharsets.UTF_8))
            } ?: error("Output stream unavailable")
            Toast.makeText(this, "Backup exportado com sucesso.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível exportar o backup.", Toast.LENGTH_SHORT).show()
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val raw = contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }.orEmpty()
            val ok = raw.isNotBlank() && app.shortcutStorage.importBackupJson(raw)
            if (ok) {
                app.iconPackManager.clearCache()
                setupIconPacks()
                seekOpacity.progress = (app.shortcutStorage.getPanelOpacityPercent() - 25).coerceIn(0, 70)
                loadTransitionsSection(replayPreview = true)
                Toast.makeText(this, "Backup restaurado com sucesso.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Arquivo de backup inválido.", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível importar o backup.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        app = application as NossoUniversoApp

        txtDefaultStatus = findViewById(R.id.txtDefaultStatus)
        btnSetDefaultLauncher = findViewById(R.id.btnSetDefaultLauncher)
        txtSettingsActivePack = findViewById(R.id.txtSettingsActivePack)
        btnOpenIconPackManager = findViewById(R.id.btnOpenIconPackManager)
        radioIconPacks = findViewById(R.id.radioIconPacks)
        seekOpacity = findViewById(R.id.seekOpacity)

        txtTransitionAccessibilityNote = findViewById(R.id.txtTransitionAccessibilityNote)
        switchTransitionsEnabled = findViewById(R.id.switchTransitionsEnabled)
        radioTransitionEffects = findViewById(R.id.radioTransitionEffects)
        txtTransitionDurationValue = findViewById(R.id.txtTransitionDurationValue)
        seekTransitionDuration = findViewById(R.id.seekTransitionDuration)
        transitionPreviewStage = findViewById(R.id.transitionPreviewStage)
        txtTransitionPreviewStatus = findViewById(R.id.txtTransitionPreviewStatus)
        btnPreviewTransition = findViewById(R.id.btnPreviewTransition)
        btnApplyTransition = findViewById(R.id.btnApplyTransition)

        btnSetDefaultLauncher.setOnClickListener {
            startActivity(app.appRepository.createDefaultLauncherIntent())
        }

        btnOpenIconPackManager.setOnClickListener {
            startActivity(Intent(this, IconPackManagerActivity::class.java))
            HilmTransitionAnimator.applyActivityEnterTransition(
                this,
                app.shortcutStorage.getTransitionConfig()
            )
        }

        findViewById<Button>(R.id.btnExportBackup).setOnClickListener {
            exportBackupLauncher.launch("nosso-universo-backup.json")
        }

        findViewById<Button>(R.id.btnImportBackup).setOnClickListener {
            importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        findViewById<Button>(R.id.btnResetShortcuts).setOnClickListener {
            app.shortcutStorage.clearAllSlots()
            Toast.makeText(this, "Atalhos restaurados.", Toast.LENGTH_SHORT).show()
            finish()
        }

        setupIconPacks()
        setupOpacitySlider()
        setupTransitionsSection()
    }

    override fun onResume() {
        super.onResume()
        refreshDefaultStatus()
        setupIconPacks()
        updateAccessibilityBanner()
    }

    override fun finish() {
        super.finish()
        HilmTransitionAnimator.applyActivityExitTransition(
            this,
            app.shortcutStorage.getTransitionConfig()
        )
    }

    private fun refreshDefaultStatus() {
        val isDefault = app.appRepository.isDefaultLauncher()
        txtDefaultStatus.text = if (isDefault) {
            getString(R.string.default_launcher_active)
        } else {
            "O Nosso Universo ainda não está definido como launcher padrão do sistema."
        }
        btnSetDefaultLauncher.isEnabled = !isDefault
    }

    private fun setupIconPacks() {
        val packs = app.iconPackManager.getAvailableIconPacks()
        val currentId = app.shortcutStorage.getSelectedIconPackId()
        val activePack = packs.firstOrNull { it.id == currentId }
        txtSettingsActivePack.text = getString(
            R.string.active_icon_pack_format,
            activePack?.title ?: "HILM Artefatos 3D (Universal)"
        )

        radioIconPacks.setOnCheckedChangeListener(null)
        radioIconPacks.removeAllViews()

        packs.forEachIndexed { index, pack ->
            val rb = RadioButton(this).apply {
                id = 1000 + index
                text = pack.title
                setTextColor(ContextCompat.getColor(context, R.color.hud_text_primary))
                setPadding(12, 18, 12, 18)
                tag = pack.id
                isChecked = pack.id == currentId
            }
            radioIconPacks.addView(rb)
        }

        radioIconPacks.setOnCheckedChangeListener { group, checkedId ->
            val selected = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
            app.shortcutStorage.setSelectedIconPackId(selected)
            app.iconPackManager.clearCache()
            val updated = packs.firstOrNull { it.id == selected }
            txtSettingsActivePack.text = getString(
                R.string.active_icon_pack_format,
                updated?.title ?: selected
            )
        }
    }

    private fun setupOpacitySlider() {
        val current = app.shortcutStorage.getPanelOpacityPercent()
        seekOpacity.progress = (current - 25).coerceIn(0, 70)
        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    app.shortcutStorage.setPanelOpacityPercent(progress + 25)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun setupTransitionsSection() {
        radioTransitionEffects.removeAllViews()
        HilmTransitionEffect.values().forEachIndexed { index, effect ->
            val rb = RadioButton(this).apply {
                id = 2000 + index
                text = "${effect.title}\n${effect.description}"
                setTextColor(ContextCompat.getColor(context, R.color.hud_text_primary))
                textSize = 13f
                setPadding(12, 16, 12, 16)
                tag = effect.id
            }
            radioTransitionEffects.addView(rb)
        }

        switchTransitionsEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingTransitionUi) return@setOnCheckedChangeListener
            draftTransitionConfig = draftTransitionConfig.copy(enabled = isChecked)
            refreshTransitionDraftUi(replayPreview = true)
        }

        radioTransitionEffects.setOnCheckedChangeListener { group, checkedId ->
            if (isUpdatingTransitionUi) return@setOnCheckedChangeListener
            val rawId = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
            val chosen = HilmTransitionEffect.fromId(rawId)
            draftTransitionConfig = draftTransitionConfig.copy(effect = chosen)
            refreshTransitionDraftUi(replayPreview = true)
        }

        seekTransitionDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || isUpdatingTransitionUi) return
                val ms = (HilmTransitionConfig.MIN_DURATION_MS + progress * 10)
                    .coerceIn(HilmTransitionConfig.MIN_DURATION_MS, HilmTransitionConfig.MAX_DURATION_MS)
                draftTransitionConfig = draftTransitionConfig.copy(durationMs = ms)
                refreshTransitionDraftUi(replayPreview = false)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                transitionPreviewStage.playPreview(draftTransitionConfig)
            }
        })

        btnPreviewTransition.setOnClickListener {
            transitionPreviewStage.playPreview(draftTransitionConfig)
        }

        btnApplyTransition.setOnClickListener {
            val clean = draftTransitionConfig.sanitized()
            app.shortcutStorage.saveTransitionConfig(clean)
            savedTransitionConfig = clean
            draftTransitionConfig = clean
            refreshTransitionDraftUi(replayPreview = true)
            Toast.makeText(this, getString(R.string.transitions_applied_toast), Toast.LENGTH_SHORT).show()
        }

        loadTransitionsSection(replayPreview = false)
    }

    private fun loadTransitionsSection(replayPreview: Boolean) {
        savedTransitionConfig = app.shortcutStorage.getTransitionConfig()
        draftTransitionConfig = savedTransitionConfig
        refreshTransitionDraftUi(replayPreview = replayPreview)
        updateAccessibilityBanner()
    }

    private fun updateAccessibilityBanner() {
        val sysReduced = HilmTransitionAnimator.isSystemReducedMotionEnabled(this)
        txtTransitionAccessibilityNote.visibility = if (sysReduced) View.VISIBLE else View.GONE
    }

    private fun refreshTransitionDraftUi(replayPreview: Boolean) {
        isUpdatingTransitionUi = true
        try {
            switchTransitionsEnabled.isChecked = draftTransitionConfig.enabled
            val targetRadioId = 2000 + HilmTransitionEffect.values().indexOf(draftTransitionConfig.effect)
            if (targetRadioId >= 2000 && radioTransitionEffects.checkedRadioButtonId != targetRadioId) {
                radioTransitionEffects.check(targetRadioId)
            }

            val progress = ((draftTransitionConfig.durationMs - HilmTransitionConfig.MIN_DURATION_MS) / 10)
                .coerceIn(0, 42)
            seekTransitionDuration.progress = progress
            seekTransitionDuration.isEnabled = draftTransitionConfig.enabled

            for (i in 0 until radioTransitionEffects.childCount) {
                val child = radioTransitionEffects.getChildAt(i)
                child.isEnabled = draftTransitionConfig.enabled
                child.alpha = if (draftTransitionConfig.enabled) 1f else 0.5f
            }

            txtTransitionDurationValue.text = getString(
                R.string.transitions_duration_format,
                draftTransitionConfig.durationMs
            )

            val hasPendingChanges = draftTransitionConfig != savedTransitionConfig
            txtTransitionPreviewStatus.text = if (hasPendingChanges) {
                "Em prévia (${draftTransitionConfig.effect.title}) — toque em Aplicar Transição para salvar."
            } else if (!savedTransitionConfig.enabled) {
                "Aplicado: efeitos de transição desativados."
            } else {
                "Aplicado: ${savedTransitionConfig.effect.title} (${savedTransitionConfig.durationMs} ms)."
            }
            txtTransitionPreviewStatus.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (hasPendingChanges) R.color.hud_amber else R.color.hud_text_secondary
                )
            )
            btnApplyTransition.isEnabled = hasPendingChanges
            btnApplyTransition.alpha = if (hasPendingChanges) 1f else 0.55f
        } finally {
            isUpdatingTransitionUi = false
        }

        if (replayPreview) {
            transitionPreviewStage.playPreview(draftTransitionConfig)
        }
    }
}
