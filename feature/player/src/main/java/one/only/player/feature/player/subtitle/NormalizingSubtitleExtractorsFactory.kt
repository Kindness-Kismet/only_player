package one.only.player.feature.player.subtitle

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingExtractor
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.SubtitleParser
import java.io.ByteArrayOutputStream
import java.io.EOFException
import one.only.player.feature.player.engine.media3.SeekMapInjectingExtractor
import one.only.player.feature.player.extensions.normalizeAssDialogueFields
import one.only.player.feature.player.extensions.normalizeAssSubtitleText

@UnstableApi
internal class NormalizingSubtitleExtractorsFactory(
    private val delegate: ExtractorsFactory,
    private val seekMap: SeekMap? = null,
) : ExtractorsFactory by delegate {

    override fun setSubtitleParserFactory(factory: SubtitleParser.Factory) = apply {
        delegate.setSubtitleParserFactory(factory)
    }

    override fun createExtractors(): Array<Extractor> = delegate.createExtractors()
        .map(::wrapExtractor)
        .toTypedArray()

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
    ): Array<Extractor> = delegate.createExtractors(uri, responseHeaders)
        .map(::wrapExtractor)
        .toTypedArray()

    private fun wrapExtractor(extractor: Extractor): Extractor {
        val isMatroska = extractor.underlyingImplementation is MatroskaExtractor
        val compatibleExtractor = if (isMatroska) MatroskaCompatibilityExtractor(extractor) else extractor
        val seekableExtractor = if (seekMap != null && isMatroska) {
            SeekMapInjectingExtractor(compatibleExtractor, seekMap)
        } else {
            compatibleExtractor
        }
        return NormalizingSubtitleExtractor(seekableExtractor)
    }
}

@UnstableApi
internal class NormalizingSubtitleExtractor(
    delegate: Extractor,
) : ForwardingExtractor(delegate) {

    private val subtitleOutputs = mutableMapOf<Int, NormalizingAssTrackOutput>()

    override fun init(output: ExtractorOutput) {
        subtitleOutputs.clear()
        super.init(object : ExtractorOutput by output {
            override fun track(
                id: Int,
                type: Int,
            ): TrackOutput {
                val trackOutput = output.track(id, type)
                if (type != C.TRACK_TYPE_TEXT) return trackOutput
                return subtitleOutputs.getOrPut(id) { NormalizingAssTrackOutput(trackOutput) }
            }
        })
    }

    override fun seek(
        position: Long,
        timeUs: Long,
    ) {
        subtitleOutputs.values.forEach(NormalizingAssTrackOutput::resetPendingData)
        super.seek(position, timeUs)
    }

    override fun release() {
        subtitleOutputs.clear()
        super.release()
    }
}

@UnstableApi
private class NormalizingAssTrackOutput(
    private val delegate: TrackOutput,
) : TrackOutput by delegate {

    private var shouldNormalizeAss = false
    private val pendingData = ByteArrayOutputStream()

    override fun format(format: Format) {
        val canNormalizeAss = format.sampleMimeType == MimeTypes.TEXT_SSA && format.drmInitData == null
        if (!canNormalizeAss) stopNormalizingSamples()
        shouldNormalizeAss = canNormalizeAss
        delegate.format(if (canNormalizeAss) format.normalizeAssHeader() else format)
    }

    override fun sampleData(
        input: DataReader,
        length: Int,
        allowEndOfInput: Boolean,
    ): Int = sampleData(input, length, allowEndOfInput, TrackOutput.SAMPLE_DATA_PART_MAIN)

    override fun sampleData(
        input: DataReader,
        length: Int,
        allowEndOfInput: Boolean,
        sampleDataPart: Int,
    ): Int {
        if (sampleDataPart != TrackOutput.SAMPLE_DATA_PART_MAIN) stopNormalizingSamples()
        if (!shouldNormalizeAss) return delegate.sampleData(input, length, allowEndOfInput, sampleDataPart)

        val bytes = ByteArray(minOf(length, INPUT_BUFFER_SIZE))
        val count = input.read(bytes, 0, bytes.size)
        if (count == C.RESULT_END_OF_INPUT) {
            if (!allowEndOfInput) throw EOFException()
            return count
        }
        pendingData.write(bytes, 0, count)
        return count
    }

    override fun sampleData(
        data: ParsableByteArray,
        length: Int,
    ) = sampleData(data, length, TrackOutput.SAMPLE_DATA_PART_MAIN)

    override fun sampleData(
        data: ParsableByteArray,
        length: Int,
        sampleDataPart: Int,
    ) {
        if (sampleDataPart != TrackOutput.SAMPLE_DATA_PART_MAIN) stopNormalizingSamples()
        if (!shouldNormalizeAss) {
            delegate.sampleData(data, length, sampleDataPart)
            return
        }

        pendingData.write(data.data, data.position, length)
        data.skipBytes(length)
    }

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?,
    ) {
        if (!shouldNormalizeAss) {
            delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData)
            return
        }

        val bytes = pendingData.toByteArray()
        val sampleEnd = bytes.size - offset
        val sample = bytes.copyOfRange(sampleEnd - size, sampleEnd)
        val normalized = if (cryptoData == null) sample.normalizeAssSample() else sample
        delegate.sampleData(ParsableByteArray(normalized), normalized.size)
        delegate.sampleMetadata(timeUs, flags, normalized.size, 0, cryptoData)
        pendingData.reset()
        // offset 指定的尾部数据属于后续样本，保留到对应的 metadata。
        pendingData.write(bytes, sampleEnd, offset)
    }

    fun resetPendingData() = pendingData.reset()

    private fun stopNormalizingSamples() {
        if (pendingData.size() > 0) {
            val bytes = pendingData.toByteArray()
            delegate.sampleData(ParsableByteArray(bytes), bytes.size)
            pendingData.reset()
        }
        shouldNormalizeAss = false
    }

    private fun Format.normalizeAssHeader(): Format {
        var hasChanges = false
        val normalizedData = initializationData.map { bytes ->
            val source = bytes.decodeToString()
            val normalized = source.normalizeAssSubtitleText()
            if (normalized == source) {
                bytes
            } else {
                hasChanges = true
                normalized.encodeToByteArray()
            }
        }
        if (!hasChanges) return this
        return buildUpon().setInitializationData(normalizedData).build()
    }

    private fun ByteArray.normalizeAssSample(): ByteArray {
        val source = decodeToString()
        if (!source.startsWith("Dialogue: ")) return this
        val firstComma = source.indexOf(',')
        if (firstComma < 0) return this
        val secondComma = source.indexOf(',', firstComma + 1)
        if (secondComma < 0) return this

        // Nextlib 需要原始的两个时间字段，只规范化 Matroska ASS 载荷的文本。
        val fields = source.substring(secondComma + 1)
        val normalizedFields = fields.normalizeAssDialogueFields(ASS_MATROSKA_TEXT_INDEX)
        if (normalizedFields == fields) return this
        return (source.substring(0, secondComma + 1) + normalizedFields).encodeToByteArray()
    }

    companion object {
        private const val INPUT_BUFFER_SIZE = 8192
        private const val ASS_MATROSKA_TEXT_INDEX = 8
    }
}
