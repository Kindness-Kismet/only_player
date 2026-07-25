package one.only.player.core.data.models

import android.net.Uri

data class VideoState(
    val path: String,
    val position: Long?,
    val audioTrackIndex: Int?,
    val subtitleTrackIndex: Int?,
    val playbackSpeed: Float?,
    val externalSubs: List<Uri>,
    val videoScale: Float,
    val subtitleDelayMilliseconds: Long,
    val subtitleSpeed: Float,
    // 与 position 一样按媒体 URI 持久化的解码配置（DecoderPriority.name）
    val decoderPriority: String? = null,
    val contentScale: String? = null,
)
