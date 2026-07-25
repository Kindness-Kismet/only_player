from pathlib import Path
import re

# RotationState per-file support
rot = Path(r"E:/Downloads/only_player_src/feature/player/src/main/java/one/only/player/feature/player/state/RotationState.kt")
rt = rot.read_text(encoding="utf-8")
if "perFileOrientation" not in rt:
    rt = rt.replace(
        """fun rememberRotationState(
    player: Player,
    screenOrientation: ScreenOrientation,
    shouldRememberScreenOrientation: Boolean,
    lastScreenOrientation: LastPlayerScreenOrientation?,
    onLastScreenOrientationChange: (LastPlayerScreenOrientation) -> Unit,
): RotationState {
    val activity = LocalActivity.current as ComponentActivity
    val rotationState = remember(screenOrientation, shouldRememberScreenOrientation, lastScreenOrientation) {
        RotationState(
            activity = activity,
            screenOrientation = screenOrientation,
            shouldRememberScreenOrientation = shouldRememberScreenOrientation,
            lastScreenOrientation = lastScreenOrientation,
            onLastScreenOrientationChange = onLastScreenOrientationChange,
        )
    }
""",
        """fun rememberRotationState(
    player: Player,
    screenOrientation: ScreenOrientation,
    shouldRememberScreenOrientation: Boolean,
    lastScreenOrientation: LastPlayerScreenOrientation?,
    perFileOrientation: LastPlayerScreenOrientation? = null,
    onLastScreenOrientationChange: (LastPlayerScreenOrientation) -> Unit,
): RotationState {
    val activity = LocalActivity.current as ComponentActivity
    val rotationState = remember(screenOrientation, shouldRememberScreenOrientation, lastScreenOrientation, perFileOrientation) {
        RotationState(
            activity = activity,
            screenOrientation = screenOrientation,
            shouldRememberScreenOrientation = shouldRememberScreenOrientation,
            lastScreenOrientation = lastScreenOrientation,
            perFileOrientation = perFileOrientation,
            onLastScreenOrientationChange = onLastScreenOrientationChange,
        )
    }
""",
    )
    rt = rt.replace(
        """class RotationState(
    private val activity: ComponentActivity,
    private val screenOrientation: ScreenOrientation,
    private val shouldRememberScreenOrientation: Boolean,
    private val lastScreenOrientation: LastPlayerScreenOrientation?,
    private val onLastScreenOrientationChange: (LastPlayerScreenOrientation) -> Unit,
) {""",
        """class RotationState(
    private val activity: ComponentActivity,
    private val screenOrientation: ScreenOrientation,
    private val shouldRememberScreenOrientation: Boolean,
    private val lastScreenOrientation: LastPlayerScreenOrientation?,
    private val perFileOrientation: LastPlayerScreenOrientation? = null,
    private val onLastScreenOrientationChange: (LastPlayerScreenOrientation) -> Unit,
) {""",
    )
    rt = rt.replace(
        """        // 优先使用记住的手动旋转方向（含“按视频方向”模式）
        val remembered = lastScreenOrientation
            ?.takeIf { shouldRememberScreenOrientation }
            ?.toActivityOrientation()
        if (remembered != null) {
            activity.requestedOrientation = remembered
            return
        }
""",
        """        // 1) 文件级方向 2) 全局记住方向 3) 设置模式
        val preferred = perFileOrientation
            ?: lastScreenOrientation?.takeIf { shouldRememberScreenOrientation }
        preferred?.toActivityOrientation()?.let {
            activity.requestedOrientation = it
            return
        }
""",
    )
    rt = rt.replace(
        """        // 已记住手动方向时，不要被视频宽高覆盖
        if (shouldRememberScreenOrientation && lastScreenOrientation != null) return
""",
        """        // 已有文件级/全局记住方向时，不要被视频宽高覆盖
        if (perFileOrientation != null) return
        if (shouldRememberScreenOrientation && lastScreenOrientation != null) return
""",
    )
    rot.write_text(rt, encoding="utf-8")
    print("RotationState per-file support")
else:
    print("RotationState already has perFile")

