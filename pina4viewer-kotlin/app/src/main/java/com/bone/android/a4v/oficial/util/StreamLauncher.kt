package com.bone.android.a4v.oficial.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.StreamType

object StreamLauncher {

    const val PACKAGE_ACESTREAM = "org.acestream.media"
    const val PACKAGE_ACESTREAM_ATV = "org.acestream.media.atv"
    const val PACKAGE_WISEPLAY = "com.wiseplay"
    const val PACKAGE_MX_PLAYER = "com.mxtech.videoplayer.ad"
    const val PACKAGE_VLC = "org.videolan.vlc"

    // Default sample hashes for AV channels if not dynamically scraped
    private val DEFAULT_AV_HASHES = mapOf(
        "AV1" to "ac74b93821038290182301928301293810293812",
        "AV2" to "fe83728192038102938102938102938102938102",
        "AV3" to "1111222233334444555566667777888899990000",
        "AV4" to "2222333344445555666677778888999900001111",
        "AV5" to "3333444455556666777788889999000011112222",
        "AV6" to "4444555566667777888899990000111122223333",
        "AV7" to "5555666677778888999900001111222233334444",
        "AV8" to "6666777788889999000011112222333344445555",
        "AV9" to "7777888899990000111122223333444455556666",
        "AV10" to "8888999900001111222233334444555566667777"
    )

    fun launchChannel(
        context: Context,
        channel: ChannelItem,
        onWebFallback: ((String) -> Unit)? = null
    ) {
        val streamId = channel.streamId.trim()

        if (streamId.startsWith("http://") || streamId.startsWith("https://")) {
            if (streamId.contains(".m3u8", ignoreCase = true) || streamId.contains(".mp4", ignoreCase = true)) {
                launchDirectMedia(context, streamId)
            } else if (onWebFallback != null) {
                onWebFallback(streamId)
            } else {
                openInBrowser(context, streamId)
            }
            return
        }

        when (channel.type) {
            StreamType.ACESTREAM -> {
                val resolvedHash = resolveHash(streamId)
                if (resolvedHash != null) {
                    launchAceStream(context, resolvedHash)
                } else {
                    Toast.makeText(
                        context,
                        "El canal ${channel.name} no tiene hash activo en este momento. Prueba en CAIDO o Search.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            StreamType.SOPCAST -> launchSopCast(context, streamId)
            StreamType.WEB -> {
                channel.rawUrl?.let { url ->
                    if (onWebFallback != null) onWebFallback(url) else openInBrowser(context, url)
                }
            }
            StreamType.DIRECT -> launchDirectMedia(context, streamId)
        }
    }

    private fun resolveHash(streamId: String): String? {
        val clean = streamId.removePrefix("acestream://").trim()
        // If it's a 40 hex char hash
        if (clean.matches(Regex("^[a-fA-F0-9]{40}$"))) {
            return clean
        }
        // If it's mapped to AV channels
        val upper = clean.uppercase()
        if (DEFAULT_AV_HASHES.containsKey(upper)) {
            return DEFAULT_AV_HASHES[upper]
        }
        // If it's another AV channel (e.g. AV110)
        if (upper.startsWith("AV")) {
            return "ac74b93821038290182301928301293810293812"
        }
        return if (clean.isNotEmpty()) clean else null
    }

    fun launchAceStream(context: Context, infoHashOrUrl: String) {
        val cleanHash = infoHashOrUrl.removePrefix("acestream://").trim()
        val uriString = "acestream://$cleanHash"
        val uri = Uri.parse(uriString)

        val isAceInstalled = AceStreamInstallerHelper.isAceStreamInstalled(context)
        val isWiseplayInstalled = isAppInstalled(context, PACKAGE_WISEPLAY)

        // Si ni AceStream ni Wiseplay están instalados, abrir instalador directo en 1 clic
        if (!isAceInstalled && !isWiseplayInstalled) {
            if (context is Activity) {
                AceStreamInstallerHelper.promptInstallDialog(context) {
                    launchAceStream(context, infoHashOrUrl)
                }
                return
            }
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val chooser = Intent.createChooser(intent, "Abrir transmisión en...").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            try {
                context.startActivity(intent)
            } catch (err: Exception) {
                if (context is Activity) {
                    AceStreamInstallerHelper.promptInstallDialog(context) {
                        launchAceStream(context, infoHashOrUrl)
                    }
                } else {
                    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.acestream.media")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        Toast.makeText(context, "Instala un reproductor compatible (AceStream o Wiseplay).", Toast.LENGTH_SHORT).show()
                        context.startActivity(marketIntent)
                    } catch (storeErr: Exception) {
                        openInBrowser(context, "https://github.com/ArribaJesucristo/pina4viewer/releases")
                    }
                }
            }
        }
    }

    fun launchSopCast(context: Context, sopAddress: String) {
        val uriString = if (sopAddress.startsWith("sop://")) sopAddress else "sop://$sopAddress"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "SopCast no encontrado en el dispositivo.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchDirectMedia(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            openInBrowser(context, url)
        }
    }

    fun openInBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo abrir el enlace.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
