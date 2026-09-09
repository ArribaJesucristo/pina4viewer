package com.bone.android.a4v.oficial.data.parser

import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.StreamType
import org.json.JSONArray

object PinaVisionParser {

    var lastParsedUpdatedAt: String? = null
        private set

    fun init(savedTime: String?) {
        if (lastParsedUpdatedAt == null && !savedTime.isNullOrBlank()) {
            lastParsedUpdatedAt = savedTime
        }
    }

    fun parse(jsonString: String): List<EventItem> {
        return try {
            val events = mutableListOf<EventItem>()
            val array = JSONArray(jsonString.trim())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue

                // Check for metadata entry with workflow execution timestamp
                if (obj.optBoolean("_metadata", false) || obj.has("updatedAt")) {
                    val upd = obj.optString("updatedAt", "")
                    if (upd.isNotEmpty()) {
                        lastParsedUpdatedAt = upd
                    }
                    continue
                }

                val id = obj.optString("id", "pina_$i")
                val title = obj.optString("title", "").trim()
                val sport = obj.optString("sport", "")
                val competition = obj.optString("competition", "")
                val time = obj.optString("time", "")
                val date = obj.optString("date", "")

                // Defense-in-depth: skip events with dates strictly in the past (e.g. yesterday)
                if (date.matches(Regex("^\\d{2}/\\d{2}/\\d{4}$"))) {
                    try {
                        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                        sdf.timeZone = java.util.TimeZone.getTimeZone("Europe/Madrid")
                        val evDate = sdf.parse(date)
                        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        cal.set(java.util.Calendar.MINUTE, 0)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        if (evDate != null && evDate.before(cal.time)) {
                            continue
                        }
                    } catch (_: Exception) {
                    }
                }

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

                if (title.isNotEmpty() && title != "00" && channels.isNotEmpty()) {
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

            // Client-side defense-in-depth consolidation against duplicates from older/unmerged agendas
            val consolidated = mutableListOf<EventItem>()
            for (ev in events) {
                if (ev.title == "00") continue
                val existing = consolidated.find { c ->
                    c.date.equals(ev.date, ignoreCase = true) &&
                    c.time.equals(ev.time, ignoreCase = true) &&
                    (c.channels.any { ch1 -> ev.channels.any { ch2 -> ch1.streamId == ch2.streamId } } ||
                     isSameTitleFuzzy(c.title, ev.title))
                }

                if (existing != null) {
                    val existingHashes = existing.channels.map { it.streamId }.toSet()
                    val newChannels = existing.channels.toMutableList()
                    for (ch in ev.channels) {
                        if (!existingHashes.contains(ch.streamId)) {
                            newChannels.add(ch)
                        }
                    }

                    // Prefer clean Title Case over ALL_CAPS
                    val bestTitle = if (existing.title.all { it.isUpperCase() || !it.isLetter() } &&
                        !ev.title.all { it.isUpperCase() || !it.isLetter() }) {
                        ev.title
                    } else existing.title

                    val bestSport = if (existing.sport in listOf("OTROS", "DEPORTES", "FUTBOL") &&
                        ev.sport in listOf("FUTBOL AMERICANO", "NFL", "CICLISMO", "SNOOKER", "GOLF", "BALONCESTO", "TENIS")) {
                        ev.sport
                    } else existing.sport

                    val index = consolidated.indexOf(existing)
                    consolidated[index] = existing.copy(
                        title = bestTitle,
                        sport = bestSport,
                        channels = newChannels
                    )
                } else {
                    consolidated.add(ev)
                }
            }

            consolidated
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isSameTitleFuzzy(t1: String, t2: String): Boolean {
        val clean1 = cleanTitleForComparison(t1)
        val clean2 = cleanTitleForComparison(t2)
        if (clean1.isEmpty() || clean2.isEmpty()) return false
        if (clean1 == clean2) return true
        if (clean1.contains(clean2) || clean2.contains(clean1)) return true

        val words1 = clean1.split(" ").filter { it.length > 3 }.toSet()
        val words2 = clean2.split(" ").filter { it.length > 3 }.toSet()
        if (words1.isNotEmpty() && words2.isNotEmpty()) {
            val common = words1.intersect(words2)
            if (common.size >= 2 || (words1.size == 1 && common.isNotEmpty()) || (words2.size == 1 && common.isNotEmpty())) {
                return true
            }
        }
        return false
    }

    private fun cleanTitleForComparison(s: String): String {
        return s.lowercase()
            .replace("vs", " ")
            .replace("-", " ")
            .replace("feyenord", "feyenoord")
            .replace("napoli", "napoles")
            .replace("stage", "etapa")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}

