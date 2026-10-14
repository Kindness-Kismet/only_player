package one.only.player.feature.player.service

import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.trackselection.ExoTrackSelection

@UnstableApi
class PlaybackLoadControl(
    private val maxHeapBytes: Long = Runtime.getRuntime().maxMemory(),
) : DefaultLoadControl() {
    private val period = Timeline.Period()
    private val window = Timeline.Window()

    override fun calculateTargetBufferBytes(
        parameters: LoadControl.Parameters,
        trackSelectionArray: Array<out ExoTrackSelection?>,
    ): Int {
        val windowIndex = parameters.timeline.getPeriodByUid(parameters.mediaPeriodId.periodUid, period).windowIndex
        val uri = parameters.timeline.getWindow(windowIndex, window).mediaItem.localConfiguration?.uri
        if (uri?.scheme != "smb") {
            return (maxHeapBytes / 6).coerceIn(MIN_BUFFER_BYTES, LOCAL_MAX_BUFFER_BYTES).toInt()
        }

        // 与上游一致，SMB 按选中轨道计算缓冲目标，不提前触及本地播放的字节限制。
        return super.calculateTargetBufferBytes(parameters, trackSelectionArray)
    }

    companion object {
        private const val MIN_BUFFER_BYTES = 16L * 1024 * 1024
        private const val LOCAL_MAX_BUFFER_BYTES = 64L * 1024 * 1024
    }
}
