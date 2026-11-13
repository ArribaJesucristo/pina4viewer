package com.bone.android.a4v.oficial.util

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AlertDialog
import com.bone.android.a4v.oficial.ui.view.TvDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean,
    val remoteVersionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val apkSha256: String? = null
)

object UpdateHelper {

    private const val VERSION_URL = "https://raw.githubusercontent.com/ArribaJesucristo/pina4viewer/main/version.json"

    private val client = OkHttpClient.Builder()
        .dns(DnsHelper.customDns)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .build()

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val urlWithBuster = "$VERSION_URL?t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(urlWithBuster)
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)

                val remoteVersionCode = json.optInt("versionCode", 0)
                val versionName = json.optString("versionName", "")
                val apkUrl = json.optString("apkUrl", "")
                val changelog = json.optString("changelog", "")
                val apkSha256 = json.optString("apkSha256", "").trim().takeIf { it.isNotEmpty() }

                val currentVersionCode = getAppVersionCode(context)

                UpdateInfo(
                    hasUpdate = remoteVersionCode > currentVersionCode,
                    remoteVersionCode = remoteVersionCode,
                    versionName = versionName,
                    apkUrl = apkUrl,
                    changelog = changelog,
                    apkSha256 = apkSha256
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun promptUpdateDialog(activity: Activity, updateInfo: UpdateInfo) {
        TvDialogHelper.show(
            activity,
            AlertDialog.Builder(activity)
                .setTitle("🚀 Actualización Disponible: v${updateInfo.versionName}")
                .setMessage("${updateInfo.changelog}\n\n¿Deseas descargar e instalar la nueva versión ahora?")
                .setPositiveButton("Actualizar Ahora") { _, _ ->
                    startDownloadAndInstall(activity, updateInfo.apkUrl, updateInfo.versionName, updateInfo.apkSha256)
                }
                .setNegativeButton("Más tarde", null),
            preferPositiveFocus = true
        )
    }

    private fun startDownloadAndInstall(
        activity: Activity,
        apkUrl: String,
        versionName: String,
        apkSha256: String? = null
    ) {
        ApkDownloadManager.downloadAndInstall(
            activity = activity,
            client = client,
            title = "Descargando actualización",
            initialMessage = "Descargando Piña4Viewer v$versionName...",
            apkFileName = "pina4viewer_update.apk",
            urlProvider = { apkUrl },
            expectedSha256Provider = { apkSha256 }
        )
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
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "8.4.3"
        } catch (e: Exception) {
            "8.4.3"
        }
    }
}
