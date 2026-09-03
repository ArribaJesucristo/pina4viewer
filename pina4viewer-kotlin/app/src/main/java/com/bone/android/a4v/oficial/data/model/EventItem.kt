package com.bone.android.a4v.oficial.data.model

import com.bone.android.a4v.oficial.R

data class EventItem(
    val id: String,
    val title: String,
    val sport: String = "",
    val competition: String = "",
    val time: String = "",
    val date: String = "",
    val channels: List<ChannelItem> = emptyList(),
    val directStreamId: String = ""
) {
    fun getSportIconRes(): Int {
        val s = sport.uppercase().trim()
        return when {
            s.contains("FUTBOL") || s.contains("SOCCER") || s.contains("FOOTBALL") -> R.mipmap.futbol
            s.contains("BALONCESTO") || s.contains("BASKET") || s.contains("NBA") -> R.mipmap.baloncesto
            s.contains("TENIS") || s.contains("TENNIS") || s.contains("ATP") || s.contains("WTA") -> R.mipmap.tenis
            s.contains("F1") || s.contains("FORMULA") -> R.mipmap.f1
            s.contains("MOTO") || s.contains("MOTOCYCLING") || s.contains("MOTOGP") -> R.mipmap.motos
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
            else -> R.mipmap.general
        }
    }

    fun getChannelsFormatted(): String {
        if (channels.isEmpty()) return ""
        return channels.joinToString("/") { it.name }
    }
}
