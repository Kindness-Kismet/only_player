from datetime import datetime
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import uuid

from app_debug import PROJECT_ROOT
from app_debug.commands import CommandFailure

PACKAGE = "one.only.player.debug"
ACTIVITY = PACKAGE + "/one.only.player.MainActivity"
AUTHORITY = "content://" + PACKAGE + ".commands"


def find_adb() -> str:
    executable = "adb.exe" if os.name == "nt" else "adb"
    installed = shutil.which(executable)
    if installed:
        return installed
    sdk_paths = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    properties = PROJECT_ROOT / "local.properties"
    if properties.is_file():
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.strip().startswith("sdk.dir="):
                sdk_paths.append(line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\"))
    for sdk in sdk_paths:
        if sdk:
            candidate = Path(sdk) / "platform-tools" / executable
            if candidate.is_file():
                return str(candidate)
    raise CommandFailure("adb was not found; configure PATH, ANDROID_HOME, ANDROID_SDK_ROOT or local.properties")


def adb(serial: str | None, *arguments: str, is_binary: bool = False) -> str | bytes:
    command = [find_adb()]
    if serial:
        command += ["-s", serial]
    command += list(arguments)
    try:
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90, env={**os.environ, "MSYS_NO_PATHCONV": "1"})
    except subprocess.TimeoutExpired as error:
        raise CommandFailure("adb command timed out", 124) from error
    if result.stderr:
        print(result.stderr.decode("utf-8", errors="replace"), end="", file=sys.stderr)
    if result.returncode:
        if result.stdout and not is_binary:
            print(result.stdout.decode("utf-8", errors="replace"), file=sys.stderr)
        raise CommandFailure("adb returned exit code " + str(result.returncode), result.returncode)
    return result.stdout if is_binary else result.stdout.decode("utf-8", errors="replace")


def shell(serial: str, *arguments: str) -> str:
    # adb 会再次交给设备 Shell 解析，逐参数引用以保留空格、引号和特殊字符。
    return adb(serial, "shell", shlex.join(arguments))


def list_devices(args) -> dict:
    output = adb(None, "devices", "-l")
    devices = []
    for line in output.splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2:
            devices.append({"serial": fields[0], "state": fields[1], "details": " ".join(fields[2:])})
    return {"devices": devices}


def wake_device(args) -> dict:
    shell(args.device_id, "input", "keyevent", "KEYCODE_WAKEUP")
    shell(args.device_id, "wm", "dismiss-keyguard")
    return {"device_id": args.device_id, "wake_requested": True}


def sleep_device(args) -> dict:
    shell(args.device_id, "input", "keyevent", "KEYCODE_SLEEP")
    return {"device_id": args.device_id, "sleep_requested": True}


def provider_command(args) -> dict:
    if args.command == "page.open":
        output = shell(args.device_id, "am", "start", "-W", "-n", ACTIVITY)
        print(output, end="", file=sys.stderr)
        if "Error:" in output or "Exception" in output:
            raise CommandFailure("Could not start the Debug activity")
    command = ["content", "call", "--uri", AUTHORITY, "--method", args.command]
    if args.target is not None:
        command += ["--arg", args.target]
    extras = list(args.extra)
    if args.value is not None:
        extras.append(("value", "s", args.value))
    if args.enabled is not None:
        extras.append(("enabled", "b", args.enabled))
    extras.append(("response_json", "b", True))
    for key, kind, value in extras:
        text = str(value).lower() if kind == "b" else str(value)
        command += ["--extra", f"{key}:{kind}:{text}"]
    output = shell(args.device_id, *command)
    match = re.search(r"Result: Bundle\[\{response_json=(.*)\}\]\s*$", output, re.DOTALL)
    if not match:
        raise CommandFailure("Expected a JSON response from the Debug provider; install the current Debug APK and check the device connection")
    try:
        result = json.loads(match.group(1))
    except json.JSONDecodeError as error:
        raise CommandFailure("Debug provider returned malformed JSON") from error
    if not isinstance(result, dict) or not isinstance(result.get("ok"), bool):
        raise CommandFailure("Debug provider response has no Boolean ok field")
    if not result["ok"]:
        raise CommandFailure(result.get("message") or "Application command failed", result=result)
    return result


def take_screenshot(args) -> dict:
    displays = shell(args.device_id, "dumpsys", "SurfaceFlinger", "--display-id")
    display_ids = re.findall(r"^Display\s+(\d+)", displays, re.MULTILINE)
    display_id = args.display_id or next(iter(display_ids), None)
    if not display_id or display_id not in display_ids:
        raise CommandFailure("Requested display was not found in SurfaceFlinger output")
    image = adb(args.device_id, "exec-out", "screencap", "-d", display_id, "-p", is_binary=True)
    if not image.startswith(b"\x89PNG\r\n\x1a\n"):
        raise CommandFailure("Device screenshot did not return PNG data")
    label = re.sub(r"[^\w.-]", "_", args.name)[:80] or "screen"
    filename = f"{label}-{datetime.now():%Y%m%d-%H%M%S}-{uuid.uuid4().hex[:8]}.png"
    destination = PROJECT_ROOT / "build" / "screenshots" / filename
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("xb") as stream:
        stream.write(image)
    return {"path": str(destination), "display_id": display_id, "bytes": len(image), "format": "PNG"}
