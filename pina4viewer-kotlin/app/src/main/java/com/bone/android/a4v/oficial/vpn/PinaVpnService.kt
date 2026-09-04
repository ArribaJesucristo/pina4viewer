package com.bone.android.a4v.oficial.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.bone.android.a4v.oficial.R
import com.bone.android.a4v.oficial.ui.view.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class PinaVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var isRunning = false

    companion object {
        const val ACTION_CONNECT = "com.bone.android.a4v.oficial.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.bone.android.a4v.oficial.vpn.DISCONNECT"
        const val CHANNEL_ID = "pina_vpn_shield_channel"
        const val NOTIFICATION_ID = 4040

        var isVpnActive = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, PinaVpnService::class.java).apply {
                action = ACTION_CONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PinaVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                disconnectVpn()
                stopSelf()
            }
            else -> {
                connectVpn()
            }
        }
        return START_STICKY
    }

    private fun connectVpn() {
        if (isRunning) return
        createNotificationChannel()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val builder = Builder()
                .setSession("Piña4Viewer Escudo Anti-Bloqueos")
                .addAddress("10.0.0.2", 32)
                .addDnsServer("9.9.9.9") // Quad9 Suiza (Inmune a bloqueos en España)
                .addDnsServer("149.112.112.112") // Quad9 Backup
                .addDnsServer("94.140.14.14") // AdGuard Europa
                .addDnsServer("8.8.8.8") // Google DNS
                .addDnsServer("1.1.1.1") // Cloudflare
                .addRoute("9.9.9.9", 32)
                .addRoute("149.112.112.112", 32)
                .addRoute("94.140.14.14", 32)
                .addRoute("8.8.8.8", 32)
                .addRoute("1.1.1.1", 32)
                .setMtu(1500)

            val openAppIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setConfigureIntent(pendingIntent)

            val pfd = builder.establish()
            if (pfd == null) {
                // Denegado por el sistema (por ejemplo, si otra VPN como Proton VPN está activa)
                disconnectVpn()
                stopSelf()
                return
            }

            vpnInterface = pfd
            isRunning = true
            isVpnActive = true
            com.bone.android.a4v.oficial.util.VpnHelper.vpnStateFlow.value = true

            startPacketForwarding(pfd)
        } catch (e: Exception) {
            e.printStackTrace()
            disconnectVpn()
            stopSelf()
        }
    }

    private fun startPacketForwarding(pfd: ParcelFileDescriptor) {

        serviceScope.launch {
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val packet = ByteArray(32767)

            val udpSocket = DatagramSocket()
            protect(udpSocket)
            udpSocket.soTimeout = 2500

            val swissDnsServer = InetAddress.getByName("9.9.9.9")

            while (isActive && isRunning) {
                try {
                    val length = inputStream.read(packet)
                    if (length > 0) {
                        val buffer = ByteBuffer.wrap(packet, 0, length)
                        val versionAndIHL = buffer.get(0).toInt() and 0xFF
                        val ihl = (versionAndIHL and 0x0F) * 4
                        val protocol = buffer.get(9).toInt() and 0xFF

                        if (protocol == 17 && length >= ihl + 8) { // UDP
                            val srcIp = ByteArray(4)
                            val dstIp = ByteArray(4)
                            buffer.position(12)
                            buffer.get(srcIp)
                            buffer.get(dstIp)

                            buffer.position(ihl)
                            val srcPort = buffer.getShort().toInt() and 0xFFFF
                            val dstPort = buffer.getShort().toInt() and 0xFFFF

                            if (dstPort == 53) {
                                val udpPayloadOffset = ihl + 8
                                val udpPayloadLen = length - udpPayloadOffset

                                val dnsQuery = ByteArray(udpPayloadLen)
                                System.arraycopy(packet, udpPayloadOffset, dnsQuery, 0, udpPayloadLen)

                                val outPacket = DatagramPacket(dnsQuery, dnsQuery.size, swissDnsServer, 53)
                                udpSocket.send(outPacket)

                                val inBuf = ByteArray(4096)
                                val inPacket = DatagramPacket(inBuf, inBuf.size)
                                try {
                                    udpSocket.receive(inPacket)
                                    val replyPacket = buildIpUdpPacket(
                                        srcIp = dstIp,
                                        dstIp = srcIp,
                                        srcPort = dstPort,
                                        dstPort = srcPort,
                                        payload = inPacket.data,
                                        payloadLen = inPacket.length
                                    )
                                    outputStream.write(replyPacket)
                                    outputStream.flush()
                                } catch (ignored: Exception) {
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!isRunning) break
                }
            }
        }
    }

    private fun buildIpUdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
        payloadLen: Int
    ): ByteArray {
        val totalLength = 20 + 8 + payloadLen
        val packet = ByteArray(totalLength)
        val buffer = ByteBuffer.wrap(packet)

        // IPv4 Header
        buffer.put(0x45.toByte()) // Version 4, IHL 5 (20 bytes)
        buffer.put(0x00.toByte()) // DSCP/ECN
        buffer.putShort(totalLength.toShort())
        buffer.putShort(0x1234.toShort()) // ID
        buffer.putShort(0x4000.toShort()) // DF Flag
        buffer.put(64.toByte()) // TTL
        buffer.put(17.toByte()) // UDP Protocol
        buffer.putShort(0.toShort()) // Checksum placeholder
        buffer.put(srcIp)
        buffer.put(dstIp)

        // Calculate and set IP checksum
        val checksum = computeIpChecksum(packet, 0, 20)
        packet[10] = (checksum.toInt() shr 8).toByte()
        packet[11] = (checksum.toInt() and 0xFF).toByte()

        // UDP Header
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort((8 + payloadLen).toShort())
        buffer.putShort(0.toShort()) // UDP Checksum optional in IPv4

        // Payload
        buffer.put(payload, 0, payloadLen)

        return packet
    }

    private fun computeIpChecksum(header: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            if (i == offset + 10) {
                i += 2
                continue
            }
            val high = header[i].toInt() and 0xFF
            val low = if (i + 1 < offset + length) header[i + 1].toInt() and 0xFF else 0
            sum += (high shl 8) or low
            i += 2
        }
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv().toShort()
    }

    private fun disconnectVpn() {
        isRunning = false
        isVpnActive = false
        com.bone.android.a4v.oficial.util.VpnHelper.vpnStateFlow.value = false
        try {
            serviceJob.cancel()
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Piña4Viewer Escudo VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activo el túnel anti-bloqueos para transmisiones deportivas"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Escudo Anti-Bloqueos Activo")
            .setContentText("Partidos y AceStream desbloqueados a máxima velocidad")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        disconnectVpn()
        super.onDestroy()
    }
}
