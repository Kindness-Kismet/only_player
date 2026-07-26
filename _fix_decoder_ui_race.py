# -*- coding: utf-8 -*-
"""Fix decoder panel: no double recreate; global prefs apply exact choice not extension."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
MS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
GRADLE = ROOT / "app/build.gradle.kts"


def patch_service() -> None:
    t = PS.read_text(encoding="utf-8")

    # global collector: exact preference
    pat = re.compile(
        r"        serviceScope\.launch \{\n"
        r"            // 全局默认解码[\s\S]*?"
        r"            preferencesRepository\.playerPreferences\n"
        r"                \.distinctUntilChanged \{ old, new -> old\.decoderPriority == new\.decoderPriority \}\n"
        r"                \.collect \{\n"
        r"                    val current = mediaSession\?\.player\?\.currentMediaItem\n"
        r"                    applyExtensionDecoderForMediaItem\(current\)\n"
        r"                \}\n"
        r"        \}\n",
    )
    repl = """        serviceScope.launch {
            // 全局解码偏好：严格应用所选值，禁止再经扩展名解析（否则控件选 HW 会被打回 AUTO）
            preferencesRepository.playerPreferences
                .distinctUntilChanged { old, new -> old.decoderPriority == new.decoderPriority }
                .collect { prefs ->
                    if (prefs.decoderPriority != activeDecoderPriority) {
                        switchPlayerDecoderPriority(prefs.decoderPriority)
                    }
                }
        }
"""
    t2, n = pat.subn(repl, t, count=1)
    if n != 1:
        raise SystemExit(f"global collector replace count={n}")
    t = t2
    print("global collector fixed")

    if "isDecoderSwitchInFlight" not in t:
        t = t.replace(
            "    private var activeDecoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC\n",
            "    private var activeDecoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC\n"
            "    @Volatile private var isDecoderSwitchInFlight: Boolean = false\n",
        )

    old_start = """    private fun switchPlayerDecoderPriority(decoderPriority: DecoderPriority) {
        if (decoderPriority == activeDecoderPriority) return
        val session = mediaSession ?: return
        val currentPlayer = session.player as? ExoPlayer ?: return
"""
    new_start = """    private fun switchPlayerDecoderPriority(decoderPriority: DecoderPriority) {
        if (decoderPriority == activeDecoderPriority) return
        if (isDecoderSwitchInFlight) {
            Logger.info(TAG, "Skip nested decoder recreate to ${decoderPriority.logName()}")
            return
        }
        val session = mediaSession ?: return
        val currentPlayer = session.player as? ExoPlayer ?: return
        isDecoderSwitchInFlight = true
"""
    if old_start not in t:
        raise SystemExit("switch start missing")
    t = t.replace(old_start, new_start, 1)

    old_end = """        runCatching {
            currentPlayer.clearMediaItems()
            currentPlayer.stop()
            currentPlayer.release()
        }
    }

    private fun applyAmbienceModeToPlayer"""
    new_end = """        runCatching {
            currentPlayer.clearMediaItems()
            currentPlayer.stop()
            currentPlayer.release()
        }
        isDecoderSwitchInFlight = false
    }

    private fun applyAmbienceModeToPlayer"""
    if old_end not in t:
        raise SystemExit("switch end missing")
    t = t.replace(old_end, new_end, 1)
    print("in-flight guard")

    # SET_DECODER: set active via switch only (exact)
    # also temporarily block preference collector racing by using same in-flight flag

    PS.write_text(t, encoding="utf-8")


def patch_ui() -> None:
    t = MS.read_text(encoding="utf-8")
    old_click = """                                onDecoderPriorityClick = { priority ->
                                    selectedPriority = priority
                                    // 1) 写全局默认 2) 立刻用所选解码重建当前播放（扩展名设置页仍管扩展名）
                                    viewModel.updateDecoderPriority(priority)
                                    val controller = player as? androidx.media3.session.MediaController
                                    controller?.setDecoderPriorityNow(priority.name)
                                },
"""
    new_click = """                                onDecoderPriorityClick = { priority ->
                                    selectedPriority = priority
                                    // 先立刻按所选值重建，再写全局偏好（service 不再用扩展名覆盖）
                                    val controller = player as? androidx.media3.session.MediaController
                                    controller?.setDecoderPriorityNow(priority.name)
                                    viewModel.updateDecoderPriority(priority)
                                },
"""
    if old_click in t:
        t = t.replace(old_click, new_click, 1)
        print("click order fixed")
    else:
        # already modified?
        if "setDecoderPriorityNow(priority.name)" in t and "updateDecoderPriority(priority)" in t:
            # force order: command first
            t = re.sub(
                r"onDecoderPriorityClick = \{ priority ->\n"
                r"\s*selectedPriority = priority\n"
                r"[\s\S]*?viewModel\.updateDecoderPriority\(priority\)\n"
                r"[\s\S]*?controller\?\.setDecoderPriorityNow\(priority\.name\)\n"
                r"\s*\},",
                """onDecoderPriorityClick = { priority ->
                                    selectedPriority = priority
                                    val controller = player as? androidx.media3.session.MediaController
                                    controller?.setDecoderPriorityNow(priority.name)
                                    viewModel.updateDecoderPriority(priority)
                                },""",
                t,
                count=1,
            )
            print("click order regex")
        else:
            print("click block not found")

    t2, n = re.subn(
        r"var selectedPriority by remember\([\s\S]*?\) \{\n"
        r"\s*mutableStateOf\(initial\)\n"
        r"\s*\}",
        "var selectedPriority by remember(player.currentMediaItem?.mediaId) {\n"
        "                                mutableStateOf(initial)\n"
        "                            }",
        t,
        count=1,
    )
    if n == 1:
        t = t2
        print("remember keys fixed")
    else:
        print("remember keys not fixed", n)

    MS.write_text(t, encoding="utf-8")


def bump() -> None:
    t = GRADLE.read_text(encoding="utf-8")
    t = t.replace("versionCode = 162", "versionCode = 163")
    t = t.replace('versionName = "1.0.161"', 'versionName = "1.0.162"')
    GRADLE.write_text(t, encoding="utf-8")
    print("version 1.0.162")


def main() -> None:
    patch_service()
    patch_ui()
    bump()
    print("done")


if __name__ == "__main__":
    main()
