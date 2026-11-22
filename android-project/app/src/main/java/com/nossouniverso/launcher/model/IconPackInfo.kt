package com.nossouniverso.launcher.model

enum class IconPackKind {
    ILLUSTRATED_ASSET,
    BUILT_IN_STYLE,
    SYSTEM_DEFAULT,
    EXTERNAL_APK
}

data class IconPackPreviewItem(
    val key: String,
    val label: String,
    val assetPath: String
)

data class IconPackInfo(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val author: String = "HILM",
    val version: String = "1.0.0",
    val packageName: String? = null,
    val isBuiltIn: Boolean = true,
    val kind: IconPackKind = IconPackKind.BUILT_IN_STYLE,
    val assetFolder: String? = null,
    val previewFiles: List<String> = emptyList(),
    val iconCount: Int = 0
) {
    companion object {
        const val STYLE_NOSSO_UNIVERSO = "builtin_nosso_universo"
        const val STYLE_MONOCHROME_HUD = "builtin_monochrome_hud"
        const val STYLE_SYSTEM_DEFAULT = "builtin_system_default"
        const val ASSET_PACK_PREFIX = "asset_pack:"
    }
}
