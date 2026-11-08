package one.only.player.feature.player.subtitle

import android.util.SparseArray
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ForwardingExtractor
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.EbmlProcessor
import androidx.media3.extractor.mkv.MatroskaExtractor

@UnstableApi
internal class MatroskaCompatibilityExtractor(
    delegate: Extractor,
) : ForwardingExtractor(delegate) {

    private val matroska = delegate.underlyingImplementation as MatroskaExtractor

    init {
        val reader = readerField.get(matroska)
        val processor = processorField.get(reader) as EbmlProcessor
        processorField.set(
            reader,
            object : EbmlProcessor by processor {
                override fun stringElement(id: Int, value: String) {
                    val codecId = if (id == ID_CODEC_ID && value == WEBM_DESCRIPTIONS_CODEC_ID) {
                        WEBM_CAPTIONS_CODEC_ID
                    } else {
                        value
                    }
                    processor.stringElement(id, codecId)
                }

                override fun endMasterElement(id: Int) {
                    processor.endMasterElement(id)
                    if (id == ID_EDITION_ENTRY) publishChapterFormats()
                }
            },
        )
    }

    private fun publishChapterFormats() {
        val tracks = tracksField.get(matroska) as SparseArray<*>
        for (index in 0 until tracks.size()) {
            val track = tracks.valueAt(index)
            if (waitingForDtsAnalysisField.getBoolean(track)) continue

            val format = formatField.get(track) as Format
            val output = outputField.get(track) as TrackOutput
            output.format(format)
        }
    }

    companion object {
        private const val ID_CODEC_ID = 0x86
        private const val ID_EDITION_ENTRY = 0x45B9
        private const val WEBM_DESCRIPTIONS_CODEC_ID = "D_WEBVTT/DESCRIPTIONS"
        private const val WEBM_CAPTIONS_CODEC_ID = "D_WEBVTT/CAPTIONS"

        // Media3 1.11.1 没有公开 EBML 拦截入口，反射字段与对应保留规则须同步维护。
        private val readerField = MatroskaExtractor::class.java.getDeclaredField("reader").apply {
            isAccessible = true
        }
        private val processorField = Class.forName("androidx.media3.extractor.mkv.DefaultEbmlReader")
            .getDeclaredField("processor")
            .apply { isAccessible = true }
        private val tracksField = MatroskaExtractor::class.java.getDeclaredField("tracks").apply {
            isAccessible = true
        }
        private val trackClass = Class.forName("androidx.media3.extractor.mkv.MatroskaExtractor\$Track")
        private val formatField = trackClass.getDeclaredField("format").apply { isAccessible = true }
        private val outputField = trackClass.getDeclaredField("output").apply { isAccessible = true }
        private val waitingForDtsAnalysisField = trackClass.getDeclaredField("waitingForDtsAnalysis").apply {
            isAccessible = true
        }
    }
}
