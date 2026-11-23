import argparse
from dataclasses import dataclass
import math


class CommandFailure(RuntimeError):
    def __init__(self, message: str, exit_code: int = 1, result: dict | None = None):
        super().__init__(message)
        self.exit_code = exit_code
        self.result = result


def boolean_value(value: str) -> bool:
    if value.lower() not in ("true", "false"):
        raise argparse.ArgumentTypeError("Expected true or false")
    return value.lower() == "true"


def extra_value(value: str) -> tuple[str, str, object]:
    parts = value.split(":", 2)
    if len(parts) != 3 or not parts[0]:
        raise argparse.ArgumentTypeError("Use KEY:TYPE:VALUE, with TYPE s, b, i, l or f")
    key, kind, raw = parts
    if key == "response_json":
        raise argparse.ArgumentTypeError("response_json is reserved for the transport")
    try:
        converters = {"s": str, "b": boolean_value, "i": int, "l": int, "f": float}
        if kind not in converters:
            raise ValueError("Unsupported extra type: " + kind)
        parsed = converters[kind](raw)
        if kind == "i" and not -(2**31) <= parsed < 2**31:
            raise ValueError("Integer extra must fit a signed 32-bit value")
        if kind == "l" and not -(2**63) <= parsed < 2**63:
            raise ValueError("Long extra must fit a signed 64-bit value")
        if kind == "f" and not math.isfinite(parsed):
            raise ValueError("Float extra must be finite")
    except ValueError as error:
        raise argparse.ArgumentTypeError(str(error)) from error
    return key, kind, parsed


PAGES = (
    "home", "search", "recycle_bin", "favorites", "playlists", "history", "cloud", "settings",
    "settings.appearance", "settings.media_library", "settings.folders", "settings.thumbnails",
    "settings.player", "settings.player_controls", "settings.gestures", "settings.decoder",
    "settings.audio", "settings.subtitle", "settings.general", "settings.about", "settings.libraries",
    "settings.logs",
)

