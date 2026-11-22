package com.nossouniverso.launcher.ui.settings

import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nossouniverso.launcher.NossoUniversoApp
import com.nossouniverso.launcher.R

class LauncherSettingsActivity : AppCompatActivity() {

    private lateinit var app: NossoUniversoApp
    private lateinit var txtDefaultStatus: TextView
    private lateinit var btnSetDefaultLauncher: Button
    private lateinit var txtSettingsActivePack: TextView
    private lateinit var btnOpenIconPackManager: Button
    private lateinit var radioIconPacks: RadioGroup
    private lateinit var seekOpacity: SeekBar

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
            startActivity(android.content.Intent(this, IconPackManagerActivity::class.java))
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
            activePack?.title ?: "Nosso Universo Padrão"
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
