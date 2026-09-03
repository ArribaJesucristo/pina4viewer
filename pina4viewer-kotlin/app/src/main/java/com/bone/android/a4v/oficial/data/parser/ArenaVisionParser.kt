package com.bone.android.a4v.oficial.data.parser

import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.StreamType
import java.util.regex.Pattern

object ArenaVisionParser {

    private val ROW_PATTERN = Pattern.compile("(?i)<tr[^>]*>(.*?)</tr>", Pattern.DOTALL)
    private val COL_PATTERN = Pattern.compile("(?i)<td[^>]*>(.*?)</td>", Pattern.DOTALL)
    private val CHANNEL_PATTERN = Pattern.compile("([\\d\\-]+)\\s*(\\[[A-Za-z0-9]+\\])?")

    val cachedStreams = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun parseStreams(html: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val startIdx = html.indexOf("streams")
        if (startIdx != -1) {
            val divStart = html.indexOf(">", startIdx)
            val divEnd = html.indexOf("</div>", startIdx)
            if (divStart != -1 && divEnd != -1 && divEnd > divStart) {
                val block = html.substring(divStart + 1, divEnd)
                block.split(",").forEach { item ->
                    val parts = item.split("#")
                    if (parts.size >= 2) {
                        val chNum = parts[0].trim().lowercase().removePrefix("av")
                        val streamUrl = parts[1].trim()
                        map[chNum] = streamUrl
                        cachedStreams[chNum] = streamUrl
                    }
                }
            }
        }
        return map
    }

    fun parseHtmlAgenda(html: String): List<EventItem> {
        val streamMap = parseStreams(html)
        val events = mutableListOf<EventItem>()
        val rowMatcher = ROW_PATTERN.matcher(html)

        var index = 0
        while (rowMatcher.find()) {
            val rowContent = rowMatcher.group(1) ?: continue
            val colMatcher = COL_PATTERN.matcher(rowContent)
            val columns = mutableListOf<String>()

            while (colMatcher.find()) {
                val rawText = colMatcher.group(1)
                    ?.replace(Regex("(?i)<br\\s*/?>"), " ")
                    ?.replace(Regex("<[^>]+>"), "")
                    ?.replace("&nbsp;", " ")
                    ?.trim()
                    .orEmpty()
                columns.add(rawText)
            }

            // ArenaVision table columns: [Date, Time, Sport, Competition, Match, Channels]
            if (columns.size >= 5) {
                val date = columns.getOrNull(0).orEmpty()
                val time = columns.getOrNull(1).orEmpty()
                val sport = columns.getOrNull(2).orEmpty()
                val competition = columns.getOrNull(3).orEmpty()
                val match = columns.getOrNull(4).orEmpty()
                val channelsRaw = columns.getOrNull(5).orEmpty()

                if (match.isNotEmpty() && !match.equals("EVENT", ignoreCase = true) && !match.equals("MATCH", ignoreCase = true)) {
                    val channelList = extractChannels(channelsRaw, streamMap)
                    events.add(
                        EventItem(
                            id = (index++).toString(),
                            title = match,
                            sport = sport,
                            competition = competition,
                            time = time,
                            date = date,
                            channels = channelList
                        )
                    )
                }
            }
        }

        if (events.isEmpty()) {
            return getFallbackAgenda()
        }

        return events
    }

    fun extractChannels(raw: String, streamMap: Map<String, String> = cachedStreams): List<ChannelItem> {
        if (raw.isEmpty()) return emptyList()
        val matcher = CHANNEL_PATTERN.matcher(raw)
        val result = mutableListOf<ChannelItem>()

        while (matcher.find()) {
            val numGroup = matcher.group(1).orEmpty()
            val langGroup = matcher.group(2)?.trim().orEmpty()

            val nums = numGroup.split("-")
            for (n in nums) {
                val cleanNum = n.trim()
                if (cleanNum.isNotEmpty()) {
                    val displayName = if (langGroup.isNotEmpty()) "AV$cleanNum $langGroup" else "AV$cleanNum"
                    val streamUrl = streamMap[cleanNum] ?: "AV$cleanNum"
                    result.add(
                        ChannelItem(
                            name = displayName,
                            streamId = streamUrl,
                            type = StreamType.ACESTREAM
                        )
                    )
                }
            }
        }

        return result
    }