# 只登记应用端已经实现的方法；参数细节由逐命令帮助与对应业务指南共同维护。
PROVIDER_COMMANDS = {
    "page.open": "Open an application page",
    "settings.set": "Set an explicit application or player preference",
    "settings.toggle": "Toggle a supported Boolean preference",
    "settings.action": "Run a settings maintenance action",
    "cloud.server.add": "Add a cloud server",
    "cloud.server.update": "Update a cloud server",
    "cloud.server.delete": "Delete a cloud server",
    "cloud.server.clear": "Clear all cloud servers",
    "cloud.server.list": "List cloud servers",
    "cloud.media.list": "List playable media in a cloud directory",
    "cloud.media.open": "Open a cloud video",
    "cloud.quick_settings.get": "Read cloud media layout and sorting preferences",
    "cloud.quick_settings.set": "Set cloud media layout and sorting preferences",
    "quick_settings.get": "Read local media layout and sorting preferences",
    "quick_settings.set": "Set local media layout and sorting preferences",
    "favorite.add": "Add a favorite item or folder",
    "favorite.list": "List favorite items",
    "favorite.delete": "Delete a favorite item",
    "favorite.move": "Move a favorite item to another folder",
    "favorite.clear": "Clear all favorite items",
    "playlist.create": "Create a local playlist",
    "playlist.add": "Add local media to a playlist",
    "playlist.list": "List local playlists",
    "playlist.items": "List items in a playlist",
    "playlist.delete": "Delete a playlist",
    "playlist.clear": "Clear all playlists",
    "history.list": "List playback history",
    "history.remove": "Remove a playback history entry",
    "history.clear": "Clear playback history",
    "media.list": "List local videos and file status",
    "media.open": "Open a local video",
    "media.move_to_recycle_bin": "Move a video to the recycle bin",
    "media.move_to_folder": "Move a video to a folder",
    "media.move_folder_to_folder": "Move a media folder",
    "media.restore_from_recycle_bin": "Restore a recycled video",
    "media.delete_permanently": "Permanently delete a local video",
    "media.refresh": "Refresh the media library",
    "media.scan_path": "Scan an existing media path",
    "media.status": "Read a video's database and file status",
    "player.play": "Start playback",
    "player.pause": "Pause playback",
    "player.toggle_play_pause": "Toggle playback",
    "player.previous": "Play the previous video",
    "player.next": "Play the next video",
    "player.seek_to": "Seek to a playback position",
    "player.seek_by": "Seek by a signed time offset",
    "player.stress_seek": "Run repeated seek operations",
    "player.long_press_speed": "Temporarily change playback speed",
    "player.stop": "Stop playback",
    "player.shuffle": "Set or toggle shuffle",
    "player.loop": "Set or cycle the repeat mode",
    "player.rotate": "Invoke the player rotation action",
    "player.toggle_ambience": "Toggle ambient lighting",
    "player.toggle_mirror": "Toggle video mirroring",
    "player.show_controls": "Show player controls",
    "player.hide_controls": "Hide player controls",
    "player.show_playlist": "Open the playback queue panel",
    "player.show_speed": "Open the playback speed panel",
    "player.show_audio": "Open the audio track panel",
    "player.show_subtitle": "Open the subtitle panel",
    "player.lock": "Lock player controls",
    "player.unlock": "Unlock player controls",
    "player.toggle_lock": "Toggle the player control lock",
    "player.cycle_scale": "Cycle the video scale mode",
    "player.show_scale": "Open video scale options",
    "player.show_decoder": "Open decoder options",
    "player.show_video_filters": "Open video filters",
    "player.show_audio_equalizer": "Open the audio equalizer",
    "player.pip": "Enter picture-in-picture mode",
    "player.screenshot": "Invoke the player's video screenshot action",
    "player.background": "Switch to background playback",
    "player.show_sleep_timer": "Open the sleep timer",
    "player.show_marks": "Open playback marks",
    "player.show_chapters": "Open the chapter panel",
    "player.chapter.seek": "Seek to a chapter index",
    "player.chapter.next": "Seek to the next chapter",
    "player.chapter.previous": "Seek to the previous chapter",
    "player.chapter.swipe_next": "Invoke the two-finger next-chapter action",
    "player.chapter.swipe_previous": "Invoke the two-finger previous-chapter action",
    "player.mark.add": "Add a playback mark",
    "player.mark.list": "List playback marks",
    "player.mark.seek": "Seek to a playback mark",
    "player.mark.delete": "Delete a playback mark",
    "player.show_menu": "Open the player menu",
    "player.menu_back": "Return to the parent player menu",
    "player.stress_pan_zoom": "Run repeated video pan and zoom actions",
    "player.panel_resize": "Resize a floating player panel",
    "player.panel_move": "Move a floating player panel",
    "player.panel_state": "Read the floating panel layout",
    "player.back": "Exit the player and end playback",
    "player.state": "Read playback state and diagnostics",
    "player.position": "Read the playback position",
    "player.duration": "Read the media duration",
    "player.cues": "Read active subtitle cues",
    "player.video_format": "Read video format and decoder information",
    "player.chapters": "Read media chapters",
}

GUIDES = {
    "page": "pages.md", "settings": "settings.md", "cloud": "cloud.md",
    "quick_settings": "quick_settings.md", "favorite": "favorites.md",
    "playlist": "playlists.md", "history": "playlists.md", "media": "media.md", "player": "player.md",
}


