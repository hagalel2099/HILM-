package com.nossouniverso.launcher.data

import android.content.Context
import android.content.SharedPreferences
import com.nossouniverso.launcher.model.HilmTransitionConfig
import com.nossouniverso.launcher.model.HilmTransitionEffect
import com.nossouniverso.launcher.model.IconPackInfo
import com.nossouniverso.launcher.model.ShortcutSlot
import com.nossouniverso.launcher.model.SlotPosition
import org.json.JSONArray
import org.json.JSONObject

class ShortcutStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSlots(): Map<SlotPosition, ShortcutSlot> {
        val raw = prefs.getString(KEY_SLOTS, null)
        val result = SlotPosition.values().associateWith { ShortcutSlot(position = it) }.toMutableMap()
        if (raw.isNullOrBlank()) return result

        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pos = SlotPosition.fromId(obj.optString("position")) ?: continue
                result[pos] = ShortcutSlot(
                    position = pos,
                    packageName = obj.optString("packageName").takeIf { it.isNotBlank() },
                    activityName = obj.optString("activityName").takeIf { it.isNotBlank() },
                    customLabel = obj.optString("customLabel").takeIf { it.isNotBlank() },
                    customIconUri = obj.optString("customIconUri").takeIf { it.isNotBlank() }
                )
            }
        } catch (_: Exception) {
            // Keep clean empty slots if persisted JSON is malformed
        }
        return result
    }

    fun saveSlots(slots: Map<SlotPosition, ShortcutSlot>) {
        val array = JSONArray()
        SlotPosition.values().forEach { position ->
            val slot = slots[position] ?: ShortcutSlot(position)
            val obj = JSONObject().apply {
                put("position", position.id)
                put("packageName", slot.packageName ?: "")
                put("activityName", slot.activityName ?: "")
                put("customLabel", slot.customLabel ?: "")
                put("customIconUri", slot.customIconUri ?: "")
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_SLOTS, array.toString()).apply()
    }

    fun swapSlots(first: SlotPosition, second: SlotPosition): Map<SlotPosition, ShortcutSlot> {
        val current = loadSlots().toMutableMap()
        val a = current[first] ?: ShortcutSlot(first)
        val b = current[second] ?: ShortcutSlot(second)
        current[first] = b.copy(position = first)
        current[second] = a.copy(position = second)
        saveSlots(current)
        return current
    }

    fun assignSlot(
        position: SlotPosition,
        packageName: String,
        activityName: String,
        customLabel: String? = null,
        customIconUri: String? = null
    ): Map<SlotPosition, ShortcutSlot> {
        val current = loadSlots().toMutableMap()
        current[position] = ShortcutSlot(
            position = position,
            packageName = packageName,
            activityName = activityName,
            customLabel = customLabel,
            customIconUri = customIconUri
        )
        saveSlots(current)
        return current
    }

    fun clearSlot(position: SlotPosition): Map<SlotPosition, ShortcutSlot> {
        val current = loadSlots().toMutableMap()
        current[position] = ShortcutSlot(position)
        saveSlots(current)
        return current
    }

    fun renameSlot(position: SlotPosition, customLabel: String?): Map<SlotPosition, ShortcutSlot> {
        val current = loadSlots().toMutableMap()
        val existing = current[position] ?: return current
        val clean = customLabel?.trim()?.takeIf { it.isNotEmpty() }
        current[position] = existing.copy(customLabel = clean)
        saveSlots(current)
        return current
    }

    fun clearAllSlots() {
        prefs.edit().remove(KEY_SLOTS).apply()
    }

    fun getRecentPackages(): List<String> {
        val raw = prefs.getString(KEY_RECENT_PACKAGES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun recordRecentPackage(packageName: String) {
        if (packageName.isBlank()) return
        val updated = (listOf(packageName) + getRecentPackages().filter { it != packageName }).take(5)
        prefs.edit().putString(KEY_RECENT_PACKAGES, JSONArray(updated).toString()).apply()
    }

    fun exportBackupJson(): String {
        val slotsObj = JSONObject()
        val loaded = loadSlots()
        SlotPosition.values().forEach { pos ->
            val slot = loaded[pos] ?: ShortcutSlot(pos)
            slotsObj.put(
                pos.id,
                JSONObject().apply {
                    put("slotId", pos.id)
                    put("packageName", slot.packageName ?: JSONObject.NULL)
                    if (!slot.activityName.isNullOrBlank()) put("activityName", slot.activityName)
                    if (!slot.customLabel.isNullOrBlank()) put("customLabel", slot.customLabel)
                    if (!slot.customIconUri.isNullOrBlank()) put("customIconUri", slot.customIconUri)
                }
            )
        }
        val transition = getTransitionConfig()
        val theme = getLauncherTheme()
        return JSONObject().apply {
            put("schema", "nosso_universo_layout_v1")
            put("themeId", if (theme == THEME_LUNA) "luna-entre-luz-e-sombra" else "default")
            put("launcherTheme", theme)
            put("iconPack", getSelectedIconPackId())
            put("panelOpacity", getPanelOpacityPercent())
            put(
                "transition",
                JSONObject().apply {
                    put("enabled", transition.enabled)
                    put("effect", transition.effect.id)
                    put("durationMs", transition.durationMs)
                }
            )
            put("slots", slotsObj)
        }.toString(2)
    }

    fun importBackupJson(rawJson: String): Boolean {
        return try {
            val root = JSONObject(rawJson)
            val current = SlotPosition.values().associateWith { ShortcutSlot(it) }.toMutableMap()
            val slotsNode = root.opt("slots")
            when (slotsNode) {
                is JSONObject -> {
                    SlotPosition.values().forEach { pos ->
                        val item = slotsNode.optJSONObject(pos.id) ?: return@forEach
                        current[pos] = ShortcutSlot(
                            position = pos,
                            packageName = item.optString("packageName").takeIf {
                                it.isNotBlank() && it != "null"
                            },
                            activityName = item.optString("activityName").takeIf { it.isNotBlank() },
                            customLabel = item.optString("customLabel").takeIf { it.isNotBlank() },
                            customIconUri = item.optString("customIconUri").takeIf { it.isNotBlank() }
                        )
                    }
                }
                is JSONArray -> {
                    for (i in 0 until slotsNode.length()) {
                        val item = slotsNode.optJSONObject(i) ?: continue
                        val pos = SlotPosition.fromId(item.optString("position")) ?: continue
                        current[pos] = ShortcutSlot(
                            position = pos,
                            packageName = item.optString("packageName").takeIf {
                                it.isNotBlank() && it != "null"
                            },
                            activityName = item.optString("activityName").takeIf { it.isNotBlank() },
                            customLabel = item.optString("customLabel").takeIf { it.isNotBlank() },
                            customIconUri = item.optString("customIconUri").takeIf { it.isNotBlank() }
                        )
                    }
                }
                else -> return false
            }

            saveSlots(current)
            if (root.has("panelOpacity")) {
                setPanelOpacityPercent(root.optInt("panelOpacity", 78))
            }
            val importedThemeRaw = root.optString("themeId")
                .ifBlank { root.optString("launcherTheme") }
            if (importedThemeRaw.isNotBlank()) {
                val mappedTheme = when (importedThemeRaw.lowercase()) {
                    "luna-entre-luz-e-sombra", "luna_entre_luz_e_sombra", THEME_LUNA -> THEME_LUNA
                    else -> THEME_DEFAULT
                }
                setLauncherTheme(mappedTheme)
            }
            val importedPack = root.optString("iconPack")
            if (importedPack.isNotBlank()) {
                val mappedPack = when (importedPack) {
                    "hilm-artifacts", IconPackInfo.LEGACY_STYLE_HILM_ARTIFACTS -> IconPackInfo.STYLE_HILM_ARTIFACTS
                    "obsidian-core", "obsidian_core" -> IconPackInfo.STYLE_OBSIDIAN_CORE
                    "cyberpunk-holo", "cyberpunk_holo" -> IconPackInfo.STYLE_CYBERPUNK_HOLO
                    "tron-neon", "tron_neon" -> IconPackInfo.STYLE_TRON_NEON
                    "luna-holographic", "luna_holographic", IconPackInfo.STYLE_LUNA_HOLOGRAPHIC -> IconPackInfo.STYLE_HILM_ARTIFACTS
                    "titanium-liquid" -> IconPackInfo.ASSET_PACK_PREFIX + "titanium_liquid"
                    "nosso-universo" -> IconPackInfo.STYLE_NOSSO_UNIVERSO
                    "monochrome-hud" -> IconPackInfo.STYLE_MONOCHROME_HUD
                    "system-default" -> IconPackInfo.STYLE_SYSTEM_DEFAULT
                    else -> importedPack
                }
                setSelectedIconPackId(mappedPack)
            }
            val transitionNode = root.optJSONObject("transition")
            if (transitionNode != null) {
                val importedTransition = HilmTransitionConfig(
                    enabled = transitionNode.optBoolean("enabled", true),
                    effect = HilmTransitionEffect.fromId(transitionNode.optString("effect")),
                    durationMs = transitionNode.optInt(
                        "durationMs",
                        HilmTransitionConfig.DEFAULT_DURATION_MS
                    )
                ).sanitized()
                saveTransitionConfig(importedTransition)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getLauncherTheme(): String {
        val raw = prefs.getString(KEY_LAUNCHER_THEME, THEME_DEFAULT) ?: THEME_DEFAULT
        return if (raw == THEME_LUNA || raw == "luna-entre-luz-e-sombra") THEME_LUNA else THEME_DEFAULT
    }

    fun setLauncherTheme(themeId: String) {
        val clean = if (themeId == THEME_LUNA || themeId == "luna-entre-luz-e-sombra") {
            THEME_LUNA
        } else {
            THEME_DEFAULT
        }
        prefs.edit().putString(KEY_LAUNCHER_THEME, clean).apply()
    }

    fun getSelectedIconPackId(): String {
        val raw = prefs.getString(KEY_ICON_PACK, IconPackInfo.STYLE_HILM_ARTIFACTS)
            ?: IconPackInfo.STYLE_HILM_ARTIFACTS
        return when (raw) {
            IconPackInfo.LEGACY_STYLE_HILM_ARTIFACTS,
            "hilm-artifacts",
            IconPackInfo.STYLE_LUNA_HOLOGRAPHIC,
            "luna-holographic",
            "luna_holographic" -> IconPackInfo.STYLE_HILM_ARTIFACTS
            else -> raw
        }
    }

    fun setSelectedIconPackId(id: String) {
        val clean = when (id) {
            IconPackInfo.STYLE_LUNA_HOLOGRAPHIC,
            "luna-holographic",
            "luna_holographic" -> IconPackInfo.STYLE_HILM_ARTIFACTS
            else -> id
        }
        prefs.edit().putString(KEY_ICON_PACK, clean).apply()
    }

    fun getPanelOpacityPercent(): Int {
        return prefs.getInt(KEY_PANEL_OPACITY, 78).coerceIn(25, 95)
    }

    fun setPanelOpacityPercent(percent: Int) {
        prefs.edit().putInt(KEY_PANEL_OPACITY, percent.coerceIn(25, 95)).apply()
    }

    fun getTransitionConfig(): HilmTransitionConfig {
        val enabled = prefs.getBoolean(KEY_TRANSITION_ENABLED, true)
        val effect = HilmTransitionEffect.fromId(prefs.getString(KEY_TRANSITION_EFFECT, null))
        val durationMs = prefs.getInt(
            KEY_TRANSITION_DURATION_MS,
            HilmTransitionConfig.DEFAULT_DURATION_MS
        )
        return HilmTransitionConfig(
            enabled = enabled,
            effect = effect,
            durationMs = durationMs
        ).sanitized()
    }

    fun saveTransitionConfig(config: HilmTransitionConfig) {
        val clean = config.sanitized()
        prefs.edit()
            .putBoolean(KEY_TRANSITION_ENABLED, clean.enabled)
            .putString(KEY_TRANSITION_EFFECT, clean.effect.id)
            .putInt(KEY_TRANSITION_DURATION_MS, clean.durationMs)
            .apply()
    }

    companion object {
        const val THEME_DEFAULT = "default"
        const val THEME_LUNA = "luna_entre_luz_e_sombra"

        private const val PREFS_NAME = "nosso_universo_prefs"
        private const val KEY_SLOTS = "shortcut_slots_v1"
        private const val KEY_LAUNCHER_THEME = "launcher_theme_v1"
        private const val KEY_ICON_PACK = "selected_icon_pack_v1"
        private const val KEY_PANEL_OPACITY = "hud_panel_opacity_v1"
        private const val KEY_RECENT_PACKAGES = "recent_packages_v1"
        private const val KEY_TRANSITION_ENABLED = "hilm_transition_enabled_v1"
        private const val KEY_TRANSITION_EFFECT = "hilm_transition_effect_v1"
        private const val KEY_TRANSITION_DURATION_MS = "hilm_transition_duration_ms_v1"
    }
}
