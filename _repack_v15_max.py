# -*- coding: utf-8 -*-
"""Aggressive repack of v15: max deflate everything except keep .so stored for install safety.
Also produce experimental all-deflate (including .so) for size floor demo.
"""
from __future__ import annotations

import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

SRC = Path(r"E:/Downloads/Only-Player-ui-fix-v15-arm64-1.0.148.apk")
OUT_SAFE = Path(r"E:/Downloads/Only-Player-ui-fix-v15-arm64-1.0.148-compressed.apk")
OUT_EXP = Path(r"E:/Downloads/Only-Player-ui-fix-v15-arm64-1.0.148-compressed-experimental.apk")
BUILD_TOOLS = Path(r"E:/Android/Sdk/build-tools/37.0.0")
ZIPALIGN = BUILD_TOOLS / "zipalign.exe"
APKSIGNER = BUILD_TOOLS / "apksigner.bat"
KEYSTORE = Path(r"C:/Users/hm823/.android/debug.keystore")


def run(cmd: list[str], shell: bool = False) -> None:
    print("run:", " ".join(str(c) for c in cmd))
    result = subprocess.run(
        subprocess.list2cmdline([str(c) for c in cmd]) if shell else [str(c) for c in cmd],
        capture_output=True,
        text=True,
        shell=shell,
    )
    if result.stdout.strip():
        print(result.stdout)
    if result.stderr.strip():
        print(result.stderr)
    if result.returncode != 0:
        raise SystemExit(f"failed {result.returncode}: {cmd[0]}")


def repack(store_so: bool, out_path: Path) -> None:
    work = Path(tempfile.mkdtemp(prefix="v15_repack2_"))
    unsigned = work / "unsigned.apk"
    aligned = work / "aligned.apk"
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
                    continue
                data = zin.read(info.filename)
                zi = zipfile.ZipInfo(filename=name, date_time=info.date_time)
                zi.external_attr = info.external_attr
                is_so = name.lower().endswith(".so")
                is_arsc = name == "resources.arsc" or name.lower().endswith(".arsc")
                if store_so and (is_so or is_arsc):
                    zi.compress_type = zipfile.ZIP_STORED
                    zout.writestr(zi, data)
                else:
                    zi.compress_type = zipfile.ZIP_DEFLATED
                    zout.writestr(zi, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)

        run([str(ZIPALIGN), "-f", "-p", "4", str(unsigned), str(aligned)])
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
                str(out_path),
                str(aligned),
            ],
            shell=True,
        )
        print(f"OK {out_path.name}: {out_path.stat().st_size / (1024 * 1024):.1f} MB")
        run([str(APKSIGNER), "verify", str(out_path)], shell=True)
    finally:
        shutil.rmtree(work, ignore_errors=True)


def main() -> None:
    print(f"src: {SRC.stat().st_size / (1024 * 1024):.1f} MB")
    repack(store_so=True, out_path=OUT_SAFE)
    # experimental floor (so also deflated) — may install poorly on some devices
    repack(store_so=False, out_path=OUT_EXP)
    print("summary:")
    print(f"  original : {SRC.stat().st_size / (1024 * 1024):.1f} MB")
    print(f"  safe     : {OUT_SAFE.stat().st_size / (1024 * 1024):.1f} MB")
    print(f"  experiment:{OUT_EXP.stat().st_size / (1024 * 1024):.1f} MB")


if __name__ == "__main__":
    main()
