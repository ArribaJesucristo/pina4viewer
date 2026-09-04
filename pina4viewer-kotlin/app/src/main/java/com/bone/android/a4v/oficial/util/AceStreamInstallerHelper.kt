package com.bone.android.a4v.oficial.util

import android.app.Activity
import android.app.ProgressDialog
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
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

object AceStreamInstallerHelper {

    private const val VERSION_URL = "https://raw.githubusercontent.com/ArribaJesucristo/pina4viewer/main/version.json"

    // Default fallback direct download URLs (Ace Stream Pro is universal and auto-updates)
    private const val DEFAULT_ATV_URL = "https://android.acestream.net/download/apk"
    private const val DEFAULT_MOBILE_URL = "https://android.acestream.net/download/apk"

    // Recognized AceStream package names
    val TV_PACKAGES = listOf(
        "org.acestream.node.web",
        "org.acestream.node",
        "org.acestream.media.atv",
        "org.acestream.core.atv"
    )

    val MOBILE_PACKAGES = listOf(
        "org.acestream.media",
        "org.acestream.core",
        "org.acestream.node",
        "org.acestream.node.web",
        "org.acestream.core.web",
        "org.acestream.media.web",
        "org.acestream.engine",
        "org.acestream.engine.web"
    )

    private val client = OkHttpClient.Builder()
        .dns(DnsHelper.customDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Determines whether the current device is an Android TV / Fire TV Stick or a Mobile / Tablet.
     */
    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasTvFeature = context.packageManager.hasSystemFeature("android.hardware.type.television")
        return isTvMode || hasLeanback || hasTvFeature
    }

    /**
     * Checks if any AceStream engine or player is installed on the device.
     */
    fun isAceStreamInstalled(context: Context): Boolean {
        val pm = context.packageManager
        val allPackages = TV_PACKAGES + MOBILE_PACKAGES
        for (pkg in allPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (_: Exception) {
                // Not installed under this package name
            }
        }
        return false
    }

    /**
     * Prompts the user with a simple, grandmother-friendly dialog to install AceStream.
     * On positive click, downloads the appropriate APK and launches Android's package installer.
     */
    fun promptInstallDialog(
        activity: Activity,
        force: Boolean = false,
        onInstalled: (() -> Unit)? = null
    ) {
        if (!force && isAceStreamInstalled(activity)) {
            onInstalled?.invoke()
            return
        }

        val isTv = isTvDevice(activity)
        val deviceDesc = if (isTv) "Android TV / Fire TV" else "Móvil / Tablet"

        val title = if (force) "Instalar / Actualizar AceStream" else "📺 Reproductor necesario"
        val message = if (force) {
            "¿Deseas descargar e instalar la versión más reciente del reproductor deportivo para $deviceDesc?"
        } else {
            "Para ver este canal se necesita el reproductor deportivo.\n\nSolo se instala una vez ($deviceDesc).\n\n¿Deseas instalarlo ahora?"
        }

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Instalar Ahora") { _, _ ->
                startDownloadAndInstall(activity, isTv, onInstalled)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Resolves the download URL from version.json or defaults to GitHub Release assets.
     */
    private suspend fun resolveDownloadUrl(isTv: Boolean): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(VERSION_URL)
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val urlKey = if (isTv) "acestreamAtvUrl" else "acestreamMobileUrl"
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

        if (isTv) DEFAULT_ATV_URL else DEFAULT_MOBILE_URL
    }

    /**
     * Downloads the APK file displaying a horizontal progress dialog, then opens the Android installer.
     */
    fun startDownloadAndInstall(
        activity: Activity,
        isTv: Boolean = isTvDevice(activity),
        onInstalled: (() -> Unit)? = null
    ) {
        val progressDialog = ProgressDialog(activity).apply {
            setTitle("Instalando reproductor deportivo")
            setMessage("Descargando AceStream Pro (${if (isTv) "Android TV" else "Móvil"})...\nPor favor espera unos segundos.")
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
                    .header("User-Agent", "Pina4Viewer/${UpdateHelper.getAppVersionName(activity)}")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            val errorMsg = when (response.code) {
                                404 -> "El archivo APK aún no está disponible en el servidor (404).\nSube el APK a GitHub Releases o actualiza version.json."
                                else -> "Error en el servidor al descargar: Código ${response.code}"
                            }
                            AlertDialog.Builder(activity)
                                .setTitle("Aviso de Descarga")
                                .setMessage(errorMsg)
                                .setPositiveButton("Aceptar", null)
                                .show()
                        }
                        return@launch
                    }

                    val body = response.body ?: return@launch
                    val contentLength = body.contentLength()
                    val targetDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.cacheDir
                    val apkFileName = "acestream_pro.apk"
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
                    Toast.makeText(activity, "Error durante la descarga: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Launches the Android system package installer via FileProvider.
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
}

