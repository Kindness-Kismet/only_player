# -*- coding: utf-8 -*-
"""v17: stabilize media session like v15, rewrite AUDIO_ONLY as pure .mp3 suffix gate,
fix decoder remember file-name resolution for content uris, keep scale remember,
top-right end padding +10dp.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
MS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
TOP = ROOT / "feature/player/src/main/java/one/only/player/feature/player/ui/controls/ControlsTopView.kt"
GRADLE = ROOT / "app/build.gradle.kts"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"MISSING block: {label}")
    return text.replace(old, new, 1)


def patch_player_service() -> None:
    t = PS.read_text(encoding="utf-8")

    # 1) onConnect: never gate local; never reject; always give local full commands.
    # External control not limited via empty commands (can break OEMs) — only hide via notification/legacy.
    old_connect = """        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // 切勿 ConnectionResult.reject()：部分机型会把本机 MediaController 一并误伤，
            // 表现为「仅 MP3 / 不显示」时一点文件就返回。可见性用通知 + legacy active 控制。
            val connectionResult = super.onConnect(session, controller)
            val canControlPlayer = shouldAllowControllerPlayerCommands(controller)
            if (!canControlPlayer) {
                // 外部控制器：允许连上但不给播放控制，系统媒体中心也拿不到可用命令
                val emptySessionCommands = SessionCommands.Builder().build()
                val emptyPlayerCommands = Player.Commands.Builder().build()
                return MediaSession.ConnectionResult.accept(
                    emptySessionCommands,
                    emptyPlayerCommands,
                )
            }
            return MediaSession.ConnectionResult.accept(
                connectionResult.availableSessionCommands
                    .buildUpon()
                    .addSessionCommands(customCommands)
                    .build(),
                connectionResult.availablePlayerCommands,
            )
        }"""
    new_connect = """        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // 永远不要 reject()，也不要给本机空 commands——OEM 会把本机 MediaController 误伤成一点就返回。
            // 系统媒体可见性只靠：通知 MediaStyle + legacy MediaSessionCompat.active。
            val connectionResult = super.onConnect(session, controller)
            return MediaSession.ConnectionResult.accept(
                connectionResult.availableSessionCommands
                    .buildUpon()
                    .addSessionCommands(customCommands)
                    .build(),
                connectionResult.availablePlayerCommands,
            )
        }"""
    t = replace_once(t, old_connect, new_connect, "onConnect")

    # 2) Replace mp3 detection + dead accept helpers with simple path extension helpers
    # matching decoder extension resolution.
    old_mp3_block = """    private fun isMp3MediaItem(mediaItem: MediaItem?): Boolean {
        if (mediaItem == null) return false
        val uri = mediaItem.localConfiguration?.uri
        val resolvedPath = uri?.let { candidateUri ->
            when (candidateUri.scheme) {
                ContentResolver.SCHEME_FILE -> candidateUri.path
                ContentResolver.SCHEME_CONTENT -> getPath(candidateUri)
                else -> candidateUri.path
            }
        }
        val candidates = listOfNotNull(
            resolvedPath,
            mediaItem.mediaMetadata.remoteFilePath,
            uri?.lastPathSegment,
            uri?.path,
            mediaItem.mediaId,
            mediaItem.mediaMetadata.title?.toString(),
            mediaItem.requestMetadata?.mediaUri?.toString(),
        )
        for (candidate in candidates) {
            val clean = candidate
                .substringAfterLast('/')
                .substringBefore('?')
                .substringBefore('#')
            val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
            if (ext.equals("mp3", ignoreCase = true)) return true
        }
        // 仅按 .mp3 后缀判定，避免 mime 误伤其它格式
        return false
    }

    private fun isLocalMediaController(controller: MediaSession.ControllerInfo): Boolean {
        val packageName = controller.packageName.orEmpty()
        val myUid = android.os.Process.myUid()
        // 本应用 / Media3 本机会话 / 同 UID / 通知控制器一律视为本地
        if (packageName.isBlank()) return true
        if (packageName == applicationContext.packageName) return true
        if (packageName == "androidx.media3.session") return true
        if (packageName.startsWith("androidx.media3")) return true
        if (controller.uid == applicationInfo.uid || controller.uid == myUid) return true
        // 通知栏控制器：本地播放链路需要，不能当外部拒掉
        if (mediaSession?.isMediaNotificationController(controller) == true) return true
        return false
    }

    /**
     * 是否允许该控制器操作 Player。
     * - 本机：始终允许（否则一点就返回）
     * - 外部：SHOW 允许；HIDE 禁止；AUDIO_ONLY 仅当前是 .mp3 时允许
     */
    private fun shouldAllowControllerPlayerCommands(controller: MediaSession.ControllerInfo): Boolean {
        if (isLocalMediaController(controller)) return true
        return when (playerPreferences.mediaSessionVisibility) {
            MediaSessionVisibility.SHOW -> true
            MediaSessionVisibility.HIDE -> false
            MediaSessionVisibility.AUDIO_ONLY -> isMp3MediaItem(mediaSession?.player?.currentMediaItem)
        }
    }

    @Deprecated("Use shouldAllowControllerPlayerCommands; reject() breaks local playback on some OEMs")
    private fun shouldAcceptMediaController(controller: MediaSession.ControllerInfo): Boolean =
        shouldAllowControllerPlayerCommands(controller)
