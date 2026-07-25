# -*- coding: utf-8 -*-
"""Repack v15 debug APK with stronger DEFLATE; keep .so/resources.arsc stored; resign."""
from __future__ import annotations

import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

SRC = Path(r"E:/Downloads/Only-Player-ui-fix-v15-arm64-1.0.148.apk")
OUT = Path(r"E:/Downloads/Only-Player-ui-fix-v15-arm64-1.0.148-compressed.apk")
OUT2 = Path(r"E:/Downloads/only_player_src/build/apk/Only-Player-ui-fix-v15-arm64-1.0.148-compressed.apk")
BUILD_TOOLS = Path(r"E:/Android/Sdk/build-tools/37.0.0")
ZIPALIGN = BUILD_TOOLS / "zipalign.exe"
APKSIGNER = BUILD_TOOLS / "apksigner.bat"
KEYSTORE = Path(r"C:/Users/hm823/.android/debug.keystore")


def should_store(name: str) -> bool:
    n = name.replace("\\", "/")
    lower = n.lower()
    if n == "resources.arsc" or lower.endswith(".arsc"):
        return True
    if lower.endswith(".so"):
        return True
    return False


def run(cmd: list[str], shell: bool = False) -> None:
    print("run:", " ".join(str(c) for c in cmd))
    result = subprocess.run(
        cmd if not shell else subprocess.list2cmdline([str(c) for c in cmd]),
        capture_output=True,
        text=True,
        shell=shell,
    )
    if result.stdout:
        print(result.stdout)
    if result.stderr:
        print(result.stderr)
    if result.returncode != 0:
        raise SystemExit(f"command failed ({result.returncode}): {cmd[0]}")


def main() -> None:
    if not SRC.is_file():
        raise SystemExit(f"missing source apk: {SRC}")
    print(f"src size={SRC.stat().st_size}")

    workdir = Path(tempfile.mkdtemp(prefix="v15_repack_"))
    unsigned = workdir / "unsigned.apk"
    aligned = workdir / "aligned.apk"

    try:
        with zipfile.ZipFile(SRC, "r") as zin, zipfile.ZipFile(
            unsigned,
            "w",
            compression=zipfile.ZIP_DEFLATED,
            compresslevel=9,
            allowZip64=True,
        ) as zout:
            for info in zin.infolist():
                name = info.filename.replace("\\", "/")
                if name.startswith("META-INF/"):
                    # drop old signature blocks; will resign
                    continue
                data = zin.read(info.filename)
                zi = zipfile.ZipInfo(filename=name, date_time=info.date_time)
                zi.external_attr = info.external_attr
                zi.create_system = info.create_system
                if should_store(name):
                    zi.compress_type = zipfile.ZIP_STORED
                    zout.writestr(zi, data)
                else:
                    zi.compress_type = zipfile.ZIP_DEFLATED
                    zout.writestr(zi, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)

        print(f"unsigned size={unsigned.stat().st_size}")

        run([str(ZIPALIGN), "-f", "-p", "4", str(unsigned), str(aligned)])
        print(f"aligned size={aligned.stat().st_size}")

        # apksigner.bat needs shell on Windows
        run(
            [
                str(APKSIGNER),
                "sign",
                "--ks",
                str(KEYSTORE),
                "--ks-pass",
                "pass:android",
                "--key-pass",
                "pass:android",
                "--ks-key-alias",
                "androiddebugkey",
                "--v1-signing-enabled",
                "true",
                "--v2-signing-enabled",
                "true",
                "--out",
                str(OUT),
                str(aligned),
            ],
            shell=True,
        )
        print(f"final size={OUT.stat().st_size} path={OUT}")

        run([str(APKSIGNER), "verify", "--verbose", str(OUT)], shell=True)

        OUT2.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(OUT, OUT2)
        print(f"copied {OUT2}")
        print(
            "compression ratio vs original: "
            f"{OUT.stat().st_size / SRC.stat().st_size:.3f} "
            f"({SRC.stat().st_size // (1024 * 1024)}MB -> {OUT.stat().st_size // (1024 * 1024)}MB)"
        )
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


if __name__ == "__main__":
    main()
