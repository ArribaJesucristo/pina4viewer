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

object VpnInstallerHelper {

    private const val VERSION_URL = "https://raw.githubusercontent.com/ArribaJesucristo/pina4viewer/main/version.json"
    private const val DEFAULT_PSIPHON_URL = "https://psiphon.ca/PsiphonAndroid.apk"

    const val PSIPHON_PACKAGE = "com.psiphon3"

    private val client = OkHttpClient.Builder()
        .dns(DnsHelper.customDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun isPsiphonInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PSIPHON_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun resolveDownloadUrl(): String = withContext(Dispatchers.IO) {
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
                        val remoteUrl = json.optString("psiphonUrl", "")
                        if (remoteUrl.isNotBlank() && remoteUrl.startsWith("http")) {
                            return@withContext remoteUrl
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        DEFAULT_PSIPHON_URL
    }

    fun startDownloadAndInstall(
        activity: Activity,
        onInstalled: (() -> Unit)? = null
    ) {
        val progressDialog = ProgressDialog(activity).apply {
            setTitle("🛡️ Instalando VPN Anti-Bloqueos")
            setMessage("Descargando Psiphon (Sin cuentas ni contraseñas)...\nPor favor espera unos segundos.")
            isIndeterminate = false
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val downloadUrl = resolveDownloadUrl()

                // Standard desktop browser User-Agent is required to prevent 403 Forbidden from psiphon.ca
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            AlertDialog.Builder(activity)
                                .setTitle("Error de Descarga")
                                .setMessage("No se pudo descargar el instalador: Código ${response.code}.\nPuedes instalarlo manualmente desde Ajustes.")
                                .setPositiveButton("Aceptar", null)
                                .show()
                        }
                        return@launch
                    }

                    val body = response.body ?: return@launch
                    val contentLength = body.contentLength()
                    val targetDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.cacheDir
                    val apkFile = File(targetDir, "psiphon.apk")

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
