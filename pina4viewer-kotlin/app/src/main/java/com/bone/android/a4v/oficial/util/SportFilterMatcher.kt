package com.bone.android.a4v.oficial.util

import com.bone.android.a4v.oficial.R
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.StreamType
import java.text.Normalizer
import java.util.Locale

/**
 * Taxonomía unificada de deportes, iconos y filtros (evita fallos por tildes como "Fútbol"/"Béisbol"
 * o colisiones de FUTBOL AMERICANO (NFL) dentro de Fútbol).
 */
object SportFilterMatcher {

    const val FILTER_ALL = ""
    const val FILTER_FUTBOL = "FUTBOL"
    const val FILTER_TENIS_PADEL = "TENIS_PADEL"
    const val FILTER_MOTOR = "MOTOR"
    const val FILTER_BASKET = "BASKET"
    const val FILTER_OTROS = "OTROS"

    const val FILTER_TV_TDT = "247_TDT"
    const val FILTER_TV_DEPORTES = "247_DEPORTES"
    const val FILTER_TV_ARENAVISION = "247_ARENAVISION"

    data class SportMenuOption(val label: String, val filterKey: String)

    val MENU_SPORTS: List<SportMenuOption> = listOf(
        SportMenuOption("Todos (Sin filtro)", FILTER_ALL),
        SportMenuOption("Fútbol", FILTER_FUTBOL),
        SportMenuOption("Fútbol Americano / NFL", "FUTBOL AMERICANO"),
        SportMenuOption("Baloncesto", "BALONCESTO"),
        SportMenuOption("Tenis", "TENIS"),
        SportMenuOption("Pádel", "PADEL"),
        SportMenuOption("Motor (F1 / MotoGP)", FILTER_MOTOR),
        SportMenuOption("Ciclismo", "CICLISMO"),
        SportMenuOption("Balonmano", "BALONMANO"),
        SportMenuOption("Boxeo / MMA", "BOXEO"),
        SportMenuOption("Rugby", "RUGBY"),
        SportMenuOption("Béisbol", "BEISBOL"),
        SportMenuOption("Golf", "GOLF"),
        SportMenuOption("Hockey", "HOCKEY"),
        SportMenuOption("Snooker / Billar", "SNOOKER"),
        SportMenuOption("Voleibol", "VOLEIBOL")
    )

    fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase(Locale.ROOT)
            .trim()
    }

    private fun isAmericanFootball(normalizedSport: String): Boolean {
        return normalizedSport.contains("FUTBOL AMERICANO") ||
            normalizedSport.contains("AMERICAN FOOTBALL") ||
            normalizedSport.contains("NFL")
    }

    private fun isSoccer(normalizedSport: String): Boolean {
        if (isAmericanFootball(normalizedSport)) return false
        return normalizedSport.contains("FUTBOL") ||
            normalizedSport.contains("SOCCER") ||
            normalizedSport.contains("FOOTBALL") ||
            normalizedSport.contains("FUTSAL")
    }

    private fun isTennis(normalizedSport: String): Boolean {
        return normalizedSport.contains("TENIS") ||
            normalizedSport.contains("TENNIS") ||
            normalizedSport.contains("ATP") ||
            normalizedSport.contains("WTA")
    }

    private fun isPadel(normalizedSport: String): Boolean {
        return normalizedSport.contains("PADEL")
    }

    private fun isMotor(normalizedSport: String): Boolean {
        return normalizedSport.contains("MOTOR") ||
            normalizedSport.contains("F1") ||
            normalizedSport.contains("FORMULA") ||
            normalizedSport.contains("MOTO") ||
            normalizedSport.contains("MOTOGP") ||
            normalizedSport.contains("SUPERBIKE")
    }

    private fun isBasket(normalizedSport: String): Boolean {
        return normalizedSport.contains("BALONCESTO") ||
            normalizedSport.contains("BASKET") ||
            normalizedSport.contains("NBA") ||
            normalizedSport.contains("EUROLEAGUE")
    }

    fun matchesFilter(item: EventItem, filterKey: String): Boolean {
        val key = normalize(filterKey)
        if (key.isEmpty() || key == "ALL") return true

        val sportNorm = normalize(item.sport)
        val titleNorm = normalize(item.title)

        return when (key) {
            FILTER_TV_TDT -> isTdtChannel(item, titleNorm)
            FILTER_TV_ARENAVISION -> titleNorm.contains("ARENAVISION")
            FILTER_TV_DEPORTES -> !titleNorm.contains("ARENAVISION") && !isTdtChannel(item, titleNorm)
            FILTER_FUTBOL -> isSoccer(sportNorm)
            "FUTBOL AMERICANO" -> isAmericanFootball(sportNorm)
            FILTER_TENIS_PADEL -> isTennis(sportNorm) || isPadel(sportNorm)
            "TENIS" -> isTennis(sportNorm)
            "PADEL" -> isPadel(sportNorm)
            FILTER_MOTOR -> isMotor(sportNorm)
            FILTER_BASKET, "BALONCESTO" -> isBasket(sportNorm)
            "BOXEO" -> sportNorm.contains("BOX") || sportNorm.contains("MMA") || sportNorm.contains("UFC")
            "SNOOKER" -> sportNorm.contains("SNOOKER") || sportNorm.contains("BILLAR")
            FILTER_OTROS -> {
                !(isSoccer(sportNorm) || isTennis(sportNorm) || isPadel(sportNorm) || isMotor(sportNorm) || isBasket(sportNorm))
            }
            else -> sportNorm.contains(key)
        }
    }

    private fun isTdtChannel(item: EventItem, titleNorm: String): Boolean {
        if (item.channels.any { it.type == StreamType.DIRECT }) return true
        return titleNorm.contains("[TDT") ||
            titleNorm.contains("TELEDEPORTE") ||
            titleNorm == "TDP" ||
            titleNorm.contains("ESPORT3") ||
            titleNorm.contains("REAL MADRID TV") ||
            titleNorm.contains("BETIS TV") ||
            titleNorm.contains("SEVILLA FC TV")
    }

    fun iconResForSport(sport: String): Int {
        val s = normalize(sport)
        return when {
            isAmericanFootball(s) -> R.mipmap.futbol_americano
            isSoccer(s) -> R.mipmap.futbol
            isBasket(s) -> R.mipmap.baloncesto
            isTennis(s) || isPadel(s) -> R.mipmap.tenis
            s.contains("F1") || s.contains("FORMULA") -> R.mipmap.f1
            s.contains("MOTO") || s.contains("SUPERBIKE") || s.contains("MOTOR") -> R.mipmap.motos
            s.contains("CICLISMO") || s.contains("CYCLING") -> R.mipmap.ciclismo
            s.contains("BALONMANO") || s.contains("HANDBALL") -> R.mipmap.balonmano
            s.contains("BEISBOL") || s.contains("BASEBALL") || s.contains("MLB") -> R.mipmap.beisbol
            s.contains("GOLF") -> R.mipmap.golf
            s.contains("HOCKEY") || s.contains("NHL") -> R.mipmap.hockey
            s.contains("BOX") || s.contains("MMA") || s.contains("UFC") -> R.mipmap.mma
            s.contains("RUGBY") -> R.mipmap.rudby
            s.contains("SNOOKER") || s.contains("BILLAR") -> R.mipmap.snooker
            s.contains("VOLEIBOL") || s.contains("VOLLEY") -> R.mipmap.voleibol
            s.contains("BADMINTON") -> R.mipmap.badminton
            s.contains("DIRECTO 24/7") -> R.mipmap.evento
            else -> R.mipmap.general
        }
    }
}
