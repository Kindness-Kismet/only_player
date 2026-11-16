#!/usr/bin/env python3
"""同步 Codex 技能及 Claude 镜像，保留独立修改和可恢复备份。"""

import sys

sys.dont_write_bytecode = True

import os

os.environ["PYTHONDONTWRITEBYTECODE"] = "1"

import argparse
import hashlib
import json
import shutil
import time
from pathlib import Path

SOURCE = Path(".codex/skills")
MIRROR = Path(".claude/skills")
STATE = Path("build/skills-sync")


def snapshot(directory: Path) -> dict[str, str]:
    if not directory.is_dir():
        return {}
    result = {}
    for path in sorted(directory.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"Symbolic links are not supported: {path.relative_to(directory)}")
        if path.is_file():
            result[path.relative_to(directory).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    return result


def check_skills(root: Path) -> int:
    source = snapshot(root / SOURCE)
    if not source:
        raise ValueError(f"Skill source is missing or empty: {SOURCE.as_posix()}")
    mirror = snapshot(root / MIRROR)
    differences = [name for name in sorted(source.keys() | mirror.keys()) if source.get(name) != mirror.get(name)]
    if differences:
        raise ValueError("Skill mirrors differ: " + ", ".join(differences))
    print(f"  Files          {len(source)} matching files")
    return 0


def sync_skills(root: Path) -> int:
    source = snapshot(root / SOURCE)
    if not source:
        raise ValueError(f"Skill source is missing or empty: {SOURCE.as_posix()}")
    mirror = snapshot(root / MIRROR)
    baseline_path = root / STATE / "baseline.json"
    if baseline_path.is_file():
        baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    elif mirror and mirror != source:
        raise ValueError("No sync baseline exists; reconcile the current mirrors before syncing")
    else:
        baseline = mirror

    conflicts = [
        name
        for name in sorted(baseline.keys() | mirror.keys())
        if mirror.get(name) != baseline.get(name) and mirror.get(name) != source.get(name)
    ]
    if conflicts:
        raise ValueError("Claude mirror has independent changes: " + ", ".join(conflicts))

    changed = [name for name in sorted(source.keys() | mirror.keys()) if source.get(name) != mirror.get(name)]
    backup = root / STATE / "backups" / str(time.time_ns())
    # 先核对完整计划，再写入；被覆盖或移除的镜像文件保留备份。
    for name in changed:
        target = root / MIRROR / name
        if not target.resolve().is_relative_to((root / MIRROR).resolve()):
            raise ValueError(f"Mirror path escapes its root: {name}")
    for name in changed:
        target = root / MIRROR / name
        if target.is_file():
            saved = backup / name
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(target), str(saved))
        if name in source:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(root / SOURCE / name, target)

    check_skills(root)
    baseline_path.parent.mkdir(parents=True, exist_ok=True)
    baseline_path.write_text(json.dumps(source, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"  Updated        {len(changed)} files")
    if changed and backup.exists():
        print(f"  Backup         {backup.relative_to(root).as_posix()}")
    return 0


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(
        description="Sync .codex/skills to .claude/skills without overwriting independent mirror changes.",
        formatter_class=argparse.RawTextHelpFormatter,
    )
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--sync", action="store_true", help="Sync files and save a local baseline; preserve replaced files")
    action.add_argument("--check", action="store_true", help="Compare file sets and bytes without writing or accessing the network")
    if len(sys.argv) == 1:
        parser.print_help()
        return 0
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    started = time.perf_counter()
    print("==> Sync skills" if args.sync else "==> Check skills")
    try:
        result = sync_skills(root) if args.sync else check_skills(root)
    except ValueError as error:
        print(f"  error: {error}", file=sys.stderr)
        return 1
    except OSError as error:
        print(f"  error: Skill file operation failed ({type(error).__name__}, errno={error.errno})", file=sys.stderr)
        return 1
    print(f"  · {time.perf_counter() - started:.2f}s\n")
    return result


if __name__ == "__main__":
    raise SystemExit(main())
