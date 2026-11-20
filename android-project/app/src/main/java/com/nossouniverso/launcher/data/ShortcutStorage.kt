package com.nossouniverso.launcher.data

import android.content.Context
import android.content.SharedPreferences
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

    fun clearAllSlots() {
        prefs.edit().remove(KEY_SLOTS).apply()
    }

    fun getSelectedIconPackId(): String {
        return prefs.getString(KEY_ICON_PACK, IconPackInfo.STYLE_NOSSO_UNIVERSO)
            ?: IconPackInfo.STYLE_NOSSO_UNIVERSO
    }

    fun setSelectedIconPackId(id: String) {
        prefs.edit().putString(KEY_ICON_PACK, id).apply()
    }

    fun getPanelOpacityPercent(): Int {
        return prefs.getInt(KEY_PANEL_OPACITY, 78).coerceIn(25, 95)
    }

    fun setPanelOpacityPercent(percent: Int) {
        prefs.edit().putInt(KEY_PANEL_OPACITY, percent.coerceIn(25, 95)).apply()
    }

    companion object {
        private const val PREFS_NAME = "nosso_universo_prefs"
        private const val KEY_SLOTS = "shortcut_slots_v1"
        private const val KEY_ICON_PACK = "selected_icon_pack_v1"
        private const val KEY_PANEL_OPACITY = "hud_panel_opacity_v1"
    }
}
