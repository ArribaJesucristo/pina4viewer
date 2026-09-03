package com.bone.android.a4v.oficial.data.repository

import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.SourceType
import com.bone.android.a4v.oficial.data.parser.ArenaVisionParser
import com.bone.android.a4v.oficial.data.parser.M3uParser
import com.bone.android.a4v.oficial.data.parser.MarkelScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class EventsRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(com.bone.android.a4v.oficial.util.DnsHelper.customDns)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    private val cache = mutableMapOf<SourceType, List<EventItem>>()

    suspend fun getEvents(source: SourceType, forceRefresh: Boolean = false): Result<List<EventItem>> =
        withContext(Dispatchers.IO) {
            if (!forceRefresh && cache.containsKey(source)) {
                return@withContext Result.success(cache[source].orEmpty())
            }

            if (source == SourceType.OFF_MODE) {
                val fallback = ArenaVisionParser.getFallbackAgenda()
                cache[source] = fallback
                return@withContext Result.success(fallback)
            }

            try {
                val request = Request.Builder()
                    .url(source.url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val fallback = when (source) {
                            SourceType.CAIDO -> MarkelScraper.getFallbackMarkelEvents()
                            SourceType.SEARCH, SourceType.PETICIONES -> emptyList()
                            else -> ArenaVisionParser.getFallbackAgenda()
                        }
                        cache[source] = fallback
                        return@withContext Result.success(fallback)
                    }

                    val body = response.body?.string() ?: ""
                    val events = when (source) {
                        SourceType.CAIDO -> {
                            val parsed = MarkelScraper.parse(body)
                            if (parsed.isEmpty()) MarkelScraper.getFallbackMarkelEvents() else parsed
                        }
                        SourceType.PETICIONES, SourceType.SEARCH -> M3uParser.parse(body)
                        else -> {
                            val parsed = ArenaVisionParser.parseHtmlAgenda(body)
                            if (parsed.isEmpty()) ArenaVisionParser.getFallbackAgenda() else parsed
                        }
                    }

                    cache[source] = events
                    Result.success(events)
                }
            } catch (e: Exception) {
                val fallback = when (source) {
                    SourceType.CAIDO -> MarkelScraper.getFallbackMarkelEvents()
                    SourceType.SEARCH, SourceType.PETICIONES -> emptyList()
                    else -> ArenaVisionParser.getFallbackAgenda()
                }
                cache[source] = fallback
                Result.success(fallback)
            }
        }
}
