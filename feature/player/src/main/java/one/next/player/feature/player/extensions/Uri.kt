package one.next.player.feature.player.extensions

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
import one.next.player.core.common.Logger
import one.next.player.core.common.extensions.convertToUTF8
import one.next.player.core.common.extensions.getFilenameFromUri
import one.next.player.core.common.extensions.subtitleCacheDir

fun Uri.getSubtitleMime(displayName: String? = null): String {
    val name = displayName ?: path ?: ""
    return when {
        name.endsWith(".ssa", ignoreCase = true) ||
            name.endsWith(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
        name.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
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
): MediaItem.SubtitleConfiguration {
    val charset = if (subtitleEncoding.isNotEmpty() && Charset.isSupported(subtitleEncoding)) {
        Charset.forName(subtitleEncoding)
    } else {
        null
    }
    val label = getFilenameFromUri(uri)
    val mimeType = uri.getSubtitleMime(displayName = label)
    val utf8ConvertedUri = convertToUTF8(uri = uri, charset = charset)
    val subtitleUri = normalizeAssStyleFonts(uri = utf8ConvertedUri, label = label, mimeType = mimeType)
    Logger.debug(
        "SubtitleConfig",
        "uri=$uri, convertedUri=$utf8ConvertedUri, subtitleUri=$subtitleUri, mime=$mimeType, label=$label",
    )
    return MediaItem.SubtitleConfiguration.Builder(subtitleUri).apply {
        setId(uri.toString())
        setMimeType(mimeType)
        setLabel(label)
        if (isSelected) setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
    }.build()
}

private suspend fun Context.normalizeAssStyleFonts(
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
            val normalizedText = sourceText.normalizeAssStyleFonts()
            if (normalizedText == sourceText) return@withContext uri

            val file = File(subtitleCacheDir, normalizedAssStyleFileName(label = label, uri = uri))
            file.writeText(normalizedText, StandardCharsets.UTF_8)
            Uri.fromFile(file)
        } catch (exception: Exception) {
            Logger.error(SUBTITLE_CONFIG_TAG, "Failed to normalize ASS subtitle fonts", exception)
            uri
        }
    }
}

private fun String.normalizeAssStyleFonts(): String {
    val lineSeparator = detectLineSeparator()
    var isInStyleSection = false
    var fontNameIndex: Int? = null
    var hasChanges = false
    val normalizedLines = lineSequence().map { line ->
        val trimmedLine = line.trim()
        if (trimmedLine.startsWith("[") && trimmedLine.endsWith("]")) {
            isInStyleSection = trimmedLine.lowercase(Locale.US) in ASS_STYLE_SECTION_HEADERS
            fontNameIndex = null
            return@map line
        }
        if (!isInStyleSection) return@map line
        if (trimmedLine.startsWith("Format:", ignoreCase = true)) {
            fontNameIndex = trimmedLine
                .substringAfter(':')
                .split(',')
                .indexOfFirst { field -> field.trim().equals("Fontname", ignoreCase = true) }
                .takeIf { index -> index >= 0 }
            return@map line
        }

        val styleFontIndex = fontNameIndex ?: return@map line
        if (!trimmedLine.startsWith("Style:", ignoreCase = true)) return@map line

        val normalizedLine = line.normalizeAssStyleFont(fontNameIndex = styleFontIndex)
        if (normalizedLine != line) hasChanges = true
        normalizedLine
    }.toList()

    if (!hasChanges) return this

    val normalizedText = normalizedLines.joinToString(separator = lineSeparator)
    return if (endsWith("\n") || endsWith("\r")) normalizedText + lineSeparator else normalizedText
}

private fun String.normalizeAssStyleFont(fontNameIndex: Int): String {
    val headerEndIndex = indexOf(':')
    if (headerEndIndex < 0) return this

    val prefix = substring(0, headerEndIndex + 1)
    val fields = substring(headerEndIndex + 1).split(',').toMutableList()
    if (fontNameIndex !in fields.indices) return this

    val fontField = fields[fontNameIndex]
    val fontName = fontField.trim().trim('"')
    if (fontName.lowercase(Locale.US) !in ASS_WINDOWS_FONT_NAMES) return this

    val leadingWhitespace = fontField.takeWhile(Char::isWhitespace)
    val trailingWhitespace = fontField.takeLastWhile(Char::isWhitespace)
    fields[fontNameIndex] = leadingWhitespace + ASS_ANDROID_FALLBACK_FONT + trailingWhitespace
    return prefix + fields.joinToString(",")
}

private fun String.detectLineSeparator(): String = when {
    contains("\r\n") -> "\r\n"
    contains("\r") -> "\r"
    else -> "\n"
}

private fun normalizedAssStyleFileName(label: String, uri: Uri): String {
    val baseName = label.ifBlank { "subtitle" }.substringBeforeLast('.', missingDelimiterValue = label.ifBlank { "subtitle" })
    val extension = label.substringAfterLast('.', missingDelimiterValue = "ass")
    val cacheKey = uri.toString().hashCode().toUInt().toString(radix = 16)
    return "$baseName-$cacheKey.ass-style.$extension"
}

fun Bundle.getParcelableUriArray(key: String): ArrayList<Uri>? = BundleCompat.getParcelableArrayList(this, key, Uri::class.java)

private const val SUBTITLE_CONFIG_TAG = "SubtitleConfig"
private const val ASS_ANDROID_FALLBACK_FONT = "Roboto"

private val NETWORK_URI_SCHEMES = setOf("http", "https", "ftp")

private val ASS_STYLE_SECTION_HEADERS = setOf(
    "[v4 styles]",
    "[v4+ styles]",
)

private val ASS_WINDOWS_FONT_NAMES = setOf(
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
