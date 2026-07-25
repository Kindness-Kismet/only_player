# -*- coding: utf-8 -*-
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent


def patch_player_service() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
    text = path.read_text(encoding="utf-8")
    if "import androidx.media3.session.SessionCommands" not in text:
        text = text.replace(
            "import androidx.media3.session.SessionCommand\n",
            "import androidx.media3.session.SessionCommand\n"
            "import androidx.media3.session.SessionCommands\n",
        )
    old = """            if (!canControlPlayer) {
                // 外部控制器：允许连上但不给播放控制，系统媒体中心也拿不到可用命令
                return MediaSession.ConnectionResult.accept(
                    SessionCommands.EMPTY,
                    Player.Commands.EMPTY,
                )
            }"""
    new = """            if (!canControlPlayer) {
                // 外部控制器：允许连上但不给播放控制，系统媒体中心也拿不到可用命令
                val emptySessionCommands = SessionCommands.Builder().build()
                val emptyPlayerCommands = Player.Commands.Builder().build()
                return MediaSession.ConnectionResult.accept(
                    emptySessionCommands,
                    emptyPlayerCommands,
                )
            }"""
    if old in text:
        text = text.replace(old, new)
        print("player_service: empty commands")
    else:
        print("player_service: empty commands already patched or missing")
    path.write_text(text, encoding="utf-8")


def write_scale_selector() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/ui/VideoContentScaleSelectorView.kt"
    path.write_text(
        """package one.only.player.feature.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.only.player.core.model.VideoContentScale
import one.only.player.core.ui.R
import one.only.player.feature.player.extensions.nameRes

@Composable
fun BoxScope.VideoContentScaleSelectorView(
    modifier: Modifier = Modifier,
    shouldShow: Boolean,
    videoContentScale: VideoContentScale,
    isCustomZoomActive: Boolean = false,
    onVideoContentScaleChanged: (VideoContentScale) -> Unit,
    isRememberForThisFileEnabled: Boolean = false,
    onRememberForThisFileChanged: ((Boolean) -> Unit)? = null,
    onShowVideoFilters: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        shouldShow = shouldShow,
        title = stringResource(R.string.video_zoom),
    ) {
        VideoContentScaleSelectorContent(
            videoContentScale = videoContentScale,
            isCustomZoomActive = isCustomZoomActive,
            onVideoContentScaleChanged = onVideoContentScaleChanged,
            isRememberForThisFileEnabled = isRememberForThisFileEnabled,
            onRememberForThisFileChanged = onRememberForThisFileChanged,
            onShowVideoFilters = onShowVideoFilters,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun VideoContentScaleSelectorContent(
    videoContentScale: VideoContentScale,
    isCustomZoomActive: Boolean = false,
    onVideoContentScaleChanged: (VideoContentScale) -> Unit,
    isRememberForThisFileEnabled: Boolean = false,
    onRememberForThisFileChanged: ((Boolean) -> Unit)? = null,
    onShowVideoFilters: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val isRememberCallbackProvided = onRememberForThisFileChanged != null
    var localRememberEnabled by remember(isRememberForThisFileEnabled) {
        mutableStateOf(isRememberForThisFileEnabled)
    }
    val rememberChecked = if (isRememberCallbackProvided) {
        isRememberForThisFileEnabled
    } else {
        localRememberEnabled
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp),
    ) {
        if (onShowVideoFilters != null) {
            FilledTonalButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_open_video_filters"),
                onClick = onShowVideoFilters,
            ) {
                Text(text = stringResource(R.string.video_filters))
            }
            Spacer(modifier = Modifier.size(16.dp))
        }

        Column(modifier = Modifier.selectableGroup()) {
            VideoContentScale.entries.forEach { contentScale ->
                RadioButtonRow(
                    isSelected = !isCustomZoomActive && contentScale == videoContentScale,
                    text = stringResource(contentScale.nameRes()),
                    testTag = "btn_video_scale_${contentScale.name.lowercase()}",
                    onClick = {
                        onVideoContentScaleChanged(contentScale)
                        // 有记住开关时不立刻关闭，方便继续操作
                        if (!isRememberCallbackProvided) {
                            onDismiss()
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("row_video_scale_remember_this_file")
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.remember_video_scale_for_this_file),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = rememberChecked,
                onCheckedChange = { enabled ->
                    if (isRememberCallbackProvided) {
                        onRememberForThisFileChanged?.invoke(enabled)
                    } else {
                        localRememberEnabled = enabled
                    }
                },
                modifier = Modifier.testTag("switch_video_scale_remember_this_file"),
            )
        }
    }
}
""",
        encoding="utf-8",
    )
    print("wrote VideoContentScaleSelectorView")


