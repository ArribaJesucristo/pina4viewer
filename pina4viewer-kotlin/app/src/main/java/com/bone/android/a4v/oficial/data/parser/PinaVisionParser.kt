package com.bone.android.a4v.oficial.data.parser

import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.StreamType
import com.bone.android.a4v.oficial.util.SportFilterMatcher
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PinaVisionParser {

    @Volatile
    var lastParsedUpdatedAt: String? = null
        private set

    private val MADRID_TZ: TimeZone = TimeZone.getTimeZone("Europe/Madrid")

    fun init(savedTime: String?) {
        if (lastParsedUpdatedAt == null && !savedTime.isNullOrBlank()) {
            lastParsedUpdatedAt = savedTime
        }
    }

    fun parse(jsonString: String, nowMillis: Long = System.currentTimeMillis()): List<EventItem> {
        return try {
            val events = mutableListOf<EventItem>()
            val array = JSONArray(jsonString.trim())

            val madridTodayStart = startOfMadridDay(nowMillis)
            val madridYesterdayStart = (Calendar.getInstance(MADRID_TZ).apply {
                timeInMillis = madridTodayStart
                add(Calendar.DAY_OF_YEAR, -1)
            }).timeInMillis
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = MADRID_TZ }

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue

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

                if (shouldSkipStaleEvent(date, lastParsedUpdatedAt, madridTodayStart, madridYesterdayStart, sdf)) {
                    continue
                }

                val channelsArray = obj.optJSONArray("channels")
                val channels = mutableListOf<ChannelItem>()
                if (channelsArray != null) {
                    for (j in 0 until channelsArray.length()) {
                        val chObj = channelsArray.optJSONObject(j) ?: continue
                        val name = chObj.optString("name", "")
                        val streamId = chObj.optString("streamId", "")
                        val rawUrl = chObj.optString("rawUrl", "").takeIf { it.isNotBlank() }
                        val typeStr = chObj.optString("type", "")
                        val streamType = when {
                            typeStr.equals("SOPCAST", ignoreCase = true) -> StreamType.SOPCAST
                            typeStr.equals("WEB", ignoreCase = true) -> StreamType.WEB
                            typeStr.equals("DIRECT", ignoreCase = true) -> StreamType.DIRECT
                            typeStr.equals("ACESTREAM", ignoreCase = true) -> StreamType.ACESTREAM
                            streamId.startsWith("http://", ignoreCase = true) || streamId.startsWith("https://", ignoreCase = true) -> StreamType.DIRECT
                            else -> StreamType.ACESTREAM
                        }
                        if (name.isNotEmpty() && (streamId.isNotEmpty() || rawUrl != null)) {
                            channels.add(
                                ChannelItem(
                                    name = name,
                                    streamId = streamId.ifEmpty { rawUrl.orEmpty() },
                                    type = streamType,
                                    rawUrl = rawUrl ?: if (streamType == StreamType.WEB) streamId else null
                                )
                            )
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

            // Consolidación defensiva en cliente evitando fusionar deportes incompatibles
            val consolidated = mutableListOf<EventItem>()
            for (ev in events) {
                if (ev.title == "00") continue
                val existing = consolidated.find { c ->
                    c.date.equals(ev.date, ignoreCase = true) &&
                        c.time.equals(ev.time, ignoreCase = true) &&
                        areSportsCompatible(c.sport, ev.sport) &&
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

                    val bestTitle = if (existing.title.all { it.isUpperCase() || !it.isLetter() } &&
                        !ev.title.all { it.isUpperCase() || !it.isLetter() }
                    ) {
                        ev.title
                    } else {
                        existing.title
                    }

                    val bestSport = if (existing.sport in listOf("OTROS", "DEPORTES", "FUTBOL") &&
                        ev.sport in listOf("FUTBOL AMERICANO", "NFL", "CICLISMO", "SNOOKER", "GOLF", "BALONCESTO", "TENIS")
                    ) {
                        ev.sport
                    } else {
                        existing.sport
                    }

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

    internal fun shouldSkipStaleEvent(
        date: String,
        updatedAt: String?,
        @Suppress("UNUSED_PARAMETER") madridTodayStart: Long,
        madridYesterdayStart: Long,
        sdf: SimpleDateFormat
    ): Boolean {
        val clean = date.trim()
        if (clean.matches(Regex("^\\d{2}/\\d{2}/\\d{4}$"))) {
            return try {
                val evDate: Date? = sdf.parse(clean)
                // Delegar la ventana exacta de 3h post-inicio a TimeZoneHelper, conservando eventos de ayer
                // para cruces de medianoche y husos horarios americanos.
                evDate != null && evDate.time < madridYesterdayStart
            } catch (_: Exception) {
                false
            }
        }
        if (updatedAt.isNullOrBlank()) return false
        val fileDateStr = updatedAt.trim().split(" ").firstOrNull().orEmpty()
        val fileTime = try {
            sdf.parse(fileDateStr)?.time
        } catch (_: Exception) {
            null
        } ?: return false

        return when {
            clean.equals("Hoy", ignoreCase = true) -> fileTime < madridYesterdayStart
            clean.equals("Mañana", ignoreCase = true) -> fileTime < madridYesterdayStart
            else -> false
        }
    }

    private fun startOfMadridDay(nowMillis: Long): Long {
        return Calendar.getInstance(MADRID_TZ).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun areSportsCompatible(s1: String, s2: String): Boolean {
        val n1 = SportFilterMatcher.normalize(s1)
        val n2 = SportFilterMatcher.normalize(s2)
        if (n1.isEmpty() || n2.isEmpty() || n1 == "OTROS" || n2 == "OTROS" || n1 == "DEPORTES" || n2 == "DEPORTES") {
            return true
        }
        if (n1 == n2) return true
        // Casos donde un scraper etiqueta FUTBOL AMERICANO y otro lo confundió previamente
        return (n1.contains("FUTBOL") && n2.contains("FUTBOL")) &&
            (n1.contains("AMERICANO") == n2.contains("AMERICANO"))
    }

    private fun isSameTitleFuzzy(t1: String, t2: String): Boolean {
        val clean1 = cleanTitleForComparison(t1)
        val clean2 = cleanTitleForComparison(t2)
        if (clean1.isEmpty() || clean2.isEmpty()) return false
        if (clean1 == clean2) return true
        if (clean1.contains(clean2) || clean2.contains(clean1)) return true

        val stopWords = setOf("real", "club", "atletico", "sporting", "city", "united", "athletic", "olympique")
        val words1 = clean1.split(" ").filter { it.length > 3 && it !in stopWords }.toSet()
        val words2 = clean2.split(" ").filter { it.length > 3 && it !in stopWords }.toSet()
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
