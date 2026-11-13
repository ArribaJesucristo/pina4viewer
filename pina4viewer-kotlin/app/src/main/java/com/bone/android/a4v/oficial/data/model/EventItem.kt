package com.bone.android.a4v.oficial.data.model

import com.bone.android.a4v.oficial.util.SportFilterMatcher

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
    fun getSportIconRes(): Int = SportFilterMatcher.iconResForSport(sport)

    fun getChannelsFormatted(): String {
        if (channels.isEmpty()) return ""
        return channels.joinToString("/") { it.name }
    }
}
