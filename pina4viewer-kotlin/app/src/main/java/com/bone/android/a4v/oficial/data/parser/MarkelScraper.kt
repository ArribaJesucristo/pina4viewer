package com.bone.android.a4v.oficial.data.parser

import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.StreamType
import org.json.JSONArray
import java.util.regex.Pattern

object MarkelScraper {

    fun parse(content: String): List<EventItem> {
        val trimmed = content.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            return parseJson(trimmed)
        }
        return parseHtml(trimmed)
    }

    private fun parseJson(jsonString: String): List<EventItem> {
        val eventsMap = LinkedHashMap<String, MutableList<ChannelItem>>()
        val sportMap = mutableMapOf<String, String>()
        val dateMap = mutableMapOf<String, String>()

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val rawTitle = item.optString("title", "").trim()
                val url = item.optString("url", "").trim()
                val tags = item.optString("tags", "").lowercase()

                if (rawTitle.isEmpty() || url.isEmpty()) continue

                // Extract 40-char hash or acestream URI
                val hash = when {
                    url.startsWith("acestream://") -> url.substringAfter("acestream://").take(40)
                    url.length == 40 -> url
                    else -> url
                }

                // Base title without "(OPCIÓN X)"
                val baseTitle = rawTitle
                    .replace(Regex("(?i)\\s*\\(opci[oó]n\\s*\\d+\\)\\s*"), "")
                    .replace(Regex("(?i)\\s*opci[oó]n\\s*\\d+\\s*"), "")
                    .trim()

                // Option name
                val optionMatch = Regex("(?i)opci[oó]n\\s*(\\d+)").find(rawTitle)
                val optionNum = optionMatch?.groupValues?.get(1) ?: "${(eventsMap[baseTitle]?.size ?: 0) + 1}"
                val channelName = "$baseTitle - Opción $optionNum"

                val channelItem = ChannelItem(
                    name = channelName,
                    streamId = hash,
                    type = StreamType.ACESTREAM
                )

                if (!eventsMap.containsKey(baseTitle)) {
                    eventsMap[baseTitle] = mutableListOf()
                    sportMap[baseTitle] = detectSport(baseTitle, tags)
                    dateMap[baseTitle] = "En Vivo"
                }

                eventsMap[baseTitle]?.add(channelItem)
            }

            var idCounter = 1
            return eventsMap.map { (baseTitle, channels) ->
                EventItem(
                    id = "markel_$idCounter",
                    title = baseTitle,
                    sport = sportMap[baseTitle] ?: "FUTBOL",
                    competition = "MarkelLinks HD (${channels.size} opciones)",
                    time = "Directo",
                    date = "Hoy",
                    channels = channels
                ).also { idCounter++ }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return parseHtml(jsonString)
        }
    }

    private fun detectSport(title: String, tags: String): String {
        val combined = "$title $tags".lowercase()
        return when {
            combined.contains("f1") || combined.contains("formula 1") || combined.contains("motogp") || combined.contains("moto gp") || combined.contains("motor") || combined.contains("dazn f1") -> "MOTOR"
            combined.contains("basket") || combined.contains("nba") || combined.contains("baloncesto") || combined.contains("endesa") || combined.contains("acb") || combined.contains("euroliga") -> "BALONCESTO"
            combined.contains("tenis") || combined.contains("atp") || combined.contains("wta") || combined.contains("roland") || combined.contains("wimbledon") -> "TENIS"
            combined.contains("laliga") || combined.contains("futbol") || combined.contains("fútbol") || combined.contains("premier") || combined.contains("champions") || combined.contains("liga") || combined.contains("copa") || combined.contains("gol") || combined.contains("movistar laliga") || combined.contains("dazn laliga") -> "FUTBOL"
            else -> "DEPORTES"
        }
    }

    private fun parseHtml(html: String): List<EventItem> {
        val hashPattern = Pattern.compile("(?i)(?:acestream://|id=)([a-f0-9]{40})")
        val matchPattern = Pattern.compile("(?i)<(?:div|tr|li)[^>]*class=[\"'][^\"']*(?:event|partido|match|item)[^\"']*[\"'][^>]*>(.*?)</(?:div|tr|li)>", Pattern.DOTALL)

        val matcher = matchPattern.matcher(html)
        val events = mutableListOf<EventItem>()
        var index = 1

        while (matcher.find()) {
            val block = matcher.group(1) ?: continue
            val text = block.replace(Regex("<[^>]+>"), " ").trim()
            val hashMatcher = hashPattern.matcher(block)
            val channels = mutableListOf<ChannelItem>()

            var chIdx = 1
            while (hashMatcher.find()) {
                val hash = hashMatcher.group(1) ?: continue
                channels.add(
                    ChannelItem(
                        name = "Opción $chIdx (AceStream)",
                        streamId = hash,
                        type = StreamType.ACESTREAM
                    )
                )
                chIdx++
            }

            if (text.isNotEmpty() && channels.isNotEmpty()) {
                events.add(
                    EventItem(
                        id = "markel_html_$index",
                        title = text.take(60),
                        sport = detectSport(text, ""),
                        competition = "MarkelLinks En Vivo",
                        time = "En Directo",
                        date = "Hoy",
                        channels = channels
                    )
                )
                index++
            }
        }

        return if (events.isNotEmpty()) events else getFallbackMarkelEvents()
    }

    fun getFallbackMarkelEvents(): List<EventItem> {
        return listOf(
            EventItem(
                id = "m1",
                title = "M+ LALIGA (Opciones HD)",
                sport = "FUTBOL",
                competition = "MarkelLinks HD",
                time = "Directo",
                date = "Hoy",
                channels = listOf(
                    ChannelItem("M+ LaLiga - Opción 1", "d4ff041287a43e3114d411d671c4b4e92e21f33y", StreamType.ACESTREAM),
                    ChannelItem("M+ LaLiga - Opción 2", "b3bdd6ef7f795c4f321a3ce5cf4907338f462929", StreamType.ACESTREAM),
                    ChannelItem("M+ LaLiga - Opción 3", "f863873de9f7b2996dde19a0657dd89835af3007", StreamType.ACESTREAM)
                )
            ),
            EventItem(
                id = "m2",
                title = "DAZN F1 (Formula 1 HD)",
                sport = "MOTOR",
                competition = "MarkelLinks HD",
                time = "Directo",
                date = "Hoy",
                channels = listOf(
                    ChannelItem("DAZN F1 - Opción 1", "c8ad94585fbe13cfc9074c2b5e23fb6344e0f66d", StreamType.ACESTREAM),
                    ChannelItem("DAZN F1 - Opción 2", "6422e8bc34282871634c81947be093c04ad1bb29", StreamType.ACESTREAM)
                )
            )
        )
    }
}
