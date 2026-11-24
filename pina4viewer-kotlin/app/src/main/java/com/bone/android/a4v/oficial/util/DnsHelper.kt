package com.bone.android.a4v.oficial.util

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object DnsHelper {

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expiresAtMillis: Long
    )

    private const val DYNAMIC_TTL_MS = 15L * 60L * 1000L

    private val preseededCache = ConcurrentHashMap<String, List<InetAddress>>()
    private val dynamicCache = ConcurrentHashMap<String, CacheEntry>()
    private val bypassedPreseedHosts = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    @Volatile
    internal var clockMillis: () -> Long = { System.currentTimeMillis() }

    init {
        // Pre-sembrar IPs conocidas para resolución ultra-rápida (0ms)
        preseed("www.arena4viewer.in", "172.67.155.146", "104.21.90.100")
        preseed("arena4viewer.in", "172.67.155.146", "104.21.90.100")
        preseed("www.arena4viewer.pl", "172.67.148.203", "104.21.73.196")
        preseed("arena4viewer.pl", "172.67.148.203", "104.21.73.196")
        preseed("www.arena4viewer.co.in", "188.114.97.3", "188.114.96.3")
        preseed("arena4viewer.co.in", "188.114.97.3", "188.114.96.3")
        preseed("www.arena4viewer.cool", "104.21.64.222", "172.67.156.38")
        preseed("arena4viewer.cool", "104.21.64.222", "172.67.156.38")
        preseed("www.arena4viewer.info", "104.21.76.92", "172.67.191.158")
        preseed("arena4viewer.info", "104.21.76.92", "172.67.191.158")
        preseed("www.arena4viewer.top", "172.67.185.120", "104.21.75.140")
        preseed("arena4viewer.top", "172.67.185.120", "104.21.75.140")
        preseed("www.arena4viewer.lv", "104.21.80.150", "172.67.200.110")
        preseed("arena4viewer.lv", "104.21.80.150", "172.67.200.110")
        preseed("www.markellinks.app", "80.225.189.168")
        preseed("markellinks.app", "80.225.189.168")
        preseed("raw.githubusercontent.com", "185.199.110.133", "185.199.109.133", "185.199.108.133", "185.199.111.133")
        preseed("objects.githubusercontent.com", "185.199.110.133", "185.199.109.133", "185.199.108.133", "185.199.111.133")
        preseed("github.com", "140.82.121.3", "140.82.121.4")
        preseed("api.github.com", "140.82.121.5", "140.82.121.6")
        preseed("download.acestream.media", "51.15.26.167")
        preseed("android.acestream.net", "51.15.26.167")
        preseed("storage.de.cloud.ovh.net", "51.195.124.64", "141.95.4.196", "145.239.139.16", "141.95.4.204")
    }

    private fun preseed(host: String, vararg ips: String) {
        try {
            val list = ips.map { InetAddress.getByName(it) }
            preseededCache[host.lowercase()] = list
        } catch (ignored: Exception) {
        }
    }

    fun invalidate(hostname: String) {
        val key = hostname.lowercase()
        dynamicCache.remove(key)
        if (preseededCache.containsKey(key)) {
            bypassedPreseedHosts.add(key)
        }
    }

    fun clearDynamicCache() {
        dynamicCache.clear()
        bypassedPreseedHosts.clear()
    }

    internal fun putDynamicCacheForTest(hostname: String, addresses: List<InetAddress>, ttlMs: Long = DYNAMIC_TTL_MS) {
        dynamicCache[hostname.lowercase()] = CacheEntry(addresses, clockMillis() + ttlMs)
    }

    internal fun lookupWithResolver(
        hostname: String,
        resolver: (String) -> List<InetAddress>
    ): List<InetAddress> {
        val key = hostname.lowercase()

        // 0. Si ya es una dirección IP numérica, devolver directo
        if (hostname.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
            return listOf(InetAddress.getByName(hostname))
        }

        val now = clockMillis()

        // 1. Caché dinámica con TTL (15 min)
        val cached = dynamicCache[key]
        if (cached != null) {
            if (now < cached.expiresAtMillis) {
                return cached.addresses
            }
            dynamicCache.remove(key, cached)
        }

        // 2. Respaldo pre-sembrado para dominios anti-bloqueo (0ms) salvo invalidación explícita
        if (!bypassedPreseedHosts.contains(key)) {
            preseededCache[key]?.let { return it }
        }

        // 3. Resolución dinámica (sistema / DoH) + guardado con TTL
        val resolved = resolver(hostname)
        if (resolved.isNotEmpty()) {
            dynamicCache[key] = CacheEntry(resolved, now + DYNAMIC_TTL_MS)
            return resolved
        }

        // Último recurso: si se invalidó un host presembrado y falló la red, usar preseededCache
        preseededCache[key]?.let { return it }

        throw UnknownHostException("No se pudo resolver el host: $hostname")
    }

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return lookupWithResolver(hostname) { host ->
                resolveSystemOrDoh(host)
            }
        }
    }

    private fun resolveSystemOrDoh(hostname: String): List<InetAddress> {
        // Intentar resolución del sistema / VPN UDP (muy rápida, ~10-20ms)
        try {
            val sysAddresses = Dns.SYSTEM.lookup(hostname)
            if (sysAddresses.isNotEmpty()) return sysAddresses
        } catch (ignored: Exception) {
        }

        // Fallback DoH Cifrado: Google DNS
        try {
            val addresses = queryGoogleDoh(hostname)
            if (addresses.isNotEmpty()) return addresses
        } catch (ignored: Exception) {
        }

        // Fallback DoH: AdGuard DNS
        try {
            val addresses = queryAdGuardDoh(hostname)
            if (addresses.isNotEmpty()) return addresses
        } catch (ignored: Exception) {
        }

        // Fallback DoH: Quad9
        try {
            val addresses = queryQuad9Doh(hostname)
            if (addresses.isNotEmpty()) return addresses
        } catch (ignored: Exception) {
        }

        return emptyList()
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
