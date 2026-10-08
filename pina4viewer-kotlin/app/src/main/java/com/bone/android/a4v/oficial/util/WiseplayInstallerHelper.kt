package com.bone.android.a4v.oficial.util

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object WiseplayInstallerHelper {

    private const val VERSION_URL = "https://raw.githubusercontent.com/ArribaJesucristo/pina4viewer/main/version.json"
    private const val DEFAULT_TV_URL = "https://static.wiseplay.tv/files/wiseplay-tv-latest.apk"
    private const val DEFAULT_MOBILE_URL = "https://static.wiseplay.tv/files/wiseplay-latest.apk"
    const val WISEPLAY_WEB_URL = "https://wiseplay.tv/download/"

    val WISEPLAY_PACKAGES = listOf("com.wiseplay", "tv.wiseplay")

    private val client = OkHttpClient.Builder()
        .dns(DnsHelper.customDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Checks if any Wiseplay package is installed on the device.
     */
    fun isWiseplayInstalled(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in WISEPLAY_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    /**
     * Resolves the Wiseplay download URL from version.json or defaults to official Wiseplay CDN links.
     */
    private suspend fun resolveDownloadUrl(isTv: Boolean): String = withContext(Dispatchers.IO) {
        try {
            val urlWithBuster = "$VERSION_URL?t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(urlWithBuster)
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val urlKey = if (isTv) "wiseplayTvUrl" else "wiseplayMobileUrl"
                        val remoteUrl = json.optString(urlKey, "")
                        if (remoteUrl.isNotBlank() && remoteUrl.startsWith("http")) {
                            return@withContext remoteUrl
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (isTv) DEFAULT_TV_URL else DEFAULT_MOBILE_URL
    }

    /**
     * Prompts the user with a dialog to download and install Wiseplay.
     */
    fun promptInstallDialog(
        activity: Activity,
        force: Boolean = false,
        onInstalled: (() -> Unit)? = null
    ) {
        if (!force && isWiseplayInstalled(activity)) {
            onInstalled?.invoke()
            return
        }

        val isTv = AceStreamInstallerHelper.isTvDevice(activity)
        val deviceDesc = if (isTv) "Android TV / Fire TV" else "Móvil / Tablet"

        val title = if (force) "▶️ Instalar / Actualizar Wiseplay" else "📺 Reproductor recomendado"
        val message = "Wiseplay es el reproductor recomendado para $deviceDesc.\n\n" +
                "• Reproduce transmisiones AceStream de forma limpia y directa.\n" +
                "• Sin carteles de pago ni suscripciones Prime de AceStream.\n\n" +
                "¿Deseas descargar e instalar la versión oficial de Wiseplay?"

        val builder = AlertDialog.Builder(activity)
            .setTitle(title)

        if (isTv) {
            builder.setMessage(message)
                .setPositiveButton("Descargar e Instalar") { _, _ ->
                    startDownloadAndInstall(activity, isTv = true, onInstalled)
                }
                .setNeutralButton("🌐 Web Oficial") { _, _ ->
                    openInBrowser(activity, WISEPLAY_WEB_URL)
                }
                .setNegativeButton("Cancelar", null)
        } else {
            builder.setMessage("$message\n\nElige la versión a descargar:")
                .setPositiveButton("Android TV") { _, _ ->
                    startDownloadAndInstall(activity, isTv = true, onInstalled)
                }
                .setNeutralButton("Móvil") { _, _ ->
                    startDownloadAndInstall(activity, isTv = false, onInstalled)
                }
                .setNegativeButton("Cancelar", null)
        }

        builder.show()
    }

    /**
     * Downloads the Wiseplay APK file displaying progress, then opens Android PackageInstaller.
     */
    fun startDownloadAndInstall(
        activity: Activity,
        isTv: Boolean = AceStreamInstallerHelper.isTvDevice(activity),
        onInstalled: (() -> Unit)? = null
    ) {
        val targetName = if (isTv) "Wiseplay (Android TV)" else "Wiseplay (Móvil)"
        val progressDialog = ProgressDialog(activity).apply {
            setTitle("Instalando reproductor Wiseplay")
            setMessage("Descargando $targetName desde la web oficial...\nPor favor espera unos segundos.")
            isIndeterminate = false
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val downloadUrl = resolveDownloadUrl(isTv)

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            AlertDialog.Builder(activity)
                                .setTitle("Error de Descarga")
                                .setMessage("No se pudo descargar automáticamente Wiseplay (Código ${response.code}).\n\n¿Deseas abrir la web oficial en el navegador?")
                                .setPositiveButton("Abrir Web") { _, _ ->
                                    openInBrowser(activity, WISEPLAY_WEB_URL)
                                }
                                .setNegativeButton("Cerrar", null)
                                .show()
                        }
                        return@launch
                    }

                    val body = response.body ?: return@launch
                    val contentLength = body.contentLength()
                    val targetDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.cacheDir
                    val apkFileName = if (isTv) "wiseplay_tv.apk" else "wiseplay_mobile.apk"
                    val apkFile = File(targetDir, apkFileName)

                    body.byteStream().use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalRead = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                if (contentLength > 0) {
                                    val progress = ((totalRead * 100) / contentLength).toInt()
                                    withContext(Dispatchers.Main) {
                                        progressDialog.progress = progress
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        installApk(activity, apkFile)
                        onInstalled?.invoke()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    AlertDialog.Builder(activity)
                        .setTitle("Aviso de Instalación")
                        .setMessage("Hubo un problema descargando el archivo: ${e.localizedMessage}\n\nPuedes descargarlo directamente desde su web oficial.")
                        .setPositiveButton("Abrir Web Oficial") { _, _ ->
                            openInBrowser(activity, WISEPLAY_WEB_URL)
                        }
                        .setNegativeButton("Cerrar", null)
                        .show()
                }
            }
        }
    }

    /**
     * Launches Android PackageInstaller via FileProvider.
     */
    private fun installApk(activity: Activity, apkFile: File) {
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, "No se pudo abrir el instalador: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo abrir el navegador: $url", Toast.LENGTH_SHORT).show()
        }
    }
}
