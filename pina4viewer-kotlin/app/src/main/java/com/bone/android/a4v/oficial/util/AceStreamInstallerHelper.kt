package com.bone.android.a4v.oficial.util

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.appcompat.app.AlertDialog
import com.bone.android.a4v.oficial.ui.view.TvDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AceStreamInstallerHelper {

    private const val VERSION_URL = "https://raw.githubusercontent.com/ArribaJesucristo/pina4viewer/main/version.json"

    private const val DEFAULT_ATV_URL = "https://android.acestream.net/download/apk"
    private const val DEFAULT_MOBILE_URL = "https://android.acestream.net/download/apk"

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
        .followSslRedirects(false)
        .build()

    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasTvFeature = context.packageManager.hasSystemFeature("android.hardware.type.television")
        return isTvMode || hasLeanback || hasTvFeature
    }

    fun isAceStreamInstalled(context: Context): Boolean {
        val pm = context.packageManager
        val allPackages = TV_PACKAGES + MOBILE_PACKAGES
        for (pkg in allPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

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

        TvDialogHelper.show(
            activity,
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Instalar Ahora") { _, _ ->
                    startDownloadAndInstall(activity, isTv, onInstalled)
                }
                .setNegativeButton("Cancelar", null),
            preferPositiveFocus = true
        )
    }

    private suspend fun resolveDownloadSpec(isTv: Boolean): ApkDownloadManager.RemoteApkSpec = withContext(Dispatchers.IO) {
        val urlKey = if (isTv) "acestreamAtvUrl" else "acestreamMobileUrl"
        val shaKey = if (isTv) "acestreamAtvSha256" else "acestreamMobileSha256"
        val fallbackUrl = if (isTv) DEFAULT_ATV_URL else DEFAULT_MOBILE_URL
        ApkDownloadManager.fetchRemoteApkSpec(
            client = client,
            manifestUrl = VERSION_URL,
            urlKey = urlKey,
            shaKey = shaKey
        ) ?: ApkDownloadManager.RemoteApkSpec(fallbackUrl)
    }

    fun startDownloadAndInstall(
        activity: Activity,
        isTv: Boolean = isTvDevice(activity),
        onInstalled: (() -> Unit)? = null
    ) {
        val verName = UpdateHelper.getAppVersionName(activity)
        ApkDownloadManager.downloadSpecAndInstall(
            activity = activity,
            client = client,
            title = "Instalando reproductor deportivo",
            initialMessage = "Descargando AceStream Pro (${if (isTv) "Android TV" else "Móvil"})...\nPor favor espera unos segundos.",
            apkFileName = "acestream_pro.apk",
            userAgent = "Pina4Viewer/$verName",
            specProvider = { resolveDownloadSpec(isTv) },
            onErrorFallback = { act, code, err ->
                val errorMsg = when (code) {
                    404 -> "El archivo APK aún no está disponible en el servidor (404)."
                    null -> "Error durante la descarga: ${err?.localizedMessage ?: "desconocido"}"
                    else -> "Error en el servidor al descargar: Código $code"
                }
                TvDialogHelper.show(
                    act,
                    AlertDialog.Builder(act)
                        .setTitle("Aviso de Descarga")
                        .setMessage(errorMsg)
                        .setPositiveButton("Aceptar", null),
                    preferPositiveFocus = true
                )
            },
            onInstallLaunched = onInstalled
        )
    }
}
