package com.bone.android.a4v.oficial.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bone.android.a4v.oficial.ui.view.TvDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
        .followSslRedirects(false)
        .build()

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

    private suspend fun resolveDownloadSpec(isTv: Boolean): ApkDownloadManager.RemoteApkSpec = withContext(Dispatchers.IO) {
        val urlKey = if (isTv) "wiseplayTvUrl" else "wiseplayMobileUrl"
        val shaKey = if (isTv) "wiseplayTvSha256" else "wiseplayMobileSha256"
        val fallbackUrl = if (isTv) DEFAULT_TV_URL else DEFAULT_MOBILE_URL
        ApkDownloadManager.fetchRemoteApkSpec(
            client = client,
            manifestUrl = VERSION_URL,
            urlKey = urlKey,
            shaKey = shaKey
        ) ?: ApkDownloadManager.RemoteApkSpec(fallbackUrl)
    }

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

        TvDialogHelper.show(activity, builder, preferPositiveFocus = true)
    }

    fun startDownloadAndInstall(
        activity: Activity,
        isTv: Boolean = AceStreamInstallerHelper.isTvDevice(activity),
        onInstalled: (() -> Unit)? = null
    ) {
        val targetName = if (isTv) "Wiseplay (Android TV)" else "Wiseplay (Móvil)"
        ApkDownloadManager.downloadSpecAndInstall(
            activity = activity,
            client = client,
            title = "Instalando reproductor Wiseplay",
            initialMessage = "Descargando $targetName desde la web oficial...\nPor favor espera unos segundos.",
            apkFileName = if (isTv) "wiseplay_tv.apk" else "wiseplay_mobile.apk",
            userAgent = "Mozilla/5.0 (Android; Mobile)",
            specProvider = { resolveDownloadSpec(isTv) },
            onErrorFallback = { act, code, err ->
                val message = if (code != null) {
                    "No se pudo descargar automáticamente Wiseplay (Código $code).\n\n¿Deseas abrir la web oficial en el navegador?"
                } else {
                    "Hubo un problema descargando el archivo: ${err?.localizedMessage ?: "desconocido"}.\n\nPuedes descargarlo directamente desde su web oficial."
                }
                TvDialogHelper.show(
                    act,
                    AlertDialog.Builder(act)
                        .setTitle("Aviso de Instalación")
                        .setMessage(message)
                        .setPositiveButton("Abrir Web Oficial") { _, _ ->
                            openInBrowser(act, WISEPLAY_WEB_URL)
                        }
                        .setNegativeButton("Cerrar", null),
                    preferPositiveFocus = true
                )
            },
            onInstallLaunched = onInstalled
        )
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
