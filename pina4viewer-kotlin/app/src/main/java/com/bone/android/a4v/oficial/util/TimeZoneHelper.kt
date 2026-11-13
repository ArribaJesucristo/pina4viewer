package com.bone.android.a4v.oficial.util

import com.bone.android.a4v.oficial.data.model.EventItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Conversión real de fechas/horas (origen Europe/Madrid -> zona horaria elegida)
 * y cálculo de caducidad de 3 horas incluyendo cruce de medianoche.
 */
object TimeZoneHelper {

    data class TimezoneOption(
        val id: String,
        val label: String
    )

    const val PREFS_NAME = "pina_prefs"
    const val KEY_TIMEZONE_ID = "selected_timezone_id"
    const val DEFAULT_TIMEZONE_ID = "Europe/Madrid"
    const val SYSTEM_TIMEZONE_ID = "SYSTEM"

    private const val THREE_HOURS_MS = 3L * 60L * 60L * 1000L
    private val SOURCE_ZONE: TimeZone = TimeZone.getTimeZone("Europe/Madrid")

    val OPTIONS: List<TimezoneOption> = listOf(
        TimezoneOption("Europe/Madrid", "Madrid, París, Bruselas (Hora España)"),
        TimezoneOption("Europe/London", "Londres, Lisboa, Canarias (GMT / BST)"),
        TimezoneOption("America/Argentina/Buenos_Aires", "Buenos Aires, Montevideo (GMT-3)"),
        TimezoneOption("America/Santiago", "Santiago de Chile"),
        TimezoneOption("America/Bogota", "Bogotá, Lima, CDMX"),
        TimezoneOption(SYSTEM_TIMEZONE_ID, "Hora Local del Dispositivo")
    )

    fun resolveTargetTimeZone(preferenceId: String?): TimeZone {
        val clean = preferenceId?.trim().orEmpty()
        if (clean.isEmpty()) return SOURCE_ZONE
        if (clean == SYSTEM_TIMEZONE_ID) return TimeZone.getDefault()
        return TimeZone.getTimeZone(clean)
    }

    fun getOptionLabel(preferenceId: String?): String {
        val id = preferenceId?.ifBlank { DEFAULT_TIMEZONE_ID } ?: DEFAULT_TIMEZONE_ID
        return OPTIONS.firstOrNull { it.id == id }?.label ?: id
    }

    /**
     * Evalúa si un evento sigue vigente (no supera 3h desde inicio) y convierte fecha/hora
     * desde Europe/Madrid a targetZone. Devuelve null si debe purgarse por antiguo.
     */
    fun adjustAndFilterEvent(
        event: EventItem,
        targetZone: TimeZone,
        referenceUpdatedAt: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): EventItem? {
        if (event.sport.equals("DIRECTO 24/7", ignoreCase = true)) {
            return event
        }

        val sourceDay = resolveSourceCalendarDay(event.date, referenceUpdatedAt, nowMillis)
        val parsedClock = parseClockTime(event.time)

        if (sourceDay != null && parsedClock != null) {
            val eventInstant = Calendar.getInstance(SOURCE_ZONE).apply {
                set(Calendar.YEAR, sourceDay.get(Calendar.YEAR))
                set(Calendar.MONTH, sourceDay.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, sourceDay.get(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, parsedClock.first)
                set(Calendar.MINUTE, parsedClock.second)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Purgar eventos que comenzaron hace más de 3 horas (funciona correctamente incluso entre 00:00 y 03:00)
            if (eventInstant < nowMillis - THREE_HOURS_MS) {
                return null
            }

            val formattedTime = formatTimeInZone(eventInstant, targetZone)
            val formattedDate = formatRelativeDate(eventInstant, targetZone, nowMillis)
            return if (formattedDate == event.date && formattedTime == event.time) {
                event
            } else {
                event.copy(date = formattedDate, time = formattedTime)
            }
        }

        // Eventos sin hora concreta ("Directo", etc.): descartar solo si la fecha es anterior a hoy
        if (sourceDay != null) {
            val todayStart = startOfDayMillis(nowMillis, SOURCE_ZONE)
            val eventDayStart = startOfDayMillis(sourceDay.timeInMillis, SOURCE_ZONE)
            if (eventDayStart < todayStart) {
                return null
            }
        }

        return event
    }

    private fun resolveSourceCalendarDay(
        rawDate: String,
        referenceUpdatedAt: String?,
        nowMillis: Long
    ): Calendar? {
        val dateStr = rawDate.trim()
        if (dateStr.isEmpty()) return null

        if (Regex("^\\d{2}/\\d{2}/\\d{4}$").matches(dateStr)) {
            val parts = dateStr.split("/")
            val day = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val year = parts[2].toIntOrNull() ?: return null
            return Calendar.getInstance(SOURCE_ZONE).apply {
                set(year, month - 1, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        val isToday = dateStr.equals("Hoy", ignoreCase = true)
        val isTomorrow = dateStr.equals("Mañana", ignoreCase = true)
        if (!isToday && !isTomorrow) return null

        val baseCal = parseReferenceDate(referenceUpdatedAt)
            ?: Calendar.getInstance(SOURCE_ZONE).apply { timeInMillis = nowMillis }

        if (isTomorrow) {
            baseCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return baseCal
    }

    private fun parseReferenceDate(updatedAt: String?): Calendar? {
        val raw = updatedAt?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val datePart = raw.split(" ").firstOrNull().orEmpty()
        if (!Regex("^\\d{2}/\\d{2}/\\d{4}$").matches(datePart)) return null
        val parts = datePart.split("/")
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null
        return Calendar.getInstance(SOURCE_ZONE).apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun parseClockTime(rawTime: String): Pair<Int, Int>? {
        val cleaned = rawTime
            .replace("CET", "", ignoreCase = true)
            .replace("CEST", "", ignoreCase = true)
            .trim()
        val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(cleaned) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun formatTimeInZone(instantMillis: Long, zone: TimeZone): String {
        return SimpleDateFormat("HH:mm", Locale.US).apply {
            timeZone = zone
        }.format(Date(instantMillis))
    }

    private fun formatRelativeDate(instantMillis: Long, zone: TimeZone, nowMillis: Long): String {
        val targetCal = Calendar.getInstance(zone).apply { timeInMillis = instantMillis }
        val todayCal = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        val tomorrowCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }

        return when {
            isSameCalendarDay(targetCal, todayCal) -> "Hoy"
            isSameCalendarDay(targetCal, tomorrowCal) -> "Mañana"
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
                timeZone = zone
            }.format(Date(instantMillis))
        }
    }

    private fun isSameCalendarDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun startOfDayMillis(millis: Long, zone: TimeZone): Long {
        return Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
