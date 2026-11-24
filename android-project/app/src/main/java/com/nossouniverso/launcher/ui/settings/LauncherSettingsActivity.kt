package com.nossouniverso.launcher.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nossouniverso.launcher.NossoUniversoApp
import com.nossouniverso.launcher.R
import java.nio.charset.StandardCharsets

class LauncherSettingsActivity : AppCompatActivity() {

    private lateinit var app: NossoUniversoApp
    private lateinit var txtDefaultStatus: TextView
    private lateinit var btnSetDefaultLauncher: Button
    private lateinit var txtSettingsActivePack: TextView
    private lateinit var btnOpenIconPackManager: Button
    private lateinit var radioIconPacks: RadioGroup
    private lateinit var seekOpacity: SeekBar

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

        btnSetDefaultLauncher.setOnClickListener {
            startActivity(app.appRepository.createDefaultLauncherIntent())
        }

        btnOpenIconPackManager.setOnClickListener {
            startActivity(Intent(this, IconPackManagerActivity::class.java))
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
    }

    override fun onResume() {
        super.onResume()
        refreshDefaultStatus()
        setupIconPacks()
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
}
