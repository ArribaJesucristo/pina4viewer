package com.bone.android.a4v.oficial.data.repository

import android.content.Context
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.SourceType
import com.bone.android.a4v.oficial.data.parser.ArenaVisionParser
import com.bone.android.a4v.oficial.data.parser.M3uParser
import com.bone.android.a4v.oficial.data.parser.MarkelScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class EventsRepository(
    private val context: Context? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .dns(com.bone.android.a4v.oficial.util.DnsHelper.customDns)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()
) {

    private val cache = mutableMapOf<SourceType, List<EventItem>>()
    private val offModeSources = mutableSetOf<SourceType>()
    private var lastArenaVisionEvents: List<EventItem>? = null

    var isCurrentSourceOffMode: Boolean = false
        private set

    fun isArenaVisionSource(source: SourceType): Boolean =
        source != SourceType.CAIDO && source != SourceType.SEARCH && source != SourceType.PETICIONES

    fun isSourceOffMode(source: SourceType): Boolean =
        offModeSources.contains(source) || source == SourceType.OFF_MODE

    fun peekCachedEvents(source: SourceType): List<EventItem>? {
        if (cache.containsKey(source)) return cache[source]
        if (isArenaVisionSource(source) && !lastArenaVisionEvents.isNullOrEmpty()) {
            return lastArenaVisionEvents
        }
        return null
    }

    suspend fun getEvents(source: SourceType, forceRefresh: Boolean = false): Result<List<EventItem>> =
        withContext(Dispatchers.IO) {
            // Si no es forzado y ya está en caché específica, retorno inmediato (0ms)
            if (!forceRefresh && cache.containsKey(source)) {
                isCurrentSourceOffMode = isSourceOffMode(source)
                return@withContext Result.success(cache[source].orEmpty())
            }

            // Si es un servidor ArenaVision y ya tenemos la agenda de otro servidor espejo, retorno instantáneo
            if (!forceRefresh && isArenaVisionSource(source) && !lastArenaVisionEvents.isNullOrEmpty() && source != SourceType.OFF_MODE) {
                isCurrentSourceOffMode = false
                val shared = lastArenaVisionEvents.orEmpty()
                cache[source] = shared
                return@withContext Result.success(shared)
            }

            if (source == SourceType.OFF_MODE) {
                isCurrentSourceOffMode = true
                offModeSources.add(source)
                val cached = loadOfflineLic()
                val events = if (!cached.isNullOrEmpty()) {
                    ArenaVisionParser.parseHtmlAgenda(cached)
                } else {
                    lastArenaVisionEvents ?: ArenaVisionParser.getFallbackAgenda()
                }
                cache[source] = events
                return@withContext Result.success(events)
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
                    Request.Builder()
                        .url(source.url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)")
                        .build()
                }

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        isCurrentSourceOffMode = isArenaVisionSource(source)
                        if (isCurrentSourceOffMode) offModeSources.add(source) else offModeSources.remove(source)
                        val fallback = getFallback(source)
                        cache[source] = fallback
                        return@withContext Result.success(fallback)
                    }

                    val body = response.body?.string() ?: ""
                    val events = when (source) {
                        SourceType.CAIDO -> {
                            isCurrentSourceOffMode = false
                            offModeSources.remove(source)
                            val marcaHtml = try {
                                val marcaRequest = Request.Builder()
                                    .url("https://www.marca.com/programacion-tv.html")
                                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)")
                                    .build()
                                client.newCall(marcaRequest).execute().use { res ->
                                    if (res.isSuccessful) res.body?.string() ?: "" else ""
                                }
                            } catch (e: Exception) {
                                ""
                            }
                            val parsed = MarkelScraper.parse(body, marcaHtml)
                            if (parsed.isEmpty()) MarkelScraper.getFallbackMarkelEvents() else parsed
                        }
                        SourceType.PETICIONES, SourceType.SEARCH -> {
                            isCurrentSourceOffMode = false
                            offModeSources.remove(source)
                            val marcaHtml = try {
                                val marcaRequest = Request.Builder()
                                    .url("https://www.marca.com/programacion-tv.html")
                                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)")
                                    .build()
                                client.newCall(marcaRequest).execute().use { res ->
                                    if (res.isSuccessful) res.body?.string() ?: "" else ""
                                }
                            } catch (e: Exception) {
                                ""
                            }
                            M3uParser.parse(body, marcaHtml)
                        }
                        else -> {
                            val parsed = ArenaVisionParser.parseHtmlAgenda(body)
                            isCurrentSourceOffMode = false
                            offModeSources.remove(source)
                            if (parsed.isNotEmpty()) {
                                lastArenaVisionEvents = parsed
                                saveOfflineLic(body)
                            }
                            parsed
                        }
                    }

                    cache[source] = events
                    Result.success(events)
                }
            } catch (e: Exception) {
                isCurrentSourceOffMode = isArenaVisionSource(source)
                if (isCurrentSourceOffMode) offModeSources.add(source) else offModeSources.remove(source)
                val fallback = getFallback(source)
                cache[source] = fallback
                Result.success(fallback)
            }
        }

    private fun getFallback(source: SourceType): List<EventItem> {
        return when (source) {
            SourceType.CAIDO -> MarkelScraper.getFallbackMarkelEvents()
            SourceType.SEARCH, SourceType.PETICIONES -> emptyList()
            else -> {
                if (!lastArenaVisionEvents.isNullOrEmpty()) {
                    return lastArenaVisionEvents.orEmpty()
                }
                val cached = loadOfflineLic()
                if (!cached.isNullOrEmpty()) {
                    val parsed = ArenaVisionParser.parseHtmlAgenda(cached)
                    if (parsed.isNotEmpty()) return parsed
                }
                ArenaVisionParser.getFallbackAgenda()
            }
        }
    }

    private fun saveOfflineLic(html: String) {
        try {
            context?.let { ctx ->
                val file = File(ctx.filesDir, "a4v.lic")
                file.writeText(html)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadOfflineLic(): String? {
        return try {
            context?.let { ctx ->
                val file = File(ctx.filesDir, "a4v.lic")
                if (file.exists() && file.length() > 0) file.readText() else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
