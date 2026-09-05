package com.bone.android.a4v.oficial.data.parser

import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.StreamType
import org.json.JSONArray
import java.util.regex.Pattern

object MarkelScraper {

    fun parse(content: String, marcaHtml: String? = null): List<EventItem> {
        val trimmed = content.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            if (!marcaHtml.isNullOrBlank()) {
                val multiSportEvents = parseMarcaSchedule(marcaHtml, trimmed)
                if (multiSportEvents.isNotEmpty()) {
                    return multiSportEvents
                }
            }
            return parseJson(trimmed)
        }
        return parseHtml(trimmed)
    }

    private fun parseMarcaSchedule(marcaHtml: String, jsonString: String): List<EventItem> {
        return try {
            val channelsByBase = LinkedHashMap<String, MutableList<ChannelItem>>()
            val sportMap = mutableMapOf<String, String>()

            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val rawTitle = item.optString("title", "").trim()
                val url = item.optString("url", "").trim()
                val tags = item.optString("tags", "").lowercase()

                if (rawTitle.isEmpty() || url.isEmpty()) continue

                val hash = when {
                    url.startsWith("acestream://") -> url.substringAfter("acestream://").take(40)
                    url.length == 40 -> url
                    else -> url
                }

                val baseTitle = rawTitle
                    .replace(Regex("(?i)\\s*\\(opci[oó]n\\s*\\d+\\)\\s*"), "")
                    .replace(Regex("(?i)\\s*opci[oó]n\\s*\\d+\\s*"), "")
                    .trim()

                val optionMatch = Regex("(?i)opci[oó]n\\s*(\\d+)").find(rawTitle)
                val optionNum = optionMatch?.groupValues?.get(1) ?: "${(channelsByBase[baseTitle]?.size ?: 0) + 1}"
                val channelName = "$baseTitle - Opción $optionNum"

                val channelItem = ChannelItem(
                    name = channelName,
                    streamId = hash,
                    type = StreamType.ACESTREAM
                )

                if (!channelsByBase.containsKey(baseTitle)) {
                    channelsByBase[baseTitle] = mutableListOf()
                    sportMap[baseTitle] = detectSport(baseTitle, tags)
                }
                channelsByBase[baseTitle]?.add(channelItem)
            }

            val eventPattern = Pattern.compile("(?si)<li\\s+class=[\"']dailyevent[\"']>(.*?)</li>")
            val sportPattern = Pattern.compile("(?si)<span\\s+class=[\"']dailyday[\"']>(.*?)</span>")
            val hourPattern = Pattern.compile("(?si)<strong\\s+class=[\"']dailyhour[\"']>(.*?)</strong>")
            val compPattern = Pattern.compile("(?si)<span\\s+class=[\"']dailycompetition[\"']>(.*?)</span>")
            val teamsPattern = Pattern.compile("(?si)<h4\\s+class=[\"']dailyteams[\"']>(.*?)</h4>")
            val channelPattern = Pattern.compile("(?si)<span\\s+class=[\"']dailychannel[\"']>(.*?)</span>")

            val matcher = eventPattern.matcher(marcaHtml)
            val events = mutableListOf<EventItem>()
            var idCounter = 1

            while (matcher.find()) {
                val block = matcher.group(1) ?: continue

                val sM = sportPattern.matcher(block)
                val rawSport = if (sM.find()) sM.group(1)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty() else ""

                val hM = hourPattern.matcher(block)
                val rawHour = if (hM.find()) hM.group(1)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty() else ""

                val cM = compPattern.matcher(block)
                val rawComp = if (cM.find()) cM.group(1)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty() else ""

                val tM = teamsPattern.matcher(block)
                val rawTeams = if (tM.find()) tM.group(1)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty() else ""

                val chM = channelPattern.matcher(block)
                val rawChannel = if (chM.find()) chM.group(1)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty() else ""

                if (rawTeams.isEmpty() || rawChannel.isEmpty()) continue

                val matchedChannels = findChannelsForEvent(rawChannel, channelsByBase)
                if (matchedChannels.isNotEmpty()) {
                    events.add(
                        EventItem(
                            id = "marca_$idCounter",
                            title = rawTeams,
                            sport = normalizeSport(rawSport),
                            competition = rawComp.ifEmpty { "Multideporte" },
                            time = rawHour.ifEmpty { "Directo" },
                            date = "Hoy",
                            channels = matchedChannels
                        )
                    )
                    idCounter++
                }
            }

            // Append 24/7 channels at the end
            channelsByBase.forEach { (baseTitle, channels) ->
                events.add(
                    EventItem(
                        id = "markel_24_7_$idCounter",
                        title = baseTitle,
                        sport = sportMap[baseTitle] ?: detectSport(baseTitle, ""),
                        competition = "Canales en Directo 24/7",
                        time = "Directo",
                        date = "24/7",
                        channels = channels
                    )
                )
                idCounter++
            }

            events
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun cleanChannelName(s: String): String {
        return s.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
            .replace("ñ", "n")
            .replace(Regex("[^a-z0-9]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeSport(sportRaw: String): String {
        val s = sportRaw.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
        return when {
            s.contains("f1") || s.contains("formula") || s.contains("moto") || s.contains("motor") || s.contains("superbike") -> "MOTOR"
            s.contains("baloncesto") || s.contains("basket") || s.contains("nba") -> "BALONCESTO"
            s.contains("tenis") || s.contains("tennis") -> "TENIS"
            s.contains("ciclismo") || s.contains("cycling") -> "CICLISMO"
            s.contains("padel") -> "PADEL"
            s.contains("box") || s.contains("mma") || s.contains("ufc") || s.contains("lucha") -> "BOXEO"
            s.contains("rugby") -> "RUGBY"
            s.contains("balonmano") || s.contains("handball") -> "BALONMANO"
            s.contains("futbol") || s.contains("soccer") || s.contains("football") || s.contains("f. sala") || s.contains("futsal") -> "FUTBOL"
            else -> sportRaw.uppercase().trim().ifEmpty { "DEPORTES" }
        }
    }

    private fun findChannelsForEvent(
        marcaChannelStr: String,
        channelsByBase: Map<String, List<ChannelItem>>
    ): List<ChannelItem> {
        val parts = marcaChannelStr.split(Regex("[/,|+()]"))
        val matched = mutableListOf<ChannelItem>()
        val seenHashes = mutableSetOf<String>()

        for (rawPart in parts) {
            val p = cleanChannelName(rawPart)
            if (p.isBlank()) continue

            val targetKeys = mutableListOf<String>()
            when {
                p == "gol" || p == "gol play" -> targetKeys.add("gol")
                p.contains("f1") || p.contains("formula 1") -> targetKeys.add("dazn f1")
                p.contains("motogp") || p.contains("moto gp") -> targetKeys.add("dazn motogp")
                p.contains("superbike") -> targetKeys.add("dazn 4")
                p.contains("tennis channel") -> targetKeys.add("tennis channel")
                p.contains("teledeporte") || p == "tdp" -> targetKeys.add("teledeporte")
                p == "la 1" || p == "tve 1" -> targetKeys.add("la 1")
                p == "la 2" || p == "tve 2" -> targetKeys.add("la 2")
                p.contains("primera federacion") || p.contains("1 rfef") -> targetKeys.add("primera federacion")
                p.contains("hypermotion") -> {
                    val num = Regex("\\b([2-5])\\b").find(p)?.groupValues?.get(1)
                    if (num != null) targetKeys.add("laliga tv hypermotion $num")
                    else targetKeys.add("laliga tv hypermotion")
                }
                p.contains("liga de campeones") || p.contains("champions") || p.contains("l de campeones") -> {
                    val num = Regex("\\b(\\d+)\\b").find(p)?.groupValues?.get(1)
                    if (num != null) targetKeys.add("m l de campeones $num")
                    else targetKeys.add("m l de campeones")
                }
                p.contains("laliga") -> {
                    val num = Regex("\\b([2-4])\\b").find(p)?.groupValues?.get(1)
                    if (p.contains("dazn")) {
                        if (num != null) targetKeys.add("dazn laliga $num")
                        else targetKeys.add("dazn laliga")
                    } else {
                        if (num != null) targetKeys.add("m laliga $num")
                        else targetKeys.add("m laliga")
                    }
                }
                p.contains("dazn") -> {
                    if (p.contains("baloncesto") || p.contains("basket")) {
                        val num = Regex("\\b([2-3])\\b").find(p)?.groupValues?.get(1)
                        if (num != null) targetKeys.add("dazn baloncesto $num")
                        else targetKeys.add("dazn baloncesto")
                    } else {
                        val num = Regex("\\b([1-4])\\b").find(p)?.groupValues?.get(1)
                        if (num != null) targetKeys.add("dazn $num")
                        else targetKeys.add("dazn 1")
                    }
                }
                p.contains("baloncesto") || p.contains("basket") -> {
                    val num = Regex("\\b([2-3])\\b").find(p)?.groupValues?.get(1)
                    if (num != null) targetKeys.add("m baloncesto $num")
                    else targetKeys.add("m baloncesto")
                }
                p.contains("deportes") -> {
                    val num = Regex("\\b([2-7])\\b").find(p)?.groupValues?.get(1)
                    if (num != null) targetKeys.add("m deportes $num")
                    else targetKeys.add("m deportes")
                }
                p.contains("vamos") -> targetKeys.add("m vamos")
                p.contains("movistar plus") || p == "movistar" -> {
                    val num = Regex("\\b([2])\\b").find(p)?.groupValues?.get(1)
                    if (num != null) targetKeys.add("movistar plus 2")
                    else targetKeys.add("movistar plus")
                }
                p.contains("eurosport") -> {
                    val num = Regex("\\b([1-2])\\b").find(p)?.groupValues?.get(1)
                    if (num != null) targetKeys.add("eurosport $num")
                    else targetKeys.add("eurosport 1")
                }
                else -> {
                    for (baseName in channelsByBase.keys) {
                        val cBase = cleanChannelName(baseName)
                        if (p == cBase || (p.length > 3 && (p.contains(cBase) || cBase.contains(p)))) {
                            targetKeys.add(cBase)
                        }
                    }
                }
            }

            for (target in targetKeys) {
                for ((baseName, chList) in channelsByBase) {
                    val cBase = cleanChannelName(baseName)
                    if (cBase == target) {
                        for (ch in chList) {
                            if (seenHashes.add(ch.streamId)) {
                                matched.add(ch)
                            }
                        }
                    }
                }
            }
        }

        return matched
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