"""
    new_mp3_block = """    /**
     * 与扩展名解码配置同样的路径候选：file/content/remote/title，只认后缀 .mp3。
     */
    private fun mediaItemPathCandidates(mediaItem: MediaItem?): List<String> {
        if (mediaItem == null) return emptyList()
        val uri = mediaItem.localConfiguration?.uri
        val resolvedPath = uri?.let { candidateUri ->
            when (candidateUri.scheme) {
                ContentResolver.SCHEME_FILE -> candidateUri.path
                ContentResolver.SCHEME_CONTENT -> getPath(candidateUri)
                else -> candidateUri.path
            }
        }
        return listOfNotNull(
            resolvedPath,
            mediaItem.mediaMetadata.remoteFilePath,
            uri?.lastPathSegment,
            uri?.path,
            uri?.toString(),
            mediaItem.mediaId,
            mediaItem.requestMetadata?.mediaUri?.toString(),
            mediaItem.mediaMetadata.title?.toString(),
        )
    }

    private fun pathLooksLikeMp3(pathOrName: String?): Boolean {
        if (pathOrName.isNullOrBlank()) return false
        val clean = pathOrName
            .substringAfterLast('/')
            .substringAfterLast('\\\\')
            .substringBefore('?')
            .substringBefore('#')
        val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
        return ext.equals("mp3", ignoreCase = true)
    }

    private fun isMp3MediaItem(mediaItem: MediaItem?): Boolean {
        // 仅按 .mp3 后缀（与扩展名解码同一套候选），不看 mime
        return mediaItemPathCandidates(mediaItem).any(::pathLooksLikeMp3)
    }