# MediaPlayerScreen rotation call
ms = Path(r"E:/Downloads/only_player_src/feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt")
mt = ms.read_text(encoding="utf-8")
if "perFileOrientation =" not in mt:
    old = """        shouldRememberScreenOrientation = playerPreferences.shouldRememberPlayerScreenOrientation,
        lastScreenOrientation = playerPreferences.lastPlayerScreenOrientation,
        onLastScreenOrientationChange = viewModel::updateLastPlayerScreenOrientation,
"""
    new = """        shouldRememberScreenOrientation = playerPreferences.shouldRememberPlayerScreenOrientation,
        lastScreenOrientation = playerPreferences.lastPlayerScreenOrientation,
        perFileOrientation = run {
            val mediaItem = player?.currentMediaItem
            val candidates = listOfNotNull(
                mediaItem?.localConfiguration?.uri?.lastPathSegment,
                mediaItem?.localConfiguration?.uri?.path,
                mediaItem?.mediaMetadata?.title?.toString(),
                mediaItem?.mediaId,
            )
            var fileName: String? = null
            for (candidate in candidates) {
                fileName = one.only.player.core.model.PerFilePlaybackPreference.fromPathOrName(candidate)
                if (!fileName.isNullOrBlank()) break
            }
            applicationPreferences.perFilePreferenceForPath(fileName)?.screenOrientation
        },
        onLastScreenOrientationChange = viewModel::updateLastPlayerScreenOrientation,
"""
    if old in mt:
        mt = mt.replace(old, new)
        ms.write_text(mt, encoding="utf-8")
        print("wired perFileOrientation into rememberRotationState")
    else:
        print("rotation call anchor miss")
        i = mt.find("rememberRotationState(")
        print(mt[i:i+450] if i >= 0 else "no call")
else:
    print("perFileOrientation already in MediaPlayerScreen")

# MediaPickerViewModel delete cleanup
mpvm = Path(r"E:/Downloads/only_player_src/feature/videopicker/src/main/java/one/only/player/feature/videopicker/screens/mediapicker/MediaPickerViewModel.kt")
mpt = mpvm.read_text(encoding="utf-8")
if "withoutPerFilePreferences" not in mpt:
    if "preferencesRepository" not in mpt:
        print("MediaPickerViewModel has no preferencesRepository")
    else:
        old_del = """            val isDeletionSuccessful = mediaService.deleteMedia(uris.map { it.toUri() })
            if (isDeletionSuccessful) {
                mediaSynchronizer.removeDeleted(uris)
                refreshDeletedPathsAsync(videos.map(SelectedVideo::path))
            }"""
        new_del = """            val isDeletionSuccessful = mediaService.deleteMedia(uris.map { it.toUri() })
            if (isDeletionSuccessful) {
                mediaSynchronizer.removeDeleted(uris)
                refreshDeletedPathsAsync(videos.map(SelectedVideo::path))
                val names = videos.mapNotNull { video ->
                    video.path.substringAfterLast('/').substringAfterLast('\\\\').ifBlank { null }
                        ?: video.uriString.substringAfterLast('/').ifBlank { null }
                }
                if (names.isNotEmpty()) {
                    preferencesRepository.updateApplicationPreferences { prefs ->
                        prefs.withoutPerFilePreferences(names)
                    }
                }
            }"""
        if old_del in mpt:
            mpt = mpt.replace(old_del, new_del)
            mpvm.write_text(mpt, encoding="utf-8")
            print("delete cleanup mediapicker")
        else:
            print("delete block miss mediapicker")
else:
    print("mediapicker cleanup exists")

# SearchViewModel delete cleanup
svm = Path(r"E:/Downloads/only_player_src/feature/videopicker/src/main/java/one/only/player/feature/videopicker/screens/search/SearchViewModel.kt")
st = svm.read_text(encoding="utf-8")
if "withoutPerFilePreferences" not in st:
    print("search has prefs repo", "preferencesRepository" in st)
    if "preferencesRepository" in st and "mediaService.deleteMedia" in st:
        # read around delete
        i = st.find("mediaService.deleteMedia")
        print(st[i-120:i+350])
else:
    print("search cleanup exists")

print("batch done")
