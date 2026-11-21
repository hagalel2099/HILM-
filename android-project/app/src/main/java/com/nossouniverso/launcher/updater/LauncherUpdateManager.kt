package com.nossouniverso.launcher.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nossouniverso.launcher.BuildConfig
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
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
        val baseUrl = BuildConfig.UPDATE_API_BASE_URL.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            mainHandler.post {
                callback(
                    UpdateCheckResult.Failure(
                        "Endereço do servidor de atualização não definido neste APK."
                    )
                )
            }
            return
        }

        executor.execute {
            val (installedVersion, installedCode) = getInstalledVersionInfo()
            var conn: HttpURLConnection? = null
            try {
                val encodedVer = URLEncoder.encode(installedVersion, "UTF-8")
                val endpoint = "$baseUrl/api/launcher/updates/check?currentVersion=$encodedVer&versionCode=$installedCode"
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/json")
                }

                val statusCode = conn.responseCode
                val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (statusCode !in 200..299) {
                    val serverError = try {
                        if (body.isNotBlank()) JSONObject(body).optString("error") else ""
                    } catch (_: Exception) {
                        ""
                    }
                    val message = serverError.ifBlank {
                        "Não foi possível verificar atualizações (HTTP $statusCode)."
                    }
                    mainHandler.post { callback(UpdateCheckResult.Failure(message)) }
                    return@execute
                }

                val json = JSONObject(body)
                val status = json.optString("status")
                val latestVersion = json.optString("latestVersion", installedVersion)

                val result = when (status) {
                    "up_to_date" -> UpdateCheckResult.UpToDate(
                        installedVersion = installedVersion,
                        latestVersion = latestVersion
                    )
                    "no_apk_asset" -> UpdateCheckResult.NoApkAsset(
                        installedVersion = installedVersion,
                        latestVersion = latestVersion
                    )
                    "update_available" -> {
                        val assetId = json.optLong("assetId", -1L)
                        val assetName = json.optString("assetName")
                        val downloadPath = json.optString("downloadPath")
                        if (assetId <= 0L || downloadPath.isBlank() || !assetName.lowercase().endsWith(".apk")) {
                            UpdateCheckResult.Failure("Pacote APK inválido retornado pelo servidor.")
                        } else {
                            UpdateCheckResult.UpdateAvailable(
                                installedVersion = installedVersion,
                                latestVersion = latestVersion,
                                releaseName = json.optString("releaseName", latestVersion),
                                assetId = assetId,
                                assetName = assetName,
                                assetSizeBytes = json.optLong("assetSizeBytes", -1L),
                                downloadPath = downloadPath
                            )
                        }
                    }
                    else -> UpdateCheckResult.Failure("Resposta inesperada do servidor de atualizações.")
                }

                mainHandler.post { callback(result) }
            } catch (_: UnknownHostException) {
                mainHandler.post {
                    callback(UpdateCheckResult.Failure("Sem conexão com a internet ou servidor inacessível."))
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
                    callback(UpdateCheckResult.Failure("Erro ao interpretar resposta de atualização."))
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
        val baseUrl = BuildConfig.UPDATE_API_BASE_URL.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            mainHandler.post {
                onComplete(ApkDownloadResult.Failure("Servidor de atualização não configurado."))
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

                val targetUrl = if (update.downloadPath.startsWith("http://") ||
                    update.downloadPath.startsWith("https://")
                ) {
                    update.downloadPath
                } else {
                    "$baseUrl${if (update.downloadPath.startsWith("/")) "" else "/"}${update.downloadPath}"
                }

                conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 60_000
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

    fun shutdown() {
        executor.shutdownNow()
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
