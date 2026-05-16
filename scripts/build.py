#!/usr/bin/env python3
import os
import sys

os.environ["PYTHONDONTWRITEBYTECODE"] = "1"
sys.dont_write_bytecode = True

import argparse
import html
import re
import shutil
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path

SUPPORTED_ABIS = ("arm64-v8a", "x86_64")
APK_OUTPUT_DIR = Path("build/apk")


@dataclass
class AppInfo:
    name: str
    version_name: str
    version_code: int


class Console:
    def __init__(self, verbose: bool) -> None:
        self.verbose = verbose

    def info(self, message: str) -> None:
        print(f"[info] {message}")

    def ok(self, message: str) -> None:
        print(f"[ ok ] {message}")

    def step(self, index: int, total: int, message: str) -> None:
        print(f"[{index}/{total}] {message}")


def fail(message: str) -> None:
    raise SystemExit(f"error: {message}")


def shell_quote_pwsh(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def build_command(executable: str | Path, args: list[str]) -> list[str]:
    executable_text = str(executable)
    if os.name == "nt" and executable_text.lower().endswith((".bat", ".cmd")):
        command_line = "& " + shell_quote_pwsh(executable_text)
        for arg in args:
            command_line += " " + shell_quote_pwsh(arg)
        return ["pwsh", "-NoProfile", "-Command", command_line]
    return [executable_text, *args]


def run_process(
    console: Console,
    executable: str | Path,
    args: list[str],
    cwd: Path | None = None,
) -> subprocess.CompletedProcess[str]:
    cmd = build_command(executable, args)
    if console.verbose:
        work_dir = f" (wd: {cwd})" if cwd else ""
        console.info(f"run{work_dir}: {' '.join(cmd)}")
    result = subprocess.run(
        cmd,
        cwd=cwd,
        env={**os.environ, "PYTHONUTF8": "1"},
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=True,
    )
    if console.verbose or result.returncode != 0:
        if result.stdout:
            print(result.stdout, end="")
        if result.stderr:
            print(result.stderr, end="", file=sys.stderr)
    return result


def resolve_project_root() -> Path:
    current = Path.cwd().resolve()
    for candidate in (current, *current.parents):
        if (candidate / "settings.gradle.kts").is_file() and (candidate / "app" / "build.gradle.kts").is_file():
            return candidate
    fail("project root not found; run from project directory")


def read_app_info(project_root: Path) -> AppInfo:
    build_file = project_root / "app" / "build.gradle.kts"
    content = build_file.read_text(encoding="utf-8")
    version_name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    version_code_match = re.search(r"versionCode\s*=\s*(\d+)", content)
    if not version_name_match:
        fail("versionName missing in app/build.gradle.kts")
    if not version_code_match:
        fail("versionCode missing in app/build.gradle.kts")
    return AppInfo(read_app_name(project_root), version_name_match.group(1), int(version_code_match.group(1)))


def read_app_name(project_root: Path) -> str:
    manifest = project_root / "app" / "src" / "main" / "AndroidManifest.xml"
    label_match = re.search(r'android:label="([^"]+)"', manifest.read_text(encoding="utf-8"))
    if not label_match:
        fail("android:label missing in app/src/main/AndroidManifest.xml")
    label = label_match.group(1)
    if not label.startswith("@string/"):
        return sanitize_file_part(label)
    resource_name = label.removeprefix("@string/")
    for strings_file in sorted(project_root.glob("**/src/main/res/values/strings.xml")):
        content = strings_file.read_text(encoding="utf-8")
        string_match = re.search(rf'<string\s+name="{re.escape(resource_name)}"[^>]*>(.*?)</string>', content)
        if string_match:
            return sanitize_file_part(html.unescape(string_match.group(1).strip()))
    fail(f"string resource not found: {resource_name}")


def sanitize_file_part(value: str) -> str:
    normalized = re.sub(r"\s+", "-", value.strip())
    sanitized = re.sub(r"[^A-Za-z0-9._-]+", "-", normalized)
    sanitized = sanitized.strip("-._")
    if not sanitized:
        fail("app name is empty after sanitizing")
    return sanitized


def resolve_abis(abi: str | None) -> list[str]:
    if not abi:
        return list(SUPPORTED_ABIS)
    if abi not in SUPPORTED_ABIS:
        fail(f"unsupported ABI: {abi} (supported: {', '.join(SUPPORTED_ABIS)})")
    return [abi]


def gradlew_path(project_root: Path) -> Path:
    return project_root / ("gradlew.bat" if os.name == "nt" else "gradlew")


def build_type_name(build_type: str) -> str:
    return "debug" if build_type == "debug" else ""


def assemble_task(build_type: str) -> str:
    return "assemble" + "".join(part.capitalize() for part in build_type.split("-"))


def apk_source_names(abi: str, build_type: str) -> list[str]:
    return [
        f"app-{abi}-{build_type}.apk",
        f"app-{abi}-{build_type}-unsigned.apk",
    ]


def apk_target_name(app_info: AppInfo, abi: str, build_type: str) -> str:
    parts = [app_info.name]
    type_name = build_type_name(build_type)
    if type_name:
        parts.append(type_name)
    parts.extend([abi, app_info.version_name])
    return "-".join(parts) + ".apk"


def clean_output_dir(project_root: Path) -> None:
    output_dir = project_root / APK_OUTPUT_DIR
    if not output_dir.exists():
        return
    for path in output_dir.iterdir():
        if path.is_file() and path.suffix == ".apk":
            path.unlink()


def collect_apks(console: Console, project_root: Path, app_info: AppInfo, abis: list[str], build_type: str) -> None:
    source_dir = project_root / "app" / "build" / "outputs" / "apk" / build_type
    output_dir = project_root / APK_OUTPUT_DIR
    output_dir.mkdir(parents=True, exist_ok=True)
    for abi in abis:
        source = next((source_dir / name for name in apk_source_names(abi, build_type) if (source_dir / name).exists()), None)
        if not source:
            expected = ", ".join(apk_source_names(abi, build_type))
            fail(f"APK not found in {source_dir.relative_to(project_root)}: {expected}")
        target = output_dir / apk_target_name(app_info, abi, build_type)
        if target.exists():
            target.unlink()
        shutil.copy2(source, target)
        console.ok(f"apk={target.relative_to(project_root)} size_mb={target.stat().st_size / 1024 / 1024:.2f}")


def signing_args_from_env() -> list[str]:
    pairs = (
        ("ANDROID_KEYSTORE_PATH", "android.injected.signing.store.file"),
        ("ANDROID_KEYSTORE_PASSWORD", "android.injected.signing.store.password"),
        ("ANDROID_KEY_ALIAS", "android.injected.signing.key.alias"),
        ("ANDROID_KEY_PASSWORD", "android.injected.signing.key.password"),
        ("KEYSTORE_PATH", "android.injected.signing.store.file"),
        ("KEYSTORE_PASSWORD", "android.injected.signing.store.password"),
        ("KEY_ALIAS", "android.injected.signing.key.alias"),
        ("KEY_PASSWORD", "android.injected.signing.key.password"),
    )
    return [f"-P{property_name}={os.environ[key]}" for key, property_name in pairs if os.environ.get(key)]


def build_apk(args: argparse.Namespace) -> None:
    start = time.monotonic()
    console = Console(args.verbose)
    project_root = resolve_project_root()
    app_info = read_app_info(project_root)
    abis = resolve_abis(args.abi)
    build_type = args.build_type

    console.info(f"app={app_info.name} version={app_info.version_name} code={app_info.version_code} abi={', '.join(abis)} type={build_type}")
    if args.clean:
        clean_output_dir(project_root)
        console.ok("output dir cleaned")

    console.step(1, 2, "Build APK")
    gradle_args = [assemble_task(build_type), f"-PabiFilter={','.join(abis)}", *signing_args_from_env()]
    result = run_process(console, gradlew_path(project_root), gradle_args, cwd=project_root)
    if result.returncode != 0:
        fail("apk build failed")
    console.ok("apk build done")

    console.step(2, 2, "Collect APK")
    collect_apks(console, project_root, app_info, abis, build_type)
    console.ok(f"done ({time.monotonic() - start:.2f}s)")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="one-player-build",
        description="Build One Player APKs and copy them into build/apk with release-ready names.",
        epilog="Example: python scripts/build.py build-apk --abi arm64-v8a",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    apk = sub.add_parser(
        "build-apk",
        help="Build APK files",
        description="Build APK files with Gradle, then copy outputs to build/apk.",
        epilog="Name format: <app>-debug-<abi>-<version>.apk for debug, otherwise <app>-<abi>-<version>.apk.",
    )
    apk.add_argument("-v", "--verbose", action="store_true", help="Print Gradle output and command details")
    apk.add_argument("--abi", choices=SUPPORTED_ABIS, help="Build only one ABI; default builds all supported ABIs")
    apk.add_argument("--build-type", default="release", choices=("debug", "release", "release-with-debug-signing"), help="Gradle build type; default: release")
    apk.add_argument("--clean", action="store_true", help="Remove old APK files from build/apk before building")
    apk.set_defaults(func=build_apk)
    return parser


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    args = build_parser().parse_args()
    args.func(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
