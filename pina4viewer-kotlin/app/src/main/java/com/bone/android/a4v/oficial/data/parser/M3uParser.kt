package com.bone.android.a4v.oficial.data.parser

import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.StreamType
import java.io.BufferedReader
import java.io.StringReader

object M3uParser {

    fun parse(content: String): List<EventItem> {
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
