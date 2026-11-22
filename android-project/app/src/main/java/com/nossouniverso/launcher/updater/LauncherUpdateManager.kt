package com.nossouniverso.launcher.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nossouniverso.launcher.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Executors

sealed class UpdateCheckResult {
    data class UpToDate(
        val installedVersion: String,
        val latestVersion: String
    ) : UpdateCheckResult()

    data class UpdateAvailable(
        val installedVersion: String,
        val latestVersion: String,
        val releaseName: String,
        val assetId: Long,
        val assetName: String,
        val assetSizeBytes: Long,
        val downloadPath: String
    ) : UpdateCheckResult()

    data class NoApkAsset(
        val installedVersion: String,
        val latestVersion: String
    ) : UpdateCheckResult()

    data class Failure(val message: String) : UpdateCheckResult()
}

sealed class ApkDownloadResult {
    data class Success(val apkFile: File) : ApkDownloadResult()
    data class Failure(val message: String) : ApkDownloadResult()
}

class LauncherUpdateManager(private val context: Context) {

    companion object {
        private const val WORKER_RELEASES_LATEST_PATH = "/repos/hagalel2099/HILM-/releases/latest"
    }

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getInstalledVersionInfo(): Pair<String, Long> {
        return try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            }
            val versionName = pkgInfo.versionName ?: BuildConfig.VERSION_NAME
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            versionName to versionCode
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME to BuildConfig.VERSION_CODE.toLong()
        }
    }

    fun checkForUpdates(callback: (UpdateCheckResult) -> Unit) {
        val workerOrigin = resolveWorkerOrigin(BuildConfig.UPDATE_API_BASE_URL)
        if (workerOrigin == null) {
            mainHandler.post {
                callback(
                    UpdateCheckResult.Failure(
                        "Endereço do Worker do Cloudflare não configurado neste APK (NOSSO_UNIVERSO_UPDATE_URL)."
                    )
                )
            }
            return
        }

        executor.execute {
            val (installedVersionRaw, _) = getInstalledVersionInfo()
            val installedVersion = normalizeVersion(installedVersionRaw)
            var conn: HttpURLConnection? = null
            try {
                val endpoint = "$workerOrigin$WORKER_RELEASES_LATEST_PATH"
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "NossoUniversoLauncher-Android")
                }

                val statusCode = conn.responseCode
                val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (statusCode !in 200..299) {
                    val workerError = try {
                        if (body.isNotBlank()) JSONObject(body).optString("error") else ""
                    } catch (_: Exception) {
                        ""
                    }
                    val message = when {
                        statusCode == 404 && workerError.contains("não contém APK", ignoreCase = true) ->
                            "A release mais recente publicada ainda não possui um pacote .apk anexado."
                        workerError.isNotBlank() -> workerError
                        else -> "Não foi possível consultar atualizações no Worker (HTTP $statusCode)."
                    }
                    mainHandler.post { callback(UpdateCheckResult.Failure(message)) }
                    return@execute
                }

                val releaseJson = JSONObject(body)
                val tagName = releaseJson.optString("tag_name").trim()
                if (tagName.isBlank()) {
                    mainHandler.post {
                        callback(
                            UpdateCheckResult.Failure(
                                "Resposta do Worker sem identificação de versão (tag_name)."
                            )
                        )
                    }
                    return@execute
                }

                val latestVersion = normalizeVersion(tagName)
                val releaseName = releaseJson.optString("name").trim().ifBlank { tagName }
                val apkAsset = selectApkAsset(releaseJson.optJSONArray("assets"))

                val result = if (compareSemver(latestVersion, installedVersion) <= 0) {
                    UpdateCheckResult.UpToDate(
                        installedVersion = installedVersion,
                        latestVersion = latestVersion
                    )
                } else if (apkAsset == null) {
                    UpdateCheckResult.NoApkAsset(
                        installedVersion = installedVersion,
                        latestVersion = latestVersion
                    )
                } else {
                    val assetId = apkAsset.optLong("id", -1L)
                    val assetName = apkAsset.optString("name").trim()
                    val assetSizeBytes = apkAsset.optLong("size", -1L)
                    val rawDownloadUrl = apkAsset.optString("browser_download_url").trim()
                    val resolvedDownloadUrl = when {
                        rawDownloadUrl.startsWith("https://", ignoreCase = true) ||
                            rawDownloadUrl.startsWith("http://", ignoreCase = true) -> rawDownloadUrl
                        assetId > 0L -> "$workerOrigin/download/$assetId"
                        else -> ""
                    }

                    if (resolvedDownloadUrl.isBlank() || !assetName.lowercase().endsWith(".apk")) {
                        UpdateCheckResult.Failure("Pacote APK inválido retornado pelo Worker.")
                    } else {
                        UpdateCheckResult.UpdateAvailable(
                            installedVersion = installedVersion,
                            latestVersion = latestVersion,
                            releaseName = releaseName,
                            assetId = assetId,
                            assetName = assetName,
                            assetSizeBytes = assetSizeBytes,
                            downloadPath = resolvedDownloadUrl
                        )
                    }
                }

                mainHandler.post { callback(result) }
            } catch (_: UnknownHostException) {
                mainHandler.post {
                    callback(UpdateCheckResult.Failure("Sem conexão com a internet ou Worker inacessível."))
                }
            } catch (_: SocketTimeoutException) {
                mainHandler.post {
                    callback(UpdateCheckResult.Failure("Tempo limite excedido ao consultar atualizações."))
                }
            } catch (e: IOException) {
                mainHandler.post {
                    callback(
                        UpdateCheckResult.Failure(
                            "Falha de rede ao consultar atualizações: ${e.localizedMessage ?: "erro de E/S"}"
                        )
                    )
                }
            } catch (_: Exception) {
                mainHandler.post {
                    callback(UpdateCheckResult.Failure("Erro ao interpretar resposta do Worker de atualização."))
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun downloadApk(
        update: UpdateCheckResult.UpdateAvailable,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: (ApkDownloadResult) -> Unit
    ) {
        val workerOrigin = resolveWorkerOrigin(BuildConfig.UPDATE_API_BASE_URL)
        val hasAbsoluteDownloadUrl = update.downloadPath.startsWith("https://", ignoreCase = true) ||
            update.downloadPath.startsWith("http://", ignoreCase = true)

        if (workerOrigin == null && !hasAbsoluteDownloadUrl) {
            mainHandler.post {
                onComplete(ApkDownloadResult.Failure("Endereço do Worker de atualização não configurado."))
            }
            return
        }

        executor.execute {
            var conn: HttpURLConnection? = null
            val updatesDir = getUpdatesDirectory()
            val safeVersion = update.latestVersion.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val finalFile = File(updatesDir, "NossoUniverso-$safeVersion.apk")
            val partFile = File(updatesDir, "NossoUniverso-$safeVersion.apk.part")

            try {
                if (!updatesDir.exists()) {
                    updatesDir.mkdirs()
                }
                // Remove pacotes antigos para não acumular espaço no aparelho
                updatesDir.listFiles()?.forEach { existing ->
                    if (existing.isFile && existing.name != partFile.name) {
                        existing.delete()
                    }
                }
                if (partFile.exists()) {
                    partFile.delete()
                }

                val targetUrl = if (hasAbsoluteDownloadUrl) {
                    update.downloadPath
                } else {
                    val base = workerOrigin.orEmpty()
                    "$base${if (update.downloadPath.startsWith("/")) "" else "/"}${update.downloadPath}"
                }

                conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "NossoUniversoLauncher-Android")
                }

                val statusCode = conn.responseCode
                if (statusCode !in 200..299) {
                    val errText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    val serverMsg = try {
                        if (errText.isNotBlank()) JSONObject(errText).optString("error") else ""
                    } catch (_: Exception) {
                        ""
                    }
                    mainHandler.post {
                        onComplete(
                            ApkDownloadResult.Failure(
                                serverMsg.ifBlank { "Erro ao baixar o pacote APK (HTTP $statusCode)." }
                            )
                        )
                    }
                    return@execute
                }

                val totalBytes = conn.contentLength.toLong().let {
                    if (it > 0L) it else update.assetSizeBytes
                }

                var downloadedBytes = 0L
                var lastProgressPostMs = 0L
                val buffer = ByteArray(16 * 1024)

                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(partFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressPostMs >= 80L || (totalBytes > 0L && downloadedBytes >= totalBytes)) {
                                lastProgressPostMs = now
                                val progressSnapshot = downloadedBytes
                                mainHandler.post { onProgress(progressSnapshot, totalBytes) }
                            }
                        }
                        output.flush()
                    }
                }

                if (!isValidApkFile(partFile)) {
                    partFile.delete()
                    mainHandler.post {
                        onComplete(
                            ApkDownloadResult.Failure(
                                "O arquivo baixado não é um pacote APK válido."
                            )
                        )
                    }
                    return@execute
                }

                if (finalFile.exists()) {
                    finalFile.delete()
                }
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                mainHandler.post { onComplete(ApkDownloadResult.Success(finalFile)) }
            } catch (_: UnknownHostException) {
                partFile.delete()
                mainHandler.post {
                    onComplete(ApkDownloadResult.Failure("Conexão perdida durante o download do APK."))
                }
            } catch (_: SocketTimeoutException) {
                partFile.delete()
                mainHandler.post {
                    onComplete(ApkDownloadResult.Failure("Tempo limite excedido ao baixar o pacote de atualização."))
                }
            } catch (e: IOException) {
                partFile.delete()
                mainHandler.post {
                    onComplete(
                        ApkDownloadResult.Failure(
                            "Falha no download da atualização: ${e.localizedMessage ?: "erro de rede"}"
                        )
                    )
                }
            } catch (_: Exception) {
                partFile.delete()
                mainHandler.post {
                    onComplete(ApkDownloadResult.Failure("Erro inesperado ao preparar o pacote APK."))
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun createInstallPermissionIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun createInstallApkIntent(apkFile: File): Intent {
        UpdateInstallReceiver.markPendingUpdateRelaunch(appContext, true)
        val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }

    fun installApkWithPackageInstaller(
        apkFile: File,
        onFallbackLegacyIntent: (Intent) -> Unit,
        onSessionError: (String) -> Unit
    ) {
        if (!apkFile.exists() || !isValidApkFile(apkFile)) {
            onSessionError("Arquivo APK inválido ou não encontrado para instalação.")
            return
        }

        UpdateInstallReceiver.markPendingUpdateRelaunch(appContext, true)

        executor.execute {
            var session: PackageInstaller.Session? = null
            try {
                val packageInstaller = appContext.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                ).apply {
                    setAppPackageName(appContext.packageName)
                    setSize(apkFile.length())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_UNSPECIFIED)
                    }
                }

                val sessionId = packageInstaller.createSession(params)
                val activeSession = packageInstaller.openSession(sessionId)
                session = activeSession

                FileInputStream(apkFile).use { input ->
                    activeSession.openWrite("nosso_universo_update.apk", 0L, apkFile.length()).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                        }
                        activeSession.fsync(output)
                    }
                }

                val commitIntent = Intent(appContext, UpdateInstallReceiver::class.java).apply {
                    action = UpdateInstallReceiver.ACTION_PACKAGE_INSTALL_STATUS
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val callbackIntent = PendingIntent.getBroadcast(
                    appContext,
                    sessionId,
                    commitIntent,
                    flags
                )

                activeSession.commit(callbackIntent.intentSender)
                activeSession.close()
                session = null
            } catch (_: Exception) {
                try {
                    session?.abandon()
                } catch (_: Exception) {
                    // Ignore abandon failure
                }
                try {
                    session?.close()
                } catch (_: Exception) {
                    // Ignore close failure
                }
                val legacyIntent = createInstallApkIntent(apkFile)
                mainHandler.post {
                    onFallbackLegacyIntent(legacyIntent)
                }
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun resolveWorkerOrigin(rawUrl: String): String? {
        var trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isBlank() || trimmed.contains("SEU-WORKER", ignoreCase = true)) {
            return null
        }
        if (!trimmed.startsWith("https://", ignoreCase = true) &&
            !trimmed.startsWith("http://", ignoreCase = true)
        ) {
            return null
        }
        if (trimmed.endsWith(WORKER_RELEASES_LATEST_PATH, ignoreCase = true)) {
            trimmed = trimmed.substring(0, trimmed.length - WORKER_RELEASES_LATEST_PATH.length).trimEnd('/')
        }
        return try {
            val parsed = URL(trimmed)
            if (parsed.host.isNullOrBlank()) null else trimmed
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeVersion(raw: String): String {
        return raw.trim()
            .replace(Regex("^[vV]"), "")
            .substringBefore("+")
            .trim()
    }

    private fun compareSemver(aRaw: String, bRaw: String): Int {
        fun parse(v: String): Pair<List<Int>, String> {
            val normalized = normalizeVersion(v)
            val dashIdx = normalized.indexOf('-')
            val core = if (dashIdx >= 0) normalized.substring(0, dashIdx) else normalized
            val pre = if (dashIdx >= 0) normalized.substring(dashIdx + 1) else ""
            val nums = core.split('.').map { part ->
                part.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
            }.toMutableList()
            while (nums.size < 3) nums.add(0)
            return nums to pre
        }

        val (aNums, aPre) = parse(aRaw)
        val (bNums, bPre) = parse(bRaw)
        val len = maxOf(aNums.size, bNums.size)
        for (i in 0 until len) {
            val av = aNums.getOrElse(i) { 0 }
            val bv = bNums.getOrElse(i) { 0 }
            if (av != bv) return if (av > bv) 1 else -1
        }
        if (aPre.isEmpty() && bPre.isNotEmpty()) return 1
        if (aPre.isNotEmpty() && bPre.isEmpty()) return -1
        return aPre.compareTo(bPre)
    }

    private fun selectApkAsset(assets: JSONArray?): JSONObject? {
        if (assets == null || assets.length() == 0) return null
        val apkAssets = mutableListOf<JSONObject>()
        for (i in 0 until assets.length()) {
            val obj = assets.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.lowercase().endsWith(".apk")) {
                apkAssets.add(obj)
            }
        }
        if (apkAssets.isEmpty()) return null
        return apkAssets.firstOrNull { asset ->
            val lower = asset.optString("name").lowercase()
            lower.contains("release") || lower.contains("signed")
        } ?: apkAssets.first()
    }

    private fun getUpdatesDirectory(): File {
        val base = appContext.getExternalFilesDir(null) ?: appContext.cacheDir
        return File(base, "updates")
    }

    private fun isValidApkFile(file: File): Boolean {
        if (!file.exists() || file.length() < 128L) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(2)
                fis.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }
}

