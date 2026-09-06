package com.bone.android.a4v.oficial.data.parser

import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.StreamType
import java.io.BufferedReader
import java.io.StringReader

import java.util.regex.Pattern

object M3uParser {

    fun parse(content: String, marcaHtml: String? = null): List<EventItem> {
        if (!marcaHtml.isNullOrBlank()) {
            val multiSportEvents = parseMarcaSchedule(marcaHtml, content)
            if (multiSportEvents.isNotEmpty()) {
                return multiSportEvents
            }
        }
        return parseFlat(content)
    }

    private fun parseMarcaSchedule(marcaHtml: String, m3uContent: String): List<EventItem> {
        return try {
            val channelsByBase = LinkedHashMap<String, MutableList<ChannelItem>>()
            val sportMap = mutableMapOf<String, String>()

            val reader = BufferedReader(StringReader(m3uContent))
            var line: String?
            var currentTitle = ""
            var currentGroup = "Deportes"

            while (reader.readLine().also { line = it } != null) {
                val trimmed = line?.trim().orEmpty()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                    val groupMatch = Regex("group-title=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(trimmed)
                    currentGroup = groupMatch?.groupValues?.get(1) ?: "Deportes"

                    val commaIndex = trimmed.lastIndexOf(",")
                    currentTitle = if (commaIndex != -1 && commaIndex + 1 < trimmed.length) {
                        trimmed.substring(commaIndex + 1).trim()
                    } else {
                        trimmed.removePrefix("#EXTINF:").trim()
                    }
                } else if (!trimmed.startsWith("#") && currentTitle.isNotEmpty()) {
                    val streamUrl = trimmed
                    val streamType = when {
                        streamUrl.startsWith("acestream://", ignoreCase = true) -> StreamType.ACESTREAM
                        streamUrl.contains("getstream?id=", ignoreCase = true) -> StreamType.ACESTREAM
                        streamUrl.startsWith("sop://", ignoreCase = true) -> StreamType.SOPCAST
                        else -> StreamType.DIRECT
                    }

                    val streamId = when {
                        streamUrl.contains("getstream?id=", ignoreCase = true) -> {
                            val idx = streamUrl.indexOf("id=", ignoreCase = true) + 3
                            streamUrl.substring(idx).trim()
                        }
                        streamUrl.startsWith("acestream://", ignoreCase = true) -> {
                            streamUrl.removePrefix("acestream://").trim()
                        }
                        else -> streamUrl
                    }

                    val base = extractBaseChannelFromM3u(currentTitle)
                    val upperRaw = currentTitle.uppercase()
                    val sourceTag = when {
                        upperRaw.contains("ELCANO") -> " [Elcano]"
                        upperRaw.contains("NEW ERA") -> " [New Era]"
                        upperRaw.contains("NEW LOOP") -> " [New Loop]"
                        else -> ""
                    }

                    val optNum = (channelsByBase[base]?.size ?: 0) + 1
                    val displayName = "$base - Opción $optNum$sourceTag"

                    val channel = ChannelItem(
                        name = displayName,
                        streamId = streamId,
                        type = streamType,
                        rawUrl = streamUrl
                    )

                    if (!channelsByBase.containsKey(base)) {
                        channelsByBase[base] = mutableListOf()
                        sportMap[base] = detectSport(base, currentGroup)
                    }
                    channelsByBase[base]?.add(channel)
                    currentTitle = ""
                }
            }

            val sectionPattern = Pattern.compile("(?si)<li\\s+class=[\"']content-item[\"']>(.*?)(?=<li\\s+class=[\"']content-item[\"']|</ul>|</ol>\\s*</div>)")
            val eventPattern = Pattern.compile("(?si)<li\\s+class=[\"']dailyevent[\"']>(.*?)</li>")
            val sportPattern = Pattern.compile("(?si)<span\\s+class=[\"']dailyday[\"']>(.*?)</span>")
            val hourPattern = Pattern.compile("(?si)<strong\\s+class=[\"']dailyhour[\"']>(.*?)</strong>")
            val compPattern = Pattern.compile("(?si)<span\\s+class=[\"']dailycompetition[\"']>(.*?)</span>")
            val teamsPattern = Pattern.compile("(?si)<h4\\s+class=[\"']dailyteams[\"']>(.*?)</h4>")
            val channelPattern = Pattern.compile("(?si)<span\\s+class=[\"']dailychannel[\"']>(.*?)</span>")
            val headerPattern = Pattern.compile("(?si)<span\\s+class=[\"']title-section-widget[\"']>(.*?)</span>")

            val sectionMatcher = sectionPattern.matcher(marcaHtml)
            val sections = mutableListOf<Pair<String, String>>()
            var dayIdx = 0

            while (sectionMatcher.find()) {
                val secBlock = sectionMatcher.group(1) ?: continue
                val hM = headerPattern.matcher(secBlock)
                val hText = if (hM.find()) hM.group(1)?.replace(Regex("<[^>]+>"), " ")?.trim().orEmpty() else ""
                val dayLabel = when (dayIdx) {
                    0 -> "Hoy"
                    1 -> "Mañana"
                    else -> hText.ifEmpty { "Día +$dayIdx" }
                }
                sections.add(dayLabel to secBlock)
                dayIdx++
            }

            if (sections.isEmpty()) {
                sections.add("Hoy" to marcaHtml)
            }

            val events = mutableListOf<EventItem>()
            var idCounter = 1

            for ((dayLabel, secHtml) in sections) {
                val matcher = eventPattern.matcher(secHtml)
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
                                id = "peticiones_marca_$idCounter",
                                title = rawTeams,
                                sport = normalizeSport(rawSport),
                                competition = rawComp.ifEmpty { "Multideporte" },
                                time = rawHour.ifEmpty { "Directo" },
                                date = dayLabel,
                                channels = matchedChannels
                            )
                        )
                        idCounter++
                    }
                }
            }

            // Append 24/7 channels at the end
            channelsByBase.forEach { (baseTitle, channels) ->
                events.add(
                    EventItem(
                        id = "peticiones_24_7_$idCounter",
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

    private fun extractBaseChannelFromM3u(rawName: String): String {
        var s = rawName
        s = s.replace(Regex("-->.*$"), "")
        s = s.replace(Regex("\\.\\.\\..*$"), "")
        s = s.replace(Regex("(?i)\\b(1080p|720p|4k|hd|sd|fhd)\\b"), "")
        s = s.replace(Regex("[\"*#\\-_()]"), "")
        s = s.replace(Regex("\\s+"), " ").trim()
        return if (s.isNotEmpty()) s else rawName.trim()
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

    private fun detectSport(title: String, group: String): String {
        val combined = "$title $group".lowercase()
        return when {
            combined.contains("f1") || combined.contains("formula 1") || combined.contains("motogp") || combined.contains("moto gp") || combined.contains("motor") || combined.contains("rally") -> "MOTOR"
            combined.contains("basket") || combined.contains("nba") || combined.contains("baloncesto") || combined.contains("acb") || combined.contains("euroliga") -> "BALONCESTO"
            combined.contains("tenis") || combined.contains("tennis") || combined.contains("atp") || combined.contains("wta") -> "TENIS"
            combined.contains("ciclismo") || combined.contains("cycling") -> "CICLISMO"
            combined.contains("padel") -> "PADEL"
            combined.contains("box") || combined.contains("mma") || combined.contains("ufc") -> "BOXEO"
            combined.contains("rugby") -> "RUGBY"
            combined.contains("balonmano") || combined.contains("handball") -> "BALONMANO"
            combined.contains("laliga") || combined.contains("futbol") || combined.contains("fútbol") || combined.contains("premier") || combined.contains("champions") || combined.contains("liga") || combined.contains("gol") -> "FUTBOL"
            else -> "DEPORTES"
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
                p == "gol" || p == "gol play" -> targetKeys.addAll(listOf("gol", "gol tv"))
                p.contains("f1") || p.contains("formula 1") -> targetKeys.addAll(listOf("dazn f1", "sky sport f1"))
                p.contains("motogp") || p.contains("moto gp") -> targetKeys.add("dazn motogp")
                p.contains("superbike") -> targetKeys.add("dazn 4")
                p.contains("tennis channel") -> targetKeys.add("tennis channel")
                p.contains("teledeporte") || p == "tdp" -> targetKeys.addAll(listOf("teledeporte", "teledeporte spain"))
                p == "la 1" || p == "tve 1" -> targetKeys.addAll(listOf("la 1", "la 1 spain", "la 1 tve"))
                p == "la 2" || p == "tve 2" -> targetKeys.addAll(listOf("la 2", "la 2 tve"))
                p.contains("primera federacion") || p.contains("1 rfef") -> targetKeys.addAll(listOf("1 federacion", "rfef tv"))
                p.contains("hypermotion") -> {
                    val num = Regex("\\b([2-5])\\b").find(p)?.groupValues?.get(1)
                    if (num != null) targetKeys.add("laliga tv hypermotion $num")
                    else targetKeys.addAll(listOf("laliga tv hypermotion", "laliga hypermotion"))
                }
                p.contains("liga de campeones") || p.contains("champions") || p.contains("l de campeones") -> {
                    val num = Regex("\\b(\\d+)\\b").find(p)?.groupValues?.get(1)
                    if (num != null) targetKeys.addAll(listOf("m liga de campeones $num", "m l de campeones $num"))
                    else targetKeys.addAll(listOf("m liga de campeones", "m l de campeones"))
                }
                p.contains("laliga") -> {
                    val num = Regex("\\b([2-4])\\b").find(p)?.groupValues?.get(1)
                    if (p.contains("dazn")) {
                        if (num != null) targetKeys.addAll(listOf("dazn laliga $num", "dazn la liga $num"))
                        else targetKeys.addAll(listOf("dazn laliga", "dazn la liga"))
                    } else {
                        if (num != null) targetKeys.addAll(listOf("m laliga $num", "movistar laliga $num"))
                        else targetKeys.addAll(listOf("m laliga", "movistar laliga"))
                    }
                }
                p.contains("dazn") -> {
                    if (p.contains("baloncesto") || p.contains("basket")) {
                        val num = Regex("\\b([2-3])\\b").find(p)?.groupValues?.get(1)
                        if (num != null) targetKeys.add("dazn baloncesto $num")
                        else targetKeys.add("dazn baloncesto")
                    } else {
                        val num = Regex("\\b([1-4])\\b").find(p)?.groupValues?.get(1)
                        if (num != null) targetKeys.addAll(listOf("dazn $num", "dazn$num"))
                        else targetKeys.addAll(listOf("dazn 1", "dazn1"))
                    }
                }
                p.contains("baloncesto") || p.contains("basket") -> {
                    val num = Regex("\\b([2-3])\\b").find(p)?.groupValues?.get(1)
                    if (num != null) targetKeys.add("m baloncesto $num")
                    else targetKeys.add("m baloncesto")
                }
                p.contains("deportes") -> {
                    val num = Regex("\\b([2-8])\\b").find(p)?.groupValues?.get(1)
                    if (num != null) targetKeys.addAll(listOf("m deportes $num", "movistar deportes $num"))
                    else targetKeys.addAll(listOf("m deportes", "movistar deportes"))
                }
                p.contains("vamos") -> {
                    val num = Regex("\\b([2-3])\\b").find(p)?.groupValues?.get(1)
                    if (num != null) targetKeys.add("m vamos $num")
                    else targetKeys.addAll(listOf("m vamos", "vamos"))
                }
                p.contains("movistar plus") || p == "movistar" -> {
                    targetKeys.addAll(listOf("movistar plus", "movistar +", "m plus"))
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

    private fun parseFlat(content: String): List<EventItem> {
        val events = mutableListOf<EventItem>()
        val reader = BufferedReader(StringReader(content))
        var line: String?
        var currentTitle = ""
        var currentGroup = "Deportes"

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line?.trim().orEmpty()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                // Extract group-title if present
                val groupMatch = Regex("group-title=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(trimmed)
                currentGroup = groupMatch?.groupValues?.get(1) ?: "Deportes"

                // Extract title after last comma
                val commaIndex = trimmed.lastIndexOf(",")
                currentTitle = if (commaIndex != -1 && commaIndex + 1 < trimmed.length) {
                    trimmed.substring(commaIndex + 1).trim()
                } else {
                    trimmed.removePrefix("#EXTINF:").trim()
                }
            } else if (!trimmed.startsWith("#") && currentTitle.isNotEmpty()) {
                val streamUrl = trimmed
                val streamType = when {
                    streamUrl.startsWith("acestream://", ignoreCase = true) -> StreamType.ACESTREAM
                    streamUrl.contains("getstream?id=", ignoreCase = true) -> StreamType.ACESTREAM
                    streamUrl.startsWith("sop://", ignoreCase = true) -> StreamType.SOPCAST
                    else -> StreamType.DIRECT
                }

                val streamId = when {
                    streamUrl.contains("getstream?id=", ignoreCase = true) -> {
                        val idx = streamUrl.indexOf("id=", ignoreCase = true) + 3
                        streamUrl.substring(idx).trim()
                    }
                    streamUrl.startsWith("acestream://", ignoreCase = true) -> {
                        streamUrl.removePrefix("acestream://").trim()
                    }
                    else -> streamUrl
                }

                val channel = ChannelItem(
                    name = currentTitle,
                    streamId = streamId,
                    type = streamType,
                    rawUrl = streamUrl
                )

                events.add(
                    EventItem(
                        id = (events.size + 1).toString(),
                        title = currentTitle,
                        sport = currentGroup,
                        competition = "M3U Stream",
                        channels = listOf(channel),
                        directStreamId = streamId
                    )
                )
                currentTitle = ""
            }
        }
        return events
    }
}
