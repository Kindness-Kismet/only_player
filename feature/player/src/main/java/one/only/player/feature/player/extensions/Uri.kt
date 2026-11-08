package one.only.player.feature.player.extensions

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.only.player.core.common.Logger
import one.only.player.core.common.extensions.convertToUTF8
import one.only.player.core.common.extensions.getFilenameFromUri
import one.only.player.core.common.extensions.subtitleCacheDir

fun Uri.getSubtitleMime(displayName: String? = null): String {
    val name = displayName ?: path ?: ""
    return when {
        name.endsWith(".ssa", ignoreCase = true) ||
            name.endsWith(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
        name.endsWith(".vtt", ignoreCase = true) ||
            name.endsWith(".webvtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
        name.endsWith(".ttml", ignoreCase = true) ||
            name.endsWith(".xml", ignoreCase = true) ||
            name.endsWith(".dfxp", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}

val Uri.isSchemaContent: Boolean
    get() = ContentResolver.SCHEME_CONTENT.equals(scheme, ignoreCase = true)

suspend fun Context.uriToSubtitleConfiguration(
    uri: Uri,
    subtitleEncoding: String = "",
    isSelected: Boolean = false,
    displayName: String? = null,
    language: String? = null,
): MediaItem.SubtitleConfiguration {
    val charset = if (subtitleEncoding.isNotEmpty() && Charset.isSupported(subtitleEncoding)) {
        Charset.forName(subtitleEncoding)
    } else {
        null
    }
    val fileName = getFilenameFromUri(uri)
    val mimeType = uri.getSubtitleMime(displayName = fileName)
    val utf8ConvertedUri = convertToUTF8(uri = uri, charset = charset)
    val samiConvertedUri = convertSamiToSubrip(uri = utf8ConvertedUri, label = fileName)
    val subtitleUri = normalizeAssFonts(uri = samiConvertedUri, label = fileName, mimeType = mimeType)
    Logger.debug(
        "SubtitleConfig",
        "source=${uri.toSubtitleLogSummary()}, converted=${utf8ConvertedUri.toSubtitleLogSummary()}, subtitle=${subtitleUri.toSubtitleLogSummary()}, mime=$mimeType",
    )
    return MediaItem.SubtitleConfiguration.Builder(subtitleUri).apply {
        setId(uri.toString())
        setMimeType(mimeType)
        setLabel(displayName ?: fileName)
        setLanguage(language)
        if (isSelected) setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
    }.build()
}

private suspend fun Context.convertSamiToSubrip(
    uri: Uri,
    label: String,
): Uri {
    if (!label.isSamiSubtitle()) return uri

    return withContext(Dispatchers.IO) {
        try {
            if (uri.scheme?.let { it in NETWORK_URI_SCHEMES } == true) return@withContext uri
            val sourceText = contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.reader(StandardCharsets.UTF_8).readText()
            } ?: return@withContext uri
            val convertedText = sourceText.convertSamiToSubrip()
            if (convertedText == sourceText) return@withContext uri

            val file = File(subtitleCacheDir, convertedSamiFileName(label = label, uri = uri))
            file.writeText(convertedText, StandardCharsets.UTF_8)
            Uri.fromFile(file)
        } catch (exception: Exception) {
            Logger.error(SUBTITLE_CONFIG_TAG, "Failed to convert SAMI subtitle", exception)
            uri
        }
    }
}

internal fun String.convertSamiToSubrip(): String {
    val matches = SAMI_SYNC_REGEX.findAll(this).toList()
    if (matches.isEmpty()) return this

    val cues = ArrayList<SamiCue>(matches.size)
    for (index in matches.indices) {
        val startMs = matches[index].groupValues[1].toLong()
        val contentStart = matches[index].range.last + 1
        val contentEnd = matches.getOrNull(index + 1)?.range?.first ?: length
        cues += SamiCue(
            startMs = startMs,
            text = substring(contentStart, contentEnd).samiCueText(),
        )
    }
    if (cues.none { cue -> cue.text.isNotBlank() }) return this

    return buildString {
        var cueNumber = 1
        cues.forEachIndexed { index, cue ->
            if (cue.text.isBlank()) return@forEachIndexed
            val endMs = cues.getOrNull(index + 1)?.startMs ?: (cue.startMs + DEFAULT_SAMI_CUE_DURATION_MS)
            if (cueNumber > 1) appendLine()
            appendLine(cueNumber)
            append(cue.startMs.toSubripTimestamp())
            append(" --> ")
            appendLine(endMs.toSubripTimestamp())
            appendLine(cue.text)
            cueNumber++
        }
    }
}

private fun String.samiCueText(): String {
    val withBreaks = SAMI_BREAK_REGEX.replace(this, "\n")
    val withoutTags = SAMI_TAG_REGEX.replace(withBreaks, "")
    return withoutTags
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.isNotEmpty() }
        .joinToString(separator = "\n")
}

private fun Long.toSubripTimestamp(): String {
    val totalMs = coerceAtLeast(0L)
    val hours = totalMs / 3_600_000L
    val minutes = (totalMs % 3_600_000L) / 60_000L
    val seconds = (totalMs % 60_000L) / 1_000L
    val millis = totalMs % 1_000L
    return "%02d:%02d:%02d,%03d".format(Locale.US, hours, minutes, seconds, millis)
}

private fun String.isSamiSubtitle(): Boolean = endsWith(".smi", ignoreCase = true) ||
    endsWith(".sami", ignoreCase = true)

private fun convertedSamiFileName(label: String, uri: Uri): String {
    val baseName = label.ifBlank { "subtitle" }.substringBeforeLast('.', missingDelimiterValue = label.ifBlank { "subtitle" })
    val cacheKey = uri.toString().hashCode().toUInt().toString(radix = 16)
    return "$baseName-$cacheKey.sami.srt"
}

private suspend fun Context.normalizeAssFonts(
    uri: Uri,
    label: String,
    mimeType: String,
): Uri {
    if (mimeType != MimeTypes.TEXT_SSA) return uri

    return withContext(Dispatchers.IO) {
        try {
            if (uri.scheme?.let { it in NETWORK_URI_SCHEMES } == true) return@withContext uri
            val sourceText = contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.reader(StandardCharsets.UTF_8).readText()
            } ?: return@withContext uri
            val normalizedText = sourceText.normalizeAssSubtitleText()
            if (normalizedText == sourceText) return@withContext uri

            val file = File(subtitleCacheDir, normalizedAssFontFileName(label = label, uri = uri))
            file.writeText(normalizedText, StandardCharsets.UTF_8)
            Uri.fromFile(file)
        } catch (exception: Exception) {
            Logger.error(SUBTITLE_CONFIG_TAG, "Failed to normalize ASS subtitle fonts", exception)
            uri
        }
    }
}

internal fun String.normalizeAssSubtitleText(): String {
    val lineSeparator = detectLineSeparator()
    var section = AssSection.Other
    var styleFontNameIndex: Int? = null
    var eventTextIndex: Int? = null
    var hasChanges = false
    val normalizedLines = lineSequence().map { line ->
        val trimmedLine = line.trim()
        if (trimmedLine.startsWith("[") && trimmedLine.endsWith("]")) {
            section = trimmedLine.assSection()
            styleFontNameIndex = null
            eventTextIndex = null
            return@map line
        }

        val normalizedLine = when {
            section == AssSection.Style && trimmedLine.startsWith("Format:", ignoreCase = true) -> {
                styleFontNameIndex = trimmedLine.assFormatFieldIndex("Fontname")
                line
            }
            section == AssSection.Style && trimmedLine.startsWith("Style:", ignoreCase = true) -> {
                styleFontNameIndex?.let(line::normalizeAssStyleFont) ?: line
            }
            section == AssSection.Event && trimmedLine.startsWith("Format:", ignoreCase = true) -> {
                eventTextIndex = trimmedLine.assFormatFieldIndex("Text")
                line
            }
            section == AssSection.Event && isAssEventLine(trimmedLine) -> {
                eventTextIndex?.let(line::normalizeAssEventText) ?: line
            }
            else -> line
        }
        if (normalizedLine != line) hasChanges = true
        normalizedLine
    }.toList()

    if (!hasChanges) return this

    val normalizedText = normalizedLines.joinToString(separator = lineSeparator)
    return if (endsWith("\n") || endsWith("\r")) normalizedText + lineSeparator else normalizedText
}

private fun String.assSection(): AssSection = when (lowercase(Locale.US)) {
    in ASS_STYLE_SECTION_HEADERS -> AssSection.Style
    "[events]" -> AssSection.Event
    else -> AssSection.Other
}

private fun String.assFormatFieldIndex(name: String): Int? = substringAfter(':')
    .split(',')
    .indexOfFirst { field -> field.trim().equals(name, ignoreCase = true) }
    .takeIf { index -> index >= 0 }

private fun String.normalizeAssStyleFont(fontNameIndex: Int): String {
    val headerEndIndex = indexOf(':')
    if (headerEndIndex < 0) return this

    val prefix = substring(0, headerEndIndex + 1)
    val fields = substring(headerEndIndex + 1).split(',').toMutableList()
    if (fontNameIndex !in fields.indices) return this

    val fontField = fields[fontNameIndex]
    val fontName = fontField.trim().trim('"')
    if (fontName.lowercase(Locale.US) !in ASS_FONT_NAMES_REQUIRING_FALLBACK) return this

    val leadingWhitespace = fontField.takeWhile(Char::isWhitespace)
    val trailingWhitespace = fontField.takeLastWhile(Char::isWhitespace)
    fields[fontNameIndex] = leadingWhitespace + ASS_ANDROID_FALLBACK_FONT + trailingWhitespace
    return prefix + fields.joinToString(",")
}

private fun String.normalizeAssEventText(textIndex: Int): String {
    val headerEndIndex = indexOf(':')
    if (headerEndIndex < 0) return this

    val prefix = substring(0, headerEndIndex + 1)
    return prefix + substring(headerEndIndex + 1).normalizeAssDialogueFields(textIndex)
}

internal fun String.normalizeAssDialogueFields(textIndex: Int): String {
    val fields = split(',', limit = textIndex + 1).toMutableList()
    if (textIndex !in fields.indices) return this

    fields[textIndex] = fields[textIndex].normalizeAssDialogueText()
    return fields.joinToString(",")
}

internal fun String.normalizeAssDialogueText(): String = normalizeAssInlineFonts()
    .widenLatinSpaces()

private fun String.normalizeAssInlineFonts(): String = ASS_INLINE_FONT_REGEX.replace(this) { match ->
    val fontName = match.groupValues[1].trim()
    if (fontName.lowercase(Locale.US) in ASS_FONT_NAMES_REQUIRING_FALLBACK) {
        "\\fn$ASS_ANDROID_FALLBACK_FONT"
    } else {
        match.value
    }
}

private fun String.widenLatinSpaces(): String = buildString(length) {
    var isInOverrideBlock = false
    var isDrawing = false
    var overrideStart = 0
    for (index in this@widenLatinSpaces.indices) {
        val char = this@widenLatinSpaces[index]
        when (char) {
            '{' -> {
                isInOverrideBlock = true
                overrideStart = index + 1
            }
            '}' -> {
                if (isInOverrideBlock) {
                    ASS_DRAWING_MODE_REGEX.findAll(this@widenLatinSpaces.substring(overrideStart, index))
                        .lastOrNull()
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                        ?.let { mode -> isDrawing = mode > 0 }
                }
                isInOverrideBlock = false
            }
        }
        // 绘图坐标保持原样，已添加的薄空格不重复扩展。
        if (char == ' ' &&
            !isInOverrideBlock &&
            !isDrawing &&
            this@widenLatinSpaces.getOrNull(index + 1) != ASS_LATIN_THIN_SPACE &&
            hasLatinNeighbor(index)
        ) {
            append(' ')
            append(ASS_LATIN_THIN_SPACE)
        } else {
            append(char)
        }
    }
}

private fun String.hasLatinNeighbor(index: Int): Boolean = previousVisibleChar(index)?.isLatinLetterOrDigit() == true &&
    nextVisibleChar(index)?.isLatinLetterOrDigit() == true

private fun String.previousVisibleChar(index: Int): Char? {
    var currentIndex = index - 1
    while (currentIndex >= 0 && this[currentIndex].isWhitespace()) currentIndex--
    return getOrNull(currentIndex)
}

private fun String.nextVisibleChar(index: Int): Char? {
    var currentIndex = index + 1
    while (currentIndex < length && this[currentIndex].isWhitespace()) currentIndex++
    return getOrNull(currentIndex)
}

private fun Char.isLatinLetterOrDigit(): Boolean = this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'

private fun isAssEventLine(line: String): Boolean = line.startsWith("Dialogue:", ignoreCase = true) ||
    line.startsWith("Comment:", ignoreCase = true)

private fun String.detectLineSeparator(): String = when {
    contains("\r\n") -> "\r\n"
    contains("\r") -> "\r"
    else -> "\n"
}

private fun normalizedAssFontFileName(label: String, uri: Uri): String {
    val baseName = label.ifBlank { "subtitle" }.substringBeforeLast('.', missingDelimiterValue = label.ifBlank { "subtitle" })
    val extension = label.substringAfterLast('.', missingDelimiterValue = "ass")
    val cacheKey = uri.toString().hashCode().toUInt().toString(radix = 16)
    return "$baseName-$cacheKey.ass-font.$extension"
}

private fun Uri.toSubtitleLogSummary(): String {
    val extension = lastPathSegment?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase(Locale.US).orEmpty()
    val hash = toString().hashCode().toUInt().toString(radix = 16)
    return "scheme=${scheme.orEmpty()} extension=$extension hash=$hash"
}

fun Bundle.getParcelableUriArray(key: String): ArrayList<Uri>? = BundleCompat.getParcelableArrayList(this, key, Uri::class.java)

private const val SUBTITLE_CONFIG_TAG = "SubtitleConfig"
private const val ASS_ANDROID_FALLBACK_FONT = "Roboto"
private const val ASS_LATIN_THIN_SPACE = '\u2009'
private const val DEFAULT_SAMI_CUE_DURATION_MS = 3_000L
private val SAMI_SYNC_REGEX = Regex(
    """<SYNC\s+Start\s*=\s*["']?(\d+)["']?\s*>""",
    setOf(RegexOption.IGNORE_CASE),
)
private val SAMI_BREAK_REGEX = Regex("""<\s*br\s*/?\s*>""", RegexOption.IGNORE_CASE)
private val SAMI_TAG_REGEX = Regex("""<[^>]+>""")

private data class SamiCue(
    val startMs: Long,
    val text: String,
)

private enum class AssSection {
    Style,
    Event,
    Other,
}

private val ASS_INLINE_FONT_REGEX = Regex("\\\\fn([^\\\\}]+)")
private val ASS_DRAWING_MODE_REGEX = Regex("""\\p\s*([+-]?\d+)""")

private val NETWORK_URI_SCHEMES = setOf("http", "https", "ftp")

private val ASS_STYLE_SECTION_HEADERS = setOf(
    "[v4 styles]",
    "[v4+ styles]",
)

private val ASS_FONT_NAMES_REQUIRING_FALLBACK = setOf(
    "arial",
    "calibri",
    "cambria",
    "candara",
    "comic sans ms",
    "consolas",
    "courier new",
    "georgia",
    "helvetica",
    "microsoft sans serif",
    "ms sans serif",
    "segoe ui",
    "tahoma",
    "times new roman",
    "trebuchet ms",
    "verdana",
)
