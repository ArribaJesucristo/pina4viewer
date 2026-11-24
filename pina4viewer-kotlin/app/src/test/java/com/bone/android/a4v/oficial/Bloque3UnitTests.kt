package com.bone.android.a4v.oficial

import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.SourceType
import com.bone.android.a4v.oficial.data.model.StreamType
import com.bone.android.a4v.oficial.data.parser.PinaVisionParser
import com.bone.android.a4v.oficial.data.repository.EventsRepository
import com.bone.android.a4v.oficial.ui.viewmodel.MainViewModel
import com.bone.android.a4v.oficial.util.DnsHelper
import com.bone.android.a4v.oficial.util.SportFilterMatcher
import com.bone.android.a4v.oficial.util.TimeZoneHelper
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class Bloque3UnitTests {

    private fun madridMillis(year: Int, month1Based: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid")).apply {
            set(year, month1Based - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun sportFilterMatcher_searchIsAccentInsensitiveAndMultiWord() {
        val cadizMatch = EventItem(
            id = "1",
            title = "Cádiz CF vs Atlético de Madrid",
            sport = "Fútbol",
            competition = "LaLiga EA Sports",
            channels = listOf(ChannelItem("M+ LaLiga", "0123456789abcdef0123456789abcdef01234567"))
        )
        val padelMatch = EventItem(
            id = "2",
            title = "Tapia / Coello vs Galán / Chingotto",
            sport = "Pádel",
            competition = "Premier Padel"
        )

        assertTrue(SportFilterMatcher.matchesSearch(cadizMatch, "futbol"))
        assertTrue(SportFilterMatcher.matchesSearch(cadizMatch, "cadiz"))
        assertTrue(SportFilterMatcher.matchesSearch(cadizMatch, "atletico"))
        assertTrue(SportFilterMatcher.matchesSearch(cadizMatch, "futbol madrid laliga"))
        assertFalse(SportFilterMatcher.matchesSearch(cadizMatch, "baloncesto"))

        assertTrue(SportFilterMatcher.matchesSearch(padelMatch, "padel galan"))
    }

    @Test
    fun timeZoneHelper_detectsLiveNowAndSupportsLiveFilter() {
        val madridZone = TimeZoneHelper.resolveTargetTimeZone("Europe/Madrid")
        val now = madridMillis(2026, 11, 24, 16, 30)
        val ref = "24/11/2026 10:00"

        val started45MinAgo = EventItem(id = "live1", title = "Partido en Juego", sport = "Fútbol", date = "Hoy", time = "15:45")
        val startsInTwoHours = EventItem(id = "future1", title = "Partido Próximo", sport = "Fútbol", date = "Hoy", time = "18:30")
        val expired4HoursAgo = EventItem(id = "old1", title = "Partido Terminado", sport = "Fútbol", date = "Hoy", time = "12:00")

        val adjLive = TimeZoneHelper.adjustAndFilterEvent(started45MinAgo, madridZone, ref, now)
        val adjFuture = TimeZoneHelper.adjustAndFilterEvent(startsInTwoHours, madridZone, ref, now)
        val adjExpired = TimeZoneHelper.adjustAndFilterEvent(expired4HoursAgo, madridZone, ref, now)

        assertNotNull(adjLive)
        assertTrue(adjLive!!.isLiveNow)
        assertNotNull(adjFuture)
        assertFalse(adjFuture!!.isLiveNow)
        assertNull(adjExpired)

        assertTrue(SportFilterMatcher.matchesFilter(adjLive, SportFilterMatcher.FILTER_LIVE))
        assertFalse(SportFilterMatcher.matchesFilter(adjFuture, SportFilterMatcher.FILTER_LIVE))
    }

    @Test
    fun mainViewModelFilter_sortsChronologicallyKeepingLiveEventsFirst() {
        val now = madridMillis(2026, 11, 24, 16, 30)
        val events = listOf(
            EventItem(id = "late", title = "Liverpool vs City", sport = "Fútbol", date = "Hoy", time = "21:00"),
            EventItem(id = "247", title = "Teledeporte 24/7", sport = "DIRECTO 24/7", date = "24/7", time = "24/7"),
            EventItem(id = "live", title = "Real Madrid vs Arsenal", sport = "Fútbol", date = "Hoy", time = "15:45"),
            EventItem(id = "mid", title = "Barça vs Betis", sport = "Fútbol", date = "Hoy", time = "18:30")
        )

        val result = MainViewModel.filterAndTransformEvents(
            events = events,
            query = "",
            sport = SportFilterMatcher.FILTER_ALL,
            timezoneId = "Europe/Madrid",
            isFileBasedSource = true,
            referenceUpdatedAt = "24/11/2026 10:00",
            nowMillis = now
        )

        assertEquals(listOf("live", "mid", "late", "247"), result.map { it.id })
    }

    @Test
    fun pinaVisionParser_respectsUpdateGlobalFlagAndHandlesTrailingMetadataAndAccents() {
        val initialTime = "24/11/2026 15:00"
        PinaVisionParser.parseWithMetadata(
            jsonString = """[{"_metadata": true, "updatedAt": "$initialTime"}]""",
            updateGlobalTimestamp = true
        )
        assertEquals(initialTime, PinaVisionParser.lastParsedUpdatedAt)

        val now = madridMillis(2026, 11, 24, 16, 0)
        val offlineAssetJson = """
            [
              {
                "id": "ev1",
                "title": "ALCARAZ VS SINNER",
                "sport": "OTROS",
                "competition": "ATP Finals",
                "time": "20:00",
                "date": "Hoy",
                "channels": [{"name": "Tenis HD", "streamId": "0123456789abcdef0123456789abcdef01234567", "type": "ACESTREAM"}]
              },
              {
                "id": "ev2",
                "title": "Alcaraz vs Sinner",
                "sport": "Tenis",
                "competition": "ATP Finals",
                "time": "20:00",
                "date": "Hoy",
                "channels": [{"name": "Tenis HD", "streamId": "0123456789abcdef0123456789abcdef01234567", "type": "ACESTREAM"}]
              },
              {"_metadata": true, "updatedAt": "24/11/2026 08:00"}
            ]
        """.trimIndent()

        val parsedOffline = PinaVisionParser.parseWithMetadata(
            jsonString = offlineAssetJson,
            nowMillis = now,
            updateGlobalTimestamp = false
        )

        assertEquals(initialTime, PinaVisionParser.lastParsedUpdatedAt)
        assertEquals("24/11/2026 08:00", parsedOffline.updatedAt)
        assertEquals(1, parsedOffline.events.size)
        assertEquals("Tenis", parsedOffline.events[0].sport)
        assertEquals("Alcaraz vs Sinner", parsedOffline.events[0].title)
    }

    @Test
    fun dnsHelper_enforcesTtlOnDynamicCacheAndPreservesPreseededHosts() {
        val originalClock = DnsHelper.clockMillis
        DnsHelper.clearDynamicCache()
        try {
            var fakeNow = 1_000_000L
            DnsHelper.clockMillis = { fakeNow }

            var resolverCalls = 0
            val resolver: (String) -> List<InetAddress> = {
                resolverCalls++
                listOf(InetAddress.getByName("1.2.3.4"))
            }

            // Presembrado no invoca resolver
            val preseeded = DnsHelper.lookupWithResolver("raw.githubusercontent.com", resolver)
            assertTrue(preseeded.isNotEmpty())
            assertEquals(0, resolverCalls)

            // Host dinámico: 1ª llamada resuelve, a los 5 min usa caché, a los 16 min expira TTL
            DnsHelper.lookupWithResolver("stream-host.example", resolver)
            assertEquals(1, resolverCalls)

            fakeNow += 5L * 60L * 1000L
            DnsHelper.lookupWithResolver("stream-host.example", resolver)
            assertEquals(1, resolverCalls)

            fakeNow += 11L * 60L * 1000L
            DnsHelper.lookupWithResolver("stream-host.example", resolver)
            assertEquals(2, resolverCalls)

            DnsHelper.clearDynamicCache()
            DnsHelper.lookupWithResolver("stream-host.example", resolver)
            assertEquals(3, resolverCalls)
        } finally {
            DnsHelper.clockMillis = originalClock
            DnsHelper.clearDynamicCache()
        }
    }

    @Test
    fun eventsRepository_doesNotStickOfflineFallbackInOnlineCacheAndSharesPinaAgenda() {
        var failNetwork = true
        var networkCalls = 0

        val madridTz = TimeZone.getTimeZone("Europe/Madrid")
        val futureInstant = System.currentTimeMillis() + 60L * 60L * 1000L
        val futureDate = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = madridTz }.format(Date(futureInstant))
        val futureTime = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = madridTz }.format(Date(futureInstant))
        val todayUpdatedAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).apply { timeZone = madridTz }.format(Date())

        val validArenaHtml = """
            <html><body>
            <div id="streams">AV7#0123456789abcdef0123456789abcdef01234567</div>
            <table>
              <tr><td>$futureDate</td><td>$futureTime</td><td>FUTBOL</td><td>LALIGA</td><td>ONLINE MATCH</td><td>7</td></tr>
            </table>
            </body></html>
        """.trimIndent()

        val validPinaJson = """
            [
              {"_metadata": true, "updatedAt": "$todayUpdatedAt"},
              {
                "id": "p1",
                "title": "Partido Online",
                "sport": "Fútbol",
                "competition": "Liga",
                "time": "$futureTime",
                "date": "Hoy",
                "channels": [{"name": "Canal 1", "streamId": "0123456789abcdef0123456789abcdef01234567", "type": "ACESTREAM"}]
              },
              {
                "id": "tv1",
                "title": "Teledeporte 24/7",
                "sport": "DIRECTO 24/7",
                "competition": "Canales en Directo 24/7",
                "time": "24/7",
                "date": "24/7",
                "channels": [{"name": "TDP", "streamId": "https://example.com/tdp.m3u8", "type": "DIRECT"}]
              }
            ]
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                networkCalls++
                val url = chain.request().url.toString()
                if (failNetwork) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body("error".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else if (url.contains("agenda.json")) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(validPinaJson.toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(validArenaHtml.toResponseBody("text/html".toMediaType()))
                        .build()
                }
            })
            .build()

        val repo = EventsRepository(context = null, client = client)

        runBlocking {
            // 1. Fallo de red devuelve fallback offline sin contaminar caché online
            val offlineResult = repo.getEventsWithMeta(SourceType.SERVER_IN, forceRefresh = false).getOrThrow()
            assertTrue(offlineResult.isOffMode)
            assertNull(repo.peekCachedEvents(SourceType.SERVER_IN))

            // 2. Al volver la red, la siguiente lectura sin forceRefresh sí recupera datos online
            failNetwork = false
            val onlineResult = repo.getEventsWithMeta(SourceType.SERVER_IN, forceRefresh = false).getOrThrow()
            assertFalse(onlineResult.isOffMode)
            assertEquals("ONLINE MATCH", onlineResult.events.first().title)

            // 3. OFF_MODE descarga agenda.json una vez y SEARCH reutiliza el mismo contenido sin segunda llamada HTTP
            val callsBeforePina = networkCalls
            val pinaRes = repo.getEventsWithMeta(SourceType.OFF_MODE, forceRefresh = false).getOrThrow()
            val tvRes = repo.getEventsWithMeta(SourceType.SEARCH, forceRefresh = false).getOrThrow()
            assertEquals(callsBeforePina + 1, networkCalls)
            assertEquals(2, pinaRes.events.size)
            assertEquals(1, tvRes.events.size)
            assertEquals("Teledeporte 24/7", tvRes.events.first().title)
        }
    }
}
