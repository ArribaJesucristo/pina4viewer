package com.bone.android.a4v.oficial.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import android.widget.Toast
import com.bone.android.a4v.oficial.vpn.PinaVpnService

object VpnHelper {

    data class VpnApp(val name: String, val packageName: String, val website: String)

    const val PACKAGE_CLOUDFLARE_WARP = "com.cloudflare.onedotonedotonedotone"

    val PROTON_VPN = VpnApp("Proton VPN", "ch.protonvpn.android", "https://protonvpn.com/download-android")

    val KNOWN_VPN_APPS = listOf(
        PROTON_VPN,
        VpnApp("NordVPN", "com.nordvpn.android", "https://nordvpn.com/"),
        VpnApp("Surfshark", "com.surfshark.vpnclient.android", "https://surfshark.com/"),
        VpnApp("Windscribe", "com.windscribe.vpn", "https://windscribe.com/"),
        VpnApp("ExpressVPN", "com.expressvpn.vpn", "https://www.expressvpn.com/"),
        VpnApp("CyberGhost", "de.mobileconcepts.cyberghost", "https://www.cyberghostvpn.com/"),
        VpnApp("Mullvad", "net.mullvad.mullvadvpn", "https://mullvad.net/"),
        VpnApp("WireGuard", "com.wireguard.android", "https://www.wireguard.com/"),
        VpnApp("OpenVPN", "net.openvpn.openvpn", "https://openvpn.net/"),
        VpnApp("OpenVPN for Android", "de.blinkt.openvpn", "https://ics-openvpn.blinkt.de/")
    )

    val vpnStateFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun isProtonVpnInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PROTON_VPN.packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launchProtonVpn(context: Context) {
        launchVpnApp(context, PROTON_VPN)
    }

    fun openProtonVpnInstall(context: Context) {
        val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${PROTON_VPN.packageName}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            Toast.makeText(context, "Abriendo Proton VPN en Google Play...", Toast.LENGTH_SHORT).show()
            context.startActivity(storeIntent)
        } catch (e: Exception) {
            openWebUrl(context, PROTON_VPN.website)
        }
    }

    fun getInstalledVpnApp(context: Context): VpnApp? {
        val pm = context.packageManager
        for (vpn in KNOWN_VPN_APPS) {
            try {
                pm.getPackageInfo(vpn.packageName, 0)
                return vpn
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun launchVpnApp(context: Context, vpn: VpnApp) {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(vpn.packageName)
        if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            Toast.makeText(context, "🚀 Abriendo ${vpn.name}...", Toast.LENGTH_SHORT).show()
            context.startActivity(intent)
        } else {
            val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${vpn.packageName}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                Toast.makeText(context, "Abriendo ${vpn.name} en Google Play...", Toast.LENGTH_SHORT).show()
                context.startActivity(storeIntent)
            } catch (e: Exception) {
                openWebUrl(context, vpn.website)
            }
        }
    }

    fun isBuiltInVpnActive(): Boolean = PinaVpnService.isVpnActive

    fun isExternalVpnActive(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } else {
                @Suppress("DEPRECATION")
                val networks = cm.allNetworks ?: return false
                for (network in networks) {
                    val caps = cm.getNetworkCapabilities(network)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return true
                    }
                }
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun isVpnActive(context: Context): Boolean {
        return isExternalVpnActive(context)
    }

    fun updateState(context: Context) {
        try {
            vpnStateFlow.value = isVpnActive(context)
        } catch (_: Throwable) {
        }
    }

    fun prepareVpn(activity: Activity): Intent? {
        return VpnService.prepare(activity)
    }

    fun startBuiltInVpn(context: Context) {
        if (isExternalVpnActive(context)) {
            // Ya hay una VPN externa activa (ej. Proton VPN). No interferir.
            return
        }
        PinaVpnService.start(context)
    }

    fun stopBuiltInVpn(context: Context) {
        PinaVpnService.stop(context)
    }

    fun toggleBuiltInVpn(activity: Activity, onPermissionNeeded: (Intent) -> Unit) {
        if (isExternalVpnActive(activity)) {
            val installed = getInstalledVpnApp(activity)
            val name = installed?.name ?: "externa"
            Toast.makeText(activity, "🟢 Ya estás protegido con tu VPN ($name)", Toast.LENGTH_LONG).show()
            return
        }

        if (isBuiltInVpnActive()) {
            stopBuiltInVpn(activity)
            Toast.makeText(activity, "🛡️ Escudo VPN Desactivado", Toast.LENGTH_SHORT).show()
        } else {
            val prepIntent = prepareVpn(activity)
            if (prepIntent != null) {
                onPermissionNeeded(prepIntent)
            } else {
                startBuiltInVpn(activity)
                Toast.makeText(activity, "🛡️ Escudo VPN Activado • Partidos Desbloqueados", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchWarpVpn(context: Context) {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(PACKAGE_CLOUDFLARE_WARP)
        if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } else {
            val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE_CLOUDFLARE_WARP")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                Toast.makeText(context, "Abriendo 1.1.1.1 + WARP en Google Play...", Toast.LENGTH_SHORT).show()
                context.startActivity(storeIntent)
            } catch (e: Exception) {
                openWebUrl(context, "https://1.1.1.1/")
            }
        }
    }

    fun openSystemVpnSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (err: Exception) {
                Toast.makeText(context, "Abre Ajustes > Redes para configurar VPN o DNS", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openWebUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo abrir el navegador", Toast.LENGTH_SHORT).show()
        }
    }
}