    private var defaultAssetHtml: String? = null

    fun initDefaultAgenda(context: android.content.Context) {
        try {
            if (defaultAssetHtml == null) {
                defaultAssetHtml = context.assets.open("default_agenda.html").bufferedReader().use { it.readText() }
                defaultAssetHtml?.let { parseStreams(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getFallbackAgenda(): List<EventItem> {
        val asset = defaultAssetHtml
        if (!asset.isNullOrEmpty()) {
            val parsed = parseHtmlAgenda(asset)
            if (parsed.isNotEmpty()) return parsed
        }
        val today = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        return listOf(
            EventItem(
                id = "1",
                title = "ITALY - SERBIA",
                sport = "BALONCESTO",
                competition = "FIBA WORLDCUP",
                time = "19:30",
                date = today,
                channels = listOf(
                    ChannelItem("AV110", "AV110", StreamType.ACESTREAM),
                    ChannelItem("AV111", "AV111", StreamType.ACESTREAM)
                )
            ),
            EventItem(
                id = "2",
                title = "OSASUNA - GETAFE",
                sport = "FUTBOL",
                competition = "SPANISH LEAGUE",
                time = "19:30",
                date = today,
                channels = listOf(
                    ChannelItem("AV7", "AV7", StreamType.ACESTREAM),
                    ChannelItem("AV8", "AV8", StreamType.ACESTREAM),
                    ChannelItem("AV90", "AV90", StreamType.ACESTREAM),
                    ChannelItem("AV91", "AV91", StreamType.ACESTREAM)
                )
            ),
            EventItem(
                id = "3",
                title = "PORTUGAL - GEORGIA",
                sport = "BALONCESTO",
                competition = "FIBA WORLDCUP",
                time = "20:00",
                date = today,
                channels = listOf(
                    ChannelItem("AV112", "AV112", StreamType.ACESTREAM),
                    ChannelItem("AV122", "AV122", StreamType.ACESTREAM)
                )
            ),
            EventItem(
                id = "4",
                title = "MONTENEGRO - UKRAINE",
                sport = "BALONCESTO",
                competition = "FIBA WORLDCUP",
                time = "20:30",
                date = today,
                channels = listOf(
                    ChannelItem("AV113", "AV113", StreamType.ACESTREAM),
                    ChannelItem("AV123", "AV123", StreamType.ACESTREAM)
                )
            ),
            EventItem(
                id = "5",
                title = "TERUEL - RM CASTILLAS",
                sport = "FUTBOL",
                competition = "1ST FEDERATION",
                time = "21:15",
                date = today,
                channels = listOf(
                    ChannelItem("AV67", "AV67", StreamType.ACESTREAM),
                    ChannelItem("AV68", "AV68", StreamType.ACESTREAM),
                    ChannelItem("AV117", "AV117", StreamType.ACESTREAM)
                )
            ),
            EventItem(
                id = "6",
                title = "ISLAND - TURKEY",
                sport = "BALONCESTO",
                competition = "FIBA WORLDCUP",
                time = "21:30",
                date = today,
                channels = listOf(
                    ChannelItem("AV110", "AV110", StreamType.ACESTREAM),
                    ChannelItem("AV111", "AV111", StreamType.ACESTREAM)
                )
            ),
            EventItem(
                id = "7",
                title = "BARCELONA - RAYO VALLECANO",
                sport = "FUTBOL",
                competition = "SPANISH LEAGUE",
                time = "21:30",
                date = today,
                channels = listOf(
                    ChannelItem("AV80", "AV80", StreamType.ACESTREAM),
                    ChannelItem("AV89", "AV89", StreamType.ACESTREAM),
                    ChannelItem("AV81", "AV81", StreamType.ACESTREAM)
                )
            ),
            EventItem(
                id = "8",
                title = "CELTA B - CASTELLON",
                sport = "FUTBOL",
                competition = "SPANISH LEAGUE 2",
                time = "21:30",
                date = today,
                channels = listOf(
                    ChannelItem("AV85", "AV85", StreamType.ACESTREAM),
                    ChannelItem("AV86", "AV86", StreamType.ACESTREAM)
                )
            )
        )
    }
}
