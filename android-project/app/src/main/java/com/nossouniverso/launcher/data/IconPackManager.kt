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
        if (storage.getLauncherTheme() == ShortcutStorage.THEME_LUNA) {
            return renderUnifiedLunaHolographicIcon(
                cacheIdentity = "theme_luna:${packageName.lowercase()}/${activityName.lowercase()}",
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

    private fun isLunaWarmAccentApp(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        val warmKeywords = listOf(
            "camera", "cam", "spotify", "music", "audio", "notes", "keep",
            "settings", "calendar", "instagram", "youtube", "mercadopago", "gallery"
        )
        if (warmKeywords.any { pkg.contains(it) }) return true
        return abs(pkg.hashCode()) % 4 == 0
    }

    /**
     * Tema Luna — Entre Luz e Sombra | HILM:
     * Floating thin-line luminous cyan (#00E5FF) & electric blue (#398BFF) wireframe
     * symbols directly over transparency / ultra-discreet navy mist (#0A1424) with
     * ZERO 3D shading fills, ZERO solid square/circle cards, crisp #D9F7FF line
     * highlights, and selective warm copper/amber micro-glints (#C47A48 / #FFB45C).
     */
    private fun renderUnifiedLunaHolographicIcon(
        cacheIdentity: String,
        packageName: String,
        fallback: Drawable
    ): Drawable {
        val size = 208
        val cacheKey = "luna_holo_v3:$cacheIdentity#$size"
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

        // Discreet circular navy mist for legibility without any solid card
        val mistRadius = size * 0.44f
        val mistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx,
                cy,
                mistRadius,
                intArrayOf(
                    Color.argb(92, 10, 20, 36),
                    Color.argb(38, 5, 7, 11),
                    Color.argb(0, 5, 7, 11)
                ),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, mistRadius, mistPaint)

        // Delicate broken holographic orbital arc
        val arcR = size * 0.425f
        val arcRect = RectF(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.7f
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(96, 57, 139, 255)
        }
        canvas.drawArc(arcRect, -40f, 74f, false, arcPaint)
        arcPaint.color = Color.argb(122, 0, 229, 255)
        canvas.drawArc(arcRect, 140f, 72f, false, arcPaint)

        val warmAccent = isLunaWarmAccentApp(packageName)

        // Prefer razor-sharp floating holographic line vectors for recognized key apps
        if (drawLunaCuratedVectorSymbol(canvas, packageName.lowercase(), cx, cy, size * 0.56f)) {
            drawLunaOptionalWarmGlint(canvas, cx, cy, size, warmAccent)
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

        // Universal Thin-Line Holographic Wireframe Extractor for all other apps:
        // Strips 3D shading gradients and solid opaque plates, projecting pure cyan contours.
        val prepared = prepareForegroundSymbol(fallback, 148)
        val drawSize = (size * if (prepared.isIsolatedSilhouette) 0.68f else 0.64f).toInt()
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

        val luma = FloatArray(nPixels)
        val colorEdge = FloatArray(nPixels)
        val isRim = BooleanArray(nPixels)
        var opaqueCount = 0
        var minL = 1f
        var maxL = 0f
        var sumL = 0f

        for (i in 0 until nPixels) {
            val px = srcPx[i]
            val a = (px ushr 24) and 0xFF
            if (a > 28) {
                opaqueCount++
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val l = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                luma[i] = l
                sumL += l
                if (l < minL) minL = l
                if (l > maxL) maxL = l
            }
        }

        if (opaqueCount == 0) {
            if (!prepared.bitmap.isRecycled) prepared.bitmap.recycle()
            bitmapCache.put(cacheKey, out)
            return BitmapDrawable(context.resources, out)
        }

        val avgLuma = sumL / opaqueCount.toFloat()
        val opaqueRatio = opaqueCount.toFloat() / nPixels.toFloat()

        // Detect perimeter contours (2px) & gate out smooth 3D shading gradients (maxDiff < 32)
        var interiorCount = 0
        for (y in 0 until drawSize) {
            val row = y * drawSize
            for (x in 0 until drawSize) {
                val idx = row + x
                val px = srcPx[idx]
                val a = (px ushr 24) and 0xFF
                if (a <= 28) continue

                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF

                var maxDiff = 0
                var nearTransparent = (x < 2 || y < 2 || x >= drawSize - 2 || y >= drawSize - 2)
                for (dy in -2..2) {
                    val ny = y + dy
                    if (ny !in 0 until drawSize) {
                        nearTransparent = true
                        continue
                    }
                    val nRow = ny * drawSize
                    for (dx in -2..2) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        if (nx !in 0 until drawSize) {
                            nearTransparent = true
                            continue
                        }
                        val nPx = srcPx[nRow + nx]
                        val nA = (nPx ushr 24) and 0xFF
                        if (nA <= 28) {
                            if (abs(dx) <= 1 && abs(dy) <= 1 || (abs(dx) + abs(dy) <= 3)) {
                                nearTransparent = true
                            }
                        } else if (abs(dx) <= 1 && abs(dy) <= 1) {
                            val nr = (nPx ushr 16) and 0xFF
                            val ng = (nPx ushr 8) and 0xFF
                            val nb = nPx and 0xFF
                            val diff = max(abs(r - nr), max(abs(g - ng), abs(b - nb)))
                            if (diff > maxDiff) maxDiff = diff
                        }
                    }
                }
                isRim[idx] = nearTransparent
                if (!nearTransparent) interiorCount++
                // Gate smooth 3D lighting ramps (diff < 32) so only real symbol seams become lines
                colorEdge[idx] = if (maxDiff >= 32) {
                    ((maxDiff - 32) / 110f).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
        }

        val interiorRatio = interiorCount.toFloat() / opaqueCount.toFloat()
        val isSparseLineGlyph = prepared.isIsolatedSilhouette &&
            (opaqueRatio < 0.27f || interiorRatio < 0.24f)

        val outPx = IntArray(nPixels)
        val glowPx = IntArray(nPixels)

        if (isSparseLineGlyph) {
            // Pure floating wireframe/glyph (Settings, Phone, Notes, Material monochrome):
            // Clean single-stroke glowing cyan & #D9F7FF edge highlights, zero fill card.
            val span = maxL - minL
            val isDark = avgLuma < 0.40f && span < 0.42f
            for (y in 0 until drawSize) {
                val row = y * drawSize
                for (x in 0 until drawSize) {
                    val idx = row + x
                    val px = srcPx[idx]
                    val a = (px ushr 24) and 0xFF
                    if (a < 10) continue

                    val diag = ((x + y).toFloat() / (2f * drawSize)).coerceIn(0f, 1f)
                    val edge = colorEdge[idx]
                    val boost = when {
                        isRim[idx] -> 0.85f
                        edge > 0.25f -> 0.92f
                        isDark -> 0.72f
                        span >= 0.28f -> (0.45f + 0.50f * ((luma[idx] - minL) / span)).coerceIn(0.45f, 0.95f)
                        else -> 0.72f
                    }
                    val cR = ((1f - boost) * 57f + max(0f, boost - 0.65f) / 0.35f * 217f).toInt().coerceIn(0, 255)
                    val cG = (165 + boost * 82f - diag * 10f).toInt().coerceIn(0, 255)
                    outPx[idx] = Color.argb(a, cR, cG, 255)
                    glowPx[idx] = Color.argb((a * 0.75f).toInt(), 0, 229, 255)
                }
            }
        } else {
            // Dense / badge / 3D / multicolor icon: convert strictly into translucent floating line-art.
            // Sample outer ring to recognize thin contrasting inner details vs background plate.
            val qSumR = LongArray(4)
            val qSumG = LongArray(4)
            val qSumB = LongArray(4)
            val qCount = LongArray(4)
            val halfSz = drawSize / 2
            for (y in 3 until drawSize - 3) {
                val row = y * drawSize
                for (x in 3 until drawSize - 3) {
                    val idx = row + x
                    val a = (srcPx[idx] ushr 24) and 0xFF
                    if (a > 120 && !isRim[idx] && colorEdge[idx] < 0.10f) {
                        val nearOuter =
                            ((srcPx[idx - 3] ushr 24) and 0xFF) < 40 ||
                                ((srcPx[idx + 3] ushr 24) and 0xFF) < 40 ||
                                ((srcPx[(y - 3) * drawSize + x] ushr 24) and 0xFF) < 40 ||
                                ((srcPx[(y + 3) * drawSize + x] ushr 24) and 0xFF) < 40
                        if (nearOuter) {
                            val q = (if (y >= halfSz) 2 else 0) or (if (x >= halfSz) 1 else 0)
                            val px = srcPx[idx]
                            qSumR[q] += ((px ushr 16) and 0xFF).toLong()
                            qSumG[q] += ((px ushr 8) and 0xFF).toLong()
                            qSumB[q] += (px and 0xFF).toLong()
                            qCount[q]++
                        }
                    }
                }
            }

            var totR = 0L
            var totG = 0L
            var totB = 0L
            var totS = 0L
            for (q in 0 until 4) {
                totR += qSumR[q]
                totG += qSumG[q]
                totB += qSumB[q]
                totS += qCount[q]
            }
            val fallbackR = if (totS > 0L) (totR / totS).toInt() else 40
            val fallbackG = if (totS > 0L) (totG / totS).toInt() else 60
            val fallbackB = if (totS > 0L) (totB / totS).toInt() else 80
            val qR = IntArray(4) { q -> if (qCount[q] > 0L) (qSumR[q] / qCount[q]).toInt() else fallbackR }
            val qG = IntArray(4) { q -> if (qCount[q] > 0L) (qSumG[q] / qCount[q]).toInt() else fallbackG }
            val qB = IntArray(4) { q -> if (qCount[q] > 0L) (qSumB[q] / qCount[q]).toInt() else fallbackB }

            fun localBgDist(x: Int, y: Int, r: Int, g: Int, b: Int): Float {
                val tx = (((x.toFloat() / max(1, drawSize - 1)) - 0.25f) * 2f).coerceIn(0f, 1f)
                val ty = (((y.toFloat() / max(1, drawSize - 1)) - 0.25f) * 2f).coerceIn(0f, 1f)
                val refR = (qR[0] * (1f - tx) + qR[1] * tx) * (1f - ty) + (qR[2] * (1f - tx) + qR[3] * tx) * ty
                val refG = (qG[0] * (1f - tx) + qG[1] * tx) * (1f - ty) + (qG[2] * (1f - tx) + qG[3] * tx) * ty
                val refB = (qB[0] * (1f - tx) + qB[1] * tx) * (1f - ty) + (qB[2] * (1f - tx) + qB[3] * tx) * ty
                return (abs(r - refR) + abs(g - refG) + abs(b - refB)) / (3f * 255f)
            }

            // Detect pixels belonging to thin internal strokes (letters/waves/glyph lines)
            val nearInternalEdge = BooleanArray(nPixels)
            for (y in 2 until drawSize - 2) {
                val row = y * drawSize
                for (x in 2 until drawSize - 2) {
                    val idx = row + x
                    if (colorEdge[idx] > 0.18f) {
                        for (dy in -2..2) {
                            val nRow = (y + dy) * drawSize
                            for (dx in -2..2) {
                                nearInternalEdge[nRow + x + dx] = true
                            }
                        }
                    }
                }
            }

            for (y in 0 until drawSize) {
                val row = y * drawSize
                for (x in 0 until drawSize) {
                    val idx = row + x
                    val px = srcPx[idx]
                    val a = (px ushr 24) and 0xFF
                    if (a < 10) continue

                    val r = (px ushr 16) and 0xFF
                    val g = (px ushr 8) and 0xFF
                    val b = px and 0xFF
                    val edge = colorEdge[idx]
                    val dBg = localBgDist(x, y, r, g, b)

                    if (edge > 0.14f) {
                        // Razor-sharp internal wireframe contour (#00E5FF -> #D9F7FF)
                        val e = ((edge - 0.14f) / 0.60f).coerceIn(0.35f, 1f)
                        val cR = ((e - 0.35f) / 0.65f * 217f).toInt().coerceIn(0, 255)
                        val cG = (212 + e * 35f).toInt().coerceIn(0, 255)
                        outPx[idx] = Color.argb((a * (0.65f + 0.35f * e)).toInt(), cR, cG, 255)
                        glowPx[idx] = Color.argb((a * e * 0.85f).toInt(), 0, 229, 255)
                    } else if (dBg > 0.17f && nearInternalEdge[idx]) {
                        // Crisp thin contrasting symbol strokes (letters, waves, small icons)
                        outPx[idx] = Color.argb((a * 0.92f).toInt(), 165, 244, 255)
                        glowPx[idx] = Color.argb((a * 0.72f).toInt(), 0, 229, 255)
                    } else if (isRim[idx]) {
                        // Delicate holographic perimeter line in electric blue / cyan
                        outPx[idx] = Color.argb((a * 0.78f).toInt(), 42, 185, 255)
                        glowPx[idx] = Color.argb((a * 0.55f).toInt(), 57, 139, 255)
                    } else {
                        // Virtually transparent holographic glass whisper (NO 3D shading or solid plate!)
                        val glassAlpha = if (dBg > 0.18f) (a * 0.18f).toInt() else (a * 0.08f).toInt()
                        outPx[idx] = Color.argb(glassAlpha, 0, 175, 225)
                    }
                }
            }
        }

        val glowBmp = Bitmap.createBitmap(drawSize, drawSize, Bitmap.Config.ARGB_8888)
        glowBmp.setPixels(glowPx, 0, drawSize, 0, 0, drawSize, drawSize)
        val glowAlpha = glowBmp.extractAlpha()

        val blueDepthPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(95, 57, 139, 255)
            maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(glowAlpha, null, destRect, blueDepthPaint)

        val cyanGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(135, 0, 229, 255)
            maskFilter = BlurMaskFilter(3.0f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(glowAlpha, null, destRect, cyanGlowPaint)

        glowAlpha.recycle()
        glowBmp.recycle()

        val holoBmp = Bitmap.createBitmap(drawSize, drawSize, Bitmap.Config.ARGB_8888)
        holoBmp.setPixels(outPx, 0, drawSize, 0, 0, drawSize, drawSize)
        canvas.drawBitmap(holoBmp, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        holoBmp.recycle()

        drawLunaOptionalWarmGlint(canvas, cx, cy, size, warmAccent)

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

    private fun drawLunaOptionalWarmGlint(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Int,
        warmAccent: Boolean
    ) {
        if (!warmAccent) return
        val glintX = cx + size * 0.25f
        val glintY = cy - size * 0.25f
        val amberHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(155, 196, 122, 72)
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(glintX, glintY, 4.2f, amberHalo)
        val amberCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFB45C")
        }
        canvas.drawCircle(glintX, glintY, 2.4f, amberCore)
    }

    private fun drawLunaCuratedVectorSymbol(
        canvas: Canvas,
        pkg: String,
        cx: Float,
        cy: Float,
        span: Float
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
            else -> return false
        }

        val r = span * 0.5f
        val strokeBase = (span * 0.044f).coerceIn(3.8f, 5.8f)

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeBase * 1.9f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(120, 0, 229, 255)
            maskFilter = BlurMaskFilter(5.2f, BlurMaskFilter.Blur.NORMAL)
        }
        val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeBase
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#00E5FF")
        }
        val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeBase * 0.92f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#398BFF")
        }
        val brightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeBase * 0.92f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#D9F7FF")
        }

        fun drawHoloPath(path: Path, primary: Paint = cyanPaint) {
            canvas.drawPath(path, glowPaint)
            canvas.drawPath(path, primary)
        }

        when (kind) {
            "chrome" -> {
                val outer = Path().apply { addCircle(cx, cy, r * 0.92f, Path.Direction.CW) }
                val innerR = r * 0.36f
                val inner = Path().apply { addCircle(cx, cy, innerR, Path.Direction.CW) }
                val spokes = Path().apply {
                    moveTo(cx, cy - innerR)
                    lineTo(cx + r * 0.84f, cy - innerR)
                    moveTo(cx - innerR * 0.86f, cy + innerR * 0.50f)
                    lineTo(cx - r * 0.72f, cy - r * 0.42f)
                    moveTo(cx + innerR * 0.86f, cy + innerR * 0.50f)
                    lineTo(cx + r * 0.02f, cy + r * 0.91f)
                }
                drawHoloPath(outer, cyanPaint)
                drawHoloPath(spokes, bluePaint)
                drawHoloPath(inner, brightPaint)
            }
            "whatsapp" -> {
                val bubble = Path().apply {
                    val rr = r * 0.90f
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
                drawHoloPath(bubble, cyanPaint)
                drawHoloPath(phone, brightPaint)
            }
            "youtube" -> {
                val screen = Path().apply {
                    addRoundRect(
                        RectF(cx - r * 0.94f, cy - r * 0.64f, cx + r * 0.94f, cy + r * 0.64f),
                        r * 0.32f,
                        r * 0.32f,
                        Path.Direction.CW
                    )
                }
                val play = Path().apply {
                    moveTo(cx - r * 0.18f, cy - r * 0.28f)
                    lineTo(cx + r * 0.34f, cy)
                    lineTo(cx - r * 0.18f, cy + r * 0.28f)
                    close()
                }
                drawHoloPath(screen, bluePaint)
                drawHoloPath(play, brightPaint)
            }
            "spotify" -> {
                val ring = Path().apply { addCircle(cx, cy, r * 0.92f, Path.Direction.CW) }
                val w1 = Path().apply {
                    moveTo(cx - r * 0.46f, cy - r * 0.22f)
                    quadTo(cx + r * 0.02f, cy - r * 0.42f, cx + r * 0.50f, cy - r * 0.14f)
                }
                val w2 = Path().apply {
                    moveTo(cx - r * 0.40f, cy + r * 0.05f)
                    quadTo(cx + r * 0.02f, cy - r * 0.12f, cx + r * 0.42f, cy + r * 0.12f)
                }
                val w3 = Path().apply {
                    moveTo(cx - r * 0.32f, cy + r * 0.32f)
                    quadTo(cx + r * 0.02f, cy + r * 0.18f, cx + r * 0.34f, cy + r * 0.36f)
                }
                drawHoloPath(ring, cyanPaint)
                drawHoloPath(w1, brightPaint)
                drawHoloPath(w2, cyanPaint)
                drawHoloPath(w3, bluePaint)
            }
            "instagram" -> {
                val frame = Path().apply {
                    addRoundRect(
                        RectF(cx - r * 0.86f, cy - r * 0.86f, cx + r * 0.86f, cy + r * 0.86f),
                        r * 0.40f,
                        r * 0.40f,
                        Path.Direction.CW
                    )
                }
                val lens = Path().apply { addCircle(cx, cy, r * 0.38f, Path.Direction.CW) }
                val flash = Path().apply { addCircle(cx + r * 0.46f, cy - r * 0.46f, r * 0.07f, Path.Direction.CW) }
                drawHoloPath(frame, cyanPaint)
                drawHoloPath(lens, brightPaint)
                drawHoloPath(flash, brightPaint)
            }
            "discord" -> {
                val visor = Path().apply {
                    moveTo(cx - r * 0.68f, cy - r * 0.48f)
                    quadTo(cx, cy - r * 0.72f, cx + r * 0.68f, cy - r * 0.48f)
                    quadTo(cx + r * 0.92f, cy + r * 0.06f, cx + r * 0.74f, cy + r * 0.56f)
                    lineTo(cx + r * 0.34f, cy + r * 0.42f)
                    quadTo(cx, cy + r * 0.54f, cx - r * 0.34f, cy + r * 0.42f)
                    lineTo(cx - r * 0.74f, cy + r * 0.56f)
                    quadTo(cx - r * 0.92f, cy + r * 0.06f, cx - r * 0.68f, cy - r * 0.48f)
                    close()
                }
                val eyes = Path().apply {
                    addCircle(cx - r * 0.30f, cy + r * 0.04f, r * 0.15f, Path.Direction.CW)
                    addCircle(cx + r * 0.30f, cy + r * 0.04f, r * 0.15f, Path.Direction.CW)
                }
                drawHoloPath(visor, cyanPaint)
                drawHoloPath(eyes, brightPaint)
            }
            "nubank" -> {
                val nStroke = Path().apply {
                    moveTo(cx - r * 0.66f, cy + r * 0.56f)
                    lineTo(cx - r * 0.66f, cy - r * 0.44f)
                    quadTo(cx - r * 0.66f, cy - r * 0.64f, cx - r * 0.46f, cy - r * 0.48f)
                    lineTo(cx + r * 0.12f, cy + r * 0.22f)
                    lineTo(cx + r * 0.12f, cy - r * 0.54f)
                }
                val uStroke = Path().apply {
                    moveTo(cx + r * 0.66f, cy - r * 0.54f)
                    lineTo(cx + r * 0.66f, cy + r * 0.44f)
                    quadTo(cx + r * 0.66f, cy + r * 0.64f, cx + r * 0.46f, cy + r * 0.48f)
                    lineTo(cx - r * 0.12f, cy - r * 0.22f)
                    lineTo(cx - r * 0.12f, cy + r * 0.54f)
                }
                drawHoloPath(nStroke, cyanPaint)
                drawHoloPath(uStroke, brightPaint)
            }
            "gmail" -> {
                val outer = Path().apply {
                    moveTo(cx - r * 0.88f, cy + r * 0.56f)
                    lineTo(cx - r * 0.88f, cy - r * 0.40f)
                    quadTo(cx - r * 0.88f, cy - r * 0.62f, cx - r * 0.64f, cy - r * 0.48f)
                    lineTo(cx, cy + r * 0.04f)
                    lineTo(cx + r * 0.64f, cy - r * 0.48f)
                    quadTo(cx + r * 0.88f, cy - r * 0.62f, cx + r * 0.88f, cy - r * 0.40f)
                    lineTo(cx + r * 0.88f, cy + r * 0.56f)
                }
                val base = Path().apply {
                    moveTo(cx - r * 0.54f, cy - r * 0.06f)
                    lineTo(cx - r * 0.54f, cy + r * 0.56f)
                    moveTo(cx + r * 0.54f, cy - r * 0.06f)
                    lineTo(cx + r * 0.54f, cy + r * 0.56f)
                }
                drawHoloPath(outer, cyanPaint)
                drawHoloPath(base, brightPaint)
            }
            "google" -> {
                val gArc = Path().apply {
                    addArc(RectF(cx - r * 0.86f, cy - r * 0.86f, cx + r * 0.86f, cy + r * 0.86f), -40f, -275f)
                }
                val bar = Path().apply {
                    moveTo(cx + r * 0.04f, cy)
                    lineTo(cx + r * 0.84f, cy)
                    lineTo(cx + r * 0.84f, cy + r * 0.34f)
                }
                drawHoloPath(gArc, cyanPaint)
                drawHoloPath(bar, brightPaint)
            }
            "playstore" -> {
                val tri = Path().apply {
                    moveTo(cx - r * 0.66f, cy - r * 0.84f)
                    lineTo(cx + r * 0.84f, cy)
                    lineTo(cx - r * 0.66f, cy + r * 0.84f)
                    close()
                }
                val prism = Path().apply {
                    moveTo(cx - r * 0.66f, cy - r * 0.84f)
                    lineTo(cx + r * 0.36f, cy + r * 0.27f)
                    moveTo(cx - r * 0.66f, cy + r * 0.84f)
                    lineTo(cx + r * 0.36f, cy - r * 0.27f)
                }
                drawHoloPath(tri, cyanPaint)
                drawHoloPath(prism, brightPaint)
            }
            "maps" -> {
                val pin = Path().apply {
                    addArc(RectF(cx - r * 0.66f, cy - r * 0.90f, cx + r * 0.66f, cy + r * 0.42f), 155f, 230f)
                    lineTo(cx, cy + r * 0.92f)
                    close()
                }
                val hole = Path().apply { addCircle(cx, cy - r * 0.24f, r * 0.24f, Path.Direction.CW) }
                drawHoloPath(pin, cyanPaint)
                drawHoloPath(hole, brightPaint)
            }
            "mercadopago", "mercadolivre" -> {
                val oval = Path().apply {
                    addOval(RectF(cx - r * 0.92f, cy - r * 0.62f, cx + r * 0.92f, cy + r * 0.62f), Path.Direction.CW)
                }
                val clasp = Path().apply {
                    moveTo(cx - r * 0.62f, cy + r * 0.04f)
                    quadTo(cx - r * 0.20f, cy - r * 0.30f, cx + r * 0.16f, cy + r * 0.02f)
                    moveTo(cx + r * 0.62f, cy - r * 0.04f)
                    quadTo(cx + r * 0.20f, cy + r * 0.30f, cx - r * 0.16f, cy - r * 0.02f)
                    moveTo(cx - r * 0.18f, cy + r * 0.18f)
                    lineTo(cx + r * 0.18f, cy + r * 0.18f)
                }
                drawHoloPath(oval, if (kind == "mercadopago") bluePaint else cyanPaint)
                drawHoloPath(clasp, brightPaint)
            }
            "inss" -> {
                val shield = Path().apply {
                    addRoundRect(
                        RectF(cx - r * 0.84f, cy - r * 0.66f, cx + r * 0.84f, cy + r * 0.66f),
                        r * 0.24f,
                        r * 0.24f,
                        Path.Direction.CW
                    )
                }
                val glyph = Path().apply {
                    // Geometric INSS stylized pillars & arch
                    moveTo(cx - r * 0.52f, cy + r * 0.28f)
                    lineTo(cx - r * 0.52f, cy - r * 0.28f)
                    moveTo(cx - r * 0.24f, cy + r * 0.28f)
                    lineTo(cx - r * 0.24f, cy - r * 0.28f)
                    lineTo(cx + r * 0.06f, cy + r * 0.28f)
                    lineTo(cx + r * 0.06f, cy - r * 0.28f)
                    moveTo(cx + r * 0.54f, cy - r * 0.28f)
                    lineTo(cx + r * 0.28f, cy - r * 0.28f)
                    lineTo(cx + r * 0.28f, cy)
                    lineTo(cx + r * 0.54f, cy)
                    lineTo(cx + r * 0.54f, cy + r * 0.28f)
                    lineTo(cx + r * 0.28f, cy + r * 0.28f)
                }
                drawHoloPath(shield, bluePaint)
                drawHoloPath(glyph, brightPaint)
            }
            "kodi" -> {
                val diamond = Path().apply {
                    moveTo(cx, cy - r * 0.90f)
                    lineTo(cx + r * 0.90f, cy)
                    lineTo(cx, cy + r * 0.90f)
                    lineTo(cx - r * 0.90f, cy)
                    close()
                }
                val facets = Path().apply {
                    moveTo(cx - r * 0.44f, cy - r * 0.44f)
                    lineTo(cx + r * 0.44f, cy + r * 0.44f)
                    moveTo(cx - r * 0.44f, cy + r * 0.44f)
                    lineTo(cx + r * 0.44f, cy - r * 0.44f)
                    moveTo(cx - r * 0.08f, cy - r * 0.22f)
                    lineTo(cx + r * 0.22f, cy)
                    lineTo(cx - r * 0.08f, cy + r * 0.22f)
                }
                drawHoloPath(diamond, cyanPaint)
                drawHoloPath(facets, brightPaint)
            }
            "rar" -> {
                val books = Path().apply {
                    addRoundRect(RectF(cx - r * 0.80f, cy - r * 0.78f, cx + r * 0.80f, cy - r * 0.34f), r * 0.12f, r * 0.12f, Path.Direction.CW)
                    addRoundRect(RectF(cx - r * 0.80f, cy - r * 0.22f, cx + r * 0.80f, cy + r * 0.22f), r * 0.12f, r * 0.12f, Path.Direction.CW)
                    addRoundRect(RectF(cx - r * 0.80f, cy + r * 0.34f, cx + r * 0.80f, cy + r * 0.78f), r * 0.12f, r * 0.12f, Path.Direction.CW)
                }
                val belt = Path().apply {
                    addRect(RectF(cx - r * 0.18f, cy - r * 0.84f, cx + r * 0.18f, cy + r * 0.84f), Path.Direction.CW)
                }
                drawHoloPath(books, cyanPaint)
                drawHoloPath(belt, brightPaint)
            }
            "snaptube" -> {
                val ring = Path().apply {
                    addArc(RectF(cx - r * 0.86f, cy - r * 0.86f, cx + r * 0.86f, cy + r * 0.86f), 25f, 310f)
                }
                val arrow = Path().apply {
                    moveTo(cx, cy - r * 0.52f)
                    lineTo(cx, cy + r * 0.44f)
                    moveTo(cx - r * 0.38f, cy + r * 0.08f)
                    lineTo(cx, cy + r * 0.48f)
                    lineTo(cx + r * 0.38f, cy + r * 0.08f)
                }
                drawHoloPath(ring, bluePaint)
                drawHoloPath(arrow, brightPaint)
            }
            "xplayer" -> {
                val frame = Path().apply {
                    addRoundRect(RectF(cx - r * 0.86f, cy - r * 0.72f, cx + r * 0.86f, cy + r * 0.72f), r * 0.24f, r * 0.24f, Path.Direction.CW)
                }
                val xPlay = Path().apply {
                    moveTo(cx - r * 0.38f, cy - r * 0.32f)
                    lineTo(cx + r * 0.36f, cy + r * 0.32f)
                    moveTo(cx + r * 0.36f, cy - r * 0.32f)
                    lineTo(cx - r * 0.38f, cy + r * 0.32f)
                }
                drawHoloPath(frame, cyanPaint)
                drawHoloPath(xPlay, brightPaint)
            }
            "andftp" -> {
                val globe = Path().apply {
                    addCircle(cx - r * 0.18f, cy - r * 0.14f, r * 0.56f, Path.Direction.CW)
                }
                val arrows = Path().apply {
                    moveTo(cx - r * 0.20f, cy + r * 0.60f)
                    lineTo(cx + r * 0.78f, cy + r * 0.60f)
                    lineTo(cx + r * 0.54f, cy + r * 0.38f)
                    moveTo(cx + r * 0.78f, cy + r * 0.22f)
                    lineTo(cx - r * 0.04f, cy + r * 0.22f)
                    lineTo(cx + r * 0.20f, cy)
                }
                drawHoloPath(globe, cyanPaint)
                drawHoloPath(arrows, brightPaint)
            }
            "videoconverter" -> {
                val loop = Path().apply {
                    addArc(RectF(cx - r * 0.84f, cy - r * 0.84f, cx + r * 0.84f, cy + r * 0.84f), -20f, 155f)
                    addArc(RectF(cx - r * 0.84f, cy - r * 0.84f, cx + r * 0.84f, cy + r * 0.84f), 160f, 155f)
                }
                val play = Path().apply {
                    moveTo(cx - r * 0.22f, cy - r * 0.30f)
                    lineTo(cx + r * 0.32f, cy)
                    lineTo(cx - r * 0.22f, cy + r * 0.30f)
                    close()
                }
                drawHoloPath(loop, cyanPaint)
                drawHoloPath(play, brightPaint)
            }
            "files" -> {
                val folder = Path().apply {
                    moveTo(cx - r * 0.86f, cy - r * 0.54f)
                    lineTo(cx - r * 0.20f, cy - r * 0.54f)
                    lineTo(cx - r * 0.02f, cy - r * 0.32f)
                    lineTo(cx + r * 0.86f, cy - r * 0.32f)
                    lineTo(cx + r * 0.86f, cy + r * 0.64f)
                    lineTo(cx - r * 0.86f, cy + r * 0.64f)
                    close()
                }
                val sheet = Path().apply {
                    moveTo(cx - r * 0.48f, cy + r * 0.06f)
                    lineTo(cx + r * 0.48f, cy + r * 0.06f)
                    moveTo(cx - r * 0.48f, cy + r * 0.32f)
                    lineTo(cx + r * 0.24f, cy + r * 0.32f)
                }
                drawHoloPath(folder, cyanPaint)
                drawHoloPath(sheet, brightPaint)
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
