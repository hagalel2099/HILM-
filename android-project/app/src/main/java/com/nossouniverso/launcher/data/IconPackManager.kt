package com.nossouniverso.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.LruCache
import com.nossouniverso.launcher.model.IconPackInfo
import com.nossouniverso.launcher.model.IconPackKind
import com.nossouniverso.launcher.model.IconPackPreviewItem
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

class IconPackManager(
    private val context: Context,
    private val storage: ShortcutStorage
) {
    private data class AssetIconEntry(
        val key: String,
        val label: String,
        val fileName: String,
        val packages: Set<String>,
        val keywords: List<String>
    )

    private data class ParsedAssetPack(
        val info: IconPackInfo,
        val folderName: String,
        val entries: List<AssetIconEntry>,
        val packageToFile: Map<String, String>
    )

    private val packageManager: PackageManager = context.packageManager
    private var cachedPackPackage: String? = null
    private var cachedResources: Resources? = null
    private val componentToDrawable = mutableMapOf<String, String>()
    private var cachedAssetPacks: Map<String, ParsedAssetPack>? = null

    private val bitmapCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024L).toInt() / 16).coerceIn(2048, 8192)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    fun getAvailableIconPacks(): List<IconPackInfo> {
        val packs = mutableListOf<IconPackInfo>()

        // 1. Auto-discovered illustrated asset packs (extensible via assets/icon_packs/<folder>/manifest.json)
        loadAssetPacks().values
            .sortedBy { it.info.title }
            .forEach { packs.add(it.info) }

        // 2. Built-in HUD styles & native restore option
        packs.add(
            IconPackInfo(
                id = IconPackInfo.STYLE_NOSSO_UNIVERSO,
                title = "Nosso Universo Padrão (HUD Ciano & Âmbar)",
                subtitle = "Tonalização técnica ciano integrada às molduras HUD.",
                kind = IconPackKind.BUILT_IN_STYLE,
                isBuiltIn = true
            )
        )
        packs.add(
            IconPackInfo(
                id = IconPackInfo.STYLE_MONOCHROME_HUD,
                title = "HUD Monocromático Prata",
                subtitle = "Escala metálica fria com contraste uniforme.",
                kind = IconPackKind.BUILT_IN_STYLE,
                isBuiltIn = true
            )
        )
        packs.add(
            IconPackInfo(
                id = IconPackInfo.STYLE_SYSTEM_DEFAULT,
                title = "Ícones Originais do Android",
                subtitle = "Restaura a aparência nativa original de todos os aplicativos.",
                kind = IconPackKind.SYSTEM_DEFAULT,
                isBuiltIn = true
            )
        )

        // 3. External installed ADW / GO / Nova icon packs
        val themeActions = listOf(
            "org.adw.ActivityStarter.THEMES",
            "com.gau.go.launcherex.theme",
            "com.novalauncher.THEME"
        )
        val discoveredPackages = mutableSetOf<String>()

        themeActions.forEach { action ->
            val intent = Intent(action)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
            resolved.forEach { info ->
                val pkg = info.activityInfo.packageName
                if (discoveredPackages.add(pkg)) {
                    val label = info.loadLabel(packageManager)?.toString() ?: pkg
                    packs.add(
                        IconPackInfo(
                            id = pkg,
                            title = label,
                            subtitle = "Pacote externo instalado ($pkg)",
                            author = "Pacote Externo",
                            packageName = pkg,
                            isBuiltIn = false,
                            kind = IconPackKind.EXTERNAL_APK
                        )
                    )
                }
            }
        }
        return packs
    }

    fun getSelectedPackInfo(): IconPackInfo {
        val selectedId = storage.getSelectedIconPackId()
        return getAvailableIconPacks().firstOrNull { it.id == selectedId }
            ?: getAvailableIconPacks().firstOrNull { it.id == IconPackInfo.STYLE_NOSSO_UNIVERSO }
            ?: IconPackInfo(
                id = IconPackInfo.STYLE_SYSTEM_DEFAULT,
                title = "Ícones Originais do Android",
                kind = IconPackKind.SYSTEM_DEFAULT
            )
    }

    fun getPreviewItemsForPack(packId: String): List<IconPackPreviewItem> {
        val parsed = loadAssetPacks()[packId] ?: return emptyList()
        return parsed.entries.map { entry ->
            IconPackPreviewItem(
                key = entry.key,
                label = entry.label,
                assetPath = "icon_packs/${parsed.folderName}/icons/${entry.fileName}"
            )
        }
    }

    fun loadAssetDrawable(assetPath: String, targetSizePx: Int = 192): Drawable? {
        val cacheKey = "$assetPath#$targetSizePx"
        bitmapCache.get(cacheKey)?.let { cached ->
            return BitmapDrawable(context.resources, cached)
        }

        return try {
            val decoded = context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null

            val finalBitmap = if (decoded.width > targetSizePx || decoded.height > targetSizePx) {
                Bitmap.createScaledBitmap(decoded, targetSizePx, targetSizePx, true).also {
                    if (it != decoded) decoded.recycle()
                }
            } else {
                decoded
            }
            bitmapCache.put(cacheKey, finalBitmap)
            BitmapDrawable(context.resources, finalBitmap)
        } catch (_: Exception) {
            null
        }
    }

    fun clearCache() {
        bitmapCache.evictAll()
        cachedAssetPacks = null
    }

    fun resolveCustomUriDrawable(uriString: String?): Drawable? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                Drawable.createFromStream(stream, uriString)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun resolveAppIcon(packageName: String, activityName: String, fallback: Drawable): Drawable {
        val selectedId = storage.getSelectedIconPackId()
        return when {
            selectedId == IconPackInfo.STYLE_SYSTEM_DEFAULT -> fallback
            selectedId == IconPackInfo.STYLE_NOSSO_UNIVERSO -> applyHudCyanTint(fallback)
            selectedId == IconPackInfo.STYLE_MONOCHROME_HUD -> applyMonochromeSilver(fallback)
            selectedId.startsWith(IconPackInfo.ASSET_PACK_PREFIX) -> {
                resolveFromAssetPack(selectedId, packageName, activityName, fallback)
            }
            else -> loadFromExternalPack(selectedId, packageName, activityName) ?: fallback
        }
    }

    private fun resolveFromAssetPack(
        packId: String,
        appPackage: String,
        activityName: String,
        fallback: Drawable
    ): Drawable {
        val parsed = loadAssetPacks()[packId] ?: return fallback
        val normalizedPkg = appPackage.lowercase()
        val normalizedAct = activityName.lowercase()

        val exactFile = parsed.packageToFile[normalizedPkg]
        if (exactFile != null) {
            loadAssetDrawable("icon_packs/${parsed.folderName}/icons/$exactFile")?.let { return it }
        }

        val keywordMatch = parsed.entries.firstOrNull { entry ->
            entry.keywords.any { kw ->
                normalizedPkg.contains(kw) || normalizedAct.contains(kw)
            }
        }
        if (keywordMatch != null) {
            loadAssetDrawable("icon_packs/${parsed.folderName}/icons/${keywordMatch.fileName}")
                ?.let { return it }
        }

        // Unified dark-titanium adaptive enclosure for unmapped third-party apps
        return applyAdaptiveLiquidEnclosure(fallback)
    }

    private fun loadAssetPacks(): Map<String, ParsedAssetPack> {
        cachedAssetPacks?.let { return it }
        val discovered = linkedMapOf<String, ParsedAssetPack>()
        val folders = try {
            context.assets.list("icon_packs")?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        folders.forEach { folder ->
            val manifestPath = "icon_packs/$folder/manifest.json"
            val parsed = parseManifest(folder, manifestPath)
            if (parsed != null) {
                discovered[parsed.info.id] = parsed
            }
        }
        cachedAssetPacks = discovered
        return discovered
    }

    private fun parseManifest(folder: String, manifestPath: String): ParsedAssetPack? {
        return try {
            val raw = context.assets.open(manifestPath).bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            val rawId = json.optString("id").ifBlank { folder }
            val packId = if (rawId.startsWith(IconPackInfo.ASSET_PACK_PREFIX)) {
                rawId
            } else {
                "${IconPackInfo.ASSET_PACK_PREFIX}$rawId"
            }

            val title = json.optString("title").ifBlank { rawId }
            val subtitle = json.optString("subtitle")
            val author = json.optString("author").ifBlank { "HILM Studio" }
            val version = json.optString("version").ifBlank { "1.0.0" }

            val previewList = mutableListOf<String>()
            val previewArray = json.optJSONArray("previewFiles")
            if (previewArray != null) {
                for (i in 0 until previewArray.length()) {
                    val fileName = previewArray.optString(i)
                    if (fileName.isNotBlank()) {
                        previewList.add("icon_packs/$folder/icons/$fileName")
                    }
                }
            }

            val entries = mutableListOf<AssetIconEntry>()
            val pkgMap = mutableMapOf<String, String>()
            val iconsArray = json.optJSONArray("icons")
            if (iconsArray != null) {
                for (i in 0 until iconsArray.length()) {
                    val item = iconsArray.optJSONObject(i) ?: continue
                    val key = item.optString("key")
                    val label = item.optString("label").ifBlank { key }
                    val fileName = item.optString("file")
                    if (fileName.isBlank()) continue

                    val packages = mutableSetOf<String>()
                    val pkgArr = item.optJSONArray("packages")
                    if (pkgArr != null) {
                        for (p in 0 until pkgArr.length()) {
                            val pkg = pkgArr.optString(p).trim().lowercase()
                            if (pkg.isNotEmpty()) {
                                packages.add(pkg)
                                pkgMap[pkg] = fileName
                            }
                        }
                    }

                    val keywords = mutableListOf<String>()
                    val kwArr = item.optJSONArray("keywords")
                    if (kwArr != null) {
                        for (k in 0 until kwArr.length()) {
                            val kw = kwArr.optString(k).trim().lowercase()
                            if (kw.isNotEmpty()) keywords.add(kw)
                        }
                    }

                    entries.add(
                        AssetIconEntry(
                            key = key,
                            label = label,
                            fileName = fileName,
                            packages = packages,
                            keywords = keywords
                        )
                    )
                }
            }

            val info = IconPackInfo(
                id = packId,
                title = title,
                subtitle = subtitle,
                author = author,
                version = version,
                isBuiltIn = true,
                kind = IconPackKind.ILLUSTRATED_ASSET,
                assetFolder = folder,
                previewFiles = if (previewList.isNotEmpty()) {
                    previewList
                } else {
                    entries.take(6).map { "icon_packs/$folder/icons/${it.fileName}" }
                },
                iconCount = entries.size
            )
            ParsedAssetPack(info, folder, entries, pkgMap)
        } catch (_: Exception) {
            null
        }
    }

    private fun applyAdaptiveLiquidEnclosure(source: Drawable): Drawable {
        val size = 144
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val rect = RectF(6f, 6f, size - 6f, size - 6f)
        val radius = 32f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                intArrayOf(
                    Color.rgb(16, 22, 34),
                    Color.rgb(6, 9, 16),
                    Color.rgb(14, 18, 32)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            shader = LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                intArrayOf(
                    Color.parseColor("#E2E8F0"),
                    Color.parseColor("#22D3EE"),
                    Color.parseColor("#8B5CF6"),
                    Color.parseColor("#CBD5E1")
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        val appBitmap = drawableToBitmap(source, 86)
        val satMatrix = ColorMatrix().apply { setSaturation(0.45f) }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(satMatrix)
        }
        val offset = (size - appBitmap.width) / 2f
        canvas.drawBitmap(appBitmap, offset, offset, iconPaint)
        return BitmapDrawable(context.resources, out)
    }

    private fun applyHudCyanTint(source: Drawable): Drawable {
        val bmp = drawableToBitmap(source, 96)
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val satMatrix = ColorMatrix().apply { setSaturation(0.35f) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(satMatrix)
        }
        canvas.drawBitmap(bmp, 0f, 0f, paint)

        val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(0x3322D3EE, PorterDuff.Mode.SRC_ATOP)
        }
        canvas.drawBitmap(out.copy(Bitmap.Config.ARGB_8888, false), 0f, 0f, tintPaint)
        return BitmapDrawable(context.resources, out)
    }

    private fun applyMonochromeSilver(source: Drawable): Drawable {
        val bmp = drawableToBitmap(source, 96)
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val matrix = ColorMatrix().apply { setSaturation(0f) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        return BitmapDrawable(context.resources, out)
    }

    private fun loadFromExternalPack(
        packPackage: String,
        appPackage: String,
        activityName: String
    ): Drawable? {
        ensurePackLoaded(packPackage)
        val res = cachedResources ?: return null
        val key = "ComponentInfo{$appPackage/$activityName}"
        val drawableName = componentToDrawable[key] ?: return null
        val resId = res.getIdentifier(drawableName, "drawable", packPackage)
        if (resId == 0) return null
        return try {
            res.getDrawable(resId, null)
        } catch (_: Exception) {
            null
        }
    }

    private fun ensurePackLoaded(packPackage: String) {
        if (cachedPackPackage == packPackage && cachedResources != null) return
        cachedPackPackage = packPackage
        componentToDrawable.clear()
        try {
            val res = packageManager.getResourcesForApplication(packPackage)
            cachedResources = res
            val xmlId = res.getIdentifier("appfilter", "xml", packPackage)
            if (xmlId != 0) {
                val parser = res.getXml(xmlId)
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val comp = parser.getAttributeValue(null, "component")
                        val draw = parser.getAttributeValue(null, "drawable")
                        if (!comp.isNullOrBlank() && !draw.isNullOrBlank()) {
                            componentToDrawable[comp] = draw
                        }
                    }
                    parser.next()
                }
            }
        } catch (_: Exception) {
            cachedResources = null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }
}
