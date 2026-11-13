package com.bone.android.a4v.oficial.util

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.bone.android.a4v.oficial.ui.view.TvDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object VpnInstallerHelper {

    private const val VERSION_URL = "https://raw.githubusercontent.com/ArribaJesucristo/pina4viewer/main/version.json"
    private const val DEFAULT_PSIPHON_URL = "https://github.com/ArribaJesucristo/pina4viewer/releases/download/v8.4.3/psiphon-classic-tv.apk"

    const val PSIPHON_PACKAGE = "com.psiphon3"
    const val PSIPHON_PACKAGE_PLAY = "com.psiphon3.subscription"

    private val client = OkHttpClient.Builder()
        .dns(DnsHelper.customDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    fun isPsiphonInstalled(context: Context): Boolean {
        return getInstalledPsiphonPackage(context) != null
    }

    fun getInstalledPsiphonPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in listOf(PSIPHON_PACKAGE, PSIPHON_PACKAGE_PLAY)) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) {
            }
        }
        return null
    }

    private suspend fun resolveDownloadSpec(): ApkDownloadManager.RemoteApkSpec = withContext(Dispatchers.IO) {
        ApkDownloadManager.fetchRemoteApkSpec(
            client = client,
            manifestUrl = VERSION_URL,
            urlKey = "psiphonUrl",
            shaKey = "psiphonSha256"
        ) ?: ApkDownloadManager.RemoteApkSpec(DEFAULT_PSIPHON_URL)
    }

    fun startDownloadAndInstall(
        activity: Activity,
        onInstalled: (() -> Unit)? = null
    ) {
        ApkDownloadManager.downloadSpecAndInstall(
            activity = activity,
            client = client,
            title = "🛡️ Instalando VPN Anti-Bloqueos",
            initialMessage = "Descargando Psiphon (Sin cuentas ni contraseñas)...\nPor favor espera unos segundos.",
            apkFileName = "psiphon.apk",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            specProvider = { resolveDownloadSpec() },
            onErrorFallback = { act, code, err ->
                val details = if (code != null) {
                    "No se pudo descargar el instalador: Código $code.\nPuedes instalarlo manualmente desde Ajustes."
                } else {
                    "Error durante la descarga: ${err?.localizedMessage ?: "desconocido"}"
                }
                TvDialogHelper.show(
                    act,
                    AlertDialog.Builder(act)
                        .setTitle("Error de Descarga")
                        .setMessage(details)
                        .setPositiveButton("Aceptar", null),
                    preferPositiveFocus = true
                )
            },
            onInstallLaunched = onInstalled
        )
    }
}
