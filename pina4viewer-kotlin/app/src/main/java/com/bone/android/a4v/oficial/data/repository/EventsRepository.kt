package com.bone.android.a4v.oficial.data.repository

import android.content.Context
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.SourceType
import com.bone.android.a4v.oficial.data.parser.ArenaVisionParser
import com.bone.android.a4v.oficial.data.parser.M3uParser
import com.bone.android.a4v.oficial.data.parser.MarkelScraper
import com.bone.android.a4v.oficial.data.parser.PinaVisionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class EventsRepository(
    private val context: Context? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .dns(com.bone.android.a4v.oficial.util.DnsHelper.customDns)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()
) {

    private val cache = ConcurrentHashMap<SourceType, List<EventItem>>()
    private val offModeSources = java.util.Collections.newSetFromMap(ConcurrentHashMap<SourceType, Boolean>())

    @Volatile
    private var lastArenaVisionEvents: List<EventItem>? = null

    @Volatile
    var isCurrentSourceOffMode: Boolean = false
        private set

    fun isArenaVisionSource(source: SourceType): Boolean =
        source != SourceType.CAIDO && source != SourceType.SEARCH && source != SourceType.PETICIONES && source != SourceType.OFF_MODE

    fun isSourceOffMode(source: SourceType): Boolean =
        offModeSources.contains(source)

    fun peekCachedEvents(source: SourceType): List<EventItem>? {
        cache[source]?.let { return it }
        if (isArenaVisionSource(source)) {
            val shared = lastArenaVisionEvents
            if (!shared.isNullOrEmpty()) return shared
        }
        return null
    }

    suspend fun getEvents(source: SourceType, forceRefresh: Boolean = false): Result<List<EventItem>> =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                cache[source]?.let { cached ->
                    isCurrentSourceOffMode = isSourceOffMode(source)
                    return@withContext Result.success(cached)
                }

                if (isArenaVisionSource(source)) {
                    val shared = lastArenaVisionEvents
                    if (!shared.isNullOrEmpty()) {
                        isCurrentSourceOffMode = false
                        cache[source] = shared
                        return@withContext Result.success(shared)
                    }
                }
            }

            try {
                val request = if (source.url.contains("misguia2.php")) {
                    val expireVal = (20200000 + (Math.random() * 500000).toInt()).toString()
                    val formBody = FormBody.Builder()
                        .add("key", "fc8c75bd41f06b0fa1d32c8b0b76493d")
                        .add("expire", expireVal)
                        .build()

                    Request.Builder()
                        .url(source.url)
                        .post(formBody)
                        .header("User-Agent", "Apache-HttpClient/UNAVAILABLE (java 1.4)")
                        .build()
                } else {
                    val finalUrl = if (source.url.contains("githubusercontent")) {
                        "${source.url}?t=${System.currentTimeMillis()}"
                    } else {
                        source.url
                    }
                    Request.Builder()
                        .url(finalUrl)
                        .header("Cache-Control", "no-cache")
                        .header("Pragma", "no-cache")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)")
                        .build()
                }

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext returnOfflineFallback(source)
                    }

                    val body = response.body?.string() ?: ""
                    val events = when (source) {
                        SourceType.CAIDO -> {
                            isCurrentSourceOffMode = false
                            offModeSources.remove(source)
                            val marcaHtml = fetchOptionalMarcaHtml()
                            val parsed = MarkelScraper.parse(body, marcaHtml)
                            if (parsed.isEmpty()) MarkelScraper.getFallbackMarkelEvents() else parsed
                        }
                        SourceType.SEARCH -> {
                            val parsed = PinaVisionParser.parse(body)
                            val channels247 = parsed.filter {
                                it.sport == "DIRECTO 24/7" || it.competition.contains("24/7", ignoreCase = true)
                            }
                            val effective = if (channels247.isNotEmpty()) channels247 else parsed
                            if (effective.isNotEmpty()) {
                                isCurrentSourceOffMode = false
                                offModeSources.remove(source)
                                saveOfflineCache(PINA_CACHE_FILE, body)
                                effective
                            } else {
                                return@withContext returnOfflineFallback(source)
                            }
                        }
                        SourceType.PETICIONES -> {
                            isCurrentSourceOffMode = false
                            offModeSources.remove(source)
                            val marcaHtml = fetchOptionalMarcaHtml()
                            M3uParser.parse(body, marcaHtml)
                        }
                        SourceType.OFF_MODE -> {
                            val parsed = PinaVisionParser.parse(body)
                            if (parsed.isNotEmpty()) {
                                isCurrentSourceOffMode = false
                                offModeSources.remove(source)
                                saveOfflineCache(PINA_CACHE_FILE, body)
                                parsed
                            } else {
                                return@withContext returnOfflineFallback(source)
                            }
                        }
                        else -> {
                            val parsed = ArenaVisionParser.parseHtmlAgenda(body)
                            if (parsed.isNotEmpty()) {
                                isCurrentSourceOffMode = false
                                offModeSources.remove(source)
                                lastArenaVisionEvents = parsed
                                saveOfflineCache(ARENA_CACHE_FILE, body)
                                parsed
                            } else {
                                return@withContext returnOfflineFallback(source)
                            }
                        }
                    }

                    cache[source] = events
                    Result.success(events)
                }
            } catch (_: Exception) {
                returnOfflineFallback(source)
            }
        }

    private fun fetchOptionalMarcaHtml(): String {
        return try {
            val marcaRequest = Request.Builder()
                .url("https://www.marca.com/programacion-tv.html")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)")
                .build()
            client.newCall(marcaRequest).execute().use { res ->
                if (res.isSuccessful) res.body?.string() ?: "" else ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun returnOfflineFallback(source: SourceType): Result<List<EventItem>> {
        val fallback = getFallback(source)
        val usingOffline = isArenaVisionSource(source) || source == SourceType.OFF_MODE || source == SourceType.SEARCH
        isCurrentSourceOffMode = usingOffline
        if (usingOffline) offModeSources.add(source) else offModeSources.remove(source)
        if (fallback.isNotEmpty()) {
            cache[source] = fallback
        }
        return Result.success(fallback)
    }

    private fun getFallback(source: SourceType): List<EventItem> {
        return when (source) {
            SourceType.OFF_MODE -> {
                val cached = loadOfflineCache(PINA_CACHE_FILE)
                    ?: loadLegacyLicIfLooksLikeJson()
                if (!cached.isNullOrEmpty()) {
                    val parsed = PinaVisionParser.parse(cached)
                    if (parsed.isNotEmpty()) return parsed
                }
                val fromAsset = readAssetAgenda()
                if (!fromAsset.isNullOrEmpty()) {
                    val parsed = PinaVisionParser.parse(fromAsset)
                    if (parsed.isNotEmpty()) return parsed
                }
                cache[source].orEmpty()
            }
            SourceType.CAIDO -> MarkelScraper.getFallbackMarkelEvents()
            SourceType.SEARCH -> {
                val cachedMem = cache[source]
                if (!cachedMem.isNullOrEmpty()) return cachedMem
                val cachedDisk = loadOfflineCache(PINA_CACHE_FILE) ?: readAssetAgenda()
                if (!cachedDisk.isNullOrEmpty()) {
                    val parsed = PinaVisionParser.parse(cachedDisk)
                    val channels247 = parsed.filter {
                        it.sport == "DIRECTO 24/7" || it.competition.contains("24/7", ignoreCase = true)
                    }
                    if (channels247.isNotEmpty()) return channels247
                }
                emptyList()
            }
            SourceType.PETICIONES -> emptyList()
            else -> {
                lastArenaVisionEvents?.takeIf { it.isNotEmpty() }?.let { return it }
                val cached = loadOfflineCache(ARENA_CACHE_FILE) ?: loadLegacyLicIfLooksLikeHtml()
                if (!cached.isNullOrEmpty()) {
                    val parsed = ArenaVisionParser.parseHtmlAgenda(cached)
                    if (parsed.isNotEmpty()) return parsed
                }
                ArenaVisionParser.getFallbackAgenda()
            }
        }
    }

    private fun readAssetAgenda(): String? {
        return try {
            context?.assets?.open("agenda.json")?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadLegacyLicIfLooksLikeJson(): String? {
        val raw = loadOfflineCache(LEGACY_CACHE_FILE)?.trimStart() ?: return null
        return if (raw.startsWith("[")) raw else null
    }

    private fun loadLegacyLicIfLooksLikeHtml(): String? {
        val raw = loadOfflineCache(LEGACY_CACHE_FILE)?.trimStart() ?: return null
        return if (raw.startsWith("<")) raw else null
    }

    private fun saveOfflineCache(fileName: String, content: String) {
        try {
            context?.let { ctx ->
                File(ctx.filesDir, fileName).writeText(content)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadOfflineCache(fileName: String): String? {
        return try {
            context?.let { ctx ->
                val file = File(ctx.filesDir, fileName)
                if (file.exists() && file.length() > 0) file.readText() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PINA_CACHE_FILE = "pina_agenda.json"
        private const val ARENA_CACHE_FILE = "arenavision_agenda.html"
        private const val LEGACY_CACHE_FILE = "a4v.lic"
    }
}