EXAMPLE_ARGUMENTS = {
    "page.open": "home",
    "settings.set": "appearance.navigation_bar_style --value HYPER_OS_4",
    "settings.toggle": "appearance.top_bar_blur",
    "settings.action": "player.reset_controls",
    "cloud.server.add": "--extra protocol:s:WEBDAV --extra host:s:192.168.1.10 --extra name:s:Demo",
    "cloud.server.update": "SERVER_ID --extra name:s:Demo",
    "cloud.server.delete": "SERVER_ID",
    "cloud.media.list": "--extra server_id:s:SERVER_ID --extra path:s:/Movies",
    "cloud.media.open": "--extra server_id:s:SERVER_ID --extra path:s:/Movies/example.mp4",
    "cloud.quick_settings.get": "--extra server_id:s:SERVER_ID",
    "cloud.quick_settings.set": "layout_mode --value GRID --extra server_id:s:SERVER_ID",
    "quick_settings.set": "layout_mode --value GRID",
    "favorite.add": "--extra type:s:FAVORITE_FOLDER --extra name:s:Demo",
    "favorite.delete": "ITEM_ID",
    "favorite.move": "ITEM_ID --extra parent_id:s:FOLDER_ID",
    "playlist.create": '"Demo playlist"',
    "playlist.add": "--extra id:s:PLAYLIST_ID --extra path:s:/storage/emulated/0/Movies/example.mp4",
    "playlist.items": "PLAYLIST_ID",
    "playlist.delete": "PLAYLIST_ID",
    "history.remove": "example.mp4",
    "media.open": "example.mp4",
    "media.move_to_recycle_bin": "example.mp4",
    "media.move_to_folder": "example.mp4 --extra target_folder:s:/storage/emulated/0/Movies",
    "media.move_folder_to_folder": "/storage/emulated/0/Download/Movies --extra target_folder:s:/storage/emulated/0/Movies",
    "media.restore_from_recycle_bin": "example.mp4",
    "media.delete_permanently": "example.mp4",
    "media.scan_path": "/storage/emulated/0/Movies",
    "media.status": "example.mp4",
    "player.seek_to": "15s",
    "player.seek_by": "--value=-5s",
    "player.stress_seek": "10 --extra interval_ms:s:120",
    "player.long_press_speed": "2.0 --extra duration_ms:s:1500",
    "player.shuffle": "--enabled true",
    "player.loop": "one",
    "player.chapter.seek": "0",
    "player.mark.seek": "MARK_ID",
    "player.mark.delete": "MARK_ID",
    "player.stress_pan_zoom": "10 --extra interval_ms:s:120",
    "player.panel_resize": "default",
    "player.panel_move": "center",
    "screenshot.take": "--name home",
}

VALUE_HELP = {
    "player.seek_to": "Time: milliseconds, 1500ms, 15s or 2m; clamped to the media duration.",
    "player.seek_by": "Signed time offset; use --value=-5s for a negative value with a unit.",
    "player.long_press_speed": "Speed: 0.2..4.0. Extra duration_ms: positive milliseconds, recommended <=2500.",
    "player.loop": "Mode: off, one or all; omitted means cycle.",
    "player.shuffle": "--enabled true|false; omitted means toggle.",
    "player.panel_resize": "Target: max, min, default, height_max or width_max.",
    "player.panel_move": "Target: center or default.",
    "player.chapter.seek": "Target: zero-based chapter index.",
    "cloud.server.add": "Extras: protocol (FTP|WEBDAV|SMB), host required; name, port, path, username, password and proxy_* optional.",
    "cloud.server.update": "Target or id extra: server ID. Optional extras: name, protocol, host, port, path, username, password, proxy_enabled, proxy_host, proxy_port.",
    "cloud.media.list": "Extra server_id or id required; path is a directory (default: server root).",
    "cloud.media.open": "Extra server_id or id required; path is a file, directory_path supplies context for name/index selection.",
    "favorite.add": "Extra type: FAVORITE_FOLDER, LOCAL_VIDEO, LOCAL_FOLDER, REMOTE_SERVER_ROOT, REMOTE_DIRECTORY or REMOTE_FILE. Extras: name, path, server_id, parent_id.",
    "playlist.add": "Extra id: playlist ID; type: video|folder (default video); select with value, name or path.",
    "settings.set": "--enabled true|false for switches; --value for strings, enums and numbers. Supported targets and ranges are in guides/settings.md.",
    "settings.toggle": "Target: a Boolean settings key; enum and numeric preferences require settings.set.",
    "settings.action": "Target: a maintenance action listed in guides/settings.md; named presets use --value NAME.",
    "quick_settings.set": "Targets: view_mode (VIDEOS|FOLDERS|FOLDER_TREE), layout_mode (LIST|GRID), layout_scale (0.75..1.5), sort_by (TITLE|LENGTH|DATE|SIZE|PATH), sort_order (ASCENDING|DESCENDING), field.* and independent. Use extra directory for overrides.",
    "cloud.quick_settings.set": "Extra server_id or id required. Targets: layout_mode (LIST|GRID), layout_scale (0.75..1.5), sort_by (TITLE|SIZE|PATH), sort_order (ASCENDING|DESCENDING), field.* and independent. Use extra directory for overrides.",
}


