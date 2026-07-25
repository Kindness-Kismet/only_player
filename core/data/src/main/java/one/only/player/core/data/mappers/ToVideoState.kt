package one.only.player.core.data.mappers

import one.only.player.core.data.models.VideoState
import one.only.player.core.database.converter.UriListConverter
import one.only.player.core.database.entities.MediumStateEntity

fun MediumStateEntity.toVideoState(): VideoState = VideoState(
    path = uriString,
    position = playbackPosition.takeIf { it != 0L },
    audioTrackIndex = audioTrackIndex,
    subtitleTrackIndex = subtitleTrackIndex,
    playbackSpeed = playbackSpeed,
    externalSubs = UriListConverter.fromStringToList(externalSubs),
    videoScale = videoScale,
    subtitleDelayMilliseconds = subtitleDelayMilliseconds,
    subtitleSpeed = subtitleSpeed,
    decoderPriority = decoderPriority?.takeIf { it.isNotBlank() },
    contentScale = contentScale?.takeIf { it.isNotBlank() },
)
