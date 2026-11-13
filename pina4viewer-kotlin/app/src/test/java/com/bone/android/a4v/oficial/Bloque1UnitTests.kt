package com.bone.android.a4v.oficial

import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.StreamType
import com.bone.android.a4v.oficial.data.parser.PinaVisionParser
import com.bone.android.a4v.oficial.ui.viewmodel.MainViewModel
import com.bone.android.a4v.oficial.util.SportFilterMatcher
import com.bone.android.a4v.oficial.util.StreamLauncher
import com.bone.android.a4v.oficial.util.TimeZoneHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class Bloque1UnitTests {

    private fun madridMillis(year: Int, month1Based: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid")).apply {
            set(year, month1Based - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun sportFilter_handlesAccentsAndSeparatesSoccerFromNfl() {
        val soccer = EventItem(id = "1", title = "Real Madrid vs Barcelona", sport = "Fútbol")
        val nfl = EventItem(id = "2", title = "Chiefs vs Bills", sport = "Fútbol Americano / NFL")
        val baseball = EventItem(id = "3", title = "Yankees vs Dodgers", sport = "Béisbol")
        val mma = EventItem(id = "4", title = "UFC Main Event", sport = "Boxeo / MMA")

        assertTrue(SportFilterMatcher.matchesFilter(soccer, SportFilterMatcher.FILTER_FUTBOL))
        assertFalse(SportFilterMatcher.matchesFilter(nfl, SportFilterMatcher.FILTER_FUTBOL))

        val nflMenuKey = SportFilterMatcher.MENU_SPORTS
            .first { it.label.contains("NFL") }.filterKey
        assertTrue(SportFilterMatcher.matchesFilter(nfl, nflMenuKey))
        assertFalse(SportFilterMatcher.matchesFilter(soccer, nflMenuKey))

        val baseballKey = SportFilterMatcher.MENU_SPORTS
            .first { it.label.contains("Béisbol") }.filterKey
        assertTrue(SportFilterMatcher.matchesFilter(baseball, baseballKey))

        val boxeoKey = SportFilterMatcher.MENU_SPORTS
            .first { it.label.contains("Boxeo") }.filterKey
        assertTrue(SportFilterMatcher.matchesFilter(mma, boxeoKey))

        assertEquals(R.mipmap.futbol_americano, SportFilterMatcher.iconResForSport(nfl.sport))
        assertEquals(R.mipmap.futbol, SportFilterMatcher.iconResForSport(soccer.sport))
        assertNotEquals(R.mipmap.futbol, SportFilterMatcher.iconResForSport(nfl.sport))
        assertEquals(R.mipmap.beisbol, SportFilterMatcher.iconResForSport(baseball.sport))
    }

    @Test
    fun sportFilter_247ContextualFiltersSplitTdtArenaAndSports() {
        val tdtChannel = EventItem(
            id = "tdt1",
            title = "Teledeporte HD",
            sport = "DIRECTO 24/7",
            channels = listOf(ChannelItem("Web", "https://rtve.es", StreamType.DIRECT))
        )
        val arenaChannel = EventItem(
            id = "av1",
            title = "ArenaVision 01",
            sport = "DIRECTO 24/7"
        )
        val daznChannel = EventItem(
            id = "dep1",
            title = "DAZN F1 1080p",
            sport = "DIRECTO 24/7"
        )

        assertTrue(SportFilterMatcher.matchesFilter(tdtChannel, SportFilterMatcher.FILTER_TV_TDT))
        assertFalse(SportFilterMatcher.matchesFilter(arenaChannel, SportFilterMatcher.FILTER_TV_TDT))
        assertFalse(SportFilterMatcher.matchesFilter(daznChannel, SportFilterMatcher.FILTER_TV_TDT))

        assertTrue(SportFilterMatcher.matchesFilter(arenaChannel, SportFilterMatcher.FILTER_TV_ARENAVISION))
        assertFalse(SportFilterMatcher.matchesFilter(daznChannel, SportFilterMatcher.FILTER_TV_ARENAVISION))

        assertTrue(SportFilterMatcher.matchesFilter(daznChannel, SportFilterMatcher.FILTER_TV_DEPORTES))
        assertFalse(SportFilterMatcher.matchesFilter(arenaChannel, SportFilterMatcher.FILTER_TV_DEPORTES))
        assertFalse(SportFilterMatcher.matchesFilter(tdtChannel, SportFilterMatcher.FILTER_TV_DEPORTES))
    }

    @Test
    fun timeZoneHelper_convertsTimesAndCrossesMidnightProperly() {
        val londonZone = TimeZoneHelper.resolveTargetTimeZone("Europe/London")
        val buenosAiresZone = TimeZoneHelper.resolveTargetTimeZone("America/Argentina/Buenos_Aires")

        val afternoonNow = madridMillis(2026, 11, 12, 15, 0)
        val lateMatch = EventItem(id = "e1", title = "Match", sport = "Fútbol", date = "Hoy", time = "23:30")
        val inLondon = TimeZoneHelper.adjustAndFilterEvent(lateMatch, londonZone, "12/11/2026 14:00", afternoonNow)
        assertNotNull(inLondon)
        assertEquals("22:30", inLondon?.time)
        assertEquals("Hoy", inLondon?.date)

        val nightNow = madridMillis(2026, 11, 12, 22, 0)
        val postMidnightMadrid = EventItem(
            id = "e2",
            title = "NBA",
            sport = "Baloncesto",
            date = "13/11/2026",
            time = "00:30"
        )
        val inBuenosAires = TimeZoneHelper.adjustAndFilterEvent(
            postMidnightMadrid,
            buenosAiresZone,
            "12/11/2026 21:00",
            nightNow
        )
        assertNotNull(inBuenosAires)
        assertEquals("20:30", inBuenosAires?.time)
        assertEquals("Hoy", inBuenosAires?.date)
    }

    @Test
    fun timeZoneHelper_threeHourPurgeWorksAcrossMidnight() {
        val madridZone = TimeZoneHelper.resolveTargetTimeZone("Europe/Madrid")
        val nowAt1Am = madridMillis(2026, 11, 13, 1, 0)
        val refPrevEvening = "12/11/2026 20:00"

        val recentLateMatch = EventItem(id = "recent", title = "Recent", sport = "Fútbol", date = "Hoy", time = "22:30")
        val expiredMatch = EventItem(id = "expired", title = "Expired", sport = "Fútbol", date = "Hoy", time = "20:00")
        val live247 = EventItem(id = "live", title = "Teledeporte", sport = "DIRECTO 24/7", date = "", time = "24/7")

        val kept = TimeZoneHelper.adjustAndFilterEvent(recentLateMatch, madridZone, refPrevEvening, nowAt1Am)
        val purged = TimeZoneHelper.adjustAndFilterEvent(expiredMatch, madridZone, refPrevEvening, nowAt1Am)
        val kept247 = TimeZoneHelper.adjustAndFilterEvent(live247, madridZone, refPrevEvening, nowAt1Am)

        assertNotNull(kept)
        assertEquals("12/11/2026", kept?.date)
        assertNull(purged)
        assertNotNull(kept247)
    }

    @Test
    fun mainViewModelFilter_ignoresStaleReferenceDateForLiveSourcesAndFiltersQuery() {
        val now = madridMillis(2026, 11, 13, 18, 0)
        val events = listOf(
            EventItem(id = "1", title = "Girona vs Sevilla", sport = "Fútbol", date = "Hoy", time = "21:00"),
            EventItem(id = "2", title = "Lakers vs Celtics", sport = "Baloncesto", date = "Hoy", time = "22:00")
        )

        val liveFiltered = MainViewModel.filterAndTransformEvents(
            events = events,
            query = "girona",
            sport = SportFilterMatcher.FILTER_FUTBOL,
            timezoneId = "Europe/London",
            isFileBasedSource = false,
            referenceUpdatedAt = "01/11/2026 10:00",
            nowMillis = now
        )

        assertEquals(1, liveFiltered.size)
        assertEquals("Girona vs Sevilla", liveFiltered[0].title)
        assertEquals("20:00", liveFiltered[0].time)
        assertEquals("Hoy", liveFiltered[0].date)
    }

    @Test
    fun streamLauncherResolveHash_validatesHexStripsPrefixAndRejectsBrokenHashes() {
        val validHex = "0123456789abcdef0123456789abcdef01234567"
        assertEquals(validHex, StreamLauncher.resolveHash(validHex))
        assertEquals(validHex.uppercase(), StreamLauncher.resolveHash("acestream://${validHex.uppercase()}"))
        assertNull(StreamLauncher.resolveHash("0123456789abcdef0123456789abcdef0123f33y"))
        assertNull(StreamLauncher.resolveHash("AV99_NONEXISTENT"))
        assertNull(StreamLauncher.resolveHash(""))
    }

    @Test
    fun pinaVisionParser_preservesYesterdayWindowForMidnightRolloverAndDropsOlderAssets() {
        val madridTz = TimeZone.getTimeZone("Europe/Madrid")
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = madridTz }
        val todayStart = madridMillis(2026, 11, 13, 0, 0)
        val yesterdayStart = madridMillis(2026, 11, 12, 0, 0)

        assertFalse(PinaVisionParser.shouldSkipStaleEvent("Hoy", "12/11/2026 20:00", todayStart, yesterdayStart, sdf))
        assertFalse(PinaVisionParser.shouldSkipStaleEvent("12/11/2026", "12/11/2026 20:00", todayStart, yesterdayStart, sdf))
        assertTrue(PinaVisionParser.shouldSkipStaleEvent("10/11/2026", "12/11/2026 20:00", todayStart, yesterdayStart, sdf))
        assertTrue(PinaVisionParser.shouldSkipStaleEvent("Hoy", "10/11/2026 20:00", todayStart, yesterdayStart, sdf))
    }
}
