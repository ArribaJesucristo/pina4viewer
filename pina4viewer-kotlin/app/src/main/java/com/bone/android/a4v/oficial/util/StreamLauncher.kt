package com.bone.android.a4v.oficial.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.StreamType

object StreamLauncher {

    const val PACKAGE_ACESTREAM = "org.acestream.media"
    const val PACKAGE_ACESTREAM_ATV = "org.acestream.media.atv"
    const val PACKAGE_WISEPLAY = "com.wiseplay"
    const val PACKAGE_WISEPLAY_TV = "tv.wiseplay"
    const val PACKAGE_MX_PLAYER = "com.mxtech.videoplayer.ad"
    const val PACKAGE_VLC = "org.videolan.vlc"

    private val HASH_REGEX = Regex("^[a-fA-F0-9]{40}$")

    fun launchChannel(
        context: Context,
        channel: ChannelItem,
        onWebFallback: ((String) -> Unit)? = null
    ) {
        val streamId = channel.streamId.trim()

        when (channel.type) {
            StreamType.ACESTREAM -> {
                if (streamId.startsWith("http://", ignoreCase = true) || streamId.startsWith("https://", ignoreCase = true)) {
                    launchHttpByContentHint(context, streamId, onWebFallback)
                    return
                }
                val resolvedHash = resolveHash(streamId)
                if (resolvedHash != null) {
                    launchAceStream(context, resolvedHash)
                } else {
                    Toast.makeText(
                        context,
                        "⚠️ El canal ${channel.name} no está emitiendo en este momento. Prueba otra opción.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            StreamType.SOPCAST -> launchSopCast(context, streamId)
            StreamType.WEB -> {
                val url = (channel.rawUrl ?: streamId).trim()
                if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                    if (onWebFallback != null) onWebFallback(url) else openInBrowser(context, url)
                } else {
                    Toast.makeText(context, "Enlace web no válido.", Toast.LENGTH_SHORT).show()
                }
            }
            StreamType.DIRECT -> launchDirectMedia(context, streamId, onWebFallback)
        }
    }

    private fun launchHttpByContentHint(
        context: Context,
        url: String,
        onWebFallback: ((String) -> Unit)?
    ) {
        if (url.contains(".m3u8", ignoreCase = true) || url.contains(".mp4", ignoreCase = true) || url.contains(".ts", ignoreCase = true)) {
            launchDirectMedia(context, url, onWebFallback)
        } else if (onWebFallback != null) {
            onWebFallback(url)
        } else {
            openInBrowser(context, url)
        }
    }

    internal fun resolveHash(streamId: String): String? {
        val clean = streamId.removePrefix("acestream://").trim()
        val upper = clean.uppercase()
        if (upper.startsWith("AV")) {
            val num = upper.removePrefix("AV")
            val live = com.bone.android.a4v.oficial.data.parser.ArenaVisionParser.cachedStreams[num]
            if (!live.isNullOrBlank() && live.matches(HASH_REGEX)) {
                return live
            }
            return null
        }
        if (clean.matches(HASH_REGEX)) {
            if (clean == "ac74b93821038290182301928301293810293812" || clean.all { it == clean[0] }) {
                return null
            }
            return clean
        }
        return null
    }

    fun launchAceStream(context: Context, infoHashOrUrl: String) {
        val cleanHash = infoHashOrUrl.removePrefix("acestream://").trim()
        val uri = Uri.parse("acestream://$cleanHash")

        val isAceInstalled = AceStreamInstallerHelper.isAceStreamInstalled(context)
        val isWiseplayInstalled = WiseplayInstallerHelper.isWiseplayInstalled(context) ||
            isAppInstalled(context, PACKAGE_WISEPLAY) ||
            isAppInstalled(context, PACKAGE_WISEPLAY_TV)

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

        val handlers = try {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Exception) {
            emptyList()
        }

        when {
            handlers.isEmpty() -> {
                if (context is Activity) {
                    AceStreamInstallerHelper.promptInstallDialog(context) {
                        launchAceStream(context, infoHashOrUrl)
                    }
                } else {
                    openStoreOrReleasesFallback(context)
                }
            }
            handlers.size == 1 -> {
                try {
                    val onlyHandler = handlers.first().activityInfo
                    intent.setClassName(onlyHandler.packageName, onlyHandler.name)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    openStoreOrReleasesFallback(context)
                }
            }
            else -> {
                val chooser = Intent.createChooser(intent, "Abrir transmisión en...").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(chooser)
                } catch (_: Exception) {
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        openStoreOrReleasesFallback(context)
                    }
                }
            }
        }
    }

    private fun openStoreOrReleasesFallback(context: Context) {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.acestream.media")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            Toast.makeText(context, "Instala un reproductor compatible (AceStream o Wiseplay).", Toast.LENGTH_SHORT).show()
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            openInBrowser(context, "https://github.com/ArribaJesucristo/pina4viewer/releases")
        }
    }

    fun launchSopCast(context: Context, sopAddress: String) {
        val uriString = if (sopAddress.startsWith("sop://")) sopAddress else "sop://$sopAddress"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "SopCast no encontrado en el dispositivo.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchDirectMedia(
        context: Context,
        url: String,
        onWebFallback: ((String) -> Unit)? = null
    ) {
        val mimeType = if (url.contains(".m3u8", ignoreCase = true)) {
            "application/x-mpegURL"
        } else {
            "video/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), mimeType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val handlers = try {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Exception) {
            emptyList()
        }
        if (handlers.isNotEmpty()) {
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }

        // Reintentar con MIME genérico si ninguna app declaró application/x-mpegURL explícitamente
        if (mimeType != "video/*") {
            val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "video/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(genericIntent)
                return
            } catch (_: Exception) {
            }
        }

        if (onWebFallback != null) {
            onWebFallback(url)
        } else {
            openInBrowser(context, url)
        }
    }

    fun openInBrowser(context: Context, url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            Toast.makeText(context, "Enlace no soportado.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No se pudo abrir el enlace.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
