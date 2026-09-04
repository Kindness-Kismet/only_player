package one.only.player.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import one.only.player.core.model.PlayerControl
import one.only.player.core.ui.extensions.icon
import one.only.player.core.ui.extensions.id
import one.only.player.core.ui.extensions.label

// 控件点击后的行为：要么打开面板，要么直接执行
internal sealed interface PlayerControlAction {
    data class OpenPanel(val route: MenuRoute) : PlayerControlAction

    data class Execute(val onExecute: () -> Unit) : PlayerControlAction
}

// 菜单和角落共用同一份绑定，控件换位置不改变行为
internal data class PlayerControlBinding(
    val control: PlayerControl,
    val icon: ImageVector,
    val label: String,
    val menuTestTag: String,
    val cornerTestTag: String,
    val action: PlayerControlAction,
    // isAvailable 为 false 时整个控件不渲染；isEnabled 为 false 时渲染但不可点
    val isAvailable: Boolean = true,
    val isEnabled: Boolean = true,
)

@Composable
internal fun playerControlBindings(
    isPipSupported: Boolean,
    isTakingScreenshot: Boolean,
    hasChapters: Boolean,
    onRotate: () -> Unit,
    onPictureInPicture: () -> Unit,
    onScreenshot: () -> Unit,
    onPlayInBackground: () -> Unit,
    onToggleControlsLock: () -> Unit,
): Map<PlayerControl, PlayerControlBinding> {
    val bindings = listOf(
        binding(
            control = PlayerControl.ROTATE,
            action = PlayerControlAction.Execute(onRotate),
        ),
        binding(
            control = PlayerControl.PLAYLIST,
            action = PlayerControlAction.OpenPanel(MenuRoute.Playlist),
        ),
        binding(
            control = PlayerControl.PLAYBACK_SPEED,
            action = PlayerControlAction.OpenPanel(MenuRoute.PlaybackSpeed),
        ),
        binding(
            control = PlayerControl.SUBTITLE,
            action = PlayerControlAction.OpenPanel(MenuRoute.Subtitle),
        ),
        binding(
            control = PlayerControl.AUDIO,
            action = PlayerControlAction.OpenPanel(MenuRoute.Audio),
        ),
        binding(
            control = PlayerControl.CHAPTERS,
            action = PlayerControlAction.OpenPanel(MenuRoute.Chapters),
            isAvailable = hasChapters,
        ),
        binding(
            control = PlayerControl.SCALE,
            action = PlayerControlAction.OpenPanel(MenuRoute.VideoContentScale),
        ),
        binding(
            control = PlayerControl.DECODER,
            action = PlayerControlAction.OpenPanel(MenuRoute.Decoder),
        ),
        binding(
            control = PlayerControl.VIDEO_INFO,
            action = PlayerControlAction.OpenPanel(MenuRoute.VideoInfo),
        ),
        binding(
            control = PlayerControl.VIDEO_FILTERS,
            action = PlayerControlAction.OpenPanel(MenuRoute.VideoFilters),
        ),
        binding(
            control = PlayerControl.SLEEP_TIMER,
            action = PlayerControlAction.OpenPanel(MenuRoute.SleepTimer),
        ),
        binding(
            control = PlayerControl.MARK,
            action = PlayerControlAction.OpenPanel(MenuRoute.PlaybackMarks),
        ),
        binding(
            control = PlayerControl.LOCK,
            action = PlayerControlAction.Execute(onToggleControlsLock),
        ),
        binding(
            control = PlayerControl.MUTE,
            action = PlayerControlAction.OpenPanel(MenuRoute.Mute),
        ),
        binding(
            control = PlayerControl.AMBIENCE_MODE,
            action = PlayerControlAction.OpenPanel(MenuRoute.AmbienceMode),
        ),
        binding(
            control = PlayerControl.MIRROR_VIDEO,
            action = PlayerControlAction.OpenPanel(MenuRoute.MirrorVideo),
        ),
        binding(
            control = PlayerControl.PIP,
            action = PlayerControlAction.Execute(onPictureInPicture),
            isAvailable = isPipSupported,
        ),
        binding(
            control = PlayerControl.SCREENSHOT,
            action = PlayerControlAction.Execute(onScreenshot),
            isEnabled = !isTakingScreenshot,
        ),
        binding(
            control = PlayerControl.BACKGROUND_PLAY,
            action = PlayerControlAction.Execute(onPlayInBackground),
        ),
        binding(
            control = PlayerControl.LOOP,
            action = PlayerControlAction.OpenPanel(MenuRoute.LoopMode),
        ),
        binding(
            control = PlayerControl.SHUFFLE,
            action = PlayerControlAction.OpenPanel(MenuRoute.ShuffleMode),
        ),
    )
    return bindings.associateBy(PlayerControlBinding::control)
}

// 按编排顺序取出可渲染的绑定
internal fun Map<PlayerControl, PlayerControlBinding>.resolve(controls: List<PlayerControl>): List<PlayerControlBinding> = controls
    .mapNotNull { control -> get(control) }
    .filter { it.isAvailable }

private fun PlayerControl.menuTestTag(): String = "menu_item_$id"

private fun PlayerControl.cornerTestTag(): String = "btn_corner_$id"

@Composable
private fun binding(
    control: PlayerControl,
    action: PlayerControlAction,
    isAvailable: Boolean = true,
    isEnabled: Boolean = true,
): PlayerControlBinding = PlayerControlBinding(
    control = control,
    icon = control.icon(),
    label = control.label(),
    menuTestTag = control.menuTestTag(),
    cornerTestTag = control.cornerTestTag(),
    action = action,
    isAvailable = isAvailable,
    isEnabled = isEnabled,
)
