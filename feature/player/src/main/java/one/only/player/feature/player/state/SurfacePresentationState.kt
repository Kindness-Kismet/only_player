package one.only.player.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import one.only.player.feature.player.extensions.hasRenderedFirstFrame

/**
 * 本地 surface 展示状态：替代 Media3 [androidx.media3.ui.compose.state.rememberPresentationState]。
 *
 * MediaController 的 timeline 是 RemotableTimeline，不支持 getIndexOfPeriod；
 * Media3 PresentationState 在 tracks 清空时会用 lastPeriodUid 反查 period 索引，直接崩溃。
 * 这里只订阅 videoSize / firstFrame / media 切换，不做 period uid 查找。
 *
 * 注意：replaceMediaItem（盖章 contentScale / firstFrame 元数据）也会发
 * EVENT_MEDIA_ITEM_TRANSITION。同一 mediaId 的元数据替换绝不能盖住 surface，
 * 否则记住缩放/首帧标记会整段黑屏直到 seek（logs12）。
 */
@UnstableApi
@Composable
fun rememberSurfacePresentationState(player: Player): SurfacePresentationState {
    val state = remember { SurfacePresentationState() }
    LaunchedEffect(player) { state.observe(player) }
    return state
}

@Stable
class SurfacePresentationState internal constructor() {
    var videoSizeDp: Size? by mutableStateOf(null)
        private set

    /** true 时盖住 surface（真正切条 / 尚未出首帧），避免旧画面残影 */
    var coverSurface: Boolean by mutableStateOf(true)
        private set

    private var lastMediaId: String? = null

    @UnstableApi
    suspend fun observe(player: Player) {
        applyVideoSize(player.videoSize)
        lastMediaId = player.currentMediaItem?.mediaId
        coverSurface = !player.mediaMetadata.hasRenderedFirstFrame

        player.listen { events ->
            if (events.contains(Player.EVENT_VIDEO_SIZE_CHANGED)) {
                applyVideoSize(player.videoSize)
            }
            if (events.contains(Player.EVENT_RENDERED_FIRST_FRAME)) {
                coverSurface = false
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                val mediaId = player.currentMediaItem?.mediaId
                val isSameItem = mediaId != null && mediaId == lastMediaId
                lastMediaId = mediaId
                applyVideoSize(player.videoSize)
                if (isSameItem) {
                    // 同条元数据 replace（stamp / firstFrame / zoom）：绝不能盖住
                    if (player.mediaMetadata.hasRenderedFirstFrame) {
                        coverSurface = false
                    }
                } else {
                    // 真正切条：先挡旧帧，等首帧再露
                    coverSurface = true
                    if (player.mediaMetadata.hasRenderedFirstFrame) {
                        coverSurface = false
                    }
                }
            }
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                if (player.mediaMetadata.hasRenderedFirstFrame) {
                    coverSurface = false
                }
            }
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    // 停播时保留最后一帧尺寸，不强制盖住
                }
            }
        }
    }

    private fun applyVideoSize(videoSize: VideoSize) {
        val width = videoSize.width
        val height = videoSize.height
        if (width <= 0 || height <= 0) {
            // 未知尺寸时保留上一值，避免 surface 缩成 0
            return
        }
        val rotated = videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270
        videoSizeDp = if (rotated) {
            Size(height.toFloat(), width.toFloat())
        } else {
            Size(width.toFloat(), height.toFloat())
        }
    }
}