@dataclass(frozen=True)
class Command:
    name: str
    summary: str
    handler: str
    has_device: bool = True

    def configure(self, parser: argparse.ArgumentParser) -> None:
        if self.has_device:
            parser.add_argument("-s", "--device", dest="command_device_ids", action="append", help="Confirmed adb serial; shared by all commands in a chain")
        if self.name == "screenshot.take":
            parser.add_argument("--name", default="screen", help="Filename label; a unique timestamp is always added")
            parser.add_argument("--display-id", help="SurfaceFlinger display ID; default is the first reported display")
            return
        if self.name not in PROVIDER_COMMANDS:
            return
        target_options = {"help": "Provider target (arg): setting key, media path, item ID or action value"}
        if self.name == "page.open":
            target_options.update(choices=PAGES, help="Application page ID; returns navigation acceptance")
        elif self.name not in ("settings.set", "settings.toggle", "settings.action", "quick_settings.set", "cloud.quick_settings.set"):
            target_options["nargs"] = "?"
        parser.add_argument("target", **target_options)
        parser.add_argument("--value", help="String value; use --extra value:i:N for an explicit integer")
        parser.add_argument("--enabled", type=boolean_value, metavar="true|false", help="Explicit Boolean preference or action state")
        parser.add_argument("--extra", action="append", type=extra_value, default=[], metavar="KEY:TYPE:VALUE", help="Typed provider extra; repeatable. Types: s=string, b=Boolean, i=int, l=long, f=float")

    def details(self) -> str:
        if self.name not in PROVIDER_COMMANDS:
            return self.summary
        guide = "quick_settings.md" if ".quick_settings." in self.name else GUIDES[self.name.split(".", 1)[0]]
        values = VALUE_HELP.get(self.name, "The positional target maps to Provider arg; --extra passes additional typed fields.")
        return self.summary + f".\n{values}\nParameter values and result fields: guides/{guide}.\nBusiness actions use the existing Debug provider, not simulated UI input."

    def examples(self) -> str:
        prefix = "python .codex/skills/app-debug/scripts/run.py"
        arguments = EXAMPLE_ARGUMENTS.get(self.name, "")
        example = " ".join(part for part in (prefix, self.name, arguments, "--device SERIAL" if self.has_device else "") if part)
        return "Examples (replace SERIAL and resource IDs with actual values):\n  " + example


COMMANDS = tuple(Command(name, summary, "app_debug.client:provider_command") for name, summary in PROVIDER_COMMANDS.items()) + (
    Command("device.list", "List connected Android devices", "app_debug.client:list_devices", has_device=False),
    Command("device.wake", "Wake the device and dismiss an unsecured keyguard", "app_debug.client:wake_device"),
    Command("device.sleep", "Put the device to sleep", "app_debug.client:sleep_device"),
    Command("screenshot.take", "Save a full-resolution device screenshot as PNG", "app_debug.client:take_screenshot"),
)
