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
import java.util.ArrayDeque
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.max

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
    private var cachedManagedPacks: Map<String, ParsedPack>? = null

    private val importedRootDir: File
        get() = File(context.filesDir, "imported_icon_packs")

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
                storage.setSelectedIconPackId(IconPackInfo.STYLE_NOSSO_UNIVERSO)
            }
            clearCache()
        }
        return deleted
    }

    fun clearCache() {
        bitmapCache.evictAll()
        cachedManagedPacks = null
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

        // Automatically sculpt ANY unmapped / future third-party app into unified Titanium Liquid style
        return when (parsed.info.fallbackStyle.lowercase()) {
            "none", "original", "system" -> fallback
            "hud_cyan" -> applyHudCyanTint(fallback)
            "monochrome" -> applyMonochromeSilver(fallback)
            else -> renderUnifiedTitaniumLiquidIcon(
                cacheIdentity = "$packId:$normalizedPkg/$normalizedAct",
                fallback = fallback
            )
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
        return BitmapDrawable(context.resources, out)
    }

    private data class PreparedSymbol(
        val bitmap: Bitmap,
        val isIsolatedSilhouette: Boolean
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
                bmp
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
