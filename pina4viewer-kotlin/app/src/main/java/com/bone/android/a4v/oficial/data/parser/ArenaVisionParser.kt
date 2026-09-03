package com.bone.android.a4v.oficial.data.parser

import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.StreamType
import java.util.regex.Pattern

object ArenaVisionParser {

    private val ROW_PATTERN = Pattern.compile("(?i)<tr[^>]*>(.*?)</tr>", Pattern.DOTALL)
    private val COL_PATTERN = Pattern.compile("(?i)<td[^>]*>(.*?)</td>", Pattern.DOTALL)

    fun parseHtmlAgenda(html: String): List<EventItem> {
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
                    val channelList = extractChannels(channelsRaw)
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

    fun extractChannels(raw: String): List<ChannelItem> {
        if (raw.isEmpty()) return emptyList()
        val cleaned = raw.replace("{", "[").replace("}", "]").replace("<br/>", "/").replace(" ", "/")
        val tokens = cleaned.split("/", ",").filter { it.isNotBlank() }
        
        return tokens.mapNotNull { ch ->
            val trimmed = ch.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("[") && trimmed.endsWith("]")) {
                null
            } else {
                ChannelItem(
                    name = trimmed,
                    streamId = trimmed,
                    type = StreamType.ACESTREAM
                )
            }
        }
    }

    fun getFallbackAgenda(): List<EventItem> {
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