"""
    t = replace_once(t, old_mp3_block, new_mp3_block, "mp3 helpers")

    # 3) onUpdateNotification — keep v15-style hard hide (quiet + legacy inactive) without empty-command gating
    # Already mostly good; ensure publish helpers use isMp3MediaItem only.
    # Make sure SessionCommands import unused is ok (keep).

    # 4) Strengthen resolveDecoderPriority to log when file-level applied (optional) — skip

    # 5) When updating legacy inactive on HIDE, also try setSessionActivity null? skip

    # Remove unused SessionCommands import if no longer referenced
    if "SessionCommands." not in t and "SessionCommands " not in t:
        t = t.replace("import androidx.media3.session.SessionCommands\n", "")

    PS.write_text(t, encoding="utf-8")
    print("PlayerService patched")


def patch_media_player_screen() -> None:
    t = MS.read_text(encoding="utf-8")

    # Fix currentMediaFileName for content:// without extension in lastPathSegment —
    # also try DISPLAY_NAME via ContentResolver query if available is heavy; instead
    # expand candidates with remote path + title which often has .mp4
    old_fn = """    fun currentMediaFileName(): String? {
        val mediaItem = player.currentMediaItem
        val candidates = listOfNotNull(
            mediaItem?.localConfiguration?.uri?.lastPathSegment,
            mediaItem?.localConfiguration?.uri?.path,
            mediaItem?.mediaMetadata?.title?.toString(),
            mediaItem?.mediaId,
            mediaItem?.mediaMetadata?.extras?.getString("media_metadata_remote_file_path"),
        )
        for (candidate in candidates) {
            val name = one.only.player.core.model.PerFilePlaybackPreference.fromPathOrName(candidate)
            if (!name.isNullOrBlank()) return name
        }
        return null
    }"""
    new_fn = """    fun currentMediaFileName(): String? {
        val mediaItem = player.currentMediaItem
        val uri = mediaItem?.localConfiguration?.uri
        // content:// 的 lastPathSegment 常常没有扩展名；优先 remote path / path / title
        val candidates = listOfNotNull(
            mediaItem?.mediaMetadata?.extras?.getString("media_metadata_remote_file_path"),
            mediaItem?.mediaMetadata?.remoteFilePath,
            uri?.path,
            uri?.lastPathSegment,
            mediaItem?.mediaMetadata?.title?.toString(),
            mediaItem?.requestMetadata?.mediaUri?.path,
            mediaItem?.requestMetadata?.mediaUri?.lastPathSegment,
            mediaItem?.mediaId,
            uri?.toString(),
        )
        // 先找带扩展名的文件名，便于 per-file / 扩展名配置命中
        for (candidate in candidates) {
            val name = one.only.player.core.model.PerFilePlaybackPreference.fromPathOrName(candidate)
            if (!name.isNullOrBlank() && name.contains('.')) return name
        }
        for (candidate in candidates) {
            val name = one.only.player.core.model.PerFilePlaybackPreference.fromPathOrName(candidate)
            if (!name.isNullOrBlank()) return name
        }
        return null
    }"""
    # remoteFilePath is extension property — need import usage; MediaMetadata.remoteFilePath exists
    # MediaPlayerScreen may need import - it's used as mediaMetadata.remoteFilePath via extension
    if "import one.only.player.feature.player.extensions.remoteFilePath" not in t:
        # add near other feature.player.extensions imports if any
        if "import one.only.player.feature.player.extensions." in t:
            # inject after first extensions import
            import re
            t = re.sub(
                r"(import one\.only\.player\.feature\.player\.extensions\.[^\n]+\n)",
                r"\1import one.only.player.feature.player.extensions.remoteFilePath\n",
                t,
                count=1,
            )
        else:
            t = t.replace(
                "import one.only.player.feature.player.ui.",
                "import one.only.player.feature.player.extensions.remoteFilePath\nimport one.only.player.feature.player.ui.",
                1,
            )
    t = replace_once(t, old_fn, new_fn, "currentMediaFileName")

    # Decoder remember: fix remember keys so switch reflects persisted state after reopen
    old_dec_remember = """                            var selectedPriority by remember(fileName, applicationPreferences, playerPreferences.decoderPriority) {
                                val mediaExtension = fileName
                                    ?.substringAfterLast('.', missingDelimiterValue = "")
                                    ?.takeIf { it.isNotBlank() && it.length <= 10 && it.all { ch -> ch.isLetterOrDigit() } }
                                    ?.lowercase()
                                val initial = perFileDecoder
                                    ?: mediaExtension?.let { ext ->
                                        applicationPreferences.normalizedExtensionDecoderPreferences()
                                            .firstOrNull { it.extension == ext }
                                            ?.decoderPriority
                                    }
                                    ?: playerPreferences.decoderPriority
                                mutableStateOf(initial)
                            }
                            var isRememberForThisFile by remember(fileName, perFileDecoder) {
                                mutableStateOf(perFileDecoder != null)
                            }"""
    new_dec_remember = """                            var selectedPriority by remember(fileName, perFileDecoder, playerPreferences.decoderPriority) {
                                val mediaExtension = fileName
                                    ?.substringAfterLast('.', missingDelimiterValue = "")
                                    ?.takeIf { it.isNotBlank() && it.length <= 10 && it.all { ch -> ch.isLetterOrDigit() } }
                                    ?.lowercase()
                                val initial = perFileDecoder
                                    ?: mediaExtension?.let { ext ->
                                        applicationPreferences.normalizedExtensionDecoderPreferences()
                                            .firstOrNull { it.extension == ext }
                                            ?.decoderPriority
                                    }
                                    ?: playerPreferences.decoderPriority
                                mutableStateOf(initial)
                            }
                            var isRememberForThisFile by remember(fileName, perFileDecoder) {
                                mutableStateOf(perFileDecoder != null)
                            }"""
    t = replace_once(t, old_dec_remember, new_dec_remember, "decoder remember keys")

    MS.write_text(t, encoding="utf-8")
    print("MediaPlayerScreen patched")


def patch_top_padding() -> None:
    t = TOP.read_text(encoding="utf-8")
    old = """            // 与进度条上方右侧控件最右间距一致：容器 8 + 控件行 8
            .padding(start = 8.dp, end = 16.dp)"""
    new = """            // 旧版右上角最右再加 10dp（相对进度条上方有效 16dp → 26dp）
            .padding(start = 8.dp, end = 26.dp)"""
    t = replace_once(t, old, new, "top end padding")
    TOP.write_text(t, encoding="utf-8")
    print("ControlsTopView end=26")


def bump_version() -> None:
    t = GRADLE.read_text(encoding="utf-8")
    t2 = t.replace("versionCode = 150", "versionCode = 151").replace(
        'versionName = "1.0.149"',
        'versionName = "1.0.150"',
    )
    if t2 == t:
        # try other
        import re
        print("version lines:", re.findall(r"versionCode = \d+|versionName = \"[^\"]+\"", t))
    else:
        GRADLE.write_text(t2, encoding="utf-8")
        print("version 1.0.150 / 151")


def main() -> None:
    patch_player_service()
    patch_media_player_screen()
    patch_top_padding()
    bump_version()


if __name__ == "__main__":
    main()