def patch_strings() -> None:
    mapping = {
        "core/ui/src/main/res/values/strings.xml": "Remember video zoom for this file",
        "core/ui/src/main/res/values-zh-rCN/strings.xml": "记住该文件缩放方式",
        "core/ui/src/main/res/values-zh-rTW/strings.xml": "記住該檔案縮放方式",
    }
    for rel, value in mapping.items():
        path = ROOT / rel
        text = path.read_text(encoding="utf-8")
        if "remember_video_scale_for_this_file" in text:
            print("string exists", rel)
            continue
        text = text.replace(
            '<string name="remember_decoder_for_this_file">',
            f'<string name="remember_video_scale_for_this_file">{value}</string>\n    '
            f'<string name="remember_decoder_for_this_file">',
            1,
        )
        path.write_text(text, encoding="utf-8")
        print("string added", rel)


def patch_controls_top() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/ui/controls/ControlsTopView.kt"
    text = path.read_text(encoding="utf-8")
    old = ".padding(start = 8.dp, end = 8.dp)"
    new = (
        "// 与进度条上方右侧控件最右间距一致：容器 8 + 控件行 8\n"
        "            .padding(start = 8.dp, end = 16.dp)"
    )
    if old in text:
        text = text.replace(old, new, 1)
        path.write_text(text, encoding="utf-8")
        print("controls top end=16")
    else:
        print("controls top padding already patched?")


def patch_media_player_screen() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
    text = path.read_text(encoding="utf-8")

    old = """                        MenuRoute.VideoContentScale -> VideoContentScaleSelectorContent(
                            videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                            isCustomZoomActive = !videoZoomAndContentScaleState.zoom.isDefaultVideoZoom(),
                            onVideoContentScaleChanged = {
                                videoZoomAndContentScaleState.onVideoContentScaleChanged(it)
                            },
                            onShowVideoFilters = null,
                            onDismiss = ::dismissOverlay,
                        )"""
    new = """                        MenuRoute.VideoContentScale -> {
                            val fileName = currentMediaFileName()
                            val perFileScale = applicationPreferences.perFilePreferenceForPath(fileName)?.videoContentScale
                            var selectedScale by remember(
                                fileName,
                                perFileScale,
                                videoZoomAndContentScaleState.videoContentScale,
                            ) {
                                mutableStateOf(perFileScale ?: videoZoomAndContentScaleState.videoContentScale)
                            }
                            var isRememberScaleForThisFile by remember(fileName, perFileScale) {
                                mutableStateOf(perFileScale != null)
                            }
                            VideoContentScaleSelectorContent(
                                videoContentScale = selectedScale,
                                isCustomZoomActive = !videoZoomAndContentScaleState.zoom.isDefaultVideoZoom() &&
                                    perFileScale == null &&
                                    !isRememberScaleForThisFile,
                                onVideoContentScaleChanged = { scale ->
                                    selectedScale = scale
                                    videoZoomAndContentScaleState.onVideoContentScaleChanged(scale)
                                    if (isRememberScaleForThisFile) {
                                        viewModel.rememberVideoContentScaleForFile(fileName, scale)
                                    } else {
                                        viewModel.updateVideoContentScale(scale)
                                    }
                                },
                                isRememberForThisFileEnabled = isRememberScaleForThisFile,
                                onRememberForThisFileChanged = { enabled ->
                                    isRememberScaleForThisFile = enabled
                                    viewModel.setRememberVideoContentScaleForFile(
                                        fileName = fileName,
                                        contentScale = selectedScale,
                                        isEnabled = enabled,
                                    )
                                },
                                onShowVideoFilters = null,
                                onDismiss = ::dismissOverlay,
                            )
                        }"""
    if old in text:
        text = text.replace(old, new, 1)
        print("wired scale remember menu")
    else:
        print("scale menu block missing")
        idx = text.find("MenuRoute.VideoContentScale")
        print(repr(text[idx : idx + 400]))

    if "currentMediaIdForScale" not in text:
        pattern = re.compile(
            r"val videoZoomAndContentScaleState = rememberVideoZoomAndContentScaleState\([\s\S]*?\n    \)",
        )
        match = pattern.search(text)
        if match:
            insert = """

    // Apply per-file video scale when media item changes
    val currentMediaIdForScale = player.currentMediaItem?.mediaId
    LaunchedEffect(currentMediaIdForScale, applicationPreferences.perFilePlaybackPreferences) {
        val fileName = currentMediaFileName()
        val remembered = applicationPreferences.perFilePreferenceForPath(fileName)?.videoContentScale
        if (remembered != null && remembered != videoZoomAndContentScaleState.videoContentScale) {
            videoZoomAndContentScaleState.onVideoContentScaleChanged(remembered)
        }
    }"""
            text = text[: match.end()] + insert + text[match.end() :]
            print("added LaunchedEffect for scale")
        else:
            print("videoZoom state not found")

    if "import androidx.compose.runtime.LaunchedEffect" not in text:
        text = text.replace(
            "import androidx.compose.runtime.getValue\n",
            "import androidx.compose.runtime.LaunchedEffect\nimport androidx.compose.runtime.getValue\n",
            1,
        )
        print("import LaunchedEffect")

    path.write_text(text, encoding="utf-8")
    print("MediaPlayerScreen saved")


def main() -> None:
    patch_player_service()
    write_scale_selector()
    patch_strings()
    patch_controls_top()
    patch_media_player_screen()


if __name__ == "__main__":
    main()
