package com.bone.android.a4v.oficial.data.repository

import android.content.Context
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.SourceType
import com.bone.android.a4v.oficial.data.parser.ArenaVisionParser
import com.bone.android.a4v.oficial.data.parser.M3uParser
import com.bone.android.a4v.oficial.data.parser.MarkelScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class EventsRepository(
    private val context: Context? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(com.bone.android.a4v.oficial.util.DnsHelper.customDns)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    private val cache = mutableMapOf<SourceType, List<EventItem>>()
    private val offModeSources = mutableSetOf<SourceType>()
    var isCurrentSourceOffMode: Boolean = false
        private set

    fun isSourceOffMode(source: SourceType): Boolean = offModeSources.contains(source) || source == SourceType.OFF_MODE

    suspend fun getEvents(source: SourceType, forceRefresh: Boolean = false): Result<List<EventItem>> =
        withContext(Dispatchers.IO) {
            if (!forceRefresh && cache.containsKey(source)) {
                isCurrentSourceOffMode = isSourceOffMode(source)
                return@withContext Result.success(cache[source].orEmpty())
            }

            if (source == SourceType.OFF_MODE) {
                isCurrentSourceOffMode = true
                offModeSources.add(source)
                val cached = loadOfflineLic()
                val events = if (!cached.isNullOrEmpty()) {
                    ArenaVisionParser.parseHtmlAgenda(cached)
                } else {
                    ArenaVisionParser.getFallbackAgenda()
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
                        isCurrentSourceOffMode = (source != SourceType.CAIDO && source != SourceType.SEARCH)
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
                            val parsed = MarkelScraper.parse(body)
                            if (parsed.isEmpty()) MarkelScraper.getFallbackMarkelEvents() else parsed
                        }
                        SourceType.PETICIONES, SourceType.SEARCH -> {
                            isCurrentSourceOffMode = false
                            offModeSources.remove(source)
                            M3uParser.parse(body)
                        }
                        else -> {
                            val parsed = ArenaVisionParser.parseHtmlAgenda(body)
                            if (parsed.isNotEmpty()) {
                                isCurrentSourceOffMode = false
                                offModeSources.remove(source)
                                saveOfflineLic(body)
                                parsed
                            } else {
                                isCurrentSourceOffMode = true
                                offModeSources.add(source)
                                getFallback(source)
                            }
                        }
                    }

                    cache[source] = events
                    Result.success(events)
                }
            } catch (e: Exception) {
                isCurrentSourceOffMode = (source != SourceType.CAIDO && source != SourceType.SEARCH)
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
