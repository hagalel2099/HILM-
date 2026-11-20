package com.nossouniverso.launcher.model

data class IconPackInfo(
    val id: String,
    val title: String,
    val packageName: String? = null,
    val isBuiltIn: Boolean = true
) {
    companion object {
        const val STYLE_NOSSO_UNIVERSO = "builtin_nosso_universo"
        const val STYLE_MONOCHROME_HUD = "builtin_monochrome_hud"
        const val STYLE_SYSTEM_DEFAULT = "builtin_system_default"
    }
}
