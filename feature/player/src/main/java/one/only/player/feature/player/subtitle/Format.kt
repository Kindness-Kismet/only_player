package one.only.player.feature.player.subtitle

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

internal fun Format.isNativeSubtitle(): Boolean = sampleMimeType in NATIVE_SUBTITLE_MIME_TYPES || codecs in NATIVE_SUBTITLE_MIME_TYPES

private val NATIVE_SUBTITLE_MIME_TYPES = setOf(
    MimeTypes.TEXT_SSA,
    MimeTypes.APPLICATION_PGS,
    MimeTypes.APPLICATION_VOBSUB,
    MimeTypes.APPLICATION_DVBSUBS,
)
