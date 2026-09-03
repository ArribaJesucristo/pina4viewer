package com.bone.android.a4v.oficial.util

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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

data class UpdateInfo(
    val hasUpdate: Boolean,
    val remoteVersionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String
)

object UpdateHelper {

    private const val VERSION_URL = "https://raw.githubusercontent.com/ArribaJesucristo/pina4viewer/main/version.json"

    private val client = OkHttpClient.Builder()
        .dns(DnsHelper.customDns)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(VERSION_URL)
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)

                val remoteVersionCode = json.optInt("versionCode", 0)
                val versionName = json.optString("versionName", "")
                val apkUrl = json.optString("apkUrl", "")
                val changelog = json.optString("changelog", "")

                val currentVersionCode = getAppVersionCode(context)

                UpdateInfo(
                    hasUpdate = remoteVersionCode > currentVersionCode,
                    remoteVersionCode = remoteVersionCode,
                    versionName = versionName,
                    apkUrl = apkUrl,
                    changelog = changelog
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun promptUpdateDialog(activity: Activity, updateInfo: UpdateInfo) {
        AlertDialog.Builder(activity)
            .setTitle("🚀 Actualización Disponible: v${updateInfo.versionName}")
            .setMessage("${updateInfo.changelog}\n\n¿Deseas descargar e instalar la nueva versión ahora?")
            .setPositiveButton("Actualizar Ahora") { _, _ ->
                startDownloadAndInstall(activity, updateInfo.apkUrl, updateInfo.versionName)
            }
            .setNegativeButton("Más tarde", null)
            .show()
    }

    private fun startDownloadAndInstall(activity: Activity, apkUrl: String, versionName: String) {
        val progressDialog = ProgressDialog(activity).apply {
            setTitle("Descargando actualización")
            setMessage("Descargando Piña4Viewer v$versionName...")
            isIndeterminate = false
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(apkUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            Toast.makeText(activity, "Error al descargar el archivo de actualización", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    val body = response.body ?: return@launch
                    val contentLength = body.contentLength()
                    val targetDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.cacheDir
                    val apkFile = File(targetDir, "pina4viewer_update.apk")

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

    fun getAppVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    fun getAppVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "8.0.0"
        } catch (e: Exception) {
            "8.0.0"
        }
    }
}

