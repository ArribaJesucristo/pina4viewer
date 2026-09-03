package com.bone.android.a4v.oficial.data.model

enum class StreamType {
    ACESTREAM,
    SOPCAST,
    WEB,
    DIRECT
}

data class ChannelItem(
    val name: String,
    val streamId: String,
    val type: StreamType = StreamType.ACESTREAM,
    val rawUrl: String? = null
)

