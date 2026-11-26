package com.nossouniverso.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.LruCache
import com.nossouniverso.launcher.model.IconPackInfo
import com.nossouniverso.launcher.model.IconPackKind
import com.nossouniverso.launcher.model.IconPackPreviewItem
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class IconPackManager(
    private val context: Context,
    private val storage: ShortcutStorage
) {
    private data class PackIconEntry(
        val key: String,
        val label: String,
        val fileName: String,
        val packages: Set<String>,
        val keywords: List<String>
    )

    private data class ParsedPack(
        val info: IconPackInfo,
        val isAsset: Boolean,
        val folderPath: String,
        val entries: List<PackIconEntry>,
        val packageToFile: Map<String, String>
    )

    private val packageManager: PackageManager = context.packageManager
    private var cachedPackPackage: String? = null
    private var cachedResources: Resources? = null
    private val componentToDrawable = mutableMapOf<String, String>()

    @Volatile
    private var cachedManagedPacks: Map<String, ParsedPack>? = null

    private val importedRootDir: File
        get() = File(context.filesDir, "imported_icon_packs")

    private val renderDiskCacheDir: File
        get() = File(context.cacheDir, "hilm_icon_cache_v3")

    private val bitmapCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024L).toInt() / 16).coerceIn(2048, 8192)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    fun getAvailableIconPacks(): List<IconPackInfo> {
        val packs = mutableListOf<IconPackInfo>()

        loadAllManagedPacks().values
            .sortedWith(
                compareBy<ParsedPack> { it.info.kind != IconPackKind.IMPORTED_PACK }
                    .thenBy { it.info.id != IconPackInfo.STYLE_HILM_ARTIFACTS }
                    .thenBy { it.info.title.lowercase() }
            )
            .forEach { packs.add(it.info) }

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
            ?: getAvailableIconPacks().firstOrNull { it.id == IconPackInfo.STYLE_HILM_ARTIFACTS }
            ?: getAvailableIconPacks().firstOrNull { it.id == IconPackInfo.STYLE_NOSSO_UNIVERSO }
            ?: IconPackInfo(
                id = IconPackInfo.STYLE_SYSTEM_DEFAULT,
                title = "Ícones Originais do Android",
                kind = IconPackKind.SYSTEM_DEFAULT
            )
    }

    fun getPreviewItemsForPack(packId: String): List<IconPackPreviewItem> {
        val parsed = loadAllManagedPacks()[packId] ?: return emptyList()
        return parsed.entries.map { entry ->
            IconPackPreviewItem(
                key = entry.key,
                label = entry.label,
                sourcePath = buildIconSourcePath(parsed, entry.fileName),
                isAsset = parsed.isAsset
            )
        }
    }

    fun loadPreviewDrawable(item: IconPackPreviewItem, targetSizePx: Int = 160): Drawable? {
        return loadPackDrawable(item.sourcePath, item.isAsset, targetSizePx)
    }

    fun loadPackDrawable(sourcePath: String, isAsset: Boolean, targetSizePx: Int = 192): Drawable? {
        val cacheKey = "${if (isAsset) "asset" else "file"}:$sourcePath#$targetSizePx"
        bitmapCache.get(cacheKey)?.let { cached ->
            return BitmapDrawable(context.resources, cached)
        }

        return try {
            val decoded = if (isAsset) {
                context.assets.open(sourcePath).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                FileInputStream(File(sourcePath)).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
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

    fun importIconPackZip(uri: Uri): Result<IconPackInfo> {
        val tempDir = File(context.cacheDir, "icon_pack_import_${System.currentTimeMillis()}")
        return try {
            if (!tempDir.mkdirs()) {
                return Result.failure(IllegalStateException("Não foi possível preparar pasta temporária."))
            }

            val displayName = resolveZipDisplayName(uri)
            val extractedImages = mutableListOf<File>()
            var manifestFile: File? = null
            var totalBytes = 0L
            var entryCount = 0
            val maxBytes = 60L * 1024L * 1024L
            val maxEntries = 600

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalArgumentException("Não foi possível abrir o arquivo .zip selecionado."))

            inputStream.use { rawStream ->
                ZipInputStream(BufferedInputStream(rawStream)).use { zip ->
                    var entry = zip.nextEntry
                    val buffer = ByteArray(8192)
                    while (entry != null) {
                        entryCount++
                        if (entryCount > maxEntries) {
                            return Result.failure(IllegalArgumentException("O pacote .zip possui arquivos demais."))
                        }

                        val entryName = entry.name.replace('\\', '/').trimStart('/')
                        if (!entry.isDirectory && entryName.isNotEmpty() && !entryName.startsWith("__MACOSX/")) {
                            val outFile = File(tempDir, entryName)
                            val canonicalTemp = tempDir.canonicalPath
                            val canonicalOut = outFile.canonicalPath
                            if (!canonicalOut.startsWith("$canonicalTemp${File.separator}")) {
                                return Result.failure(SecurityException("Entrada inválida no arquivo .zip."))
                            }

                            val lower = outFile.name.lowercase()
                            val isManifest = lower == "manifest.json"
                            val isImage = lower.endsWith(".png") || lower.endsWith(".webp")

                            if (isManifest || isImage) {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { fos ->
                                    var read = zip.read(buffer)
                                    while (read != -1) {
                                        totalBytes += read
                                        if (totalBytes > maxBytes) {
                                            return Result.failure(IllegalArgumentException("O arquivo .zip excede o limite de 60 MB."))
                                        }
                                        fos.write(buffer, 0, read)
                                        read = zip.read(buffer)
                                    }
                                }
                                if (isManifest) {
                                    if (manifestFile == null || outFile.relativeTo(tempDir).path.length < manifestFile!!.relativeTo(tempDir).path.length) {
                                        manifestFile = outFile
                                    }
                                } else if (isImage) {
                                    extractedImages.add(outFile)
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            if (extractedImages.isEmpty()) {
                return Result.failure(IllegalArgumentException("Nenhuma imagem PNG/WEBP encontrada dentro do .zip."))
            }

            val parsedManifestJson = manifestFile?.let { file ->
                try {
                    JSONObject(file.readText())
                } catch (_: Exception) {
                    null
                }
            }

            val baseTitle = parsedManifestJson?.optString("title")?.takeIf { it.isNotBlank() }
                ?: parsedManifestJson?.optString("name")?.takeIf { it.isNotBlank() }
                ?: displayName

            val rawId = parsedManifestJson?.optString("id")?.takeIf { it.isNotBlank() }
                ?: sanitizeFolderId(baseTitle)
            val cleanId = sanitizeFolderId(rawId).ifBlank { "pack_${System.currentTimeMillis()}" }

            val targetRoot = importedRootDir
            targetRoot.mkdirs()
            val packDir = File(targetRoot, cleanId)
            if (packDir.exists()) {
                packDir.deleteRecursively()
            }
            val iconsDestDir = File(packDir, "icons")
            iconsDestDir.mkdirs()

            val copiedFileNames = mutableSetOf<String>()
            extractedImages.sortedBy { it.name.lowercase() }.forEach { imgFile ->
                val destName = imgFile.name
                val finalName = if (copiedFileNames.add(destName)) {
                    destName
                } else {
                    "${imgFile.parentFile?.name ?: "icon"}_$destName"
                }
                imgFile.copyTo(File(iconsDestDir, finalName), overwrite = true)
            }

            val finalManifest = if (parsedManifestJson != null && parsedManifestJson.optJSONArray("icons") != null) {
                parsedManifestJson.apply {
                    put("id", cleanId)
                    if (!has("title") && has("name")) {
                        put("title", optString("name"))
                    }
                    if (!has("fallbackStyle")) {
                        put("fallbackStyle", "titanium_liquid")
                    }
                }
            } else {
                buildAutoManifestJson(
                    packId = cleanId,
                    title = baseTitle,
                    fileNames = copiedFileNames.sorted()
                )
            }

            File(packDir, "manifest.json").writeText(finalManifest.toString(2))
            clearCache()

            val loaded = loadAllManagedPacks()["${IconPackInfo.IMPORTED_PACK_PREFIX}$cleanId"]?.info
                ?: return Result.failure(IllegalStateException("Falha ao ler manifesto do pacote importado."))
            Result.success(loaded)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun deleteImportedPack(packId: String): Boolean {
        if (!packId.startsWith(IconPackInfo.IMPORTED_PACK_PREFIX)) return false
        val parsed = loadAllManagedPacks()[packId] ?: return false
        val dir = File(parsed.folderPath)
        val deleted = dir.deleteRecursively()
        if (deleted) {
            if (storage.getSelectedIconPackId() == packId) {
                storage.setSelectedIconPackId(IconPackInfo.STYLE_HILM_ARTIFACTS)
            }
            clearCache()
        }
        return deleted
    }

    fun clearCache() {
        bitmapCache.evictAll()
        cachedManagedPacks = null
        try {
            if (renderDiskCacheDir.exists()) {
                renderDiskCacheDir.deleteRecursively()
            }
        } catch (_: Exception) {
            // Ignore disk cache cleanup issues
        }
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
        val activeTheme = storage.getLauncherTheme()
        if (activeTheme == ShortcutStorage.THEME_LUNA) {
            return renderUnifiedLunaHolographicIcon(
                cacheIdentity = "theme_luna:${packageName.lowercase()}/${activityName.lowercase()}",
                packageName = packageName,
                fallback = fallback
            )
        }
        if (activeTheme == ShortcutStorage.THEME_MAYA) {
            return renderUnifiedMayaCeramicIcon(
                cacheIdentity = "theme_maya:${packageName.lowercase()}/${activityName.lowercase()}",
                packageName = packageName,
                fallback = fallback
            )
        }
        val selectedId = storage.getSelectedIconPackId()
        return resolveForPack(selectedId, packageName, activityName, fallback)
    }

    fun resolveForPack(
        packId: String,
        packageName: String,
        activityName: String,
        fallback: Drawable
    ): Drawable {
        return when {
            packId == IconPackInfo.STYLE_SYSTEM_DEFAULT -> fallback
            packId == IconPackInfo.STYLE_LUNA_HOLOGRAPHIC -> renderUnifiedLunaHolographicIcon(
                cacheIdentity = "${packageName.lowercase()}/${activityName.lowercase()}",
                packageName = packageName,
                fallback = fallback
            )
            packId == IconPackInfo.STYLE_MAYA_CERAMIC_GOLD -> renderUnifiedMayaCeramicIcon(
                cacheIdentity = "${packageName.lowercase()}/${activityName.lowercase()}",
                packageName = packageName,
                fallback = fallback
            )
            packId == IconPackInfo.LEGACY_STYLE_HILM_ARTIFACTS -> resolveFromManagedPack(
                IconPackInfo.STYLE_HILM_ARTIFACTS,
                packageName,
                activityName,
                fallback
            )
            packId == IconPackInfo.LEGACY_STYLE_LIQUID_GOLD_OBSIDIAN -> resolveFromManagedPack(
                IconPackInfo.STYLE_LIQUID_GOLD_OBSIDIAN,
                packageName,
                activityName,
                fallback
            )
            packId == IconPackInfo.STYLE_NOSSO_UNIVERSO -> applyHudCyanTint(fallback)
            packId == IconPackInfo.STYLE_MONOCHROME_HUD -> applyMonochromeSilver(fallback)
            packId.startsWith(IconPackInfo.ASSET_PACK_PREFIX) ||
                packId.startsWith(IconPackInfo.IMPORTED_PACK_PREFIX) -> {
                resolveFromManagedPack(packId, packageName, activityName, fallback)
            }
            else -> loadFromExternalPack(packId, packageName, activityName) ?: fallback
        }
    }

    private fun resolveFromManagedPack(
        packId: String,
        appPackage: String,
        activityName: String,
        fallback: Drawable
    ): Drawable {
        val parsed = loadAllManagedPacks()[packId] ?: return fallback
        val normalizedPkg = appPackage.lowercase()
        val normalizedAct = activityName.lowercase()

        val exactFile = parsed.packageToFile[normalizedPkg]
        if (exactFile != null) {
            loadPackDrawable(buildIconSourcePath(parsed, exactFile), parsed.isAsset)?.let { return it }
        }

        val keywordMatch = parsed.entries.firstOrNull { entry ->
            entry.keywords.any { kw ->
                normalizedPkg.contains(kw) || normalizedAct.contains(kw)
            }
        }
        if (keywordMatch != null) {
            loadPackDrawable(buildIconSourcePath(parsed, keywordMatch.fileName), parsed.isAsset)
                ?.let { return it }
        }

        // Automatically sculpt ANY unmapped / future third-party app into unified HILM style
        return when (parsed.info.fallbackStyle.lowercase()) {
            "none", "original", "system" -> fallback
            "hud_cyan" -> applyHudCyanTint(fallback)
            "monochrome" -> applyMonochromeSilver(fallback)
            "luna_holographic", "luna-holographic" -> renderUnifiedLunaHolographicIcon(
                cacheIdentity = "$packId:$normalizedPkg/$normalizedAct",
                packageName = appPackage,
                fallback = fallback
            )
            "liquid_gold_obsidian", "liquid-gold-obsidian" -> renderUnifiedLiquidGoldObsidianIcon(
                cacheIdentity = "$packId:$normalizedPkg/$normalizedAct",
                fallback = fallback
            )
            "hilm_artifacts" -> renderHilmArtifactIcon(
                packageName = appPackage,
                activityName = activityName,
                fallback = fallback
            )
            "tron_neon", "tron-neon" -> renderUnifiedTronNeonIcon(
                cacheIdentity = "$packId:$normalizedPkg/$normalizedAct",
                fallback = fallback
            )
            "cyberpunk_holo", "cyberpunk-holo" -> renderUnifiedCyberpunkHoloIcon(
                cacheIdentity = "$packId:$normalizedPkg/$normalizedAct",
                fallback = fallback
            )
            "obsidian_core", "obsidian-core" -> renderUnifiedObsidianCoreIcon(
                cacheIdentity = "$packId:$normalizedPkg/$normalizedAct",
                fallback = fallback
            )
            else -> renderUnifiedTitaniumLiquidIcon(
                cacheIdentity = "$packId:$normalizedPkg/$normalizedAct",
                fallback = fallback
            )
        }
    }

    private enum class ArtifactArchetype(val baseFileName: String) {
        OPTICAL_LENS("base_optical_lens.png"),
        ACOUSTIC_WAVES("base_acoustic_waves.png"),
        GLASS_PLANES("base_glass_planes.png"),
        COMMUNICATION_PULSES("base_communication_pulses.png"),
        ORBITAL_GLASS_ORB("base_orbital_glass_orb.png"),
        PRECISION_MECHANICS("base_precision_mechanics.png")
    }

    private fun classifyAppArtifact(packageName: String, activityName: String): ArtifactArchetype {
        val id = "$packageName/$activityName".lowercase()
        return when {
            listOf(
                "camera", "gcam", "cam", "lens", "scanner", "lightroom", "vsco",
                "capcut", "youtube", "netflix", "twitch", "prime", "disney", "video"
            ).any { id.contains(it) } -> ArtifactArchetype.OPTICAL_LENS

            listOf(
                "music", "spotify", "deezer", "tidal", "soundcloud", "podcast",
                "shazam", "audio", "radio", "recorder", "bandlab", "sound"
            ).any { id.contains(it) } -> ArtifactArchetype.ACOUSTIC_WAVES

            listOf(
                "gallery", "galeria", "photos", "album", "pinterest", "canva",
                "figma", "notes", "keep", "notion", "obsidian", "docs", "drive",
                "files", "documentsui", "myfiles", "snapseed"
            ).any { id.contains(it) } -> ArtifactArchetype.GLASS_PLANES

            listOf(
                "message", "mensagens", "mms", "sms", "whatsapp", "telegram",
                "discord", "slack", "teams", "signal", "messenger", "instagram",
                "facebook", "twitter", "threads", "reddit", "tiktok", "dialer",
                "phone", "contacts", "gmail", "outlook", "mail"
            ).any { id.contains(it) } -> ArtifactArchetype.COMMUNICATION_PULSES

            listOf(
                "browser", "chrome", "firefox", "brave", "opera", "edge",
                "duckduckgo", "maps", "waze", "uber", "99", "moovit", "earth",
                "weather", "clima", "booking", "airbnb", "compass", "vpn"
            ).any { id.contains(it) } -> ArtifactArchetype.ORBITAL_GLASS_ORB

            listOf(
                "settings", "configuracoes", "github", "gitlab", "androidide",
                "termux", "vscode", "calculator", "clock", "alarm", "calendar",
                "vending", "playstore", "nubank", "inter", "itau", "bradesco",
                "santander", "mercadopago", "picpay", "paypal", "wallet", "bank",
                "pix", "steam", "game"
            ).any { id.contains(it) } -> ArtifactArchetype.PRECISION_MECHANICS

            else -> {
                val values = ArtifactArchetype.values()
                values[abs(packageName.hashCode()) % values.size]
            }
        }
    }

    private fun loadArtifactBaseBitmap(archetype: ArtifactArchetype, targetSize: Int): Bitmap? {
        val assetPath = "icon_packs/hilm_artifacts/bases/${archetype.baseFileName}"
        val cacheKey = "hilm_base:$assetPath#$targetSize"
        bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return cached
        }
        return try {
            val decoded = context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null
            val scaled = if (decoded.width != targetSize || decoded.height != targetSize) {
                Bitmap.createScaledBitmap(decoded, targetSize, targetSize, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }
            bitmapCache.put(cacheKey, scaled)
            scaled
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Universal HILM 3D Artifact renderer for EVERY unmapped third-party Android app:
     * composites the preserved colorful app symbol directly over a photorealistic 3D CGI
     * sculptural base (optical glass lens, volumetric acoustic waves, layered glass planes,
     * orbital glass orb, or precision titanium mechanics) with real depth shadows and zero square cards.
     */
    private fun renderHilmArtifactIcon(
        packageName: String,
        activityName: String,
        fallback: Drawable
    ): Drawable {
        val size = 224
        val normalizedPkg = packageName.lowercase()
        val normalizedAct = activityName.lowercase()
        val cacheKey = "hilm_artifact_cgi_v3:$normalizedPkg/$normalizedAct#$size"
        bitmapCache.get(cacheKey)?.let { cached ->
            return BitmapDrawable(context.resources, cached)
        }

        val diskFile = diskCacheFileForKey(cacheKey)
        if (diskFile.exists()) {
            try {
                FileInputStream(diskFile).use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { decoded ->
                        bitmapCache.put(cacheKey, decoded)
                        return BitmapDrawable(context.resources, decoded)
                    }
                }
            } catch (_: Exception) {
                // Re-render cleanly
            }
        }

        val archetype = classifyAppArtifact(normalizedPkg, normalizedAct)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cx = size / 2f
        val cy = size / 2f

        // 1. Real photorealistic 3D CGI sculptural base (pre-rendered RGBA 256px asset)
        val baseBitmap = loadArtifactBaseBitmap(archetype, size)
        if (baseBitmap != null && !baseBitmap.isRecycled) {
            canvas.drawBitmap(
                baseBitmap,
                null,
                RectF(0f, 0f, size.toFloat(), size.toFloat()),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        }

        // 2. Soft dark central optical vignette on wave bases so third-party icons pop sharply
        if (archetype == ArtifactArchetype.ACOUSTIC_WAVES ||
            archetype == ArtifactArchetype.COMMUNICATION_PULSES
        ) {
            val cushionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx,
                    cy,
                    size * 0.34f,
                    intArrayOf(
                        Color.argb(195, 5, 9, 18),
                        Color.argb(120, 6, 12, 24),
                        Color.argb(0, 6, 12, 24)
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, size * 0.34f, cushionPaint)
        }

        // 3. Extract & composite full-color recognizable app symbol (preserving colors + shape)
        val prepared = prepareColorfulForegroundSymbol(fallback, 148)
        drawPreservedColorfulSymbol(canvas, prepared, size, archetype)
        if (!prepared.bitmap.isRecycled) {
            prepared.bitmap.recycle()
        }

        bitmapCache.put(cacheKey, out)
        try {
            renderDiskCacheDir.mkdirs()
            FileOutputStream(diskFile).use { fos ->
                out.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (_: Exception) {
            // Disk cache write is best-effort
        }
        return BitmapDrawable(context.resources, out)
    }

    private fun prepareColorfulForegroundSymbol(drawable: Drawable, targetSize: Int): PreparedSymbol {
        var brandColor: Int? = null
        val raw: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            val fg = drawable.foreground
            val bg = drawable.background
            if (fg != null) {
                val bmp = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                val pad = (targetSize * 0.19f).toInt()
                fg.setBounds(-pad, -pad, targetSize + pad, targetSize + pad)
                fg.draw(c)
                if (bg != null && isLowSaturationBitmap(bmp)) {
                    brandColor = sampleDominantBrandColor(bg)
                }
                bmp
            } else {
                drawableToBitmap(drawable, targetSize)
            }
        } else {
            drawableToBitmap(drawable, targetSize)
        }

        val transparentRatio = countTransparentPixels(raw).toFloat() / (raw.width * raw.height).toFloat()
        val isolated = if (transparentRatio >= 0.14f) {
            val trimmed = trimTransparentToSquare(raw, targetSize)
            if (trimmed !== raw) raw.recycle()
            PreparedSymbol(trimmed, true, brandColor)
        } else {
            val stripped = stripUniformCornerBackground(raw)
            val postRatio = countTransparentPixels(stripped).toFloat() / (stripped.width * stripped.height).toFloat()
            if (postRatio in 0.16f..0.94f) {
                if (stripped !== raw) raw.recycle()
                val trimmed = trimTransparentToSquare(stripped, targetSize)
                if (trimmed !== stripped) stripped.recycle()
                PreparedSymbol(trimmed, true, brandColor)
            } else {
                if (stripped !== raw) stripped.recycle()
                PreparedSymbol(raw, false, brandColor)
            }
        }

        // If adaptive icon had a white glyph over a colored background (e.g. WhatsApp, YouTube, Nubank),
        // weave a tight contour-hugging brand color halo/tint behind the white symbol (NO square plate!)
        val extractedBrand = isolated.brandColor
        if (isolated.isIsolatedSilhouette && extractedBrand != null) {
            val tinted = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val tc = Canvas(tinted)
            val mask = isolated.bitmap.extractAlpha()
            val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                color = extractedBrand
                maskFilter = BlurMaskFilter(9f, BlurMaskFilter.Blur.NORMAL)
            }
            tc.drawBitmap(mask, 0f, 0f, haloPaint)

            val crispBackingPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(blendTowardsWhite(extractedBrand, 0.22f), PorterDuff.Mode.SRC_IN)
            }
            // Subtle crisp brand contour slightly behind foreground glyph
            val expandedRect = RectF(-2.5f, -2.5f, targetSize + 2.5f, targetSize + 2.5f)
            tc.drawBitmap(isolated.bitmap, null, expandedRect, crispBackingPaint)
            tc.drawBitmap(isolated.bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            mask.recycle()
            if (!isolated.bitmap.isRecycled) isolated.bitmap.recycle()
            return PreparedSymbol(tinted, true, extractedBrand)
        }

        return isolated
    }

    private fun isLowSaturationBitmap(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        val step = max(1, w / 18)
        val hsv = FloatArray(3)
        var opaqueSamples = 0
        var satSum = 0f
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val px = bmp.getPixel(x, y)
                if (Color.alpha(px) > 120) {
                    Color.colorToHSV(px, hsv)
                    satSum += hsv[1]
                    opaqueSamples++
                }
                x += step
            }
            y += step
        }
        if (opaqueSamples == 0) return false
        return (satSum / opaqueSamples) < 0.20f
    }

    private fun sampleDominantBrandColor(bg: Drawable): Int? {
        val sample = drawableToBitmap(bg, 48)
        val hsv = FloatArray(3)
        var bestScore = -1f
        var bestColor: Int? = null
        for (y in 8 until 40 step 4) {
            for (x in 8 until 40 step 4) {
                val px = sample.getPixel(x, y)
                if (Color.alpha(px) < 180) continue
                Color.colorToHSV(px, hsv)
                val score = hsv[1] * (0.4f + hsv[2] * 0.6f)
                if (hsv[1] > 0.22f && score > bestScore) {
                    bestScore = score
                    bestColor = Color.rgb(Color.red(px), Color.green(px), Color.blue(px))
                }
            }
        }
        sample.recycle()
        return bestColor
    }

    private fun blendTowardsWhite(color: Int, ratio: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun drawPreservedColorfulSymbol(
        canvas: Canvas,
        prepared: PreparedSymbol,
        canvasSize: Int,
        archetype: ArtifactArchetype
    ) {
        val symbolScale = when (archetype) {
            ArtifactArchetype.OPTICAL_LENS -> 0.44f
            ArtifactArchetype.ORBITAL_GLASS_ORB -> 0.48f
            else -> 0.54f
        }
        val drawSize = (canvasSize * symbolScale).toInt()
        val shiftX = if (archetype == ArtifactArchetype.OPTICAL_LENS) canvasSize * 0.065f else 0f
        val offset = (canvasSize - drawSize) / 2f
        val destRect = RectF(offset + shiftX, offset, offset + shiftX + drawSize, offset + drawSize)

        if (prepared.isIsolatedSilhouette) {
            val mask = prepared.bitmap.extractAlpha()
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                color = Color.argb(190, 2, 6, 14)
                maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawBitmap(
                mask,
                null,
                RectF(destRect.left + 3f, destRect.top + 6f, destRect.right + 3f, destRect.bottom + 6f),
                shadowPaint
            )
            mask.recycle()
            canvas.drawBitmap(
                prepared.bitmap,
                null,
                destRect,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        } else {
            // Non-isolated full-bleed legacy bitmap: clip into organic bevelled circular optical aperture (never a square tile)
            val cx = canvasSize / 2f + shiftX
            val cy = canvasSize / 2f
            val r = drawSize * 0.48f
            val aperture = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }

            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 2, 6, 14)
                maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(cx + 2.5f, cy + 5f, r, shadowPaint)

            canvas.save()
            canvas.clipPath(aperture)
            canvas.drawBitmap(
                prepared.bitmap,
                null,
                destRect,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            canvas.restore()

            val glassRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.2f
                shader = LinearGradient(
                    cx - r,
                    cy - r,
                    cx + r,
                    cy + r,
                    Color.argb(195, 255, 255, 255),
                    Color.argb(110, 34, 211, 238),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, r, glassRim)
        }
    }

    /**
     * Converts any third-party or future app icon into the exact Titanium Liquid aesthetic:
     * glossy obsidian squircle, liquid-titanium chrome bevel, electric cyan/violet rim glow,
     * and sculpted liquid-silver foreground artwork.
     */
    private fun renderUnifiedTitaniumLiquidIcon(
        cacheIdentity: String,
        fallback: Drawable
    ): Drawable {
        val size = 192
        val cacheKey = "liquid_auto:$cacheIdentity#$size"
        bitmapCache.get(cacheKey)?.let { cached ->
            return BitmapDrawable(context.resources, cached)
        }

        val diskFile = diskCacheFileForKey(cacheKey)
        if (diskFile.exists()) {
            try {
                FileInputStream(diskFile).use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { decoded ->
                        bitmapCache.put(cacheKey, decoded)
                        return BitmapDrawable(context.resources, decoded)
                    }
                }
            } catch (_: Exception) {
                // Re-render cleanly if disk entry was corrupted
            }
        }

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val margin = size * 0.06f
        val rect = RectF(margin, margin, size - margin, size - margin)
        val radius = size * 0.23f
        val squirclePath = Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        }

        // 1. Soft outer cyan & violet rim bloom clipped safely inside canvas margin
        val cyanBloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = Color.argb(115, 34, 211, 238)
            maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(squirclePath, cyanBloomPaint)

        // 2. Deep glossy obsidian interior gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    Color.rgb(20, 27, 42),
                    Color.rgb(5, 8, 15),
                    Color.rgb(10, 14, 28)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(squirclePath, bgPaint)

        // 3. Extract & sculpt app foreground symbol into liquid titanium chrome
        val prepared = prepareForegroundSymbol(fallback, 128)
        if (prepared.isIsolatedSilhouette) {
            drawSculptedLiquidSilverSymbol(canvas, prepared.bitmap, size)
        } else {
            drawMetallicFramedArtwork(canvas, prepared.bitmap, size)
        }
        if (!prepared.bitmap.isRecycled) {
            prepared.bitmap.recycle()
        }

        // 4. Glossy glass dome specular reflection overlay inside squircle
        canvas.save()
        canvas.clipPath(squirclePath)
        val domePath = Path().apply {
            addOval(
                RectF(
                    rect.left - size * 0.12f,
                    rect.top - size * 0.35f,
                    rect.right + size * 0.12f,
                    rect.top + rect.height() * 0.52f
                ),
                Path.Direction.CW
            )
        }
        val domePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                rect.top,
                0f,
                rect.top + rect.height() * 0.45f,
                Color.argb(42, 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(domePath, domePaint)
        canvas.restore()

        // 5. Molten liquid-titanium chrome bevel border + neon cyan/violet rim
        val outerChromePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    Color.parseColor("#F8FAFC"),
                    Color.parseColor("#64748B"),
                    Color.parseColor("#22D3EE"),
                    Color.parseColor("#8B5CF6"),
                    Color.parseColor("#E2E8F0")
                ),
                floatArrayOf(0f, 0.28f, 0.55f, 0.80f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(squirclePath, outerChromePaint)

        // Crisp inner bevel highlight
        val innerRect = RectF(
            rect.left + 4.5f,
            rect.top + 4.5f,
            rect.right - 4.5f,
            rect.bottom - 4.5f
        )
        val innerRadius = max(4f, radius - 4.5f)
        val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            shader = LinearGradient(
                innerRect.left,
                innerRect.top,
                innerRect.right,
                innerRect.bottom,
                intArrayOf(
                    Color.argb(215, 255, 255, 255),
                    Color.argb(90, 34, 211, 238),
                    Color.argb(170, 139, 92, 246),
                    Color.argb(130, 34, 211, 238)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(innerRect, innerRadius, innerRadius, innerRimPaint)

        // Bright top-left specular catchlight dot/glow matching Titanium Liquid style
        val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                rect.left + rect.width() * 0.16f,
                rect.top + rect.height() * 0.15f,
                size * 0.14f,
                intArrayOf(Color.argb(195, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(
            rect.left + rect.width() * 0.16f,
            rect.top + rect.height() * 0.15f,
            size * 0.14f,
            glintPaint
        )

        bitmapCache.put(cacheKey, out)
        try {
            renderDiskCacheDir.mkdirs()
            FileOutputStream(diskFile).use { fos ->
                out.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (_: Exception) {
            // Disk cache write is best-effort
        }
        return BitmapDrawable(context.resources, out)
    }

    /**
     * Liquid Gold & Obsidian 3D SDF + Height-Field Pixel Shader replicating Titanium Liquid's
     * true 3D construction: thick molded double-refraction liquid-gold squircle border, deep
     * smoked-obsidian concave glass well (#06070A), sharp optical specular flares, and
     * normal-mapped 3D carved liquid-gold internal symbols (#FFF3B0 / #D4AF37 / #785513).
     */
    private fun renderUnifiedLiquidGoldObsidianIcon(
        cacheIdentity: String,
        fallback: Drawable
    ): Drawable {
        val size = 224
        val cacheKey = "liquid_gold_obsidian_v2:$cacheIdentity#$size"
        bitmapCache.get(cacheKey)?.let { cached ->
            return BitmapDrawable(context.resources, cached)
        }

        val diskFile = diskCacheFileForKey(cacheKey)
        if (diskFile.exists()) {
            try {
                FileInputStream(diskFile).use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { decoded ->
                        bitmapCache.put(cacheKey, decoded)
                        return BitmapDrawable(context.resources, decoded)
                    }
                }
            } catch (_: Exception) {
                // Re-render cleanly if disk entry was corrupted
            }
        }

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)

        val hashShift = ((cacheIdentity.hashCode() and 0x1F) - 16) * 0.04f
        val half = size * 0.5f
        val margin = size * 0.052f
        val boxHalf = half - margin
        val cornerR = size * 0.225f
        val innerBox = boxHalf - cornerR
        val rimWidth = size * 0.088f

        fun roundedBoxSdf(px: Float, py: Float): Float {
            val dx = abs(px - half) - innerBox
            val dy = abs(py - half) - innerBox
            val ox = max(dx, 0f)
            val oy = max(dy, 0f)
            return sqrt(ox * ox + oy * oy) + min(max(dx, dy), 0f) - cornerR
        }

        for (y in 0 until size) {
            val fy = y + 0.5f
            val rowOff = y * size
            for (x in 0 until size) {
                val fx = x + 0.5f
                val d = roundedBoxSdf(fx, fy)

                if (d > 8.0f) {
                    continue
                }

                if (d > 0.7f) {
                    // Soft 3D external contact shadow + subtle molten-gold refractive halo
                    val shadowD = roundedBoxSdf(fx - 1.2f, fy - 2.8f)
                    val shadowAlpha = if (shadowD > 0f) (0.58f * kotlin.math.exp(-shadowD * 0.42f)) else 0.45f
                    val goldBloom = 0.24f * kotlin.math.exp(-d * 0.46f)
                    val a = ((shadowAlpha + goldBloom) * 255f).toInt().coerceIn(0, 235)
                    val br = (goldBloom * 212f).toInt().coerceIn(0, 255)
                    val bg = (goldBloom * 175f).toInt().coerceIn(0, 255)
                    val bb = (goldBloom * 55f).toInt().coerceIn(0, 255)
                    pixels[rowOff + x] = Color.argb(a, br, bg, bb)
                    continue
                }

                // Compute exact SDF normal via finite differences
                val gx = roundedBoxSdf(fx + 0.8f, fy) - roundedBoxSdf(fx - 0.8f, fy)
                val gy = roundedBoxSdf(fx, fy + 0.8f) - roundedBoxSdf(fx, fy - 0.8f)
                val glen = max(1e-4f, sqrt(gx * gx + gy * gy))
                val enx = gx / glen
                val eny = gy / glen

                val edgeAA = ((0.7f - d) / 1.4f).coerceIn(0f, 1f)
                val angle = atan2(fy - half, fx - half) + hashShift

                if (d >= -rimWidth) {
                    // Thick 3D sculpted toroidal liquid-gold + glass double-bevel bezel
                    val t = (-d / rimWidth).coerceIn(0f, 1f) // 0 at outer edge, 1 at inner obsidian well
                    val u = t * 2f - 1f // -1..+1 across bezel cross-section
                    val nz = sqrt((1f - u * u * 0.86f).coerceIn(0.05f, 1f))
                    val nx = enx * u * 0.88f
                    val ny = eny * u * 0.88f

                    // Key light (top-left) + secondary caustic bounce (bottom-right)
                    val diff1 = max(0f, nx * -0.60f + ny * -0.66f + nz * 0.45f)
                    val diff2 = max(0f, nx * 0.56f + ny * 0.58f + nz * 0.50f)

                    // Half-vectors for razor-sharp specular lens flares & anisotropic sheen
                    val spec1 = (nx * -0.34f + ny * -0.38f + nz * 0.86f).coerceAtLeast(0f).pow(26f)
                    val spec2 = (nx * 0.34f + ny * 0.36f + nz * 0.87f).coerceAtLeast(0f).pow(30f)

                    // Organic liquid-glass double refraction ridge along bezel
                    val doubleRidge = 0.5f + 0.5f * cos((t - 0.5f) * 6.28318f * 1.45f)
                    val sweep = 0.5f + 0.5f * cos(angle * 2f - 0.72f)
                    val cornerFlare = (0.5f + 0.5f * sin(angle * 2f + 1.57f)).pow(3.5f) * (0.35f + 0.65f * doubleRidge)

                    val metalEnergy = (
                        0.16f +
                            0.44f * diff1 +
                            0.24f * diff2 +
                            0.18f * sweep * doubleRidge +
                            0.36f * cornerFlare +
                            0.85f * spec1 +
                            0.65f * spec2
                        ).coerceIn(0f, 1.15f)

                    val rgb = sampleGoldRampRgb(metalEnergy.coerceIn(0f, 1f))
                    // Deepen outer perimeter silhouette slightly for physical 3D glass rim separation
                    val outerLipShade = if (t < 0.08f) 0.55f + (t / 0.08f) * 0.45f else 1f
                    val a = (edgeAA * 255f).toInt().coerceIn(0, 255)
                    pixels[rowOff + x] = Color.argb(
                        a,
                        (rgb[0] * outerLipShade).toInt().coerceIn(0, 255),
                        (rgb[1] * outerLipShade).toInt().coerceIn(0, 255),
                        (rgb[2] * outerLipShade).toInt().coerceIn(0, 255)
                    )
                } else {
                    // Inner recessed smoked-obsidian glass cavity (#06070A) with depth & dome gloss
                    val wellDepth = (-d - rimWidth) / (boxHalf - rimWidth)
                    val nxNorm = (fx - half) / boxHalf
                    val nyNorm = (fy - half) / boxHalf
                    val radialDist = sqrt(nxNorm * nxNorm + nyNorm * nyNorm).coerceIn(0f, 1.4f)

                    // Smoked obsidian floor with ultra-subtle concave specular dome
                    val dome = if (nyNorm < 0.12f) {
                        val arc = (1f - ((nyNorm + 0.48f) * 1.25f).pow(2f)).coerceIn(0f, 1f)
                        arc * (0.28f + 0.72f * (1f - abs(nxNorm * 0.7f))).coerceIn(0f, 1f)
                    } else 0f

                    // Delicate refractive golden hairline right inside the glass bevel lip
                    val innerLipGlow = kotlin.math.exp(-((-d - rimWidth - 2.2f).let { it * it }) * 0.32f) *
                        (0.28f + 0.72f * (0.5f + 0.5f * cos(angle * 2f - 0.6f)))

                    val rObs = 6f + dome * 24f + innerLipGlow * 212f - radialDist * 2.5f
                    val gObs = 7f + dome * 18f + innerLipGlow * 175f - radialDist * 2.5f
                    val bObs = 10f + dome * 8f + innerLipGlow * 55f - radialDist * 2.0f

                    // Subtle inner concave vignette near glass walls
                    val wallShade = (0.72f + 0.28f * (wellDepth * 3.5f).coerceIn(0f, 1f))
                    pixels[rowOff + x] = Color.argb(
                        (edgeAA * 255f).toInt().coerceIn(0, 255),
                        (rObs * wallShade).toInt().coerceIn(0, 255),
                        (gObs * wallShade).toInt().coerceIn(0, 255),
                        (bObs * wallShade).toInt().coerceIn(0, 255)
                    )
                }
            }
        }
        out.setPixels(pixels, 0, size, 0, 0, size, size)

        val canvas = Canvas(out)

        // 2. Sculpted 3D height-field molten liquid-gold internal symbol
        val prepared = prepareForegroundSymbol(fallback, 148)
        if (prepared.isIsolatedSilhouette) {
            drawSculptedLiquidGoldSymbol(canvas, prepared.bitmap, size, hashShift)
        } else {
            drawGoldMetallicFramedArtwork(canvas, prepared.bitmap, size, hashShift)
        }
        if (!prepared.bitmap.isRecycled) {
            prepared.bitmap.recycle()
        }

        // 3. Sharp optical catchlight lens flare on top-left & bottom-right liquid glass rim
        val marginPx = margin + rimWidth * 0.45f
        val flareTLX = marginPx + size * 0.11f
        val flareTLY = marginPx + size * 0.11f
        val flareTLPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                flareTLX,
                flareTLY,
                size * 0.13f,
                intArrayOf(
                    Color.argb(235, 255, 252, 232),
                    Color.argb(125, 255, 243, 176),
                    Color.argb(28, 212, 175, 55),
                    Color.argb(0, 212, 175, 55)
                ),
                floatArrayOf(0f, 0.22f, 0.56f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(flareTLX, flareTLY, size * 0.13f, flareTLPaint)

        val flareBRX = size - marginPx - size * 0.11f
        val flareBRY = size - marginPx - size * 0.11f
        val flareBRPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                flareBRX,
                flareBRY,
                size * 0.105f,
                intArrayOf(
                    Color.argb(200, 255, 248, 210),
                    Color.argb(72, 212, 175, 55),
                    Color.argb(0, 120, 85, 19)
                ),
                floatArrayOf(0f, 0.36f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(flareBRX, flareBRY, size * 0.105f, flareBRPaint)

        bitmapCache.put(cacheKey, out)
        try {
            renderDiskCacheDir.mkdirs()
            FileOutputStream(diskFile).use { fos ->
                out.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (_: Exception) {
            // Disk cache write is best-effort
        }
        return BitmapDrawable(context.resources, out)
    }

    private fun sampleGoldRampRgb(tRaw: Float): IntArray {
        val t = tRaw.coerceIn(0f, 1f)
        val stopsT = floatArrayOf(0.00f, 0.14f, 0.30f, 0.45f, 0.62f, 0.77f, 0.90f, 1.00f)
        val stopsR = intArrayOf(6, 36, 92, 120, 186, 212, 248, 255)
        val stopsG = intArrayOf(7, 24, 62, 85, 144, 175, 226, 246)
        val stopsB = intArrayOf(10, 7, 13, 19, 34, 55, 142, 204)
        for (i in 0 until stopsT.size - 1) {
            val t0 = stopsT[i]
            val t1 = stopsT[i + 1]
            if (t <= t1) {
                val u = ((t - t0) / max(1e-5f, t1 - t0)).coerceIn(0f, 1f)
                val s = u * u * (3f - 2f * u)
                return intArrayOf(
                    (stopsR[i] + (stopsR[i + 1] - stopsR[i]) * s).toInt(),
                    (stopsG[i] + (stopsG[i + 1] - stopsG[i]) * s).toInt(),
                    (stopsB[i] + (stopsB[i + 1] - stopsB[i]) * s).toInt()
                )
            }
        }
        return intArrayOf(255, 246, 204)
    }

    /**
     * Converts any unmapped native or third-party app icon into the TRON Neon aesthetic:
     * deep obsidian-teal 3D beveled tile, electric cyan neon double rim, glossy top glass specular,
     * warm amber micro-accent bar, and luminous cyan/amber sculpted foreground symbol.
     */
    private fun renderUnifiedTronNeonIcon(
        cacheIdentity: String,
        fallback: Drawable
    ): Drawable {
        val size = 192
        val cacheKey = "tron_neon_auto_v1:$cacheIdentity#$size"
        bitmapCache.get(cacheKey)?.let { cached ->
            return BitmapDrawable(context.resources, cached)
        }

        val diskFile = diskCacheFileForKey(cacheKey)
        if (diskFile.exists()) {
            try {
                FileInputStream(diskFile).use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { decoded ->
                        bitmapCache.put(cacheKey, decoded)
                        return BitmapDrawable(context.resources, decoded)
                    }
                }
            } catch (_: Exception) {
                // Re-render cleanly if disk entry was corrupted
            }
        }

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val margin = size * 0.08f
        val rect = RectF(margin, margin - 2f, size - margin, size - margin - 2f)
        val radius = size * 0.22f
        val squirclePath = Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        }

        // 1. Subtle 3D stacked back-slab edge matching TRON dimensional tiles
        val backRect = RectF(rect.left + 3f, rect.top + 5.5f, rect.right + 3f, rect.bottom + 5.5f)
        val backFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(4, 34, 46)
        }
        canvas.drawRoundRect(backRect, radius, radius, backFillPaint)
        val backStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
            color = Color.argb(145, 14, 165, 233)
        }
        canvas.drawRoundRect(backRect, radius, radius, backStrokePaint)

        // 2. Electric cyan outer neon aura
        val cyanAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.argb(150, 34, 211, 238)
            maskFilter = BlurMaskFilter(7.5f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(squirclePath, cyanAuraPaint)

        // 3. Deep obsidian-teal interior gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    Color.rgb(11, 46, 60),
                    Color.rgb(4, 18, 26),
                    Color.rgb(2, 9, 14)
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(squirclePath, bgPaint)

        // 4. Extract & render foreground app symbol in glowing TRON Neon style
        val prepared = prepareForegroundSymbol(fallback, 128)
        if (prepared.isIsolatedSilhouette) {
            drawSculptedTronNeonSymbol(canvas, prepared.bitmap, size)
        } else {
            drawTronFramedArtwork(canvas, prepared.bitmap, size)
        }
        if (!prepared.bitmap.isRecycled) {
            prepared.bitmap.recycle()
        }

        // 5. Glossy cyan glass top specular reflection inside tile
        canvas.save()
        canvas.clipPath(squirclePath)
        val domePath = Path().apply {
            addOval(
                RectF(
                    rect.left - size * 0.14f,
                    rect.top - size * 0.34f,
                    rect.right + size * 0.14f,
                    rect.top + rect.height() * 0.46f
                ),
                Path.Direction.CW
            )
        }
        val domePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                rect.top,
                0f,
                rect.top + rect.height() * 0.44f,
                Color.argb(72, 103, 232, 249),
                Color.argb(0, 34, 211, 238),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(domePath, domePaint)

        // Signature warm amber status micro-pill at top-right corner of tile
        val pillW = size * 0.145f
        val pillH = size * 0.048f
        val pillRect = RectF(
            rect.right - size * 0.11f - pillW,
            rect.top + size * 0.095f,
            rect.right - size * 0.11f,
            rect.top + size * 0.095f + pillH
        )
        val amberGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(145, 245, 158, 11)
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, amberGlowPaint)
        val amberPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                pillRect.left,
                pillRect.top,
                pillRect.right,
                pillRect.bottom,
                Color.parseColor("#FDE68A"),
                Color.parseColor("#F59E0B"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, amberPillPaint)
        canvas.restore()

        // 6. Electric cyan outer neon border + crisp ice-cyan inner bevel
        val outerNeonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5.4f
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    Color.parseColor("#A5F3FC"),
                    Color.parseColor("#22D3EE"),
                    Color.parseColor("#06B6D4"),
                    Color.parseColor("#67E8F9")
                ),
                floatArrayOf(0f, 0.35f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(squirclePath, outerNeonPaint)

        val innerRect = RectF(
            rect.left + 4f,
            rect.top + 4f,
            rect.right - 4f,
            rect.bottom - 4f
        )
        val innerRadius = max(4f, radius - 4f)
        val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            color = Color.argb(200, 224, 249, 255)
        }
        canvas.drawRoundRect(innerRect, innerRadius, innerRadius, innerRimPaint)

        bitmapCache.put(cacheKey, out)
        try {
            renderDiskCacheDir.mkdirs()
            FileOutputStream(diskFile).use { fos ->
                out.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (_: Exception) {
            // Disk cache write is best-effort
        }
        return BitmapDrawable(context.resources, out)
    }

    private fun drawSculptedTronNeonSymbol(
        canvas: Canvas,
        symbolBmp: Bitmap,
        canvasSize: Int
    ) {
        val drawSize = (canvasSize * 0.56f).toInt()
        val offset = (canvasSize - drawSize) / 2f
        val destRect = RectF(offset, offset + 2f, offset + drawSize, offset + drawSize + 2f)

        val alphaMask = symbolBmp.extractAlpha()

        // Drop shadow for floating 3D separation
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(220, 0, 0, 0)
            maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(
            alphaMask,
            null,
            RectF(destRect.left + 3f, destRect.top + 5f, destRect.right + 3f, destRect.bottom + 5f),
            shadowPaint
        )

        // Electric cyan neon halo behind symbol
        val cyanGlow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(185, 34, 211, 238)
            maskFilter = BlurMaskFilter(6.5f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(alphaMask, null, destRect, cyanGlow)

        // Subtle warm amber back-rim bounce on bottom-right edge
        val amberBounce = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(115, 245, 158, 11)
            maskFilter = BlurMaskFilter(3.5f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(
            alphaMask,
            null,
            RectF(destRect.left + 2f, destRect.top + 2f, destRect.right + 2f, destRect.bottom + 2f),
            amberBounce
        )

        // Sculpted ice-cyan to electric-cyan glowing symbol preserving internal luminance details
        val sculpted = Bitmap.createBitmap(drawSize, drawSize, Bitmap.Config.ARGB_8888)
        val sCanvas = Canvas(sculpted)
        val lumaMatrix = ColorMatrix(
            floatArrayOf(
                0.15f, 0.30f, 0.08f, 0f, 95f,
                0.28f, 0.56f, 0.14f, 0f, 115f,
                0.32f, 0.62f, 0.18f, 0f, 125f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(lumaMatrix)
        }
        sCanvas.drawBitmap(symbolBmp, null, RectF(0f, 0f, drawSize.toFloat(), drawSize.toFloat()), basePaint)

        val neonFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            shader = LinearGradient(
                0f,
                0f,
                drawSize.toFloat(),
                drawSize.toFloat(),
                intArrayOf(
                    Color.argb(190, 236, 254, 255),
                    Color.argb(165, 103, 232, 249),
                    Color.argb(155, 34, 211, 238),
                    Color.argb(165, 6, 182, 212)
                ),
                floatArrayOf(0f, 0.32f, 0.70f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        sCanvas.drawRect(0f, 0f, drawSize.toFloat(), drawSize.toFloat(), neonFillPaint)

        canvas.drawBitmap(sculpted, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        sculpted.recycle()
        alphaMask.recycle()
    }

    private fun drawTronFramedArtwork(
        canvas: Canvas,
        artworkBmp: Bitmap,
        canvasSize: Int
    ) {
        val inset = canvasSize * 0.18f
        val innerRect = RectF(inset, inset + 1f, canvasSize - inset, canvasSize - inset + 1f)
        val innerRadius = canvasSize * 0.16f
        val clipPath = Path().apply {
            addRoundRect(innerRect, innerRadius, innerRadius, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(clipPath)
        val tronSatMatrix = ColorMatrix().apply { setSaturation(0.72f) }
        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(tronSatMatrix)
        }
        canvas.drawBitmap(artworkBmp, null, innerRect, artPaint)

        val rimTint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                innerRect.left,
                innerRect.top,
                innerRect.right,
                innerRect.bottom,
                Color.argb(46, 34, 211, 238),
                Color.argb(32, 245, 158, 11),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(innerRect, rimTint)
        canvas.restore()

        val wellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(145, 34, 211, 238)
        }
        canvas.drawRoundRect(innerRect, innerRadius, innerRadius, wellBorderPaint)
    }

    /**
     * Converts any unmapped native or future third-party app icon into the Cyberpunk Holo aesthetic:
     * 3D translucent holographic glass cube housing, subtle internal micro-circuit lines,
     * electric cyan + neon magenta edge aura, and glowing cyan/magenta symbol.
     */
    private fun renderUnifiedCyberpunkHoloIcon(
        cacheIdentity: String,
        fallback: Drawable
    ): Drawable {
        val size = 192
        val cacheKey = "cyberpunk_holo_auto_v1:$cacheIdentity#$size"
        bitmapCache.get(cacheKey)?.let { cached ->
            return BitmapDrawable(context.resources, cached)
        }

        val diskFile = diskCacheFileForKey(cacheKey)
        if (diskFile.exists()) {
            try {
                FileInputStream(diskFile).use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { decoded ->
                        bitmapCache.put(cacheKey, decoded)
                        return BitmapDrawable(context.resources, decoded)
                    }
                }
            } catch (_: Exception) {
                // Re-render cleanly if disk entry was corrupted
            }
        }

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val margin = size * 0.085f
        val rect = RectF(margin - 2f, margin, size - margin - 2f, size - margin)
        val radius = size * 0.21f
        val cubePath = Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        }

        // 1. 3D glass cube back-depth edge on right/bottom
        val depthRect = RectF(rect.left + 5f, rect.top + 3.5f, rect.right + 5f, rect.bottom + 3.5f)
        val depthFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                depthRect.left,
                depthRect.top,
                depthRect.right,
                depthRect.bottom,
                Color.argb(120, 14, 116, 144),
                Color.argb(120, 131, 24, 67),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(depthRect, radius, radius, depthFillPaint)
        val depthStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            color = Color.argb(165, 34, 211, 238)
        }
        canvas.drawRoundRect(depthRect, radius, radius, depthStrokePaint)

        // 2. Dual cyan + neon pink outer holographic aura
        val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                Color.argb(145, 236, 72, 153),
                Color.argb(155, 34, 211, 238),
                Shader.TileMode.CLAMP
            )
            maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(cubePath, auraPaint)

        // 3. Translucent dark holographic glass cube interior
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    Color.rgb(13, 35, 58),
                    Color.rgb(7, 15, 34),
                    Color.rgb(26, 14, 44)
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(cubePath, bgPaint)

        // 4. Subtle internal micro-circuit traces + corner holo nodes
        canvas.save()
        canvas.clipPath(cubePath)
        val circuitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            color = Color.argb(72, 34, 211, 238)
        }
        val innerInset = size * 0.16f
        val innerBox = RectF(
            rect.left + innerInset,
            rect.top + innerInset,
            rect.right - innerInset,
            rect.bottom - innerInset
        )
        canvas.drawRoundRect(innerBox, radius * 0.65f, radius * 0.65f, circuitPaint)
        canvas.drawLine(rect.left + size * 0.12f, rect.centerY(), innerBox.left, rect.centerY(), circuitPaint)
        canvas.drawLine(innerBox.right, rect.centerY(), rect.right - size * 0.12f, rect.centerY(), circuitPaint)
        canvas.drawLine(rect.centerX(), rect.top + size * 0.12f, rect.centerX(), innerBox.top, circuitPaint)
        canvas.drawLine(rect.centerX(), innerBox.bottom, rect.centerX(), rect.bottom - size * 0.12f, circuitPaint)

        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(145, 244, 114, 182)
        }
        val nodeOffset = size * 0.13f
        val nodeR = 2.6f
        canvas.drawCircle(rect.left + nodeOffset, rect.top + nodeOffset, nodeR, nodePaint)
        canvas.drawCircle(rect.right - nodeOffset, rect.top + nodeOffset, nodeR, nodePaint)
        canvas.drawCircle(rect.left + nodeOffset, rect.bottom - nodeOffset, nodeR, nodePaint)
        canvas.drawCircle(rect.right - nodeOffset, rect.bottom - nodeOffset, nodeR, nodePaint)
        canvas.restore()

        // 5. Render foreground symbol or framed third-party artwork inside glass cube
        val prepared = prepareForegroundSymbol(fallback, 128)
        if (prepared.isIsolatedSilhouette) {
            drawSculptedCyberpunkNeonSymbol(canvas, prepared.bitmap, size)
        } else {
            drawCyberpunkFramedArtwork(canvas, prepared.bitmap, size)
        }
        if (!prepared.bitmap.isRecycled) {
            prepared.bitmap.recycle()
        }

        // 6. Specular holographic glass sheen across top-left face
        canvas.save()
        canvas.clipPath(cubePath)
        val sheenPath = Path().apply {
            addOval(
                RectF(
                    rect.left - size * 0.16f,
                    rect.top - size * 0.34f,
                    rect.right + size * 0.10f,
                    rect.top + rect.height() * 0.48f
                ),
                Path.Direction.CW
            )
        }
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.top + rect.height() * 0.45f,
                Color.argb(64, 165, 243, 252),
                Color.argb(0, 236, 72, 153),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(sheenPath, sheenPaint)
        canvas.restore()

        // 7. Outer cyan-magenta neon cube border + crisp inner glass edge
        val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4.6f
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    Color.parseColor("#F472B6"),
                    Color.parseColor("#22D3EE"),
                    Color.parseColor("#A855F7"),
                    Color.parseColor("#67E8F9")
                ),
                floatArrayOf(0f, 0.35f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(cubePath, outerBorderPaint)

        val innerGlassRect = RectF(rect.left + 4f, rect.top + 4f, rect.right - 4f, rect.bottom - 4f)
        val innerGlassRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            color = Color.argb(185, 224, 242, 254)
        }
        canvas.drawRoundRect(innerGlassRect, max(4f, radius - 4f), max(4f, radius - 4f), innerGlassRim)

        bitmapCache.put(cacheKey, out)
        try {
            renderDiskCacheDir.mkdirs()
            FileOutputStream(diskFile).use { fos ->
                out.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (_: Exception) {
            // Disk cache write is best-effort
        }
        return BitmapDrawable(context.resources, out)
    }

    private fun drawSculptedCyberpunkNeonSymbol(
        canvas: Canvas,
        symbolBmp: Bitmap,
        canvasSize: Int
    ) {
        val drawSize = (canvasSize * 0.54f).toInt()
        val offset = (canvasSize - drawSize) / 2f
        val destRect = RectF(offset - 1f, offset, offset + drawSize - 1f, offset + drawSize)

        val alphaMask = symbolBmp.extractAlpha()

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(215, 0, 0, 0)
            maskFilter = BlurMaskFilter(5.5f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(
            alphaMask,
            null,
            RectF(destRect.left + 3f, destRect.top + 4f, destRect.right + 3f, destRect.bottom + 4f),
            shadowPaint
        )

        val pinkGlow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(190, 236, 72, 153)
            maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(alphaMask, null, destRect, pinkGlow)

        val cyanEcho = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(135, 34, 211, 238)
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(
            alphaMask,
            null,
            RectF(destRect.left - 1.5f, destRect.top - 1.5f, destRect.right - 1.5f, destRect.bottom - 1.5f),
            cyanEcho
        )

        val sculpted = Bitmap.createBitmap(drawSize, drawSize, Bitmap.Config.ARGB_8888)
        val sCanvas = Canvas(sculpted)
        val lumaMatrix = ColorMatrix(
            floatArrayOf(
                0.28f, 0.50f, 0.16f, 0f, 115f,
                0.14f, 0.28f, 0.10f, 0f, 88f,
                0.26f, 0.48f, 0.18f, 0f, 125f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(lumaMatrix)
        }
        sCanvas.drawBitmap(symbolBmp, null, RectF(0f, 0f, drawSize.toFloat(), drawSize.toFloat()), basePaint)

        val neonTubePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            shader = LinearGradient(
                0f,
                0f,
                drawSize.toFloat(),
                drawSize.toFloat(),
                intArrayOf(
                    Color.argb(195, 253, 242, 248),
                    Color.argb(175, 244, 114, 182),
                    Color.argb(165, 168, 85, 247),
                    Color.argb(170, 34, 211, 238)
                ),
                floatArrayOf(0f, 0.35f, 0.70f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        sCanvas.drawRect(0f, 0f, drawSize.toFloat(), drawSize.toFloat(), neonTubePaint)

        canvas.drawBitmap(sculpted, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        sculpted.recycle()
        alphaMask.recycle()
    }

    private fun drawCyberpunkFramedArtwork(
        canvas: Canvas,
        artworkBmp: Bitmap,
        canvasSize: Int
    ) {
        val inset = canvasSize * 0.19f
        val innerRect = RectF(inset - 1f, inset, canvasSize - inset - 1f, canvasSize - inset)
        val innerRadius = canvasSize * 0.15f
        val clipPath = Path().apply {
            addRoundRect(innerRect, innerRadius, innerRadius, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(clipPath)
        val holoSatMatrix = ColorMatrix().apply { setSaturation(0.82f) }
        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(holoSatMatrix)
        }
        canvas.drawBitmap(artworkBmp, null, innerRect, artPaint)

        val rimTint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                innerRect.left,
                innerRect.top,
                innerRect.right,
                innerRect.bottom,
                Color.argb(42, 236, 72, 153),
                Color.argb(42, 34, 211, 238),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(innerRect, rimTint)
        canvas.restore()

        val wellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            shader = LinearGradient(
                innerRect.left,
                innerRect.top,
                innerRect.right,
                innerRect.bottom,
                Color.argb(160, 244, 114, 182),
                Color.argb(160, 34, 211, 238),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(innerRect, innerRadius, innerRadius, wellBorderPaint)
    }

    private fun buildChamferedOctagonPath(rect: RectF, chamfer: Float): Path {
        return Path().apply {
            moveTo(rect.left + chamfer, rect.top)
            lineTo(rect.right - chamfer, rect.top)
            lineTo(rect.right, rect.top + chamfer)
            lineTo(rect.right, rect.bottom - chamfer)
            lineTo(rect.right - chamfer, rect.bottom)
            lineTo(rect.left + chamfer, rect.bottom)
            lineTo(rect.left, rect.bottom - chamfer)
            lineTo(rect.left, rect.top + chamfer)
            close()
        }
    }

    /**
     * Converts any unmapped native or future third-party app icon into the refined OBSIDIAN CORE
     * aesthetic: deep pitch-black faceted volcanic obsidian stone, glowing incandescent amber core
     * depth, fine glowing volcanic energy fissures escaping from inside, and warm edge reflections.
     */
    private fun renderUnifiedObsidianCoreIcon(
        cacheIdentity: String,
        fallback: Drawable
    ): Drawable {
        val size = 192
        val cacheKey = "obsidian_core_auto_v2:$cacheIdentity#$size"
        bitmapCache.get(cacheKey)?.let { cached ->
            return BitmapDrawable(context.resources, cached)
        }

        val diskFile = diskCacheFileForKey(cacheKey)
        if (diskFile.exists()) {
            try {
                FileInputStream(diskFile).use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { decoded ->
                        bitmapCache.put(cacheKey, decoded)
                        return BitmapDrawable(context.resources, decoded)
                    }
                }
            } catch (_: Exception) {
                // Re-render cleanly if disk entry was corrupted
            }
        }

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val margin = size * 0.08f
        val outerRect = RectF(margin, margin - 1.5f, size - margin, size - margin - 1.5f)
        val chamfer = size * 0.19f
        val stonePath = buildChamferedOctagonPath(outerRect, chamfer)

        // 1. Heavy volcanic slab shadow + 3D stone depth base
        val backRect = RectF(outerRect.left + 2.5f, outerRect.top + 5f, outerRect.right + 2.5f, outerRect.bottom + 5f)
        val backPath = buildChamferedOctagonPath(backRect, chamfer)
        val backShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(195, 0, 0, 0)
            maskFilter = BlurMaskFilter(5.5f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(backPath, backShadowPaint)
        val backSlabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(7, 8, 11)
        }
        canvas.drawPath(backPath, backSlabPaint)

        // 2. Faceted dark graphite-obsidian chamfer bevel exterior catching warm ember edge bounce
        val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                outerRect.left,
                outerRect.top,
                outerRect.right,
                outerRect.bottom,
                intArrayOf(
                    Color.rgb(44, 39, 36),
                    Color.rgb(17, 19, 24),
                    Color.rgb(5, 6, 8)
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(stonePath, bevelPaint)

        // 3. Recessed deep pitch-black volcanic obsidian core face inside faceted bevels
        val faceInset = size * 0.06f
        val faceRect = RectF(
            outerRect.left + faceInset,
            outerRect.top + faceInset,
            outerRect.right - faceInset,
            outerRect.bottom - faceInset
        )
        val faceChamfer = max(8f, chamfer - faceInset * 0.72f)
        val facePath = buildChamferedOctagonPath(faceRect, faceChamfer)
        val obsidianFacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                faceRect.left,
                faceRect.top,
                faceRect.right,
                faceRect.bottom,
                intArrayOf(
                    Color.rgb(16, 18, 22),
                    Color.rgb(7, 8, 11),
                    Color.rgb(3, 4, 5)
                ),
                floatArrayOf(0f, 0.46f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(facePath, obsidianFacePaint)

        // Subsurface volcanic amber internal core aura pulsing behind the symbol
        canvas.save()
        canvas.clipPath(facePath)
        val internalCoreGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                faceRect.centerX(),
                faceRect.centerY() + size * 0.02f,
                faceRect.width() * 0.56f,
                intArrayOf(
                    Color.argb(95, 255, 158, 27),
                    Color.argb(46, 234, 88, 12),
                    Color.argb(0, 124, 45, 18)
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(faceRect, internalCoreGlow)

        // Fine jagged volcanic hairline energy fissures seeded deterministically per app
        val seed = (cacheIdentity.hashCode() and 0x7FFFFFFF)
        val fissurePath = Path().apply {
            val topX = faceRect.left + faceRect.width() * (0.24f + ((seed % 5) * 0.07f))
            moveTo(topX, faceRect.top)
            lineTo(topX + size * 0.045f, faceRect.top + size * 0.09f)
            lineTo(topX + size * 0.015f, faceRect.top + size * 0.16f)

            val rightY = faceRect.top + faceRect.height() * (0.30f + (((seed / 7) % 4) * 0.08f))
            moveTo(faceRect.right, rightY)
            lineTo(faceRect.right - size * 0.09f, rightY + size * 0.035f)
            lineTo(faceRect.right - size * 0.15f, rightY + size * 0.01f)

            val botX = faceRect.left + faceRect.width() * (0.28f + (((seed / 13) % 4) * 0.08f))
            moveTo(botX, faceRect.bottom)
            lineTo(botX - size * 0.035f, faceRect.bottom - size * 0.095f)
            lineTo(botX + size * 0.025f, faceRect.bottom - size * 0.155f)
        }
        val fissureGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 4.2f
            color = Color.argb(170, 234, 88, 12)
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(fissurePath, fissureGlowPaint)
        val fissureCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 1.55f
            color = Color.parseColor("#FFCC66")
        }
        canvas.drawPath(fissurePath, fissureCorePaint)

        // Subtle warm volcanic reflection sweep across polished obsidian facets
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                faceRect.left,
                faceRect.top,
                faceRect.centerX() + size * 0.12f,
                faceRect.centerY() + size * 0.12f,
                intArrayOf(
                    Color.argb(38, 255, 214, 153),
                    Color.argb(12, 245, 158, 11),
                    Color.argb(0, 0, 0, 0)
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(faceRect, sheenPaint)
        canvas.restore()

        // 4. Warm incandescent amber inner seam glow along beveled perimeter
        val seamAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.8f
            color = Color.argb(115, 249, 115, 22)
            maskFilter = BlurMaskFilter(3.5f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(facePath, seamAuraPaint)

        val amberBevelRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            shader = LinearGradient(
                faceRect.left,
                faceRect.top,
                faceRect.right,
                faceRect.bottom,
                intArrayOf(
                    Color.parseColor("#FFB020"),
                    Color.parseColor("#C2410C"),
                    Color.parseColor("#57240A"),
                    Color.parseColor("#FF9E1B")
                ),
                floatArrayOf(0f, 0.34f, 0.70f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(facePath, amberBevelRimPaint)

        // 5. Render foreground symbol glowing with incandescent volcanic amber energy
        val prepared = prepareForegroundSymbol(fallback, 128)
        if (prepared.isIsolatedSilhouette) {
            drawSculptedObsidianBronzeSymbol(canvas, prepared.bitmap, size)
        } else {
            drawObsidianFramedArtwork(canvas, prepared.bitmap, size)
        }
        if (!prepared.bitmap.isRecycled) {
            prepared.bitmap.recycle()
        }

        // 6. Active incandescent volcanic core node set into lower-right facet
        val coreCx = faceRect.right - size * 0.115f
        val coreCy = faceRect.bottom - size * 0.115f
        val coreBezelR = size * 0.048f
        val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 16, 12)
        }
        canvas.drawCircle(coreCx, coreCy, coreBezelR, bezelPaint)

        val coreGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                coreCx,
                coreCy,
                coreBezelR * 2.15f,
                intArrayOf(
                    Color.argb(215, 255, 176, 32),
                    Color.argb(115, 249, 115, 22),
                    Color.argb(0, 234, 88, 12)
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(coreCx, coreCy, coreBezelR * 2.15f, coreGlowPaint)

        val coreJewelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                coreCx - coreBezelR * 0.16f,
                coreCy - coreBezelR * 0.16f,
                coreBezelR * 0.72f,
                intArrayOf(
                    Color.parseColor("#FFF4C2"),
                    Color.parseColor("#FFB020"),
                    Color.parseColor("#EA580C")
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(coreCx, coreCy, coreBezelR * 0.68f, coreJewelPaint)

        // Warm amber-lit catchlight along chamfered outer stone edge
        val edgeHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            shader = LinearGradient(
                outerRect.left,
                outerRect.top,
                outerRect.right,
                outerRect.bottom,
                intArrayOf(
                    Color.argb(155, 255, 198, 112),
                    Color.argb(45, 234, 88, 12),
                    Color.argb(90, 255, 176, 32)
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(stonePath, edgeHighlightPaint)

        bitmapCache.put(cacheKey, out)
        try {
            renderDiskCacheDir.mkdirs()
            FileOutputStream(diskFile).use { fos ->
                out.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (_: Exception) {
            // Disk cache write is best-effort
        }
        return BitmapDrawable(context.resources, out)
    }

    private fun drawSculptedObsidianBronzeSymbol(
        canvas: Canvas,
        symbolBmp: Bitmap,
        canvasSize: Int
    ) {
        val drawSize = (canvasSize * 0.54f).toInt()
        val offset = (canvasSize - drawSize) / 2f
        val destRect = RectF(offset, offset - 1f, offset + drawSize, offset + drawSize - 1f)

        val alphaMask = symbolBmp.extractAlpha()

        // Deep carved obsidian cavity shadow behind incandescent symbol
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(240, 0, 0, 0)
            maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(
            alphaMask,
            null,
            RectF(destRect.left + 2.5f, destRect.top + 4.5f, destRect.right + 2.5f, destRect.bottom + 4.5f),
            shadowPaint
        )

        // Intense volcanic amber core bloom escaping from beneath symbol edges
        val amberAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(195, 255, 128, 16)
            maskFilter = BlurMaskFilter(6.5f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(alphaMask, null, destRect, amberAuraPaint)

        // Sculpted incandescent amber energy core preserving internal glyph shading
        val sculpted = Bitmap.createBitmap(drawSize, drawSize, Bitmap.Config.ARGB_8888)
        val sCanvas = Canvas(sculpted)
        val lumaMatrix = ColorMatrix(
            floatArrayOf(
                0.30f, 0.56f, 0.14f, 0f, 130f,
                0.20f, 0.36f, 0.10f, 0f, 70f,
                0.08f, 0.14f, 0.04f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(lumaMatrix)
        }
        sCanvas.drawBitmap(symbolBmp, null, RectF(0f, 0f, drawSize.toFloat(), drawSize.toFloat()), basePaint)

        val incandescentFinishPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            shader = LinearGradient(
                0f,
                0f,
                drawSize.toFloat(),
                drawSize.toFloat(),
                intArrayOf(
                    Color.argb(210, 255, 232, 163),
                    Color.argb(195, 255, 176, 32),
                    Color.argb(190, 234, 88, 12),
                    Color.argb(205, 255, 198, 75)
                ),
                floatArrayOf(0f, 0.35f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        sCanvas.drawRect(0f, 0f, drawSize.toFloat(), drawSize.toFloat(), incandescentFinishPaint)

        // Crisp hot-amber upper rim catchlight
        val bevelHighlight = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(145, 255, 224, 150)
        }
        canvas.drawBitmap(
            alphaMask,
            null,
            RectF(destRect.left - 0.8f, destRect.top - 0.8f, destRect.right - 0.8f, destRect.bottom - 0.8f),
            bevelHighlight
        )
        canvas.drawBitmap(sculpted, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        sculpted.recycle()
        alphaMask.recycle()
    }

    private fun drawObsidianFramedArtwork(
        canvas: Canvas,
        artworkBmp: Bitmap,
        canvasSize: Int
    ) {
        val inset = canvasSize * 0.21f
        val innerRect = RectF(inset, inset - 1f, canvasSize - inset, canvasSize - inset - 1f)
        val innerChamfer = canvasSize * 0.10f
        val clipPath = buildChamferedOctagonPath(innerRect, innerChamfer)

        val wellAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = Color.argb(145, 249, 115, 22)
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(clipPath, wellAuraPaint)

        canvas.save()
        canvas.clipPath(clipPath)
        val warmContrastMatrix = ColorMatrix().apply { setSaturation(0.84f) }
        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(warmContrastMatrix)
        }
        canvas.drawBitmap(artworkBmp, null, innerRect, artPaint)
        canvas.restore()

        val wellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            shader = LinearGradient(
                innerRect.left,
                innerRect.top,
                innerRect.right,
                innerRect.bottom,
                Color.parseColor("#FFB020"),
                Color.parseColor("#EA580C"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(clipPath, wellBorderPaint)
    }

    private fun diskCacheFileForKey(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
        return File(renderDiskCacheDir, "$hex.png")
    }

    private data class PreparedSymbol(
        val bitmap: Bitmap,
        val isIsolatedSilhouette: Boolean,
        val brandColor: Int? = null
    )

    private fun prepareForegroundSymbol(drawable: Drawable, targetSize: Int): PreparedSymbol {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            val fg = drawable.foreground
            if (fg != null) {
                // Draw AdaptiveIcon foreground zoomed slightly around the 72dp safe zone
                val bmp = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                val pad = (targetSize * 0.18f).toInt()
                fg.setBounds(-pad, -pad, targetSize + pad, targetSize + pad)
                fg.draw(c)
                if (countTransparentPixels(bmp) > (bmp.width * bmp.height * 0.985f).toInt()) {
                    bmp.recycle()
                    drawableToBitmap(drawable, targetSize)
                } else {
                    bmp
                }
            } else {
                drawableToBitmap(drawable, targetSize)
            }
        } else {
            drawableToBitmap(drawable, targetSize)
        }

        val transparentCount = countTransparentPixels(raw)
        val total = raw.width * raw.height
        val transparentRatio = transparentCount.toFloat() / total.toFloat()
        if (transparentRatio >= 0.14f) {
            val cropped = trimTransparentToSquare(raw, targetSize)
            if (cropped !== raw) raw.recycle()
            return PreparedSymbol(cropped, true)
        }

        // Legacy square icon with solid background -> strip uniform border color via floodfill
        val stripped = stripUniformCornerBackground(raw)
        val postRatio = countTransparentPixels(stripped).toFloat() / (stripped.width * stripped.height).toFloat()
        if (postRatio >= 0.16f && postRatio <= 0.94f) {
            if (stripped !== raw) raw.recycle()
            val cropped = trimTransparentToSquare(stripped, targetSize)
            if (cropped !== stripped) stripped.recycle()
            return PreparedSymbol(cropped, true)
        }

        if (stripped !== raw) stripped.recycle()
        return PreparedSymbol(raw, false)
    }

    private fun countTransparentPixels(bmp: Bitmap): Int {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        var count = 0
        for (p in pixels) {
            if ((p ushr 24) < 40) count++
        }
        return count
    }

    private fun stripUniformCornerBackground(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val corners = listOf(
            pixels[2 * w + 2],
            pixels[2 * w + (w - 3)],
            pixels[(h - 3) * w + 2],
            pixels[(h - 3) * w + (w - 3)]
        )
        if (corners.any { Color.alpha(it) < 180 }) {
            return src
        }

        val ref = corners[0]
        val uniform = corners.all { colorDistance(ref, it) < 42 }
        if (!uniform) return src

        val out = pixels.clone()
        val visited = BooleanArray(w * h)
        val queue = ArrayDeque<Int>()

        fun tryEnqueue(idx: Int) {
            if (!visited[idx] && colorDistance(ref, pixels[idx]) < 48) {
                visited[idx] = true
                queue.addLast(idx)
            }
        }

        for (x in 0 until w) {
            tryEnqueue(x)
            tryEnqueue((h - 1) * w + x)
        }
        for (y in 0 until h) {
            tryEnqueue(y * w)
            tryEnqueue(y * w + (w - 1))
        }

        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            out[idx] = Color.TRANSPARENT
            val x = idx % w
            val y = idx / w
            if (x > 0) tryEnqueue(idx - 1)
            if (x < w - 1) tryEnqueue(idx + 1)
            if (y > 0) tryEnqueue(idx - w)
            if (y < h - 1) tryEnqueue(idx + w)
        }

        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun colorDistance(c1: Int, c2: Int): Int {
        val dr = abs(Color.red(c1) - Color.red(c2))
        val dg = abs(Color.green(c1) - Color.green(c2))
        val db = abs(Color.blue(c1) - Color.blue(c2))
        return dr + dg + db
    }

    private fun trimTransparentToSquare(src: Bitmap, outSize: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((pixels[row + x] ushr 24) > 24) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return src

        val cropW = maxX - minX + 1
        val cropH = maxY - minY + 1
        val span = max(cropW, cropH)
        val dest = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val c = Canvas(dest)
        val scale = (outSize * 0.86f) / span.toFloat()
        val drawW = cropW * scale
        val drawH = cropH * scale
        val left = (outSize - drawW) / 2f
        val top = (outSize - drawH) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(
            src,
            Rect(minX, minY, maxX + 1, maxY + 1),
            RectF(left, top, left + drawW, top + drawH),
            paint
        )
        return dest
    }

    private fun drawSculptedLiquidSilverSymbol(
        canvas: Canvas,
        symbolBmp: Bitmap,
        canvasSize: Int
    ) {
        val drawSize = (canvasSize * 0.62f).toInt()
        val offset = (canvasSize - drawSize) / 2f
        val destRect = RectF(offset, offset, offset + drawSize, offset + drawSize)

        val alphaMask = symbolBmp.extractAlpha()

        // Drop shadow for 3D depth
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(210, 0, 0, 0)
            maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(
            alphaMask,
            null,
            RectF(destRect.left + 4f, destRect.top + 6f, destRect.right + 4f, destRect.bottom + 6f),
            shadowPaint
        )

        // Electric cyan & violet rim bounce behind the sculpted metal
        val cyanRim = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(155, 34, 211, 238)
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(
            alphaMask,
            null,
            RectF(destRect.left - 2f, destRect.top - 1.5f, destRect.right - 2f, destRect.bottom - 1.5f),
            cyanRim
        )

        val violetRim = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(150, 139, 92, 246)
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(
            alphaMask,
            null,
            RectF(destRect.left + 2.5f, destRect.top + 2.5f, destRect.right + 2.5f, destRect.bottom + 2.5f),
            violetRim
        )

        // Sculpted metallic liquid-silver fill preserving internal shading details
        val sculpted = Bitmap.createBitmap(drawSize, drawSize, Bitmap.Config.ARGB_8888)
        val sCanvas = Canvas(sculpted)

        // Convert original foreground to high-contrast silver-gray preserving detail
        // Even pure black or colorful third-party logos map into rich metallic titanium tones
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                0.21f, 0.41f, 0.08f, 0f, 74f,
                0.21f, 0.41f, 0.08f, 0f, 80f,
                0.21f, 0.41f, 0.08f, 0f, 92f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(contrastMatrix)
        }
        sCanvas.drawBitmap(symbolBmp, null, RectF(0f, 0f, drawSize.toFloat(), drawSize.toFloat()), basePaint)

        // Apply diagonal brushed-titanium chrome gradient onto opaque pixels
        val chromeGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            shader = LinearGradient(
                0f,
                0f,
                drawSize.toFloat(),
                drawSize.toFloat(),
                intArrayOf(
                    Color.argb(175, 255, 255, 255),
                    Color.argb(120, 203, 213, 225),
                    Color.argb(145, 71, 85, 105),
                    Color.argb(165, 226, 232, 240),
                    Color.argb(140, 103, 232, 249)
                ),
                floatArrayOf(0f, 0.25f, 0.54f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        sCanvas.drawRect(0f, 0f, drawSize.toFloat(), drawSize.toFloat(), chromeGradientPaint)

        // Top-left crisp specular highlight
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(165, 255, 255, 255)
        }
        canvas.drawBitmap(
            alphaMask,
            null,
            RectF(destRect.left - 1.2f, destRect.top - 1.2f, destRect.right - 1.2f, destRect.bottom - 1.2f),
            highlightPaint
        )
        canvas.drawBitmap(sculpted, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        sculpted.recycle()
        alphaMask.recycle()
    }

    private fun drawMetallicFramedArtwork(
        canvas: Canvas,
        artworkBmp: Bitmap,
        canvasSize: Int
    ) {
        val inset = canvasSize * 0.16f
        val innerRect = RectF(inset, inset, canvasSize - inset, canvasSize - inset)
        val innerRadius = canvasSize * 0.16f
        val clipPath = Path().apply {
            addRoundRect(innerRect, innerRadius, innerRadius, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(clipPath)
        val coolMetalMatrix = ColorMatrix().apply { setSaturation(0.28f) }
        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(coolMetalMatrix)
        }
        canvas.drawBitmap(artworkBmp, null, innerRect, artPaint)

        val tintOverlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                innerRect.left,
                innerRect.top,
                innerRect.right,
                innerRect.bottom,
                Color.argb(55, 34, 211, 238),
                Color.argb(65, 139, 92, 246),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(innerRect, tintOverlay)
        canvas.restore()
    }

    /**
     * Genuine 3D height-field & Blinn-Phong molten gold relief shader:
     * inflates any app symbol into a volumetric carved 3D liquid-gold sculpture with smooth
     * curved normals, deep crevices, brushed metallic reflections, and cast obsidian shadows.
     */
    private fun drawSculptedLiquidGoldSymbol(
        canvas: Canvas,
        symbolBmp: Bitmap,
        canvasSize: Int,
        hashShift: Float
    ) {
        val drawSize = (canvasSize * 0.60f).toInt()
        val offset = (canvasSize - drawSize) / 2f
        val destRect = RectF(offset, offset, offset + drawSize, offset + drawSize)

        val alphaMask = symbolBmp.extractAlpha()

        // 1. Deep cast shadow onto smoked obsidian glass cavity
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(235, 0, 0, 0)
            maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(
            alphaMask,
            null,
            RectF(destRect.left + 4.2f, destRect.top + 6.5f, destRect.right + 4.2f, destRect.bottom + 6.5f),
            shadowPaint
        )

        // Subtle warm molten-gold ambient bounce halo underneath sculpture
        val goldBouncePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(72, 212, 175, 55)
            maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(alphaMask, null, destRect, goldBouncePaint)

        // 2. Resample foreground symbol cleanly to drawSize x drawSize
        val srcScaled = Bitmap.createScaledBitmap(symbolBmp, drawSize, drawSize, true)
        val nPixels = drawSize * drawSize
        val srcPx = IntArray(nPixels)
        srcScaled.getPixels(srcPx, 0, drawSize, 0, 0, drawSize, drawSize)
        if (srcScaled !== symbolBmp && !srcScaled.isRecycled) srcScaled.recycle()

        // Distance transform inside opaque alpha (forward + backward Chamfer passes)
        val dist = FloatArray(nPixels)
        val luma = FloatArray(nPixels)
        val maxDist = 18f
        for (i in 0 until nPixels) {
            val px = srcPx[i]
            val a = (px ushr 24) and 0xFF
            if (a > 24) {
                dist[i] = maxDist
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                luma[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            } else {
                dist[i] = 0f
            }
        }
        for (y in 1 until drawSize - 1) {
            val row = y * drawSize
            for (x in 1 until drawSize - 1) {
                val idx = row + x
                if (dist[idx] > 0f) {
                    val dUp = dist[idx - drawSize] + 1f
                    val dLeft = dist[idx - 1] + 1f
                    val dDiag = dist[idx - drawSize - 1] + 1.414f
                    dist[idx] = min(dist[idx], min(dUp, min(dLeft, dDiag)))
                }
            }
        }
        for (y in drawSize - 2 downTo 1) {
            val row = y * drawSize
            for (x in drawSize - 2 downTo 1) {
                val idx = row + x
                if (dist[idx] > 0f) {
                    val dDown = dist[idx + drawSize] + 1f
                    val dRight = dist[idx + 1] + 1f
                    val dDiag = dist[idx + drawSize + 1] + 1.414f
                    dist[idx] = min(dist[idx], min(dDown, min(dRight, dDiag)))
                }
            }
        }

        // Build smooth volumetric 3D height field H = pillow(dist) + internal luminance relief
        val rawH = FloatArray(nPixels)
        for (i in 0 until nPixels) {
            if (dist[i] > 0f) {
                val normD = (dist[i] / 9.5f).coerceIn(0f, 1f)
                val pillow = sqrt(normD * (2f - normD))
                rawH[i] = pillow * 0.78f + luma[i] * 0.28f
            }
        }
        val smoothH = FloatArray(nPixels)
        for (y in 1 until drawSize - 1) {
            val row = y * drawSize
            for (x in 1 until drawSize - 1) {
                val idx = row + x
                smoothH[idx] = (
                    rawH[idx - drawSize - 1] + 2f * rawH[idx - drawSize] + rawH[idx - drawSize + 1] +
                        2f * rawH[idx - 1] + 4f * rawH[idx] + 2f * rawH[idx + 1] +
                        rawH[idx + drawSize - 1] + 2f * rawH[idx + drawSize] + rawH[idx + drawSize + 1]
                    ) / 16f
            }
        }

        // Shade every pixel with 3D surface normals, brushed anisotropic gold reflection & Blinn-Phong highlights
        val outPx = IntArray(nPixels)
        val angleShift = hashShift * 0.55f
        for (y in 0 until drawSize) {
            val row = y * drawSize
            val yUp = max(0, y - 1) * drawSize
            val yDn = min(drawSize - 1, y + 1) * drawSize
            for (x in 0 until drawSize) {
                val idx = row + x
                val a = (srcPx[idx] ushr 24) and 0xFF
                if (a < 8) continue

                val xL = max(0, x - 1)
                val xR = min(drawSize - 1, x + 1)
                val dhdx = (smoothH[row + xR] - smoothH[row + xL]) * 4.4f
                val dhdy = (smoothH[yDn + x] - smoothH[yUp + x]) * 4.4f

                val invLen = 1f / sqrt(dhdx * dhdx + dhdy * dhdy + 1f)
                val nx = -dhdx * invLen
                val ny = -dhdy * invLen
                val nz = invLen

                // Studio key light (top-left) + warm secondary bounce (bottom-right)
                val diffKey = max(0f, nx * -0.58f + ny * -0.64f + nz * 0.50f)
                val diffRim = max(0f, nx * 0.54f + ny * 0.56f + nz * 0.44f)

                // Crisp specular highlight peaks
                val specKey = (nx * -0.34f + ny * -0.38f + nz * 0.86f).coerceAtLeast(0f).pow(24f)
                val specBounce = (nx * 0.34f + ny * 0.36f + nz * 0.86f).coerceAtLeast(0f).pow(20f)

                // Reflected studio reflection band pattern (gives liquid gold liquid-metal flow)
                val rx = nx * 1.4f + (x.toFloat() / drawSize - 0.5f) * 0.45f + angleShift
                val ry = ny * 1.4f + (y.toFloat() / drawSize - 0.5f) * 0.45f
                val studioBand = 0.5f + 0.5f * cos((rx * 1.15f + ry * 1.25f) * 4.2f)

                // Grazing Fresnel catchlight on curved edges
                val fresnel = (1f - nz).pow(2.1f) * 0.45f

                val energy = (
                    0.18f +
                        0.46f * diffKey +
                        0.22f * diffRim +
                        0.18f * studioBand +
                        fresnel +
                        0.72f * specKey +
                        0.38f * specBounce +
                        luma[idx] * 0.14f
                    ).coerceIn(0.08f, 1.0f)

                val rgb = sampleGoldRampRgb(energy)
                outPx[idx] = Color.argb(a, rgb[0], rgb[1], rgb[2])
            }
        }

        val sculpted = Bitmap.createBitmap(drawSize, drawSize, Bitmap.Config.ARGB_8888)
        sculpted.setPixels(outPx, 0, drawSize, 0, 0, drawSize, drawSize)
        canvas.drawBitmap(sculpted, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        sculpted.recycle()
        alphaMask.recycle()
    }

    private fun drawGoldMetallicFramedArtwork(
        canvas: Canvas,
        artworkBmp: Bitmap,
        canvasSize: Int,
        hashShift: Float
    ) {
        val inset = canvasSize * 0.17f
        val innerRect = RectF(inset, inset, canvasSize - inset, canvasSize - inset)
        val innerRadius = canvasSize * 0.16f
        val clipPath = Path().apply {
            addRoundRect(innerRect, innerRadius, innerRadius, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(clipPath)
        val goldContrastMatrix = ColorMatrix().apply {
            setSaturation(0.18f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.15f, 0f, 0f, 0f, 10f,
                        0f, 0.96f, 0f, 0f, 4f,
                        0f, 0f, 0.52f, 0f, -8f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(goldContrastMatrix)
        }
        canvas.drawBitmap(artworkBmp, null, innerRect, artPaint)

        val moltenGoldTint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                innerRect.left + hashShift * 40f,
                innerRect.top,
                innerRect.right - hashShift * 40f,
                innerRect.bottom,
                intArrayOf(
                    Color.argb(62, 255, 243, 176),
                    Color.argb(85, 212, 175, 55),
                    Color.argb(95, 120, 85, 19)
                ),
                floatArrayOf(0f, 0.46f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(innerRect, moltenGoldTint)
        canvas.restore()

        val innerBevelStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.2f
            shader = LinearGradient(
                innerRect.left,
                innerRect.top,
                innerRect.right,
                innerRect.bottom,
                intArrayOf(
                    Color.argb(235, 255, 243, 176),
                    Color.argb(165, 212, 175, 55),
                    Color.argb(190, 120, 85, 19),
                    Color.argb(210, 255, 243, 176)
                ),
                floatArrayOf(0f, 0.34f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(clipPath, innerBevelStroke)
    }

    private fun buildIconSourcePath(pack: ParsedPack, fileName: String): String {
        val cleanName = fileName.substringAfterLast('/')
        return if (pack.isAsset) {
            "${pack.folderPath}/icons/$cleanName"
        } else {
            File(File(pack.folderPath, "icons"), cleanName).absolutePath
        }
    }

    private fun loadAllManagedPacks(): Map<String, ParsedPack> {
        cachedManagedPacks?.let { return it }
        val discovered = linkedMapOf<String, ParsedPack>()

        val assetFolders = try {
            context.assets.list("icon_packs")?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        assetFolders.forEach { folder ->
            val manifestPath = "icon_packs/$folder/manifest.json"
            val raw = try {
                context.assets.open(manifestPath).bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                null
            }
            if (raw != null) {
                parseManifestJson(
                    raw = raw,
                    isAsset = true,
                    folderKey = folder,
                    folderPath = "icon_packs/$folder"
                )?.let { discovered[it.info.id] = it }
            }
        }

        val dirs = importedRootDir.listFiles()?.filter { it.isDirectory }.orEmpty()
        dirs.forEach { dir ->
            val manifestFile = File(dir, "manifest.json")
            if (manifestFile.exists()) {
                val raw = try {
                    manifestFile.readText()
                } catch (_: Exception) {
                    null
                }
                if (raw != null) {
                    parseManifestJson(
                        raw = raw,
                        isAsset = false,
                        folderKey = dir.name,
                        folderPath = dir.absolutePath
                    )?.let { discovered[it.info.id] = it }
                }
            }
        }

        cachedManagedPacks = discovered
        return discovered
    }

    private fun parseManifestJson(
        raw: String,
        isAsset: Boolean,
        folderKey: String,
        folderPath: String
    ): ParsedPack? {
        return try {
            val json = JSONObject(raw)
            val rawId = json.optString("id").ifBlank { folderKey }
                .removePrefix(IconPackInfo.ASSET_PACK_PREFIX)
                .removePrefix(IconPackInfo.IMPORTED_PACK_PREFIX)
            val prefix = if (isAsset) IconPackInfo.ASSET_PACK_PREFIX else IconPackInfo.IMPORTED_PACK_PREFIX
            val packId = "$prefix$rawId"

            val title = json.optString("title").ifBlank {
                json.optString("name").ifBlank { rawId }
            }
            val subtitle = json.optString("subtitle")
            val author = json.optString("author").ifBlank {
                if (isAsset) "HILM Studio" else "Importado (.zip)"
            }
            val version = json.optString("version").ifBlank { "1.0.0" }
            val fallbackStyle = json.optString("fallbackStyle").ifBlank { "titanium_liquid" }

            val entries = mutableListOf<PackIconEntry>()
            val pkgMap = mutableMapOf<String, String>()
            val iconsArray = json.optJSONArray("icons")
            if (iconsArray != null) {
                for (i in 0 until iconsArray.length()) {
                    val item = iconsArray.optJSONObject(i) ?: continue
                    val key = item.optString("key")
                    val label = item.optString("label").ifBlank { key }
                    val fileName = item.optString("file").substringAfterLast('/')
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
                        PackIconEntry(
                            key = key.ifBlank { fileName.substringBeforeLast('.') },
                            label = label,
                            fileName = fileName,
                            packages = packages,
                            keywords = keywords
                        )
                    )
                }
            }

            val previewItems = mutableListOf<IconPackPreviewItem>()
            val previewArray = json.optJSONArray("previewFiles")
            if (previewArray != null) {
                for (i in 0 until previewArray.length()) {
                    val fileName = previewArray.optString(i).substringAfterLast('/')
                    if (fileName.isNotBlank()) {
                        val matched = entries.firstOrNull { it.fileName == fileName }
                        previewItems.add(
                            IconPackPreviewItem(
                                key = matched?.key ?: fileName.substringBeforeLast('.'),
                                label = matched?.label ?: fileName.substringBeforeLast('.'),
                                sourcePath = if (isAsset) {
                                    "$folderPath/icons/$fileName"
                                } else {
                                    File(File(folderPath, "icons"), fileName).absolutePath
                                },
                                isAsset = isAsset
                            )
                        )
                    }
                }
            }
            if (previewItems.isEmpty()) {
                entries.take(6).forEach { entry ->
                    previewItems.add(
                        IconPackPreviewItem(
                            key = entry.key,
                            label = entry.label,
                            sourcePath = if (isAsset) {
                                "$folderPath/icons/${entry.fileName}"
                            } else {
                                File(File(folderPath, "icons"), entry.fileName).absolutePath
                            },
                            isAsset = isAsset
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
                isBuiltIn = isAsset,
                kind = if (isAsset) IconPackKind.ILLUSTRATED_ASSET else IconPackKind.IMPORTED_PACK,
                assetFolder = if (isAsset) folderKey else null,
                storageDirName = if (!isAsset) folderKey else null,
                fallbackStyle = fallbackStyle,
                previewFiles = previewItems,
                iconCount = entries.size
            )
            ParsedPack(info, isAsset, folderPath, entries, pkgMap)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildAutoManifestJson(
        packId: String,
        title: String,
        fileNames: List<String>
    ): JSONObject {
        val iconsArray = JSONArray()
        val previewFiles = JSONArray()

        fileNames.forEachIndexed { idx, fileName ->
            val key = fileName.substringBeforeLast('.').lowercase()
            val mappedPackages = DEFAULT_KEY_PACKAGES[key].orEmpty()
            val mappedKeywords = DEFAULT_KEY_KEYWORDS[key] ?: listOf(key)
            val label = DEFAULT_KEY_LABELS[key] ?: key.replaceFirstChar { it.uppercase() }

            val obj = JSONObject().apply {
                put("key", key)
                put("label", label)
                put("file", fileName)
                put("packages", JSONArray(mappedPackages))
                put("keywords", JSONArray(mappedKeywords))
            }
            iconsArray.put(obj)
            if (idx < 6) previewFiles.put(fileName)
        }

        return JSONObject().apply {
            put("id", packId)
            put("title", title)
            put("subtitle", "Pacote importado via arquivo .zip (${fileNames.size} ícones).")
            put("author", "Importado localmente")
            put("version", "1.0.0")
            put("fallbackStyle", "titanium_liquid")
            put("previewFiles", previewFiles)
            put("icons", iconsArray)
        }
    }

    private fun resolveZipDisplayName(uri: Uri): String {
        var name = "Pacote Importado"
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val raw = cursor.getString(idx)
                        if (!raw.isNullOrBlank()) {
                            name = raw.substringBeforeLast('.')
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // fallback to default name
        }
        return name
    }

    private fun sanitizeFolderId(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "_")
            .trim('_')
            .take(40)
    }

    private enum class LunaLightAccentMode {
        AMBER_SPARK,
        COPPER_DETAIL,
        DUAL_MICRO_SPARK,
        COOL_PURE
    }

    private data class LunaLightProfile(
        val mode: LunaLightAccentMode,
        val angleDeg: Float,
        val warmWeight: Float
    )

    private fun resolveLunaLightProfile(packageName: String): LunaLightProfile {
        val pkg = packageName.lowercase()
        val hash = abs(pkg.hashCode())
        val angles = floatArrayOf(-42f, 38f, 138f, -142f)
        val angleDeg = angles[hash % angles.size]

        val warmKeywords = listOf(
            "camera", "cam", "spotify", "music", "audio", "notes", "keep",
            "settings", "calendar", "instagram", "youtube", "mercadopago",
            "mercadolivre", "mercadolibre", "gallery", "photos", "clock",
            "alarm", "rar", "videoconverter"
        )
        val mode = if (warmKeywords.any { pkg.contains(it) }) {
            when (hash % 3) {
                0 -> LunaLightAccentMode.COPPER_DETAIL
                1 -> LunaLightAccentMode.AMBER_SPARK
                else -> LunaLightAccentMode.DUAL_MICRO_SPARK
            }
        } else {
            when (hash % 5) {
                0 -> LunaLightAccentMode.DUAL_MICRO_SPARK
                1, 2 -> LunaLightAccentMode.COPPER_DETAIL
                3 -> LunaLightAccentMode.AMBER_SPARK
                else -> LunaLightAccentMode.COOL_PURE
            }
        }

        val warmWeight = when (mode) {
            LunaLightAccentMode.DUAL_MICRO_SPARK -> 0.92f
            LunaLightAccentMode.COPPER_DETAIL -> 0.82f
            LunaLightAccentMode.AMBER_SPARK -> 0.78f
            LunaLightAccentMode.COOL_PURE -> 0.62f
        }
        return LunaLightProfile(mode = mode, angleDeg = angleDeg, warmWeight = warmWeight)
    }

    /**
     * Tema Luna — Entre Luz e Sombra | HILM:
     * Pure floating holographic light projections drawn with razor-thin luminous cyan (#00E5FF)
     * & electric blue (#398BFF) filaments transitioning diagonally to warm amber (#FFB45C) &
     * copper (#C47A48) over 100% transparency, wrapped by delicate elliptical orbital light wisps
     * and pinpoint starlight sparks.
     * ZERO spheres, ZERO glass lenses, ZERO metallic rings, ZERO thick borders or solid plates.
     */
    private fun renderUnifiedLunaHolographicIcon(
        cacheIdentity: String,
        packageName: String,
        fallback: Drawable
    ): Drawable {
        val size = 208
        val cacheKey = "luna_holo_v6:$cacheIdentity#$size"
        bitmapCache.get(cacheKey)?.let { cached ->
            return BitmapDrawable(context.resources, cached)
        }

        val diskFile = diskCacheFileForKey(cacheKey)
        if (diskFile.exists()) {
            try {
                FileInputStream(diskFile).use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { decoded ->
                        bitmapCache.put(cacheKey, decoded)
                        return BitmapDrawable(context.resources, decoded)
                    }
                }
            } catch (_: Exception) {
                // Re-render cleanly if disk entry is unreadable
            }
        }

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cx = size / 2f
        val cy = size / 2f

        val profile = resolveLunaLightProfile(packageName)

        // Gossamer elliptical orbital light trails behind & around the suspended symbol
        drawLunaOrbitalWisps(canvas, cx, cy, size, profile)

        // Curated minimalist light-drawn symbols for key apps
        if (drawLunaCuratedVectorSymbol(canvas, packageName.lowercase(), cx, cy, size * 0.70f, profile)) {
            drawLunaLightSparks(canvas, cx, cy, size, profile)
            bitmapCache.put(cacheKey, out)
            try {
                renderDiskCacheDir.mkdirs()
                FileOutputStream(diskFile).use { fos ->
                    out.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
            } catch (_: Exception) {
                // Disk cache write is best-effort
            }
            return BitmapDrawable(context.resources, out)
        }

        // Universal Thin-Line Holographic Light Extractor (Sobel + 1px NMS thinning):
        // Strips background plates, 3D spheres, thick borders, and solid surfaces completely,
        // projecting clean floating light filaments directly over transparency.
        val prepared = prepareForegroundSymbol(fallback, 152)
        val drawSize = (size * 0.74f).toInt()
        val offset = (size - drawSize) / 2f
        val destRect = RectF(offset, offset, offset + drawSize, offset + drawSize)

        val scaledSrc = Bitmap.createScaledBitmap(prepared.bitmap, drawSize, drawSize, true)
        val nPixels = drawSize * drawSize
        val srcPx = IntArray(nPixels)
        scaledSrc.getPixels(srcPx, 0, drawSize, 0, 0, drawSize, drawSize)
        if (scaledSrc !== prepared.bitmap && !scaledSrc.isRecycled) scaledSrc.recycle()

        if (!prepared.isIsolatedSilhouette) {
            val half = drawSize * 0.5f
            val cornerR = drawSize * 0.24f
            val innerBox = half - cornerR
            for (y in 0 until drawSize) {
                val dy = abs(y + 0.5f - half) - innerBox
                val row = y * drawSize
                for (x in 0 until drawSize) {
                    val dx = abs(x + 0.5f - half) - innerBox
                    val ox = max(dx, 0f)
                    val oy = max(dy, 0f)
                    val sdf = sqrt(ox * ox + oy * oy) + min(max(dx, dy), 0f) - cornerR
                    if (sdf > 0.8f) {
                        srcPx[row + x] = Color.TRANSPARENT
                    } else if (sdf > -0.8f) {
                        val px = srcPx[row + x]
                        val aa = ((0.8f - sdf) / 1.6f).coerceIn(0f, 1f)
                        val newA = (((px ushr 24) and 0xFF) * aa).toInt().coerceIn(0, 255)
                        srcPx[row + x] = (px and 0x00FFFFFF) or (newA shl 24)
                    }
                }
            }
        }

        val srcA = FloatArray(nPixels)
        val srcR = FloatArray(nPixels)
        val srcG = FloatArray(nPixels)
        val srcB = FloatArray(nPixels)
        var opaqueCount = 0
        var sumLuma = 0f

        for (i in 0 until nPixels) {
            val px = srcPx[i]
            val a = ((px ushr 24) and 0xFF) / 255f
            srcA[i] = a
            if (a > 0.10f) {
                opaqueCount++
                val r = ((px ushr 16) and 0xFF).toFloat()
                val g = ((px ushr 8) and 0xFF).toFloat()
                val b = (px and 0xFF).toFloat()
                srcR[i] = r
                srcG[i] = g
                srcB[i] = b
                sumLuma += (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            }
        }

        if (opaqueCount == 0) {
            if (!prepared.bitmap.isRecycled) prepared.bitmap.recycle()
            bitmapCache.put(cacheKey, out)
            return BitmapDrawable(context.resources, out)
        }

        val avgLuma = sumLuma / opaqueCount.toFloat()
        val opaqueRatio = opaqueCount.toFloat() / nPixels.toFloat()

        // Step 1: Separate outer perimeter (card silhouette) from internal symbol edges
        val isOuterBoundary = BooleanArray(nPixels)
        val internalMag = FloatArray(nPixels)
        val alphaEdgeMag = FloatArray(nPixels)
        val gradAngle = FloatArray(nPixels)
        var internalStrongCount = 0
        var outerBoundaryCount = 0

        val minColorGrad = if (avgLuma < 0.36f) 48f else 72f

        for (y in 1 until drawSize - 1) {
            val row = y * drawSize
            val rowU = (y - 1) * drawSize
            val rowD = (y + 1) * drawSize
            for (x in 1 until drawSize - 1) {
                val idx = row + x
                val aC = srcA[idx]
                if (aC < 0.08f) continue

                val nearTransparent =
                    srcA[idx - 1] < 0.14f || srcA[idx + 1] < 0.14f ||
                        srcA[rowU + x] < 0.14f || srcA[rowD + x] < 0.14f ||
                        x <= 2 || y <= 2 || x >= drawSize - 3 || y >= drawSize - 3

                // Sobel alpha gradient
                val agx = -srcA[rowU + x - 1] + srcA[rowU + x + 1] -
                    2f * srcA[idx - 1] + 2f * srcA[idx + 1] -
                    srcA[rowD + x - 1] + srcA[rowD + x + 1]
                val agy = -srcA[rowU + x - 1] - 2f * srcA[rowU + x] - srcA[rowU + x + 1] +
                    srcA[rowD + x - 1] + 2f * srcA[rowD + x] + srcA[rowD + x + 1]
                val aMag = sqrt(agx * agx + agy * agy) / 4f

                if (nearTransparent) {
                    isOuterBoundary[idx] = true
                    outerBoundaryCount++
                    alphaEdgeMag[idx] = aMag.coerceIn(0f, 1f)
                    gradAngle[idx] = atan2(agy, agx)
                }

                // Only measure interior RGB Sobel when all 3x3 neighbors are reasonably opaque
                val allOpaque = srcA[rowU + x - 1] > 0.35f && srcA[rowU + x] > 0.35f && srcA[rowU + x + 1] > 0.35f &&
                    srcA[idx - 1] > 0.35f && srcA[idx + 1] > 0.35f &&
                    srcA[rowD + x - 1] > 0.35f && srcA[rowD + x] > 0.35f && srcA[rowD + x + 1] > 0.35f

                if (allOpaque) {
                    val rgx = -srcR[rowU + x - 1] + srcR[rowU + x + 1] -
                        2f * srcR[idx - 1] + 2f * srcR[idx + 1] -
                        srcR[rowD + x - 1] + srcR[rowD + x + 1]
                    val rgy = -srcR[rowU + x - 1] - 2f * srcR[rowU + x] - srcR[rowU + x + 1] +
                        srcR[rowD + x - 1] + 2f * srcR[rowD + x] + srcR[rowD + x + 1]

                    val ggx = -srcG[rowU + x - 1] + srcG[rowU + x + 1] -
                        2f * srcG[idx - 1] + 2f * srcG[idx + 1] -
                        srcG[rowD + x - 1] + srcG[rowD + x + 1]
                    val ggy = -srcG[rowU + x - 1] - 2f * srcG[rowU + x] - srcG[rowU + x + 1] +
                        srcG[rowD + x - 1] + 2f * srcG[rowD + x] + srcG[rowD + x + 1]

                    val bgx = -srcB[rowU + x - 1] + srcB[rowU + x + 1] -
                        2f * srcB[idx - 1] + 2f * srcB[idx + 1] -
                        srcB[rowD + x - 1] + srcB[rowD + x + 1]
                    val bgy = -srcB[rowU + x - 1] - 2f * srcB[rowU + x] - srcB[rowU + x + 1] +
                        srcB[rowD + x - 1] + 2f * srcB[rowD + x] + srcB[rowD + x + 1]

                    val mR = sqrt(rgx * rgx + rgy * rgy)
                    val mG = sqrt(ggx * ggx + ggy * ggy)
                    val mB = sqrt(bgx * bgx + bgy * bgy)

                    val maxC = max(mR, max(mG, mB))
                    if (maxC >= minColorGrad) {
                        val norm = ((maxC - minColorGrad) / 320f).coerceIn(0f, 1f)
                        internalMag[idx] = norm
                        // Dominant channel determines clean normal angle for 1px NMS thinning
                        val (dxC, dyC) = when (maxC) {
                            mR -> rgx to rgy
                            mG -> ggx to ggy
                            else -> bgx to bgy
                        }
                        gradAngle[idx] = atan2(dyC, dxC)
                        if (norm > 0.22f) internalStrongCount++
                    }
                }
            }
        }

        // Suppress enclosing card outline whenever the app icon wraps an inner symbol inside a card/circle
        val hasEnclosingPlate = !prepared.isIsolatedSilhouette ||
            (opaqueRatio > 0.44f && internalStrongCount > max(32, (outerBoundaryCount * 0.35f).toInt()))

        val rawEdge = FloatArray(nPixels)
        for (i in 0 until nPixels) {
            rawEdge[i] = if (hasEnclosingPlate) {
                internalMag[i]
            } else {
                max(internalMag[i], alphaEdgeMag[i] * 0.92f)
            }
        }

        // Step 2: 1-pixel Non-Maximum Suppression -> razor-thin crisp light filaments (zero thick borders)
        val nmsEdge = FloatArray(nPixels)
        for (y in 2 until drawSize - 2) {
            val row = y * drawSize
            for (x in 2 until drawSize - 2) {
                val idx = row + x
                val m = rawEdge[idx]
                if (m < 0.14f) continue

                var deg = Math.toDegrees(gradAngle[idx].toDouble()).toFloat()
                if (deg < 0f) deg += 180f

                val (n1, n2) = when {
                    deg < 22.5f || deg >= 157.5f -> rawEdge[idx - 1] to rawEdge[idx + 1]
                    deg < 67.5f -> rawEdge[(y - 1) * drawSize + x - 1] to rawEdge[(y + 1) * drawSize + x + 1]
                    deg < 112.5f -> rawEdge[(y - 1) * drawSize + x] to rawEdge[(y + 1) * drawSize + x]
                    else -> rawEdge[(y - 1) * drawSize + x + 1] to rawEdge[(y + 1) * drawSize + x - 1]
                }

                if (m >= n1 && m >= n2) {
                    nmsEdge[idx] = m
                }
            }
        }

        // Step 3: Anti-aliased 1.5px light filament spread so thin lines remain crisp & recognizable at small sizes
        val lightMap = FloatArray(nPixels)
        for (y in 2 until drawSize - 2) {
            val row = y * drawSize
            for (x in 2 until drawSize - 2) {
                val idx = row + x
                val peak = nmsEdge[idx]
                if (peak <= 0f) continue
                val boostedPeak = (0.58f + 0.42f * peak).coerceIn(0f, 1f)
                if (boostedPeak > lightMap[idx]) lightMap[idx] = boostedPeak
                val wing = boostedPeak * 0.48f
                val neighbors = intArrayOf(idx - 1, idx + 1, idx - drawSize, idx + drawSize)
                for (nb in neighbors) {
                    if (wing > lightMap[nb]) lightMap[nb] = wing
                }
            }
        }

        // Fallback safety for pure flat low-contrast shapes: project outer 1.5px light outline
        var activeLightCount = 0
        for (v in lightMap) if (v > 0.2f) activeLightCount++
        if (activeLightCount < 24) {
            for (i in 0 until nPixels) {
                if (isOuterBoundary[i] && alphaEdgeMag[i] > 0.15f) {
                    lightMap[i] = max(lightMap[i], (0.65f + 0.35f * alphaEdgeMag[i]).coerceIn(0f, 1f))
                }
            }
        }

        val outPx = IntArray(nPixels)
        val glowPx = IntArray(nPixels)
        val warmGlowPx = IntArray(nPixels)

        val angleRad = Math.toRadians(profile.angleDeg.toDouble())
        val ux = cos(angleRad).toFloat()
        val uy = sin(angleRad).toFloat()
        val invHalf = 1f / max(1f, drawSize * 0.5f)

        for (y in 0 until drawSize) {
            val row = y * drawSize
            val ny = (y - drawSize * 0.5f) * invHalf
            for (x in 0 until drawSize) {
                val idx = row + x
                val intensity = lightMap[idx]
                if (intensity <= 0.04f) continue

                val nx = (x - drawSize * 0.5f) * invHalf
                val dir = (((nx * ux + ny * uy) + 0.08f) / 1.08f).coerceIn(0f, 1f)

                val outA = (intensity * 252f).toInt().coerceIn(0, 255)
                // Primary light spectrum: #398BFF electric blue -> #00E5FF cyan -> #E4FAFF luminous core
                val coreMix = ((intensity - 0.58f) / 0.42f).coerceIn(0f, 1f)
                val baseR = (57f * (1f - intensity) + 228f * coreMix).toInt().coerceIn(0, 255)
                val baseG = (145f + 104f * intensity).toInt().coerceIn(0, 255)
                val baseB = 255

                // Smooth diagonal warm light transition (#FFB45C amber -> #C47A48 copper)
                val warmMix = if (dir > 0.14f) {
                    val t = ((dir - 0.14f) / 0.86f)
                    (t * t * profile.warmWeight * 0.94f).coerceIn(0f, 0.94f)
                } else {
                    0f
                }

                if (warmMix > 0.08f) {
                    val copperRatio = ((dir - 0.62f) / 0.38f).coerceIn(0f, 1f)
                    val wR = 255f + (196f - 255f) * copperRatio
                    val wG = 180f + (122f - 180f) * copperRatio
                    val wB = 92f + (72f - 92f) * copperRatio
                    val fR = (baseR * (1f - warmMix) + wR * warmMix).toInt().coerceIn(0, 255)
                    val fG = (baseG * (1f - warmMix) + wG * warmMix).toInt().coerceIn(0, 255)
                    val fB = (baseB * (1f - warmMix) + wB * warmMix).toInt().coerceIn(0, 255)
                    outPx[idx] = Color.argb(outA, fR, fG, fB)
                    warmGlowPx[idx] = Color.argb((outA * warmMix).toInt().coerceIn(0, 255), wR.toInt(), wG.toInt(), wB.toInt())
                } else {
                    outPx[idx] = Color.argb(outA, baseR, baseG, baseB)
                }
                glowPx[idx] = Color.argb((outA * 0.85f).toInt().coerceIn(0, 255), 0, 229, 255)
            }
        }

        // Discreet soft light halo radiating directly from the thin light strokes
        val glowBmp = Bitmap.createBitmap(drawSize, drawSize, Bitmap.Config.ARGB_8888)
        glowBmp.setPixels(glowPx, 0, drawSize, 0, 0, drawSize, drawSize)
        val glowAlpha = glowBmp.extractAlpha()

        val blueAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(72, 57, 139, 255)
            maskFilter = BlurMaskFilter(7.5f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(glowAlpha, null, destRect, blueAuraPaint)

        val cyanHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(122, 0, 229, 255)
            maskFilter = BlurMaskFilter(3.2f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(glowAlpha, null, destRect, cyanHaloPaint)

        glowAlpha.recycle()
        glowBmp.recycle()

        if (profile.warmWeight > 0f) {
            val warmGlowBmp = Bitmap.createBitmap(drawSize, drawSize, Bitmap.Config.ARGB_8888)
            warmGlowBmp.setPixels(warmGlowPx, 0, drawSize, 0, 0, drawSize, drawSize)
            val warmGlowAlpha = warmGlowBmp.extractAlpha()
            val warmHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                color = Color.argb(105, 196, 122, 72)
                maskFilter = BlurMaskFilter(4.0f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawBitmap(warmGlowAlpha, null, destRect, warmHaloPaint)
            warmGlowAlpha.recycle()
            warmGlowBmp.recycle()
        }

        val holoBmp = Bitmap.createBitmap(drawSize, drawSize, Bitmap.Config.ARGB_8888)
        holoBmp.setPixels(outPx, 0, drawSize, 0, 0, drawSize, drawSize)
        canvas.drawBitmap(holoBmp, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        holoBmp.recycle()

        drawLunaLightSparks(canvas, cx, cy, size, profile)

        if (!prepared.bitmap.isRecycled) {
            prepared.bitmap.recycle()
        }

        bitmapCache.put(cacheKey, out)
        try {
            renderDiskCacheDir.mkdirs()
            FileOutputStream(diskFile).use { fos ->
                out.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (_: Exception) {
            // Disk cache write is best-effort
        }
        return BitmapDrawable(context.resources, out)
    }

    /**
     * MAYA — Holographic Presence icon language:
     * Solid, sculptural technological ceramic chassis in matte-satin dark graphite (#131720 / #1E2534),
     * machined inner chamfer well, brushed golden-honey (#F5B942) & copper (#C47A48) metallic
     * trim and corner rivets, electric-blue (#2563EB / #38BDF8) fine circuit traces, and a
     * solid sculpted golden-honey/copper ceramic inlay foreground symbol.
     * Deliberately NOT translucent glass or liquid metal.
     */
    private fun renderUnifiedMayaCeramicIcon(
        cacheIdentity: String,
        packageName: String,
        fallback: Drawable
    ): Drawable {
        val size = 192
        val cacheKey = "maya_ceramic_v3:$cacheIdentity#$size"
        bitmapCache.get(cacheKey)?.let { cached ->
            return BitmapDrawable(context.resources, cached)
        }

        val diskFile = diskCacheFileForKey(cacheKey)
        if (diskFile.exists()) {
            try {
                FileInputStream(diskFile).use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { decoded ->
                        bitmapCache.put(cacheKey, decoded)
                        return BitmapDrawable(context.resources, decoded)
                    }
                }
            } catch (_: Exception) {
                // Re-render cleanly if disk entry was corrupted
            }
        }

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val margin = size * 0.06f
        val outerRect = RectF(margin, margin, size - margin, size - margin)
        val outerRadius = size * 0.22f
        val outerPath = Path().apply {
            addRoundRect(outerRect, outerRadius, outerRadius, Path.Direction.CW)
        }

        // 1. Soft elevation shadow underneath solid architectural ceramic block
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(195, 0, 0, 0)
            maskFilter = BlurMaskFilter(5.5f, BlurMaskFilter.Blur.NORMAL)
        }
        val shadowRect = RectF(outerRect).apply { offset(0f, 3.5f) }
        canvas.drawRoundRect(shadowRect, outerRadius, outerRadius, shadowPaint)

        // 2. Solid opaque matte-satin dark graphite ceramic slab
        val ceramicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                outerRect.left,
                outerRect.top,
                outerRect.right,
                outerRect.bottom,
                intArrayOf(
                    Color.parseColor("#252C3B"),
                    Color.parseColor("#141923"),
                    Color.parseColor("#0B0E14")
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(outerPath, ceramicPaint)

        // Subtle satin ceramic top-left sheen
        val satinSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                outerRect.left + outerRect.width() * 0.24f,
                outerRect.top + outerRect.height() * 0.20f,
                outerRect.width() * 0.72f,
                intArrayOf(Color.argb(42, 255, 228, 158), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.save()
        canvas.clipPath(outerPath)
        canvas.drawRect(outerRect, satinSheenPaint)
        canvas.restore()

        // 3. Machined inner recessed well in deep graphite
        val wellInset = size * 0.132f
        val wellRect = RectF(wellInset, wellInset, size - wellInset, size - wellInset)
        val wellRadius = size * 0.155f
        val wellPath = Path().apply {
            addRoundRect(wellRect, wellRadius, wellRadius, Path.Direction.CW)
        }
        val wellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                wellRect.left,
                wellRect.top,
                wellRect.right,
                wellRect.bottom,
                intArrayOf(
                    Color.parseColor("#0A0E16"),
                    Color.parseColor("#121722"),
                    Color.parseColor("#1B2230")
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(wellPath, wellPaint)

        // Inner well golden-copper chamfer edge
        val wellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
            shader = LinearGradient(
                wellRect.left,
                wellRect.top,
                wellRect.right,
                wellRect.bottom,
                intArrayOf(
                    Color.argb(180, 196, 122, 72),
                    Color.argb(105, 37, 99, 235),
                    Color.argb(205, 245, 185, 66)
                ),
                floatArrayOf(0f, 0.50f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(wellPath, wellBorderPaint)

        // 4. Sculpted metallic golden-honey outer bezel
        val outerTrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4.2f
            shader = LinearGradient(
                outerRect.left,
                outerRect.top,
                outerRect.right,
                outerRect.bottom,
                intArrayOf(
                    Color.parseColor("#FFE49E"),
                    Color.parseColor("#F5B942"),
                    Color.parseColor("#C47A48"),
                    Color.parseColor("#F5B942")
                ),
                floatArrayOf(0f, 0.35f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(outerPath, outerTrimPaint)

        // 5. Fine electric-blue technological circuit traces + copper rivets on top/bottom ceramic rails
        val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#38BDF8")
        }
        val topY = margin + (wellInset - margin) * 0.5f
        val botY = size - margin - (wellInset - margin) * 0.5f
        val leftX = margin + outerRadius * 0.62f
        val rightX = size - margin - outerRadius * 0.62f
        canvas.drawLine(leftX + 12f, topY, size * 0.42f, topY, tracePaint)
        canvas.drawLine(size * 0.58f, botY, rightX - 12f, botY, tracePaint)

        val rivetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C47A48")
        }
        val rivetHiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFE49E")
        }
        val rivets = arrayOf(
            leftX to topY,
            rightX to topY,
            leftX to botY,
            rightX to botY
        )
        for ((rx, ry) in rivets) {
            canvas.drawCircle(rx, ry, 2.8f, rivetPaint)
            canvas.drawCircle(rx - 0.6f, ry - 0.6f, 1.1f, rivetHiPaint)
        }

        // 6. Sculpted 3D golden-honey & copper technological ceramic miniature foreground object
        if (!drawMayaCuratedCeramicSymbol(canvas, packageName.lowercase(), size)) {
            val prepared = prepareForegroundSymbol(fallback, 136)
            if (prepared.isIsolatedSilhouette) {
                drawSculptedMayaCeramicSymbol(canvas, prepared.bitmap, size)
            } else {
                drawMayaCeramicFramedArtwork(canvas, prepared.bitmap, size)
            }
            if (!prepared.bitmap.isRecycled) {
                prepared.bitmap.recycle()
            }
        }

        bitmapCache.put(cacheKey, out)
        try {
            renderDiskCacheDir.mkdirs()
            FileOutputStream(diskFile).use { fos ->
                out.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (_: Exception) {
            // Disk cache write is best-effort
        }
        return BitmapDrawable(context.resources, out)
    }

    /**
     * Bespoke 3D technological ceramic + brushed gold/copper + graphite sculptures for the 20
     * requested apps whose original adaptive/legacy layers lose identity under generic extraction.
     * All other apps remain untouched via the existing universal 3D ceramic relief shader.
     */
    private fun drawMayaCuratedCeramicSymbol(
        canvas: Canvas,
        pkg: String,
        size: Int
    ): Boolean {
        val kind = when {
            pkg.contains("andftp") -> "andftp"
            pkg.contains("calendar") -> "calendar"
            pkg.contains("camera") && !pkg.contains("scanner") -> "camera"
            pkg == "com.android.chrome" || pkg.contains(".chrome") -> "chrome"
            pkg.contains("color.note") || pkg.contains("colornote") || pkg.contains("socialnmobile") -> "colornote"
            pkg == "com.android.settings" || pkg.endsWith(".settings") || pkg.contains("android.settings") -> "settings"
            pkg.contains("videoconverter") || pkg.contains("video.converter") || pkg.contains("video_converter") ||
                pkg.contains("mp4converter") || pkg.contains("media.converter") -> "videoconverter"
            pkg.contains("filemanager") || pkg.contains("file.manager") || pkg.contains("fileexplorer") ||
                pkg.contains("file.explorer") || pkg.contains("estrongs") || pkg.contains("solidexplorer") ||
                pkg.contains("fxfile") -> "filemanager"
            pkg.contains("kodi") || pkg.contains("xbmc") -> "kodi"
            pkg == "com.google.android.apps.maps" || pkg.contains("apps.maps") -> "maps"
            pkg.contains("mercadolibre") || pkg.contains("mercadolivre") -> "mercadolivre"
            pkg.contains("mercadopago") -> "mercadopago"
            pkg.contains("messaging") || pkg == "com.android.mms" || pkg.endsWith(".mms") || pkg.contains(".sms") -> "messages"
            pkg.contains("meuinss") || pkg.contains("inss") -> "inss"
            pkg.contains("myfiles") || pkg.contains("documentsui") || pkg.contains("nbu.files") ||
                pkg.contains("arquivos") -> "myfiles"
            pkg.contains("rarlab") || pkg == "com.rarlab.rar" -> "rar"
            pkg.contains("deskclock") || pkg.contains("clockpackage") || pkg.endsWith(".clock") ||
                pkg.contains("android.clock") -> "clock"
            pkg.contains("snaptube") -> "snaptube"
            pkg.contains("youtube.producer") || pkg.contains("youtube.create") || pkg.contains("ytcreate") -> "ytcreate"
            (pkg == "com.google.android.youtube" || pkg.contains("youtube")) &&
                !pkg.contains("producer") && !pkg.contains("create") && !pkg.contains("music") -> "youtube"
            else -> return false
        }

        val cx = size / 2f
        val cy = size / 2f
        val r = size * 0.295f

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(232, 1, 2, 5)
            maskFilter = BlurMaskFilter(5.0f, BlurMaskFilter.Blur.NORMAL)
        }
        val depthDeepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3B1E0E")
        }
        val depthMidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6E3A1C")
        }
        val goldSatinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                intArrayOf(
                    Color.parseColor("#FFE8A6"),
                    Color.parseColor("#F5B942"),
                    Color.parseColor("#CF8A3B"),
                    Color.parseColor("#9A5B2C")
                ),
                floatArrayOf(0f, 0.34f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val goldBrightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                intArrayOf(
                    Color.parseColor("#FFF5CC"),
                    Color.parseColor("#F8C65B"),
                    Color.parseColor("#C87E43")
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val copperStructPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                intArrayOf(
                    Color.parseColor("#DFA06E"),
                    Color.parseColor("#C47A48"),
                    Color.parseColor("#854925")
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val graphiteBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                intArrayOf(
                    Color.parseColor("#232A3A"),
                    Color.parseColor("#121620"),
                    Color.parseColor("#080A0F")
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val rimGoldStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
            color = Color.parseColor("#FFEBB3")
        }
        val rimCopperStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
            color = Color.parseColor("#C47A48")
        }
        val seamDarkStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.2f
            color = Color.parseColor("#080B10")
        }

        fun draw3DShape(path: Path, fillPaint: Paint, strokePaint: Paint? = rimGoldStroke) {
            canvas.save()
            canvas.translate(3.0f, 4.4f)
            canvas.drawPath(path, shadowPaint)
            canvas.restore()

            canvas.save()
            canvas.translate(2.4f, 3.4f)
            canvas.drawPath(path, depthDeepPaint)
            canvas.restore()

            canvas.save()
            canvas.translate(1.2f, 1.7f)
            canvas.drawPath(path, depthMidPaint)
            canvas.restore()

            canvas.drawPath(path, fillPaint)
            if (strokePaint != null) {
                canvas.drawPath(path, strokePaint)
            }
        }

        fun drawBlueNode(px: Float, py: Float, radius: Float = 4.6f) {
            val aura = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    px, py, radius * 2.4f,
                    intArrayOf(
                        Color.argb(235, 56, 189, 248),
                        Color.argb(125, 37, 99, 235),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(px, py, radius * 2.4f, aura)
            val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E0F7FF")
            }
            canvas.drawCircle(px, py, radius * 0.72f, core)
        }

        when (kind) {
            "chrome" -> {
                val outerR = r * 0.96f
                val innerR = r * 0.43f
                val backing = Path().apply { addCircle(cx, cy, outerR, Path.Direction.CW) }
                draw3DShape(backing, graphiteBodyPaint, null)

                val tanX = sqrt(outerR * outerR - innerR * innerR)
                val blade = Path().apply {
                    val startX = cx - innerR * 0.866f
                    val startY = cy + innerR * 0.5f
                    val p1x = cx + (-tanX * 0.5f - (-innerR) * (-0.866f))
                    val p1y = cy + (tanX * (-0.866f) + (-innerR) * (-0.5f))
                    moveTo(startX, startY)
                    lineTo(p1x, p1y)
                    val a1 = Math.toDegrees(atan2((p1y - cy).toDouble(), (p1x - cx).toDouble())).toFloat()
                    val a2 = Math.toDegrees(atan2((-innerR).toDouble(), tanX.toDouble())).toFloat()
                    var sweep = a2 - a1
                    if (sweep <= 0f) sweep += 360f
                    arcTo(RectF(cx - outerR, cy - outerR, cx + outerR, cy + outerR), a1, sweep, false)
                    lineTo(cx, cy - innerR)
                    arcTo(RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR), -90f, -120f, false)
                    close()
                }

                val bladePaints = arrayOf(goldBrightPaint, copperStructPaint, goldSatinPaint)
                for (i in 0 until 3) {
                    canvas.save()
                    canvas.rotate(i * 120f, cx, cy)
                    canvas.drawPath(blade, bladePaints[i])
                    canvas.drawPath(blade, seamDarkStroke)
                    canvas.restore()
                }

                val collar = Path().apply { addCircle(cx, cy, innerR, Path.Direction.CW) }
                canvas.drawPath(collar, graphiteBodyPaint)
                canvas.drawCircle(cx, cy, innerR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3.8f
                    shader = goldBrightPaint.shader
                })
                canvas.drawCircle(cx, cy, innerR * 0.62f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1D4ED8")
                })
                drawBlueNode(cx - 2f, cy - 2f, 5.2f)
            }

            "youtube" -> {
                val frame = Path().apply {
                    addRoundRect(
                        RectF(cx - r * 1.02f, cy - r * 0.72f, cx + r * 1.02f, cy + r * 0.72f),
                        r * 0.30f, r * 0.30f, Path.Direction.CW
                    )
                }
                draw3DShape(frame, goldSatinPaint)
                val screenRect = RectF(cx - r * 0.85f, cy - r * 0.54f, cx + r * 0.85f, cy + r * 0.54f)
                val screen = Path().apply {
                    addRoundRect(screenRect, r * 0.21f, r * 0.21f, Path.Direction.CW)
                }
                canvas.drawPath(screen, graphiteBodyPaint)
                canvas.drawPath(screen, rimCopperStroke)

                val play = Path().apply {
                    moveTo(cx - r * 0.24f, cy - r * 0.34f)
                    lineTo(cx + r * 0.42f, cy)
                    lineTo(cx - r * 0.24f, cy + r * 0.34f)
                    close()
                }
                draw3DShape(play, goldBrightPaint)
                drawBlueNode(cx + r * 0.68f, cy + r * 0.58f, 3.4f)
            }

            "ytcreate" -> {
                // Overlapping layered hexagonal studio badge distinctive to YouTube Create
                val backHex = Path().apply {
                    for (i in 0 until 6) {
                        val ang = Math.toRadians((i * 60 - 90).toDouble())
                        val hx = cx + r * 0.94f * cos(ang).toFloat()
                        val hy = cy + r * 0.94f * sin(ang).toFloat()
                        if (i == 0) moveTo(hx, hy) else lineTo(hx, hy)
                    }
                    close()
                }
                draw3DShape(backHex, copperStructPaint)

                val frontHex = Path().apply {
                    for (i in 0 until 6) {
                        val ang = Math.toRadians((i * 60 - 75).toDouble())
                        val hx = cx + r * 0.76f * cos(ang).toFloat()
                        val hy = cy + r * 0.76f * sin(ang).toFloat()
                        if (i == 0) moveTo(hx, hy) else lineTo(hx, hy)
                    }
                    close()
                }
                draw3DShape(frontHex, goldSatinPaint)

                val coreHex = Path().apply {
                    for (i in 0 until 6) {
                        val ang = Math.toRadians((i * 60 - 75).toDouble())
                        val hx = cx + r * 0.56f * cos(ang).toFloat()
                        val hy = cy + r * 0.56f * sin(ang).toFloat()
                        if (i == 0) moveTo(hx, hy) else lineTo(hx, hy)
                    }
                    close()
                }
                canvas.drawPath(coreHex, graphiteBodyPaint)

                val play = Path().apply {
                    moveTo(cx - r * 0.16f, cy - r * 0.27f)
                    lineTo(cx + r * 0.32f, cy)
                    lineTo(cx - r * 0.16f, cy + r * 0.27f)
                    close()
                }
                draw3DShape(play, goldBrightPaint)
                drawBlueNode(cx + r * 0.58f, cy - r * 0.54f, 3.8f)
            }

            "maps" -> {
                val pad = Path().apply {
                    addOval(RectF(cx - r * 0.76f, cy + r * 0.56f, cx + r * 0.76f, cy + r * 0.94f), Path.Direction.CW)
                }
                canvas.drawPath(pad, graphiteBodyPaint)
                canvas.drawPath(pad, rimCopperStroke)

                val pin = Path().apply {
                    val pr = r * 0.64f
                    val pcy = cy - r * 0.18f
                    addArc(RectF(cx - pr, pcy - pr, cx + pr, pcy + pr), 148f, 244f)
                    lineTo(cx, cy + r * 0.78f)
                    close()
                }
                draw3DShape(pin, goldSatinPaint)
                canvas.drawCircle(cx, cy - r * 0.18f, r * 0.32f, graphiteBodyPaint)
                canvas.drawCircle(cx, cy - r * 0.18f, r * 0.32f, rimCopperStroke)
                drawBlueNode(cx, cy - r * 0.18f, 5.6f)
            }

            "camera" -> {
                val body = Path().apply {
                    moveTo(cx - r * 0.88f, cy - r * 0.38f)
                    lineTo(cx - r * 0.42f, cy - r * 0.38f)
                    lineTo(cx - r * 0.24f, cy - r * 0.68f)
                    lineTo(cx + r * 0.24f, cy - r * 0.68f)
                    lineTo(cx + r * 0.42f, cy - r * 0.38f)
                    lineTo(cx + r * 0.88f, cy - r * 0.38f)
                    lineTo(cx + r * 0.88f, cy + r * 0.68f)
                    lineTo(cx - r * 0.88f, cy + r * 0.68f)
                    close()
                }
                draw3DShape(body, goldSatinPaint)
                val faceplate = Path().apply {
                    addRoundRect(
                        RectF(cx - r * 0.74f, cy - r * 0.22f, cx + r * 0.74f, cy + r * 0.54f),
                        r * 0.14f, r * 0.14f, Path.Direction.CW
                    )
                }
                canvas.drawPath(faceplate, graphiteBodyPaint)

                val lensBarrel = Path().apply { addCircle(cx, cy + r * 0.16f, r * 0.42f, Path.Direction.CW) }
                draw3DShape(lensBarrel, graphiteBodyPaint, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 4.5f
                    shader = goldBrightPaint.shader
                })
                canvas.drawCircle(cx, cy + r * 0.16f, r * 0.22f, rimCopperStroke)
                drawBlueNode(cx - r * 0.08f, cy + r * 0.08f, 3.8f)
                drawBlueNode(cx + r * 0.60f, cy - r * 0.10f, 3.4f)
            }

            "calendar" -> {
                val body = Path().apply {
                    addRoundRect(
                        RectF(cx - r * 0.88f, cy - r * 0.66f, cx + r * 0.88f, cy + r * 0.84f),
                        r * 0.22f, r * 0.22f, Path.Direction.CW
                    )
                }
                draw3DShape(body, goldSatinPaint)
                val header = Path().apply {
                    addRoundRect(
                        RectF(cx - r * 0.86f, cy - r * 0.64f, cx + r * 0.86f, cy - r * 0.24f),
                        r * 0.18f, r * 0.18f, Path.Direction.CW
                    )
                }
                canvas.drawPath(header, copperStructPaint)

                val page = Path().apply {
                    addRoundRect(
                        RectF(cx - r * 0.72f, cy - r * 0.18f, cx + r * 0.72f, cy + r * 0.68f),
                        r * 0.12f, r * 0.12f, Path.Direction.CW
                    )
                }
                canvas.drawPath(page, graphiteBodyPaint)

                // Top cylindrical metallic binder rings
                val ringL = Path().apply { addRoundRect(RectF(cx - r * 0.46f, cy - r * 0.88f, cx - r * 0.26f, cy - r * 0.48f), 4f, 4f, Path.Direction.CW) }
                val ringR = Path().apply { addRoundRect(RectF(cx + r * 0.26f, cy - r * 0.88f, cx + r * 0.46f, cy - r * 0.48f), 4f, 4f, Path.Direction.CW) }
                draw3DShape(ringL, goldBrightPaint)
                draw3DShape(ringR, goldBrightPaint)

                val cols = floatArrayOf(-0.46f, 0f, 0.46f)
                val rows = floatArrayOf(0.06f, 0.44f)
                for ((ri, ryCell) in rows.withIndex()) {
                    for ((ci, cxCell) in cols.withIndex()) {
                        val tileRect = RectF(
                            cx + r * (cxCell - 0.15f),
                            cy + r * (ryCell - 0.12f),
                            cx + r * (cxCell + 0.15f),
                            cy + r * (ryCell + 0.12f)
                        )
                        if (ri == 1 && ci == 1) {
                            canvas.drawRoundRect(tileRect, 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.parseColor("#2563EB")
                            })
                            drawBlueNode(tileRect.centerX(), tileRect.centerY(), 3.2f)
                        } else {
                            canvas.drawRoundRect(tileRect, 4f, 4f, if ((ri + ci) % 2 == 0) goldBrightPaint else copperStructPaint)
                        }
                    }
                }
            }

            "settings" -> {
                val gear = Path()
                for (i in 0 until 8) {
                    val a0 = Math.toRadians((i * 45 - 12).toDouble())
                    val a1 = Math.toRadians((i * 45 + 12).toDouble())
                    val a2 = Math.toRadians((i * 45 + 21).toDouble())
                    val a3 = Math.toRadians((i * 45 + 24).toDouble())
                    val ro = r * 0.94f
                    val ri = r * 0.69f
                    val x0 = cx + ro * cos(a0).toFloat()
                    val y0 = cy + ro * sin(a0).toFloat()
                    if (i == 0) gear.moveTo(x0, y0) else gear.lineTo(x0, y0)
                    gear.lineTo(cx + ro * cos(a1).toFloat(), cy + ro * sin(a1).toFloat())
                    gear.lineTo(cx + ri * cos(a2).toFloat(), cy + ri * sin(a2).toFloat())
                    gear.lineTo(cx + ri * cos(a3).toFloat(), cy + ri * sin(a3).toFloat())
                }
                gear.close()
                draw3DShape(gear, goldSatinPaint)
                canvas.drawCircle(cx, cy, r * 0.46f, graphiteBodyPaint)
                canvas.drawCircle(cx, cy, r * 0.46f, rimCopperStroke)
                canvas.drawCircle(cx, cy, r * 0.24f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3.2f
                    shader = goldBrightPaint.shader
                })
                drawBlueNode(cx, cy, 4.4f)
            }

            "messages" -> {
                val bubble = Path().apply {
                    moveTo(cx - r * 0.84f, cy - r * 0.68f)
                    lineTo(cx + r * 0.84f, cy - r * 0.68f)
                    quadTo(cx + r * 0.96f, cy - r * 0.68f, cx + r * 0.96f, cy - r * 0.56f)
                    lineTo(cx + r * 0.96f, cy + r * 0.38f)
                    quadTo(cx + r * 0.96f, cy + r * 0.50f, cx + r * 0.84f, cy + r * 0.50f)
                    lineTo(cx - r * 0.24f, cy + r * 0.50f)
                    lineTo(cx - r * 0.82f, cy + r * 0.88f)
                    lineTo(cx - r * 0.82f, cy + r * 0.50f)
                    quadTo(cx - r * 0.96f, cy + r * 0.50f, cx - r * 0.96f, cy + r * 0.38f)
                    lineTo(cx - r * 0.96f, cy - r * 0.56f)
                    quadTo(cx - r * 0.96f, cy - r * 0.68f, cx - r * 0.84f, cy - r * 0.68f)
                    close()
                }
                draw3DShape(bubble, goldSatinPaint)
                val inner = Path().apply {
                    addRoundRect(RectF(cx - r * 0.74f, cy - r * 0.46f, cx + r * 0.74f, cy + r * 0.30f), 8f, 8f, Path.Direction.CW)
                }
                canvas.drawPath(inner, graphiteBodyPaint)
                canvas.drawRoundRect(RectF(cx - r * 0.52f, cy - r * 0.24f, cx + r * 0.52f, cy - r * 0.08f), 4f, 4f, goldBrightPaint)
                canvas.drawRoundRect(RectF(cx - r * 0.52f, cy + r * 0.02f, cx + r * 0.22f, cy + r * 0.18f), 4f, 4f, copperStructPaint)
                drawBlueNode(cx + r * 0.44f, cy + r * 0.10f, 3.6f)
            }

            "myfiles" -> {
                val backTab = Path().apply {
                    moveTo(cx - r * 0.90f, cy - r * 0.66f)
                    lineTo(cx - r * 0.22f, cy - r * 0.66f)
                    lineTo(cx, cy - r * 0.40f)
                    lineTo(cx + r * 0.90f, cy - r * 0.40f)
                    lineTo(cx + r * 0.90f, cy + r * 0.68f)
                    lineTo(cx - r * 0.90f, cy + r * 0.68f)
                    close()
                }
                draw3DShape(backTab, copperStructPaint)
                canvas.drawRoundRect(
                    RectF(cx - r * 0.74f, cy - r * 0.32f, cx + r * 0.74f, cy - r * 0.06f),
                    4f, 4f, graphiteBodyPaint
                )
                val flap = Path().apply {
                    addRoundRect(
                        RectF(cx - r * 0.94f, cy - r * 0.16f, cx + r * 0.94f, cy + r * 0.72f),
                        r * 0.20f, r * 0.20f, Path.Direction.CW
                    )
                }
                draw3DShape(flap, goldSatinPaint)
                canvas.drawRoundRect(RectF(cx - r * 0.42f, cy + r * 0.20f, cx + r * 0.42f, cy + r * 0.36f), 5f, 5f, graphiteBodyPaint)
                drawBlueNode(cx + r * 0.68f, cy + r * 0.06f, 3.6f)
            }

            "filemanager" -> {
                val backTab = Path().apply {
                    moveTo(cx - r * 0.92f, cy - r * 0.66f)
                    lineTo(cx - r * 0.24f, cy - r * 0.66f)
                    lineTo(cx - r * 0.02f, cy - r * 0.40f)
                    lineTo(cx + r * 0.88f, cy - r * 0.40f)
                    lineTo(cx + r * 0.88f, cy + r * 0.68f)
                    lineTo(cx - r * 0.92f, cy + r * 0.68f)
                    close()
                }
                draw3DShape(backTab, copperStructPaint)
                val docCard = Path().apply {
                    addRoundRect(RectF(cx - r * 0.68f, cy - r * 0.34f, cx + r * 0.70f, cy - r * 0.04f), 4f, 4f, Path.Direction.CW)
                }
                canvas.drawPath(docCard, goldBrightPaint)

                val flap = Path().apply {
                    addRoundRect(
                        RectF(cx - r * 0.94f, cy - r * 0.16f, cx + r * 0.94f, cy + r * 0.72f),
                        r * 0.20f, r * 0.20f, Path.Direction.CW
                    )
                }
                draw3DShape(flap, goldSatinPaint)
                // Recessed architectural drawer lens badge to differentiate File Manager
                val lens = Path().apply { addCircle(cx + r * 0.32f, cy + r * 0.28f, r * 0.28f, Path.Direction.CW) }
                canvas.drawPath(lens, graphiteBodyPaint)
                canvas.drawPath(lens, rimGoldStroke)
                canvas.drawLine(
                    cx + r * 0.52f, cy + r * 0.48f, cx + r * 0.72f, cy + r * 0.68f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 5.0f
                        strokeCap = Paint.Cap.ROUND
                        color = Color.parseColor("#121620")
                    }
                )
                drawBlueNode(cx + r * 0.32f, cy + r * 0.28f, 3.6f)
            }

            "colornote" -> {
                val note = Path().apply {
                    moveTo(cx - r * 0.84f, cy - r * 0.84f)
                    lineTo(cx + r * 0.84f, cy - r * 0.84f)
                    lineTo(cx + r * 0.84f, cy + r * 0.40f)
                    lineTo(cx + r * 0.40f, cy + r * 0.84f)
                    lineTo(cx - r * 0.84f, cy + r * 0.84f)
                    close()
                }
                draw3DShape(note, goldSatinPaint)
                // Top copper sticky binder band
                canvas.drawRect(cx - r * 0.82f, cy - r * 0.82f, cx + r * 0.82f, cy - r * 0.44f, copperStructPaint)
                // Recessed dark graphite ruled note lines
                canvas.drawRoundRect(RectF(cx - r * 0.56f, cy - r * 0.22f, cx + r * 0.56f, cy - r * 0.06f), 3f, 3f, graphiteBodyPaint)
                canvas.drawRoundRect(RectF(cx - r * 0.56f, cy + r * 0.08f, cx + r * 0.56f, cy + r * 0.24f), 3f, 3f, graphiteBodyPaint)
                canvas.drawRoundRect(RectF(cx - r * 0.56f, cy + r * 0.38f, cx + r * 0.16f, cy + r * 0.54f), 3f, 3f, graphiteBodyPaint)
                // Dog-ear curled bottom-right corner
                val curl = Path().apply {
                    moveTo(cx + r * 0.40f, cy + r * 0.84f)
                    lineTo(cx + r * 0.40f, cy + r * 0.40f)
                    lineTo(cx + r * 0.84f, cy + r * 0.40f)
                    close()
                }
                canvas.drawPath(curl, copperStructPaint)
                canvas.drawPath(curl, rimGoldStroke)
                drawBlueNode(cx, cy - r * 0.63f, 3.6f)
            }

            "rar" -> {
                // Iconic 3 stacked compressed books + vertical center buckle strap
                val b1 = Path().apply { addRoundRect(RectF(cx - r * 0.84f, cy - r * 0.78f, cx + r * 0.84f, cy - r * 0.32f), 8f, 8f, Path.Direction.CW) }
                val b2 = Path().apply { addRoundRect(RectF(cx - r * 0.84f, cy - r * 0.23f, cx + r * 0.84f, cy + r * 0.23f), 8f, 8f, Path.Direction.CW) }
                val b3 = Path().apply { addRoundRect(RectF(cx - r * 0.84f, cy + r * 0.32f, cx + r * 0.84f, cy + r * 0.78f), 8f, 8f, Path.Direction.CW) }
                draw3DShape(b1, goldBrightPaint)
                draw3DShape(b2, graphiteBodyPaint, rimCopperStroke)
                draw3DShape(b3, copperStructPaint)

                val strap = Path().apply {
                    addRoundRect(RectF(cx - r * 0.24f, cy - r * 0.86f, cx + r * 0.24f, cy + r * 0.86f), 5f, 5f, Path.Direction.CW)
                }
                draw3DShape(strap, goldSatinPaint)
                canvas.drawRoundRect(RectF(cx - r * 0.16f, cy - r * 0.16f, cx + r * 0.16f, cy + r * 0.16f), 4f, 4f, graphiteBodyPaint)
                drawBlueNode(cx, cy, 3.6f)
            }

            "kodi" -> {
                // Iconic 45-degree Kodi diamond split into 4 geometric ceramic facets
                val backDiamond = Path().apply {
                    moveTo(cx, cy - r * 0.96f)
                    lineTo(cx + r * 0.96f, cy)
                    lineTo(cx, cy + r * 0.96f)
                    lineTo(cx - r * 0.96f, cy)
                    close()
                }
                draw3DShape(backDiamond, graphiteBodyPaint, rimGoldStroke)

                val gap = r * 0.08f
                val leftFacet = Path().apply {
                    moveTo(cx - r * 0.88f, cy)
                    lineTo(cx - gap, cy - r * 0.42f)
                    lineTo(cx - gap, cy + r * 0.42f)
                    close()
                }
                val topFacet = Path().apply {
                    moveTo(cx, cy - r * 0.88f)
                    lineTo(cx + r * 0.42f, cy - gap)
                    lineTo(cx - r * 0.42f, cy - gap)
                    close()
                }
                val rightFacet = Path().apply {
                    moveTo(cx + r * 0.88f, cy)
                    lineTo(cx + gap, cy - r * 0.42f)
                    lineTo(cx + gap, cy + r * 0.42f)
                    close()
                }
                val botFacet = Path().apply {
                    moveTo(cx, cy + r * 0.88f)
                    lineTo(cx + r * 0.42f, cy + gap)
                    lineTo(cx - r * 0.42f, cy + gap)
                    close()
                }
                canvas.drawPath(leftFacet, goldBrightPaint)
                canvas.drawPath(topFacet, copperStructPaint)
                canvas.drawPath(rightFacet, goldSatinPaint)
                canvas.drawPath(botFacet, copperStructPaint)
                drawBlueNode(cx, cy, 4.4f)
            }

            "mercadolivre", "mercadopago" -> {
                val isPago = kind == "mercadopago"
                val oval = Path().apply {
                    addOval(RectF(cx - r * 0.98f, cy - r * 0.66f, cx + r * 0.98f, cy + r * 0.66f), Path.Direction.CW)
                }
                draw3DShape(oval, if (isPago) graphiteBodyPaint else goldSatinPaint, if (isPago) Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 4.2f
                    color = Color.parseColor("#38BDF8")
                } else rimGoldStroke)

                val innerOval = Path().apply {
                    addOval(RectF(cx - r * 0.80f, cy - r * 0.48f, cx + r * 0.80f, cy + r * 0.48f), Path.Direction.CW)
                }
                canvas.drawPath(innerOval, graphiteBodyPaint)
                canvas.drawPath(innerOval, if (isPago) rimGoldStroke else rimCopperStroke)

                // Stylized interlocking handshake in high-relief metallic gold & copper
                val leftArm = Path().apply {
                    moveTo(cx - r * 0.70f, cy - r * 0.06f)
                    lineTo(cx - r * 0.24f, cy - r * 0.26f)
                    lineTo(cx + r * 0.14f, cy + r * 0.04f)
                    lineTo(cx - r * 0.10f, cy + r * 0.26f)
                    lineTo(cx - r * 0.64f, cy + r * 0.14f)
                    close()
                }
                val rightArm = Path().apply {
                    moveTo(cx + r * 0.70f, cy - r * 0.06f)
                    lineTo(cx + r * 0.24f, cy - r * 0.26f)
                    lineTo(cx - r * 0.14f, cy + r * 0.04f)
                    lineTo(cx + r * 0.10f, cy + r * 0.26f)
                    lineTo(cx + r * 0.64f, cy + r * 0.14f)
                    close()
                }
                draw3DShape(leftArm, copperStructPaint)
                draw3DShape(rightArm, goldBrightPaint)
                drawBlueNode(cx, cy + r * 0.04f, if (isPago) 4.4f else 3.0f)
            }

            "inss" -> {
                // Rounded rectangular tech plaque spelling crisp geometric "INSS"
                val plaque = Path().apply {
                    addRoundRect(RectF(cx - r * 0.96f, cy - r * 0.66f, cx + r * 0.96f, cy + r * 0.66f), r * 0.24f, r * 0.24f, Path.Direction.CW)
                }
                draw3DShape(plaque, graphiteBodyPaint, rimGoldStroke)
                // Top gov/tech blue bar
                canvas.drawRoundRect(RectF(cx - r * 0.78f, cy - r * 0.50f, cx + r * 0.78f, cy - r * 0.36f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#2563EB")
                })

                val strokeW = r * 0.14f
                val letterDepth = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokeW
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = Color.parseColor("#6E3A1C")
                }
                val letterGold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokeW
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    shader = goldBrightPaint.shader
                }
                val yTop = cy - r * 0.18f
                val yMid = cy + r * 0.10f
                val yBot = cy + r * 0.38f
                val letters = Path().apply {
                    // I
                    moveTo(cx - r * 0.66f, yTop); lineTo(cx - r * 0.66f, yBot)
                    // N
                    moveTo(cx - r * 0.42f, yBot); lineTo(cx - r * 0.42f, yTop)
                    lineTo(cx - r * 0.14f, yBot); lineTo(cx - r * 0.14f, yTop)
                    // S 1
                    moveTo(cx + r * 0.28f, yTop); lineTo(cx + r * 0.04f, yTop)
                    lineTo(cx + r * 0.04f, yMid); lineTo(cx + r * 0.28f, yMid)
                    lineTo(cx + r * 0.28f, yBot); lineTo(cx + r * 0.04f, yBot)
                    // S 2
                    moveTo(cx + r * 0.72f, yTop); lineTo(cx + r * 0.48f, yTop)
                    lineTo(cx + r * 0.48f, yMid); lineTo(cx + r * 0.72f, yMid)
                    lineTo(cx + r * 0.72f, yBot); lineTo(cx + r * 0.48f, yBot)
                }
                canvas.save()
                canvas.translate(1.5f, 2.2f)
                canvas.drawPath(letters, letterDepth)
                canvas.restore()
                canvas.drawPath(letters, letterGold)
                drawBlueNode(cx - r * 0.66f, cy - r * 0.43f, 3.2f)
            }

            "clock" -> {
                val dialOuter = Path().apply { addCircle(cx, cy, r * 0.94f, Path.Direction.CW) }
                draw3DShape(dialOuter, goldSatinPaint)
                canvas.drawCircle(cx, cy, r * 0.74f, graphiteBodyPaint)
                canvas.drawCircle(cx, cy, r * 0.74f, rimCopperStroke)

                val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3.2f
                    strokeCap = Paint.Cap.ROUND
                    color = Color.parseColor("#C47A48")
                }
                canvas.drawLine(cx, cy - r * 0.66f, cx, cy - r * 0.50f, tickPaint)
                canvas.drawLine(cx, cy + r * 0.50f, cx, cy + r * 0.66f, tickPaint)
                canvas.drawLine(cx - r * 0.66f, cy, cx - r * 0.50f, cy, tickPaint)
                canvas.drawLine(cx + r * 0.50f, cy, cx + r * 0.66f, cy, tickPaint)

                val hands = Path().apply {
                    moveTo(cx, cy - r * 0.48f)
                    lineTo(cx, cy)
                    lineTo(cx + r * 0.36f, cy + r * 0.22f)
                }
                val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 5.4f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    shader = goldBrightPaint.shader
                }
                canvas.drawPath(hands, handPaint)
                drawBlueNode(cx, cy, 4.4f)
            }

            "snaptube" -> {
                val ring = Path().apply { addCircle(cx, cy, r * 0.92f, Path.Direction.CW) }
                draw3DShape(ring, goldSatinPaint)
                canvas.drawCircle(cx, cy, r * 0.72f, graphiteBodyPaint)

                // Bold Snaptube download arrow with triangular play-arrow head + cradle tray
                val arrow = Path().apply {
                    moveTo(cx - r * 0.16f, cy - r * 0.52f)
                    lineTo(cx + r * 0.16f, cy - r * 0.52f)
                    lineTo(cx + r * 0.16f, cy + r * 0.04f)
                    lineTo(cx + r * 0.44f, cy + r * 0.04f)
                    lineTo(cx, cy + r * 0.46f)
                    lineTo(cx - r * 0.44f, cy + r * 0.04f)
                    lineTo(cx - r * 0.16f, cy + r * 0.04f)
                    close()
                }
                draw3DShape(arrow, goldBrightPaint)
                canvas.drawRoundRect(
                    RectF(cx - r * 0.42f, cy + r * 0.52f, cx + r * 0.42f, cy + r * 0.64f),
                    4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#38BDF8") }
                )
                drawBlueNode(cx, cy + r * 0.58f, 3.2f)
            }

            "andftp" -> {
                // Sculpted ceramic server/globe orb + dual bidirectional FTP transfer arrows
                val globe = Path().apply { addCircle(cx - r * 0.12f, cy - r * 0.10f, r * 0.66f, Path.Direction.CW) }
                draw3DShape(globe, graphiteBodyPaint, rimGoldStroke)
                canvas.drawOval(RectF(cx - r * 0.42f, cy - r * 0.74f, cx + r * 0.18f, cy + r * 0.54f), rimCopperStroke)
                canvas.drawLine(cx - r * 0.76f, cy - r * 0.10f, cx + r * 0.52f, cy - r * 0.10f, rimCopperStroke)

                val arrowRight = Path().apply {
                    moveTo(cx - r * 0.24f, cy + r * 0.22f)
                    lineTo(cx + r * 0.44f, cy + r * 0.22f)
                    lineTo(cx + r * 0.44f, cy + r * 0.06f)
                    lineTo(cx + r * 0.86f, cy + r * 0.34f)
                    lineTo(cx + r * 0.44f, cy + r * 0.62f)
                    lineTo(cx + r * 0.44f, cy + r * 0.46f)
                    lineTo(cx - r * 0.24f, cy + r * 0.46f)
                    close()
                }
                draw3DShape(arrowRight, goldBrightPaint)

                val arrowLeft = Path().apply {
                    moveTo(cx + r * 0.82f, cy - r * 0.46f)
                    lineTo(cx + r * 0.26f, cy - r * 0.46f)
                    lineTo(cx + r * 0.26f, cy - r * 0.62f)
                    lineTo(cx - r * 0.14f, cy - r * 0.34f)
                    lineTo(cx + r * 0.26f, cy - r * 0.06f)
                    lineTo(cx + r * 0.26f, cy - r * 0.22f)
                    lineTo(cx + r * 0.82f, cy - r * 0.22f)
                    close()
                }
                draw3DShape(arrowLeft, copperStructPaint)
                drawBlueNode(cx - r * 0.12f, cy - r * 0.10f, 3.8f)
            }

            "videoconverter" -> {
                // Dual curved conversion cycle arrows enclosing a sculptural 3D Play wedge
                val outerDisc = Path().apply { addCircle(cx, cy, r * 0.88f, Path.Direction.CW) }
                draw3DShape(outerDisc, graphiteBodyPaint, null)

                val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 8.5f
                    strokeCap = Paint.Cap.ROUND
                    shader = goldSatinPaint.shader
                }
                val arcCopperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 8.5f
                    strokeCap = Paint.Cap.ROUND
                    shader = copperStructPaint.shader
                }
                val arcRect = RectF(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f)
                canvas.drawArc(arcRect, -30f, 134f, false, arcPaint)
                canvas.drawArc(arcRect, 150f, 134f, false, arcCopperPaint)

                // Triangular conversion arrowheads at the ends of each arc
                val headBot = Path().apply {
                    moveTo(cx - r * 0.34f, cy + r * 0.74f)
                    lineTo(cx - r * 0.04f, cy + r * 0.62f)
                    lineTo(cx - r * 0.16f, cy + r * 0.92f)
                    close()
                }
                val headTop = Path().apply {
                    moveTo(cx + r * 0.34f, cy - r * 0.74f)
                    lineTo(cx + r * 0.04f, cy - r * 0.62f)
                    lineTo(cx + r * 0.16f, cy - r * 0.92f)
                    close()
                }
                canvas.drawPath(headBot, goldBrightPaint)
                canvas.drawPath(headTop, copperStructPaint)

                val play = Path().apply {
                    moveTo(cx - r * 0.22f, cy - r * 0.34f)
                    lineTo(cx + r * 0.38f, cy)
                    lineTo(cx - r * 0.22f, cy + r * 0.34f)
                    close()
                }
                draw3DShape(play, goldBrightPaint)
                drawBlueNode(cx + r * 0.54f, cy - r * 0.56f, 3.8f)
            }
        }
        return true
    }

    private fun drawSculptedMayaCeramicSymbol(
        canvas: Canvas,
        symbolBmp: Bitmap,
        canvasSize: Int
    ) {
        val drawSize = (canvasSize * 0.60f).toInt()
        val offset = (canvasSize - drawSize) / 2f
        val destRect = RectF(offset, offset, offset + drawSize, offset + drawSize)

        val scaledSrc = if (symbolBmp.width != drawSize || symbolBmp.height != drawSize) {
            Bitmap.createScaledBitmap(symbolBmp, drawSize, drawSize, true)
        } else {
            symbolBmp
        }
        val alphaMask = scaledSrc.extractAlpha()

        // 1. Deep contact shadow cast onto the dark graphite recessed well
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(235, 1, 2, 5)
            maskFilter = BlurMaskFilter(5.2f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(
            alphaMask,
            null,
            RectF(destRect.left + 3.2f, destRect.top + 5.0f, destRect.right + 3.2f, destRect.bottom + 5.0f),
            shadowPaint
        )

        // 2. Physical 3D extruded copper-bronze Z-thickness wall slices behind the sculpture
        val depthSteps = 4
        for (step in depthSteps downTo 1) {
            val frac = step.toFloat() / depthSteps.toFloat()
            val dx = 0.75f * step
            val dy = 1.05f * step
            val wallColor = Color.rgb(
                (148 - 68 * frac).toInt().coerceIn(32, 180),
                (82 - 42 * frac).toInt().coerceIn(18, 110),
                (42 - 22 * frac).toInt().coerceIn(10, 70)
            )
            val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                color = wallColor
            }
            canvas.drawBitmap(
                alphaMask,
                null,
                RectF(destRect.left + dx, destRect.top + dy, destRect.right + dx, destRect.bottom + dy),
                wallPaint
            )
        }

        // 3. Per-pixel 3D bevel + luminance normal-mapped satin ceramic & metallic gold sculpture
        val w = drawSize
        val h = drawSize
        val srcPixels = IntArray(w * h)
        scaledSrc.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val alphaArr = FloatArray(w * h)
        val lumaArr = FloatArray(w * h)
        for (i in srcPixels.indices) {
            val px = srcPixels[i]
            val a = Color.alpha(px) / 255f
            alphaArr[i] = a
            val lum = (0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px)) / 255f
            lumaArr[i] = lum
        }

        // Compute smooth 3D chamfered bevel heightfield from alpha boundary & internal luma relief
        val heightField = FloatArray(w * h)
        val bevelRadius = 3
        for (y in 0 until h) {
            val yOff = y * w
            for (x in 0 until w) {
                val idx = yOff + x
                val a = alphaArr[idx]
                if (a <= 0.02f) continue

                var minNeighbor = a
                for (ky in -bevelRadius..bevelRadius) {
                    val sy = y + ky
                    if (sy < 0 || sy >= h) {
                        minNeighbor = 0f
                        break
                    }
                    val syOff = sy * w
                    for (kx in -bevelRadius..bevelRadius) {
                        val sx = x + kx
                        val na = if (sx < 0 || sx >= w) 0f else alphaArr[syOff + sx]
                        if (na < minNeighbor) minNeighbor = na
                    }
                }
                val edgeBevel = minNeighbor.pow(0.48f)
                val internalRelief = 0.72f + 0.28f * lumaArr[idx]
                heightField[idx] = (edgeBevel * 0.65f + internalRelief * 0.35f) * a
            }
        }

        val outPixels = IntArray(w * h)
        // Normalized top-left directional key light + half-vector for satin ceramic specular
        val lx = -0.54f
        val ly = -0.66f
        val lz = 0.52f
        val hx = lx
        val hy = ly
        val hz = lz + 1.0f
        val hLen = sqrt(hx * hx + hy * hy + hz * hz)
        val nhx = hx / hLen
        val nhy = hy / hLen
        val nhz = hz / hLen

        for (y in 0 until h) {
            val yOff = y * w
            val yPrev = max(0, y - 1) * w
            val yNext = min(h - 1, y + 1) * w
            for (x in 0 until w) {
                val idx = yOff + x
                val a = alphaArr[idx]
                if (a <= 0.02f) {
                    outPixels[idx] = Color.TRANSPARENT
                    continue
                }

                val hL = heightField[yOff + max(0, x - 1)]
                val hR = heightField[yOff + min(w - 1, x + 1)]
                val hU = heightField[yPrev + x]
                val hD = heightField[yNext + x]

                val nx = (hL - hR) * 2.4f
                val ny = (hU - hD) * 2.4f
                val nz = 1.0f
                val nLen = sqrt(nx * nx + ny * ny + nz * nz)
                val unx = nx / nLen
                val uny = ny / nLen
                val unz = nz / nLen

                val diffuse = max(0.18f, unx * lx + uny * ly + unz * lz)
                val nDotH = max(0f, unx * nhx + uny * nhy + unz * nhz)
                val satinSpec = nDotH.pow(12f) * 0.36f
                val metalEdgeSpec = nDotH.pow(34f) * 0.44f

                val lum = lumaArr[idx]
                val bevel = heightField[idx]

                // Distinguish deep dark interior details (graphite recess) vs raised golden-honey ceramic
                val isDarkRecess = lum < 0.22f && bevel > 0.45f
                val rBase: Float
                val gBase: Float
                val bBase: Float
                if (isDarkRecess) {
                    // Dark graphite technological ceramic inlay for internal lens/cutout contrast
                    rBase = 24f + diffuse * 22f + metalEdgeSpec * 90f
                    gBase = 30f + diffuse * 24f + metalEdgeSpec * 70f
                    bBase = 44f + diffuse * 32f + metalEdgeSpec * 40f
                } else {
                    // Golden-honey satin ceramic transitioning to warm copper on shadow bevels
                    val shadowWarmth = (1f - diffuse).coerceIn(0f, 1f)
                    val goldR = 245f * (1f - shadowWarmth * 0.25f) + 196f * (shadowWarmth * 0.25f)
                    val goldG = 185f * (1f - shadowWarmth * 0.38f) + 118f * (shadowWarmth * 0.38f)
                    val goldB = 66f * (1f - shadowWarmth * 0.30f) + 68f * (shadowWarmth * 0.30f)

                    val lightScale = 0.56f + diffuse * 0.54f
                    rBase = goldR * lightScale + (satinSpec * 255f + metalEdgeSpec * 255f) * 0.85f
                    gBase = goldG * lightScale + (satinSpec * 228f + metalEdgeSpec * 242f) * 0.80f
                    bBase = goldB * lightScale + (satinSpec * 158f + metalEdgeSpec * 185f) * 0.65f
                }

                outPixels[idx] = Color.argb(
                    (a * 255f).toInt().coerceIn(0, 255),
                    rBase.toInt().coerceIn(0, 255),
                    gBase.toInt().coerceIn(0, 255),
                    bBase.toInt().coerceIn(0, 255)
                )
            }
        }

        val sculpted = Bitmap.createBitmap(outPixels, w, h, Bitmap.Config.ARGB_8888)
        canvas.drawBitmap(sculpted, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        // 4. Tiny focal electric-blue technological pinpoint node on lower-right inner well corner
        val nodeX = canvasSize * 0.745f
        val nodeY = canvasSize * 0.745f
        val nodeGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                nodeX,
                nodeY,
                6.0f,
                intArrayOf(
                    Color.argb(230, 56, 189, 248),
                    Color.argb(120, 37, 99, 235),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(nodeX, nodeY, 6.0f, nodeGlow)
        val nodeCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0F7FF")
        }
        canvas.drawCircle(nodeX, nodeY, 2.0f, nodeCore)

        sculpted.recycle()
        if (scaledSrc !== symbolBmp && !scaledSrc.isRecycled) {
            scaledSrc.recycle()
        }
        alphaMask.recycle()
    }

    private fun drawMayaCeramicFramedArtwork(
        canvas: Canvas,
        artworkBmp: Bitmap,
        canvasSize: Int
    ) {
        val inset = canvasSize * 0.185f
        val innerRect = RectF(inset, inset, canvasSize - inset, canvasSize - inset)
        val innerRadius = canvasSize * 0.14f
        val clipPath = Path().apply {
            addRoundRect(innerRect, innerRadius, innerRadius, Path.Direction.CW)
        }

        // Copper 3D extruded bevel base underneath inlaid tile
        val copperBase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6E3B1D")
        }
        val baseRect = RectF(innerRect).apply { offset(1.8f, 2.6f) }
        canvas.drawRoundRect(baseRect, innerRadius, innerRadius, copperBase)

        canvas.save()
        canvas.clipPath(clipPath)
        val warmCeramicMatrix = ColorMatrix().apply {
            setSaturation(0.32f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.18f, 0f, 0f, 0f, 16f,
                        0f, 0.98f, 0f, 0f, 7f,
                        0f, 0f, 0.60f, 0f, -8f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(warmCeramicMatrix)
        }
        canvas.drawBitmap(artworkBmp, null, innerRect, artPaint)

        // Subtle satin ceramic top-left sheen across the inlaid artwork
        val satinOverlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                innerRect.left,
                innerRect.top,
                innerRect.right,
                innerRect.bottom,
                intArrayOf(
                    Color.argb(46, 255, 232, 166),
                    Color.argb(0, 255, 232, 166),
                    Color.argb(65, 11, 14, 20)
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(innerRect, satinOverlay)
        canvas.restore()

        val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.0f
            shader = LinearGradient(
                innerRect.left,
                innerRect.top,
                innerRect.right,
                innerRect.bottom,
                intArrayOf(
                    Color.parseColor("#FFE49E"),
                    Color.parseColor("#F5B942"),
                    Color.parseColor("#C47A48")
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(clipPath, innerRimPaint)

        // Tiny focal electric-blue technological node
        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
        }
        canvas.drawCircle(innerRect.right - 7f, innerRect.bottom - 7f, 2.2f, nodePaint)
    }

    private fun drawLunaOrbitalWisps(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Int,
        profile: LunaLightProfile
    ) {
        val tiltDeg = if (profile.angleDeg > 0f) -24f else 22f
        val rx1 = size * 0.43f
        val ry1 = size * 0.17f
        val rx2 = size * 0.39f
        val ry2 = size * 0.145f

        val wispRect1 = RectF(cx - rx1, cy - ry1, cx + rx1, cy + ry1)
        val wispRect2 = RectF(cx - rx2, cy - ry2, cx + rx2, cy + ry2)

        val wispCoolShader = LinearGradient(
            cx - rx1, cy, cx + rx1, cy,
            intArrayOf(
                Color.argb(0, 57, 139, 255),
                Color.argb(235, 0, 229, 255),
                Color.argb(240, 228, 250, 255),
                Color.argb(225, 255, 180, 92),
                Color.argb(0, 196, 122, 72)
            ),
            floatArrayOf(0f, 0.25f, 0.54f, 0.80f, 1f),
            Shader.TileMode.CLAMP
        )
        val wispWarmShader = LinearGradient(
            cx + rx2, cy, cx - rx2, cy,
            intArrayOf(
                Color.argb(0, 196, 122, 72),
                Color.argb(215, 255, 180, 92),
                Color.argb(145, 0, 229, 255),
                Color.argb(0, 57, 139, 255)
            ),
            floatArrayOf(0f, 0.34f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )

        val wispHalo1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.4f
            strokeCap = Paint.Cap.ROUND
            shader = wispCoolShader
            maskFilter = BlurMaskFilter(3.4f, BlurMaskFilter.Blur.NORMAL)
        }
        val wispCore1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.35f
            strokeCap = Paint.Cap.ROUND
            shader = wispCoolShader
        }
        val wispCore2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.05f
            strokeCap = Paint.Cap.ROUND
            shader = wispWarmShader
        }

        canvas.save()
        canvas.rotate(tiltDeg, cx, cy)
        val arc1 = Path().apply { addArc(wispRect1, -18f, 228f) }
        val arc2 = Path().apply { addArc(wispRect2, 148f, 178f) }
        canvas.drawPath(arc1, wispHalo1)
        canvas.drawPath(arc1, wispCore1)
        canvas.drawPath(arc2, wispCore2)
        canvas.restore()
    }

    private fun drawLunaLightSparks(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Int,
        profile: LunaLightProfile
    ) {
        val tiltDeg = if (profile.angleDeg > 0f) -24f else 22f
        val rx = size * 0.41f
        val ry = size * 0.16f

        fun drawPinpointFlare(px: Float, py: Float, radius: Float, color: Int) {
            val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.argb(145, Color.red(color), Color.green(color), Color.blue(color))
                maskFilter = BlurMaskFilter(4.2f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(px, py, radius * 2.5f, halo)
            val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            canvas.drawCircle(px, py, radius, core)
        }

        canvas.save()
        canvas.rotate(tiltDeg, cx, cy)
        drawPinpointFlare(cx + rx * 0.94f, cy - ry * 0.22f, 2.0f, Color.parseColor("#FFE6B8"))
        drawPinpointFlare(cx - rx * 0.92f, cy + ry * 0.26f, 1.9f, Color.parseColor("#E4FAFF"))
        drawPinpointFlare(cx + rx * 0.56f, cy + ry * 0.84f, 1.45f, Color.parseColor("#FFB45C"))
        drawPinpointFlare(cx - rx * 0.52f, cy - ry * 0.82f, 1.4f, Color.parseColor("#00E5FF"))
        canvas.restore()
    }

    private fun drawLunaCuratedVectorSymbol(
        canvas: Canvas,
        pkg: String,
        cx: Float,
        cy: Float,
        span: Float,
        profile: LunaLightProfile
    ): Boolean {
        val kind = when {
            pkg == "com.android.chrome" || pkg.contains(".chrome") -> "chrome"
            pkg == "com.whatsapp" || pkg.contains("whatsapp") -> "whatsapp"
            pkg.contains("youtube") -> "youtube"
            pkg.contains("spotify") -> "spotify"
            pkg.contains("instagram") -> "instagram"
            pkg.contains("discord") -> "discord"
            pkg == "com.nu.production" || pkg.contains("nubank") -> "nubank"
            pkg.contains("android.gm") || pkg.contains("gmail") -> "gmail"
            pkg.contains("googlequicksearchbox") -> "google"
            pkg.contains("android.vending") -> "playstore"
            pkg.contains("apps.maps") -> "maps"
            pkg.contains("mercadopago") -> "mercadopago"
            pkg.contains("mercadolibre") || pkg.contains("mercadolivre") -> "mercadolivre"
            pkg.contains("inss") -> "inss"
            pkg.contains("kodi") -> "kodi"
            pkg.contains("rarlab") -> "rar"
            pkg.contains("snaptube") -> "snaptube"
            pkg.contains("xplayer") -> "xplayer"
            pkg.contains("andftp") -> "andftp"
            pkg.contains("videoconverter") || pkg.contains("video.converter") -> "videoconverter"
            pkg.contains("documentsui") || pkg.contains("filemanager") ||
                pkg.contains("myfiles") || pkg.contains("arquivos") -> "files"
            pkg.contains("dialer") || pkg.contains("telecom") || pkg.contains("phone") -> "phone"
            pkg.contains("messaging") || pkg.contains("mms") || pkg.contains("sms") -> "messages"
            pkg.contains("camera") -> "camera"
            pkg.contains("gallery") || pkg.contains("photos") -> "gallery"
            pkg.contains("settings") -> "settings"
            pkg.contains("clock") || pkg.contains("alarm") || pkg.contains("deskclock") -> "clock"
            pkg.contains("calculator") -> "calculator"
            pkg.contains("calendar") -> "calendar"
            else -> return false
        }

        val r = span * 0.5f
        val strokeBase = (span * 0.025f).coerceIn(2.6f, 3.8f)

        val dualLightShader = LinearGradient(
            cx - r * 0.85f, cy - r * 0.85f,
            cx + r * 0.85f, cy + r * 0.85f,
            intArrayOf(
                Color.parseColor("#398BFF"),
                Color.parseColor("#00E5FF"),
                Color.parseColor("#E4FAFF"),
                Color.parseColor("#FFB45C"),
                Color.parseColor("#C47A48")
            ),
            floatArrayOf(0f, 0.42f, 0.68f, 0.86f, 1f),
            Shader.TileMode.CLAMP
        )

        val dualAuraShader = LinearGradient(
            cx - r * 0.85f, cy - r * 0.85f,
            cx + r * 0.85f, cy + r * 0.85f,
            intArrayOf(
                Color.argb(120, 57, 139, 255),
                Color.argb(150, 0, 229, 255),
                Color.argb(140, 255, 180, 92),
                Color.argb(115, 196, 122, 72)
            ),
            floatArrayOf(0f, 0.48f, 0.82f, 1f),
            Shader.TileMode.CLAMP
        )

        val wideAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeBase * 3.4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            shader = dualAuraShader
            maskFilter = BlurMaskFilter(8.2f, BlurMaskFilter.Blur.NORMAL)
        }
        val cyanHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeBase * 2.0f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            shader = dualAuraShader
            maskFilter = BlurMaskFilter(4.0f, BlurMaskFilter.Blur.NORMAL)
        }
        val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeBase
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            shader = dualLightShader
        }
        val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeBase * 0.94f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            shader = dualLightShader
        }
        val brightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeBase * 0.96f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#E4FAFF")
        }
        val coreFilamentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeBase * 0.40f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(190, 240, 253, 255)
        }

        val warmLightColor = if (profile.mode == LunaLightAccentMode.COPPER_DETAIL) {
            Color.parseColor("#C47A48")
        } else {
            Color.parseColor("#FFB45C")
        }
        val warmHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeBase * 2.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(120, Color.red(warmLightColor), Color.green(warmLightColor), Color.blue(warmLightColor))
            maskFilter = BlurMaskFilter(4.4f, BlurMaskFilter.Blur.NORMAL)
        }
        val warmStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeBase * 0.92f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = warmLightColor
        }

        fun drawLightPath(path: Path, primary: Paint = cyanPaint, warmDetail: Boolean = false) {
            if (warmDetail) {
                canvas.drawPath(path, warmHaloPaint)
                canvas.drawPath(path, warmStrokePaint)
                return
            }
            canvas.drawPath(path, wideAuraPaint)
            canvas.drawPath(path, cyanHaloPaint)
            canvas.drawPath(path, primary)
            canvas.drawPath(path, coreFilamentPaint)
        }

        fun drawLightPoint(px: Float, py: Float, radius: Float = strokeBase * 0.65f, warm: Boolean = true) {
            val c = if (warm) warmLightColor else Color.parseColor("#E4FAFF")
            val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(140, Color.red(c), Color.green(c), Color.blue(c))
                maskFilter = BlurMaskFilter(4.2f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(px, py, radius * 2.2f, halo)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c }
            canvas.drawCircle(px, py, radius, dot)
        }

        when (kind) {
            "chrome" -> {
                val outer = Path().apply { addCircle(cx, cy, r * 0.88f, Path.Direction.CW) }
                val innerR = r * 0.34f
                val inner = Path().apply { addCircle(cx, cy, innerR, Path.Direction.CW) }
                val spokes = Path().apply {
                    moveTo(cx, cy - innerR)
                    lineTo(cx + r * 0.80f, cy - innerR)
                    moveTo(cx - innerR * 0.86f, cy + innerR * 0.50f)
                    lineTo(cx - r * 0.69f, cy - r * 0.40f)
                    moveTo(cx + innerR * 0.86f, cy + innerR * 0.50f)
                    lineTo(cx + r * 0.02f, cy + r * 0.87f)
                }
                drawLightPath(outer, cyanPaint)
                drawLightPath(spokes, bluePaint)
                drawLightPath(inner, brightPaint)
            }
            "whatsapp" -> {
                val bubble = Path().apply {
                    val rr = r * 0.86f
                    addArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), 146f, 304f)
                    lineTo(cx - rr * 0.94f, cy + rr * 0.94f)
                    close()
                }
                val phone = Path().apply {
                    moveTo(cx - r * 0.30f, cy - r * 0.34f)
                    quadTo(cx - r * 0.42f, cy + r * 0.02f, cx - r * 0.08f, cy + r * 0.28f)
                    quadTo(cx + r * 0.16f, cy + r * 0.44f, cx + r * 0.36f, cy + r * 0.26f)
                    lineTo(cx + r * 0.26f, cy + r * 0.12f)
                    lineTo(cx + r * 0.12f, cy + r * 0.18f)
                    quadTo(cx - r * 0.06f, cy + r * 0.08f, cx - r * 0.16f, cy - r * 0.12f)
                    lineTo(cx - r * 0.12f, cy - r * 0.24f)
                    close()
                }
                drawLightPath(bubble, cyanPaint)
                drawLightPath(phone, brightPaint)
            }
            "youtube" -> {
                val screen = Path().apply {
                    addRoundRect(
                        RectF(cx - r * 0.90f, cy - r * 0.60f, cx + r * 0.90f, cy + r * 0.60f),
                        r * 0.28f,
                        r * 0.28f,
                        Path.Direction.CW
                    )
                }
                val play = Path().apply {
                    moveTo(cx - r * 0.18f, cy - r * 0.27f)
                    lineTo(cx + r * 0.34f, cy)
                    lineTo(cx - r * 0.18f, cy + r * 0.27f)
                    close()
                }
                drawLightPath(screen, bluePaint)
                drawLightPath(play, brightPaint)
            }
            "spotify" -> {
                val ring = Path().apply { addCircle(cx, cy, r * 0.88f, Path.Direction.CW) }
                val w1 = Path().apply {
                    moveTo(cx - r * 0.45f, cy - r * 0.22f)
                    quadTo(cx + r * 0.02f, cy - r * 0.40f, cx + r * 0.48f, cy - r * 0.14f)
                }
                val w2 = Path().apply {
                    moveTo(cx - r * 0.39f, cy + r * 0.05f)
                    quadTo(cx + r * 0.02f, cy - r * 0.11f, cx + r * 0.41f, cy + r * 0.12f)
                }
                val w3 = Path().apply {
                    moveTo(cx - r * 0.31f, cy + r * 0.31f)
                    quadTo(cx + r * 0.02f, cy + r * 0.18f, cx + r * 0.33f, cy + r * 0.35f)
                }
                drawLightPath(ring, cyanPaint)
                drawLightPath(w1, brightPaint)
                drawLightPath(w2, cyanPaint)
                drawLightPath(w3, bluePaint, warmDetail = profile.mode == LunaLightAccentMode.COPPER_DETAIL)
            }
            "instagram" -> {
                val frame = Path().apply {
                    addRoundRect(
                        RectF(cx - r * 0.82f, cy - r * 0.82f, cx + r * 0.82f, cy + r * 0.82f),
                        r * 0.36f,
                        r * 0.36f,
                        Path.Direction.CW
                    )
                }
                val lens = Path().apply { addCircle(cx, cy, r * 0.36f, Path.Direction.CW) }
                drawLightPath(frame, cyanPaint)
                drawLightPath(lens, brightPaint)
                drawLightPoint(cx + r * 0.45f, cy - r * 0.45f, strokeBase * 0.72f, warm = true)
            }
            "discord" -> {
                val visor = Path().apply {
                    moveTo(cx - r * 0.66f, cy - r * 0.46f)
                    quadTo(cx, cy - r * 0.70f, cx + r * 0.66f, cy - r * 0.46f)
                    quadTo(cx + r * 0.88f, cy + r * 0.06f, cx + r * 0.72f, cy + r * 0.54f)
                    lineTo(cx + r * 0.32f, cy + r * 0.40f)
                    quadTo(cx, cy + r * 0.52f, cx - r * 0.32f, cy + r * 0.40f)
                    lineTo(cx - r * 0.72f, cy + r * 0.54f)
                    quadTo(cx - r * 0.88f, cy + r * 0.06f, cx - r * 0.66f, cy - r * 0.46f)
                    close()
                }
                val eyes = Path().apply {
                    addCircle(cx - r * 0.29f, cy + r * 0.04f, r * 0.14f, Path.Direction.CW)
                    addCircle(cx + r * 0.29f, cy + r * 0.04f, r * 0.14f, Path.Direction.CW)
                }
                drawLightPath(visor, cyanPaint)
                drawLightPath(eyes, brightPaint)
            }
            "nubank" -> {
                val nStroke = Path().apply {
                    moveTo(cx - r * 0.64f, cy + r * 0.54f)
                    lineTo(cx - r * 0.64f, cy - r * 0.42f)
                    quadTo(cx - r * 0.64f, cy - r * 0.62f, cx - r * 0.44f, cy - r * 0.46f)
                    lineTo(cx + r * 0.12f, cy + r * 0.20f)
                    lineTo(cx + r * 0.12f, cy - r * 0.52f)
                }
                val uStroke = Path().apply {
                    moveTo(cx + r * 0.64f, cy - r * 0.52f)
                    lineTo(cx + r * 0.64f, cy + r * 0.42f)
                    quadTo(cx + r * 0.64f, cy + r * 0.62f, cx + r * 0.44f, cy + r * 0.46f)
                    lineTo(cx - r * 0.12f, cy - r * 0.20f)
                    lineTo(cx - r * 0.12f, cy + r * 0.52f)
                }
                drawLightPath(nStroke, cyanPaint)
                drawLightPath(uStroke, brightPaint)
            }
            "gmail" -> {
                val outer = Path().apply {
                    moveTo(cx - r * 0.84f, cy + r * 0.54f)
                    lineTo(cx - r * 0.84f, cy - r * 0.38f)
                    quadTo(cx - r * 0.84f, cy - r * 0.60f, cx - r * 0.62f, cy - r * 0.46f)
                    lineTo(cx, cy + r * 0.04f)
                    lineTo(cx + r * 0.62f, cy - r * 0.46f)
                    quadTo(cx + r * 0.84f, cy - r * 0.60f, cx + r * 0.84f, cy - r * 0.38f)
                    lineTo(cx + r * 0.84f, cy + r * 0.54f)
                }
                val base = Path().apply {
                    moveTo(cx - r * 0.52f, cy - r * 0.06f)
                    lineTo(cx - r * 0.52f, cy + r * 0.54f)
                    moveTo(cx + r * 0.52f, cy - r * 0.06f)
                    lineTo(cx + r * 0.52f, cy + r * 0.54f)
                }
                drawLightPath(outer, cyanPaint)
                drawLightPath(base, brightPaint)
            }
            "google" -> {
                val gArc = Path().apply {
                    addArc(RectF(cx - r * 0.82f, cy - r * 0.82f, cx + r * 0.82f, cy + r * 0.82f), -40f, -275f)
                }
                val bar = Path().apply {
                    moveTo(cx + r * 0.04f, cy)
                    lineTo(cx + r * 0.80f, cy)
                    lineTo(cx + r * 0.80f, cy + r * 0.32f)
                }
                drawLightPath(gArc, cyanPaint)
                drawLightPath(bar, brightPaint)
            }
            "playstore" -> {
                val tri = Path().apply {
                    moveTo(cx - r * 0.64f, cy - r * 0.80f)
                    lineTo(cx + r * 0.80f, cy)
                    lineTo(cx - r * 0.64f, cy + r * 0.80f)
                    close()
                }
                val prism = Path().apply {
                    moveTo(cx - r * 0.64f, cy - r * 0.80f)
                    lineTo(cx + r * 0.34f, cy + r * 0.25f)
                    moveTo(cx - r * 0.64f, cy + r * 0.80f)
                    lineTo(cx + r * 0.34f, cy - r * 0.25f)
                }
                drawLightPath(tri, cyanPaint)
                drawLightPath(prism, brightPaint)
            }
            "maps" -> {
                val pin = Path().apply {
                    addArc(RectF(cx - r * 0.62f, cy - r * 0.86f, cx + r * 0.62f, cy + r * 0.38f), 155f, 230f)
                    lineTo(cx, cy + r * 0.88f)
                    close()
                }
                val hole = Path().apply { addCircle(cx, cy - r * 0.24f, r * 0.22f, Path.Direction.CW) }
                drawLightPath(pin, cyanPaint)
                drawLightPath(hole, brightPaint, warmDetail = profile.mode == LunaLightAccentMode.AMBER_SPARK)
            }
            "mercadopago", "mercadolivre" -> {
                val oval = Path().apply {
                    addOval(RectF(cx - r * 0.88f, cy - r * 0.58f, cx + r * 0.88f, cy + r * 0.58f), Path.Direction.CW)
                }
                val clasp = Path().apply {
                    moveTo(cx - r * 0.58f, cy + r * 0.04f)
                    quadTo(cx - r * 0.18f, cy - r * 0.28f, cx + r * 0.14f, cy + r * 0.02f)
                    moveTo(cx + r * 0.58f, cy - r * 0.04f)
                    quadTo(cx + r * 0.18f, cy + r * 0.28f, cx - r * 0.14f, cy - r * 0.02f)
                }
                val accentTick = Path().apply {
                    moveTo(cx - r * 0.18f, cy + r * 0.18f)
                    lineTo(cx + r * 0.18f, cy + r * 0.18f)
                }
                drawLightPath(oval, if (kind == "mercadopago") bluePaint else cyanPaint)
                drawLightPath(clasp, brightPaint)
                drawLightPath(accentTick, cyanPaint, warmDetail = true)
            }
            "inss" -> {
                val shield = Path().apply {
                    addRoundRect(
                        RectF(cx - r * 0.80f, cy - r * 0.62f, cx + r * 0.80f, cy + r * 0.62f),
                        r * 0.22f,
                        r * 0.22f,
                        Path.Direction.CW
                    )
                }
                val glyph = Path().apply {
                    moveTo(cx - r * 0.50f, cy + r * 0.26f)
                    lineTo(cx - r * 0.50f, cy - r * 0.26f)
                    moveTo(cx - r * 0.22f, cy + r * 0.26f)
                    lineTo(cx - r * 0.22f, cy - r * 0.26f)
                    lineTo(cx + r * 0.06f, cy + r * 0.26f)
                    lineTo(cx + r * 0.06f, cy - r * 0.26f)
                    moveTo(cx + r * 0.52f, cy - r * 0.26f)
                    lineTo(cx + r * 0.26f, cy - r * 0.26f)
                    lineTo(cx + r * 0.26f, cy)
                    lineTo(cx + r * 0.52f, cy)
                    lineTo(cx + r * 0.52f, cy + r * 0.26f)
                    lineTo(cx + r * 0.26f, cy + r * 0.26f)
                }
                drawLightPath(shield, bluePaint)
                drawLightPath(glyph, brightPaint)
            }
            "kodi" -> {
                val diamond = Path().apply {
                    moveTo(cx, cy - r * 0.86f)
                    lineTo(cx + r * 0.86f, cy)
                    lineTo(cx, cy + r * 0.86f)
                    lineTo(cx - r * 0.86f, cy)
                    close()
                }
                val facets = Path().apply {
                    moveTo(cx - r * 0.42f, cy - r * 0.42f)
                    lineTo(cx + r * 0.42f, cy + r * 0.42f)
                    moveTo(cx - r * 0.42f, cy + r * 0.42f)
                    lineTo(cx + r * 0.42f, cy - r * 0.42f)
                    moveTo(cx - r * 0.08f, cy - r * 0.20f)
                    lineTo(cx + r * 0.20f, cy)
                    lineTo(cx - r * 0.08f, cy + r * 0.20f)
                }
                drawLightPath(diamond, cyanPaint)
                drawLightPath(facets, brightPaint)
            }
            "rar" -> {
                val books = Path().apply {
                    addRoundRect(RectF(cx - r * 0.76f, cy - r * 0.74f, cx + r * 0.76f, cy - r * 0.32f), r * 0.10f, r * 0.10f, Path.Direction.CW)
                    addRoundRect(RectF(cx - r * 0.76f, cy - r * 0.21f, cx + r * 0.76f, cy + r * 0.21f), r * 0.10f, r * 0.10f, Path.Direction.CW)
                    addRoundRect(RectF(cx - r * 0.76f, cy + r * 0.32f, cx + r * 0.76f, cy + r * 0.74f), r * 0.10f, r * 0.10f, Path.Direction.CW)
                }
                val belt = Path().apply {
                    addRect(RectF(cx - r * 0.16f, cy - r * 0.80f, cx + r * 0.16f, cy + r * 0.80f), Path.Direction.CW)
                }
                drawLightPath(books, cyanPaint)
                drawLightPath(belt, brightPaint, warmDetail = profile.mode == LunaLightAccentMode.COPPER_DETAIL)
            }
            "snaptube" -> {
                val ring = Path().apply {
                    addArc(RectF(cx - r * 0.82f, cy - r * 0.82f, cx + r * 0.82f, cy + r * 0.82f), 25f, 310f)
                }
                val arrow = Path().apply {
                    moveTo(cx, cy - r * 0.50f)
                    lineTo(cx, cy + r * 0.42f)
                    moveTo(cx - r * 0.36f, cy + r * 0.08f)
                    lineTo(cx, cy + r * 0.46f)
                    lineTo(cx + r * 0.36f, cy + r * 0.08f)
                }
                drawLightPath(ring, bluePaint)
                drawLightPath(arrow, brightPaint)
            }
            "xplayer" -> {
                val frame = Path().apply {
                    addRoundRect(RectF(cx - r * 0.82f, cy - r * 0.68f, cx + r * 0.82f, cy + r * 0.68f), r * 0.22f, r * 0.22f, Path.Direction.CW)
                }
                val xPlay = Path().apply {
                    moveTo(cx - r * 0.36f, cy - r * 0.30f)
                    lineTo(cx + r * 0.34f, cy + r * 0.30f)
                    moveTo(cx + r * 0.34f, cy - r * 0.30f)
                    lineTo(cx - r * 0.36f, cy + r * 0.30f)
                }
                drawLightPath(frame, cyanPaint)
                drawLightPath(xPlay, brightPaint)
            }
            "andftp" -> {
                val globe = Path().apply {
                    addCircle(cx - r * 0.16f, cy - r * 0.12f, r * 0.52f, Path.Direction.CW)
                }
                val arrows = Path().apply {
                    moveTo(cx - r * 0.18f, cy + r * 0.56f)
                    lineTo(cx + r * 0.74f, cy + r * 0.56f)
                    lineTo(cx + r * 0.52f, cy + r * 0.36f)
                    moveTo(cx + r * 0.74f, cy + r * 0.20f)
                    lineTo(cx - r * 0.04f, cy + r * 0.20f)
                    lineTo(cx + r * 0.18f, cy)
                }
                drawLightPath(globe, cyanPaint)
                drawLightPath(arrows, brightPaint)
            }
            "videoconverter" -> {
                val loop = Path().apply {
                    addArc(RectF(cx - r * 0.80f, cy - r * 0.80f, cx + r * 0.80f, cy + r * 0.80f), -20f, 155f)
                    addArc(RectF(cx - r * 0.80f, cy - r * 0.80f, cx + r * 0.80f, cy + r * 0.80f), 160f, 155f)
                }
                val play = Path().apply {
                    moveTo(cx - r * 0.20f, cy - r * 0.28f)
                    lineTo(cx + r * 0.30f, cy)
                    lineTo(cx - r * 0.20f, cy + r * 0.28f)
                    close()
                }
                drawLightPath(loop, cyanPaint)
                drawLightPath(play, brightPaint)
            }
            "files" -> {
                val folder = Path().apply {
                    moveTo(cx - r * 0.82f, cy - r * 0.52f)
                    lineTo(cx - r * 0.18f, cy - r * 0.52f)
                    lineTo(cx, cy - r * 0.30f)
                    lineTo(cx + r * 0.82f, cy - r * 0.30f)
                    lineTo(cx + r * 0.82f, cy + r * 0.60f)
                    lineTo(cx - r * 0.82f, cy + r * 0.60f)
                    close()
                }
                val sheet = Path().apply {
                    moveTo(cx - r * 0.44f, cy + r * 0.06f)
                    lineTo(cx + r * 0.44f, cy + r * 0.06f)
                    moveTo(cx - r * 0.44f, cy + r * 0.30f)
                    lineTo(cx + r * 0.22f, cy + r * 0.30f)
                }
                drawLightPath(folder, cyanPaint)
                drawLightPath(sheet, brightPaint, warmDetail = profile.mode == LunaLightAccentMode.COPPER_DETAIL)
            }
            "phone" -> {
                val handset = Path().apply {
                    moveTo(cx - r * 0.56f, cy - r * 0.58f)
                    lineTo(cx - r * 0.26f, cy - r * 0.66f)
                    lineTo(cx - r * 0.14f, cy - r * 0.30f)
                    lineTo(cx - r * 0.30f, cy - r * 0.14f)
                    quadTo(cx - r * 0.10f, cy + r * 0.16f, cx + r * 0.18f, cy + r * 0.30f)
                    lineTo(cx + r * 0.34f, cy + r * 0.14f)
                    lineTo(cx + r * 0.68f, cy + r * 0.28f)
                    lineTo(cx + r * 0.60f, cy + r * 0.58f)
                    quadTo(cx + r * 0.08f, cy + r * 0.72f, cx - r * 0.44f, cy + r * 0.20f)
                    quadTo(cx - r * 0.72f, cy - r * 0.16f, cx - r * 0.56f, cy - r * 0.58f)
                    close()
                }
                val wave = Path().apply {
                    addArc(RectF(cx - r * 0.04f, cy - r * 0.66f, cx + r * 0.68f, cy + r * 0.06f), -85f, 80f)
                }
                drawLightPath(handset, cyanPaint)
                drawLightPath(wave, brightPaint, warmDetail = true)
            }
            "messages" -> {
                val bubble = Path().apply {
                    moveTo(cx - r * 0.78f, cy - r * 0.58f)
                    lineTo(cx + r * 0.78f, cy - r * 0.58f)
                    lineTo(cx + r * 0.78f, cy + r * 0.34f)
                    lineTo(cx - r * 0.26f, cy + r * 0.34f)
                    lineTo(cx - r * 0.78f, cy + r * 0.70f)
                    close()
                }
                val lines = Path().apply {
                    moveTo(cx - r * 0.42f, cy - r * 0.22f)
                    lineTo(cx + r * 0.42f, cy - r * 0.22f)
                    moveTo(cx - r * 0.42f, cy + r * 0.02f)
                    lineTo(cx + r * 0.20f, cy + r * 0.02f)
                }
                drawLightPath(bubble, cyanPaint)
                drawLightPath(lines, brightPaint)
            }
            "camera" -> {
                val body = Path().apply {
                    moveTo(cx - r * 0.84f, cy - r * 0.40f)
                    lineTo(cx - r * 0.38f, cy - r * 0.40f)
                    lineTo(cx - r * 0.22f, cy - r * 0.62f)
                    lineTo(cx + r * 0.22f, cy - r * 0.62f)
                    lineTo(cx + r * 0.38f, cy - r * 0.40f)
                    lineTo(cx + r * 0.84f, cy - r * 0.40f)
                    lineTo(cx + r * 0.84f, cy + r * 0.60f)
                    lineTo(cx - r * 0.84f, cy + r * 0.60f)
                    close()
                }
                val aperture = Path().apply {
                    addCircle(cx, cy + r * 0.10f, r * 0.30f, Path.Direction.CW)
                }
                drawLightPath(body, cyanPaint)
                drawLightPath(aperture, brightPaint)
                drawLightPoint(cx + r * 0.56f, cy - r * 0.16f, strokeBase * 0.72f, warm = true)
            }
            "gallery" -> {
                val frame = Path().apply {
                    addRoundRect(RectF(cx - r * 0.80f, cy - r * 0.66f, cx + r * 0.80f, cy + r * 0.66f), r * 0.18f, r * 0.18f, Path.Direction.CW)
                }
                val horizon = Path().apply {
                    moveTo(cx - r * 0.62f, cy + r * 0.40f)
                    lineTo(cx - r * 0.16f, cy - r * 0.04f)
                    lineTo(cx + r * 0.16f, cy + r * 0.24f)
                    lineTo(cx + r * 0.40f, cy + r * 0.02f)
                    lineTo(cx + r * 0.66f, cy + r * 0.40f)
                }
                drawLightPath(frame, cyanPaint)
                drawLightPath(horizon, brightPaint)
                drawLightPoint(cx + r * 0.40f, cy - r * 0.32f, strokeBase * 0.75f, warm = true)
            }
            "settings" -> {
                val teeth = Path().apply {
                    for (i in 0 until 8) {
                        val a0 = Math.toRadians((i * 45 - 11).toDouble())
                        val a1 = Math.toRadians((i * 45 + 11).toDouble())
                        val a2 = Math.toRadians((i * 45 + 19).toDouble())
                        val a3 = Math.toRadians((i * 45 + 34).toDouble())
                        val ro = r * 0.86f
                        val ri = r * 0.64f
                        val x0 = cx + (ro * cos(a0)).toFloat()
                        val y0 = cy + (ro * sin(a0)).toFloat()
                        if (i == 0) moveTo(x0, y0) else lineTo(x0, y0)
                        lineTo(cx + (ro * cos(a1)).toFloat(), cy + (ro * sin(a1)).toFloat())
                        lineTo(cx + (ri * cos(a2)).toFloat(), cy + (ri * sin(a2)).toFloat())
                        lineTo(cx + (ri * cos(a3)).toFloat(), cy + (ri * sin(a3)).toFloat())
                    }
                    close()
                }
                val outerHub = Path().apply { addCircle(cx, cy, r * 0.42f, Path.Direction.CW) }
                val hub = Path().apply { addCircle(cx, cy, r * 0.24f, Path.Direction.CW) }
                drawLightPath(teeth, cyanPaint)
                drawLightPath(outerHub, bluePaint)
                drawLightPath(hub, brightPaint)
            }
            "clock" -> {
                val dial = Path().apply { addCircle(cx, cy, r * 0.82f, Path.Direction.CW) }
                val hands = Path().apply {
                    moveTo(cx, cy - r * 0.50f)
                    lineTo(cx, cy)
                    lineTo(cx + r * 0.36f, cy + r * 0.20f)
                }
                val ticks = Path().apply {
                    moveTo(cx, cy - r * 0.80f); lineTo(cx, cy - r * 0.66f)
                    moveTo(cx, cy + r * 0.66f); lineTo(cx, cy + r * 0.80f)
                    moveTo(cx - r * 0.80f, cy); lineTo(cx - r * 0.66f, cy)
                    moveTo(cx + r * 0.66f, cy); lineTo(cx + r * 0.80f, cy)
                }
                drawLightPath(dial, cyanPaint)
                drawLightPath(ticks, bluePaint)
                drawLightPath(hands, brightPaint)
                drawLightPoint(cx, cy, strokeBase * 0.72f, warm = true)
            }
            "calculator" -> {
                val frame = Path().apply {
                    addRoundRect(RectF(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f), r * 0.24f, r * 0.24f, Path.Direction.CW)
                }
                val plus = Path().apply {
                    moveTo(cx - r * 0.48f, cy - r * 0.30f); lineTo(cx - r * 0.14f, cy - r * 0.30f)
                    moveTo(cx - r * 0.31f, cy - r * 0.47f); lineTo(cx - r * 0.31f, cy - r * 0.13f)
                }
                val minus = Path().apply {
                    moveTo(cx + r * 0.14f, cy - r * 0.30f); lineTo(cx + r * 0.48f, cy - r * 0.30f)
                }
                val times = Path().apply {
                    moveTo(cx - r * 0.44f, cy + r * 0.16f); lineTo(cx - r * 0.18f, cy + r * 0.42f)
                    moveTo(cx - r * 0.18f, cy + r * 0.16f); lineTo(cx - r * 0.44f, cy + r * 0.42f)
                }
                val eq = Path().apply {
                    moveTo(cx + r * 0.14f, cy + r * 0.22f); lineTo(cx + r * 0.48f, cy + r * 0.22f)
                    moveTo(cx + r * 0.14f, cy + r * 0.40f); lineTo(cx + r * 0.48f, cy + r * 0.40f)
                }
                drawLightPath(frame, cyanPaint)
                drawLightPath(plus, brightPaint)
                drawLightPath(minus, brightPaint)
                drawLightPath(times, cyanPaint)
                drawLightPath(eq, cyanPaint, warmDetail = true)
            }
            "calendar" -> {
                val box = Path().apply {
                    addRoundRect(RectF(cx - r * 0.80f, cy - r * 0.62f, cx + r * 0.80f, cy + r * 0.76f), r * 0.22f, r * 0.22f, Path.Direction.CW)
                }
                val header = Path().apply {
                    moveTo(cx - r * 0.80f, cy - r * 0.22f)
                    lineTo(cx + r * 0.80f, cy - r * 0.22f)
                    moveTo(cx - r * 0.38f, cy - r * 0.82f)
                    lineTo(cx - r * 0.38f, cy - r * 0.46f)
                    moveTo(cx + r * 0.38f, cy - r * 0.82f)
                    lineTo(cx + r * 0.38f, cy - r * 0.46f)
                }
                val cells = Path().apply {
                    val cols = floatArrayOf(-0.42f, 0f, 0.42f)
                    val rows = floatArrayOf(0.08f, 0.42f)
                    for (ryCell in rows) {
                        for (cxCell in cols) {
                            addRoundRect(
                                RectF(cx + r * (cxCell - 0.09f), cy + r * (ryCell - 0.07f), cx + r * (cxCell + 0.09f), cy + r * (ryCell + 0.07f)),
                                r * 0.03f, r * 0.03f, Path.Direction.CW
                            )
                        }
                    }
                }
                drawLightPath(box, cyanPaint)
                drawLightPath(header, brightPaint)
                drawLightPath(cells, bluePaint)
            }
        }
        return true
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

    companion object {
        private val DEFAULT_KEY_LABELS = mapOf(
            "phone" to "Telefone",
            "telefone" to "Telefone",
            "messages" to "Mensagens",
            "mensagens" to "Mensagens",
            "camera" to "Câmera",
            "browser" to "Navegador",
            "navegador" to "Navegador",
            "chrome" to "Navegador",
            "whatsapp" to "WhatsApp",
            "instagram" to "Instagram",
            "youtube" to "YouTube",
            "spotify" to "Spotify",
            "gallery" to "Galeria",
            "galeria" to "Galeria",
            "maps" to "Mapas",
            "mapas" to "Mapas",
            "calendar" to "Calendário",
            "calendario" to "Calendário",
            "notes" to "Notas",
            "notas" to "Notas",
            "files" to "Arquivos",
            "arquivos" to "Arquivos",
            "github" to "GitHub",
            "androidide" to "AndroidIDE",
            "settings" to "Configurações",
            "configuracoes" to "Configurações",
            "contacts" to "Contatos",
            "contatos" to "Contatos",
            "gmail" to "Gmail",
            "playstore" to "Play Store",
            "calculator" to "Calculadora",
            "calculadora" to "Calculadora"
        )

        private val DEFAULT_KEY_PACKAGES = mapOf(
            "phone" to listOf("com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer"),
            "telefone" to listOf("com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer"),
            "messages" to listOf("com.android.messaging", "com.google.android.apps.messaging", "com.samsung.android.messaging"),
            "mensagens" to listOf("com.android.messaging", "com.google.android.apps.messaging", "com.samsung.android.messaging"),
            "camera" to listOf("com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.sec.android.app.camera"),
            "browser" to listOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.brave.browser"),
            "navegador" to listOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.brave.browser"),
            "chrome" to listOf("com.android.chrome"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "instagram" to listOf("com.instagram.android"),
            "youtube" to listOf("com.google.android.youtube"),
            "spotify" to listOf("com.spotify.music"),
            "gallery" to listOf("com.android.gallery3d", "com.google.android.apps.photos", "com.sec.android.gallery3d"),
            "galeria" to listOf("com.android.gallery3d", "com.google.android.apps.photos", "com.sec.android.gallery3d"),
            "maps" to listOf("com.google.android.apps.maps", "com.waze"),
            "calendar" to listOf("com.android.calendar", "com.google.android.calendar", "com.samsung.android.calendar"),
            "notes" to listOf("com.nossouniverso.notes", "com.google.android.keep", "com.samsung.android.app.notes"),
            "files" to listOf("com.android.documentsui", "com.google.android.apps.nbu.files", "com.sec.android.app.myfiles"),
            "github" to listOf("com.github.android"),
            "androidide" to listOf("com.itsaky.androidide"),
            "settings" to listOf("com.android.settings"),
            "contacts" to listOf("com.android.contacts", "com.google.android.contacts", "com.samsung.android.app.contacts"),
            "gmail" to listOf("com.google.android.gm"),
            "playstore" to listOf("com.android.vending"),
            "calculator" to listOf("com.android.calculator2", "com.google.android.calculator", "com.sec.android.app.popupcalculator")
        )

        private val DEFAULT_KEY_KEYWORDS = mapOf(
            "phone" to listOf("dialer", "phone", "telefone"),
            "telefone" to listOf("dialer", "phone", "telefone"),
            "messages" to listOf("messaging", "sms", "mms"),
            "mensagens" to listOf("messaging", "sms", "mms"),
            "camera" to listOf("camera"),
            "browser" to listOf("chrome", "browser", "firefox", "brave", "opera"),
            "navegador" to listOf("chrome", "browser", "firefox", "brave", "opera"),
            "gallery" to listOf("gallery", "photos", "galeria"),
            "galeria" to listOf("gallery", "photos", "galeria"),
            "files" to listOf("files", "documentsui", "myfiles"),
            "arquivos" to listOf("files", "documentsui", "myfiles"),
            "settings" to listOf("settings"),
            "configuracoes" to listOf("settings"),
            "calculator" to listOf("calculator"),
            "calculadora" to listOf("calculator")
        )
    }
}
