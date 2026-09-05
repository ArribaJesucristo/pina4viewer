package com.bone.android.a4v.oficial.data.parser

import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.StreamType
import org.json.JSONArray

object PinaVisionParser {

    fun parse(jsonString: String): List<EventItem> {
        return try {
            val events = mutableListOf<EventItem>()
            val array = JSONArray(jsonString.trim())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "pina_$i")
                val title = obj.optString("title", "").trim()
                val sport = obj.optString("sport", "")
                val competition = obj.optString("competition", "")
                val time = obj.optString("time", "")
                val date = obj.optString("date", "")

                val channelsArray = obj.optJSONArray("channels")
                val channels = mutableListOf<ChannelItem>()
                if (channelsArray != null) {
                    for (j in 0 until channelsArray.length()) {
                        val chObj = channelsArray.optJSONObject(j) ?: continue
                        val name = chObj.optString("name", "")
                        val streamId = chObj.optString("streamId", "")
                        val typeStr = chObj.optString("type", "ACESTREAM")
                        val streamType = if (typeStr.equals("SOPCAST", ignoreCase = true)) {
                            StreamType.SOPCAST
                        } else {
                            StreamType.ACESTREAM
                        }
                        if (name.isNotEmpty() && streamId.isNotEmpty()) {
                            channels.add(ChannelItem(name = name, streamId = streamId, type = streamType))
                        }
                    }
                }

                if (title.isNotEmpty() && channels.isNotEmpty()) {
                    events.add(
                        EventItem(
                            id = id,
                            title = title,
                            sport = sport,
                            competition = competition,
                            time = time,
                            date = date,
                            channels = channels
                        )
                    )
                }
            }
            events
        } catch (e: Exception) {
            emptyList()
        }
    }
}
