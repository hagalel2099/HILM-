package com.nossouniverso.launcher.model

enum class IconPackKind {
    ILLUSTRATED_ASSET,
    IMPORTED_PACK,
    BUILT_IN_STYLE,
    SYSTEM_DEFAULT,
    EXTERNAL_APK
}

data class IconPackPreviewItem(
    val key: String,
    val label: String,
    val sourcePath: String,
    val isAsset: Boolean = true
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
    val storageDirName: String? = null,
    val fallbackStyle: String = "titanium_liquid",
    val previewFiles: List<IconPackPreviewItem> = emptyList(),
    val iconCount: Int = 0
) {
    companion object {
        const val STYLE_HILM_ARTIFACTS = "asset_pack:hilm_artifacts"
        const val STYLE_OBSIDIAN_CORE = "asset_pack:obsidian_core"
        const val STYLE_CYBERPUNK_HOLO = "asset_pack:cyberpunk_holo"
        const val STYLE_TRON_NEON = "asset_pack:tron_neon"
        const val LEGACY_STYLE_HILM_ARTIFACTS = "builtin_hilm_artifacts"
        const val STYLE_LUNA_HOLOGRAPHIC = "builtin_luna_holographic"
        const val STYLE_NOSSO_UNIVERSO = "builtin_nosso_universo"
        const val STYLE_MONOCHROME_HUD = "builtin_monochrome_hud"
        const val STYLE_SYSTEM_DEFAULT = "builtin_system_default"
        const val ASSET_PACK_PREFIX = "asset_pack:"
        const val IMPORTED_PACK_PREFIX = "imported_pack:"
    }
}
