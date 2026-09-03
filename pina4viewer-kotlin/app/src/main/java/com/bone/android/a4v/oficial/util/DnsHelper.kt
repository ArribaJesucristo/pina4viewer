package com.bone.android.a4v.oficial.util

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object DnsHelper {

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            // Si ya es una dirección IP, devolver directamente
            if (hostname.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
                return listOf(InetAddress.getByName(hostname))
            }

            // 1. Quad9 DNS Cifrado (Suiza - Inmune a bloqueos de operadoras españolas)
            try {
                val addresses = queryQuad9Doh(hostname)
                if (addresses.isNotEmpty()) return addresses
            } catch (ignored: Exception) {
            }

            // 2. Google DNS-over-HTTPS (DoH)
            try {
                val addresses = queryGoogleDoh(hostname)
                if (addresses.isNotEmpty()) return addresses
            } catch (ignored: Exception) {
            }

            // 3. AdGuard DNS Cifrado (Europa)
            try {
                val addresses = queryAdGuardDoh(hostname)
                if (addresses.isNotEmpty()) return addresses
            } catch (ignored: Exception) {
            }

            // 4. Cloudflare DoH
            try {
                val addresses = queryCloudflareDoh(hostname)
                if (addresses.isNotEmpty()) return addresses
            } catch (ignored: Exception) {
            }

            // 5. Fallback a DNS del sistema
            return try {
                Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                throw UnknownHostException("No se pudo resolver el host: $hostname")
            }
        }
    }

    private fun queryQuad9Doh(hostname: String): List<InetAddress> {
        val request = Request.Builder()
            .url("https://dns.quad9.net/dns-query?name=$hostname&type=A")
            .header("Accept", "application/dns-json")
            .build()

        bootstrapClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            return parseDnsJsonResponse(body)
        }
    }

    private fun queryGoogleDoh(hostname: String): List<InetAddress> {
        val request = Request.Builder()
            .url("https://dns.google/resolve?name=$hostname&type=A")
            .header("Accept", "application/json")
            .build()

        bootstrapClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            return parseDnsJsonResponse(body)
        }
    }

    private fun queryAdGuardDoh(hostname: String): List<InetAddress> {
        val request = Request.Builder()
            .url("https://dns.adguard-dns.com/dns-query?name=$hostname&type=A")
            .header("Accept", "application/dns-json")
            .build()

        bootstrapClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            return parseDnsJsonResponse(body)
        }
    }

    private fun queryCloudflareDoh(hostname: String): List<InetAddress> {
        val request = Request.Builder()
            .url("https://1.1.1.1/dns-query?name=$hostname&type=A")
            .header("Accept", "application/dns-json")
            .build()

        bootstrapClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            return parseDnsJsonResponse(body)
        }
    }

    private fun parseDnsJsonResponse(body: String): List<InetAddress> {
        val json = JSONObject(body)
        val answers = json.optJSONArray("Answer") ?: return emptyList()

        val list = mutableListOf<InetAddress>()
        for (i in 0 until answers.length()) {
            val item = answers.getJSONObject(i)
            val type = item.optInt("type", -1)
            if (type == 1) { // Type A (IPv4)
                val data = item.optString("data")
                if (data.isNotEmpty() && data.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
                    list.add(InetAddress.getByName(data))
                }
            }
        }
        return list
    }
}
