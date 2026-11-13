package com.bone.android.a4v.oficial.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bone.android.a4v.oficial.ui.view.TvDialogHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Gestor unificado para descargas seguras de APK e instalación mediante FileProvider.
 * Sustituye el código duplicado y los ProgressDialog heredados que congelaban o filtraban Activity.
 */
object ApkDownloadManager {

    private const val DOWNLOAD_SUBDIR = "apk_downloads"

    data class RemoteApkSpec(
        val url: String,
        val expectedSha256: String? = null
    )

    fun downloadAndInstall(
        activity: Activity,
        client: OkHttpClient,
        title: String,
        initialMessage: String,
        apkFileName: String,
        userAgent: String? = null,
        urlProvider: suspend () -> String,
        expectedSha256Provider: (suspend () -> String?)? = null,
        onErrorFallback: ((Activity, Int?, Throwable?) -> Unit)? = null,
        onInstallLaunched: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        var downloadJob: Job? = null
        val dialogHandle = TvDialogHelper.showDownloadDialog(
            activity = activity,
            title = title,
            message = initialMessage,
            onCancel = { downloadJob?.cancel() }
        ) ?: return

        val scope = (activity as? LifecycleOwner)?.lifecycleScope
            ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        downloadJob = scope.launch {
            var apkFile: File? = null
            try {
                val rawUrl = withContext(Dispatchers.IO) { urlProvider() }.trim()
                if (!rawUrl.startsWith("https://", ignoreCase = true)) {
                    dialogHandle.dismissSafely(activity)
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        if (onErrorFallback != null) {
                            onErrorFallback(activity, null, IllegalArgumentException("URL de descarga inválida o no HTTPS"))
                        } else {
                            Toast.makeText(activity, "Enlace de descarga no válido (se requiere HTTPS).", Toast.LENGTH_LONG).show()
                        }
                    }
                    return@launch
                }

                val downloadResult = withContext(Dispatchers.IO) {
                    val downloadDir = File(activity.cacheDir, DOWNLOAD_SUBDIR).apply { mkdirs() }
                    // Limpiar descargas previas para no llenar la memoria interna limitada de Fire TV / Android TV
                    downloadDir.listFiles()?.forEach { oldFile ->
                        if (oldFile.isFile) oldFile.delete()
                    }
                    val targetFile = File(downloadDir, apkFileName)
                    apkFile = targetFile

                    val requestBuilder = Request.Builder().url(rawUrl)
                    if (!userAgent.isNullOrBlank()) {
                        requestBuilder.header("User-Agent", userAgent)
                    }

                    client.newCall(requestBuilder.build()).execute().use { response ->
                        if (!response.request.url.isHttps) {
                            throw SecurityException("Descarga APK bloqueada: redirección a HTTP no cifrado.")
                        }
                        if (!response.isSuccessful) {
                            return@withContext DownloadOutcome.HttpError(response.code)
                        }

                        val body = response.body ?: return@withContext DownloadOutcome.HttpError(response.code)
                        val contentLength = body.contentLength()
                        if (contentLength <= 0L) {
                            withContext(Dispatchers.Main) {
                                if (!activity.isFinishing && !activity.isDestroyed) {
                                    dialogHandle.setIndeterminate()
                                }
                            }
                        }
                        var totalRead = 0L
                        var lastPercent = -1

                        body.byteStream().use { input ->
                            FileOutputStream(targetFile).use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    if (!coroutineContext[Job]!!.isActive) {
                                        throw CancellationException("Descarga cancelada")
                                    }
                                    output.write(buffer, 0, bytesRead)
                                    totalRead += bytesRead
                                    if (contentLength > 0) {
                                        val percent = ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100)
                                        if (percent != lastPercent) {
                                            lastPercent = percent
                                            withContext(Dispatchers.Main) {
                                                if (!activity.isFinishing && !activity.isDestroyed) {
                                                    dialogHandle.updateProgress(percent)
                                                }
                                            }
                                        }
                                    }
                                }
                                output.flush()
                            }
                        }

                        if (totalRead <= 0L || (contentLength > 0L && totalRead != contentLength)) {
                            targetFile.delete()
                            return@withContext DownloadOutcome.Incomplete
                        }

                        val expectedSha256 = expectedSha256Provider?.invoke()?.trim()?.takeIf { it.isNotEmpty() }
                        if (expectedSha256 != null) {
                            val actualSha256 = computeFileSha256Hex(targetFile)
                            if (!matchesExpectedSha256(actualSha256, expectedSha256)) {
                                throw SecurityException(
                                    "Verificación SHA-256 fallida para $apkFileName: " +
                                        "esperado=${expectedSha256.lowercase()}, real=$actualSha256"
                                )
                            }
                        }
                        DownloadOutcome.Success(targetFile)
                    }
                }

                dialogHandle.dismissSafely(activity)
                if (activity.isFinishing || activity.isDestroyed) return@launch

                when (downloadResult) {
                    is DownloadOutcome.Success -> {
                        launchInstallerOrRequestPermission(activity, downloadResult.file, onInstallLaunched)
                    }
                    is DownloadOutcome.HttpError -> {
                        if (onErrorFallback != null) {
                            onErrorFallback(activity, downloadResult.code, null)
                        } else {
                            TvDialogHelper.show(
                                activity,
                                AlertDialog.Builder(activity)
                                    .setTitle("Error de descarga")
                                    .setMessage("El servidor respondió con código ${downloadResult.code}.")
                                    .setPositiveButton("Aceptar", null)
                            )
                        }
                    }
                    DownloadOutcome.Incomplete -> {
                        if (onErrorFallback != null) {
                            onErrorFallback(activity, null, IllegalStateException("Archivo incompleto"))
                        } else {
                            Toast.makeText(activity, "La descarga resultó incompleta. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (_: CancellationException) {
                apkFile?.delete()
                dialogHandle.dismissSafely(activity)
            } catch (e: Exception) {
                e.printStackTrace()
                apkFile?.delete()
                dialogHandle.dismissSafely(activity)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    if (onErrorFallback != null) {
                        onErrorFallback(activity, null, e)
                    } else {
                        Toast.makeText(activity, "Error durante la descarga: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun launchInstallerOrRequestPermission(
        activity: Activity,
        apkFile: File,
        onInstallLaunched: (() -> Unit)?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = try {
                activity.packageManager.canRequestPackageInstalls()
            } catch (_: Exception) {
                true
            }
            if (!canInstall) {
                TvDialogHelper.show(
                    activity,
                    AlertDialog.Builder(activity)
                        .setTitle("Permiso necesario para instalar")
                        .setMessage(
                            "Android requiere activar 'Instalar aplicaciones desconocidas' para Piña4Viewer antes de continuar con el archivo descargado."
                        )
                        .setPositiveButton("Abrir Ajustes") { _, _ ->
                            openUnknownSourcesSettings(activity)
                        }
                        .setNeutralButton("Intentar igualmente") { _, _ ->
                            if (installApk(activity, apkFile)) {
                                onInstallLaunched?.invoke()
                            }
                        }
                        .setNegativeButton("Cancelar", null)
                )
                return
            }
        }

        if (installApk(activity, apkFile)) {
            onInstallLaunched?.invoke()
        }
    }

    private fun openUnknownSourcesSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        try {
            activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(activity, "Activa 'Orígenes desconocidos' en los Ajustes del sistema.", Toast.LENGTH_LONG).show()
        }
    }

    fun installApk(activity: Activity, apkFile: File): Boolean {
        return try {
            val contentUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, "No se pudo abrir el instalador: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun downloadSpecAndInstall(
        activity: Activity,
        client: OkHttpClient,
        title: String,
        initialMessage: String,
        apkFileName: String,
        userAgent: String? = null,
        specProvider: suspend () -> RemoteApkSpec,
        onErrorFallback: ((Activity, Int?, Throwable?) -> Unit)? = null,
        onInstallLaunched: (() -> Unit)? = null
    ) {
        val resolvedSpec = java.util.concurrent.atomic.AtomicReference<RemoteApkSpec?>(null)
        downloadAndInstall(
            activity = activity,
            client = client,
            title = title,
            initialMessage = initialMessage,
            apkFileName = apkFileName,
            userAgent = userAgent,
            urlProvider = {
                val spec = specProvider()
                resolvedSpec.set(spec)
                spec.url
            },
            expectedSha256Provider = { resolvedSpec.get()?.expectedSha256 },
            onErrorFallback = onErrorFallback,
            onInstallLaunched = onInstallLaunched
        )
    }

    fun computeFileSha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun matchesExpectedSha256(actualHex: String, expectedHex: String?): Boolean {
        val normalizedExpected = expectedHex?.trim()?.lowercase()
        if (normalizedExpected.isNullOrEmpty()) return true
        if (normalizedExpected.length != 64 || !normalizedExpected.all { it in '0'..'9' || it in 'a'..'f' }) {
            return false
        }
        return actualHex.trim().lowercase() == normalizedExpected
    }

    fun fetchRemoteApkSpec(
        client: OkHttpClient,
        manifestUrl: String,
        urlKey: String,
        shaKey: String
    ): RemoteApkSpec? {
        return try {
            val sep = if ('?' in manifestUrl) '&' else '?'
            val request = Request.Builder()
                .url("$manifestUrl${sep}t=${System.currentTimeMillis()}")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.request.url.isHttps || !response.isSuccessful) return null
                val body = response.body?.string()
                if (body.isNullOrEmpty()) return null
                val json = org.json.JSONObject(body)
                val remoteUrl = json.optString(urlKey, "").trim()
                if (remoteUrl.startsWith("https://", ignoreCase = true)) {
                    val sha = json.optString(shaKey, "").trim().takeIf { it.isNotEmpty() }
                    return RemoteApkSpec(remoteUrl, sha)
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private sealed interface DownloadOutcome {
        data class Success(val file: File) : DownloadOutcome
        data class HttpError(val code: Int) : DownloadOutcome
        data object Incomplete : DownloadOutcome
    }
}
