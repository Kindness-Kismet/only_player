import sys

sys.dont_write_bytecode = True

import os

os.environ["PYTHONDONTWRITEBYTECODE"] = "1"

import argparse
import contextlib
import importlib
from importlib.machinery import ModuleSpec
import json
from pathlib import Path
from types import ModuleType

SCRIPT_DIRECTORY = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIRECTORY.parents[3]

# 同层级的主源和镜像共用入口，以脚本位置定位工程。
debug_package = ModuleType("app_debug")
debug_package.__path__ = [str(SCRIPT_DIRECTORY)]
debug_package.__package__ = "app_debug"
debug_package.__spec__ = ModuleSpec("app_debug", loader=None, is_package=True)
debug_package.PROJECT_ROOT = PROJECT_ROOT
sys.modules["app_debug"] = debug_package

from app_debug.commands import COMMANDS, CommandFailure


class DebugArgumentParser(argparse.ArgumentParser):
    def error(self, message: str) -> None:
        self.print_usage(sys.stderr)
        print("error: " + message, file=sys.stderr)
        print(json.dumps({"ok": False, "error": message, "exit_code": 2}, ensure_ascii=False))
        raise SystemExit(2)


def create_parser() -> argparse.ArgumentParser:
    parser = DebugArgumentParser(
        prog="run.py", allow_abbrev=False,
        description="Run Only Player Debug commands on an explicitly selected Android device.",
        epilog=(
            "Chain: COMMAND [ARGS] + COMMAND [ARGS] ...\n"
            "All segments are parsed first; execution stops on failure.\n"
            "Use --device SERIAL once per chain; conflicting serials are rejected.\n"
            "Operations emit one JSON document on stdout; diagnostics use stderr.\n"
            "Use COMMAND --help for parameters and examples."
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument("-s", "--device", dest="chain_device_ids", action="append", help="Confirmed adb device serial")
    children = parser.add_subparsers(title="Commands", dest="command", metavar="COMMAND", required=True)
    for command in COMMANDS:
        child = children.add_parser(
            command.name, allow_abbrev=False, help=command.summary,
            description=command.details(), epilog=command.examples(),
            formatter_class=argparse.RawTextHelpFormatter,
        )
        command.configure(child)
        child.set_defaults(_command_spec=command)
    return parser


def parse_chain(parser: argparse.ArgumentParser, arguments: list[str]) -> list[argparse.Namespace]:
    segments: list[list[str]] = [[]]
    for token in arguments:
        if token == "+":
            segments.append([])
        else:
            segments[-1].append(token)
    if any(not segment for segment in segments):
        parser.error("Empty command segment; separate commands with a single '+'")
    parsed = [parser.parse_args(segment) for segment in segments]
    serials = {
        serial
        for args in parsed
        for serial in (args.chain_device_ids or []) + (getattr(args, "command_device_ids", None) or [])
    }
    if len(serials) > 1:
        parser.error("All commands in a chain must use the same device serial")
    serial = next(iter(serials), None)
    for args in parsed:
        if args._command_spec.has_device and not serial:
            parser.error("Select the confirmed device with --device SERIAL; use device.list to inspect connections")
        args.device_id = serial
        extras = getattr(args, "extra", [])
        keys = [entry[0] for entry in extras]
        keys += [key for key in ("value", "enabled") if getattr(args, key, None) is not None]
        if len(keys) != len(set(keys)):
            parser.error("Duplicate extra keys in command " + args.command)
    return parsed


def emit_failure(args: argparse.Namespace, error: CommandFailure, steps: list[dict] | None) -> int:
    code = error.exit_code or 1
    print("error: " + str(error), file=sys.stderr)
    response = {"ok": False, "command": args.command, "error": str(error), "exit_code": code}
    if error.result is not None:
        response["result"] = error.result
    if steps is not None:
        index = len(steps) + 1
        steps.append({"index": index, **response})
        response = {**response, "failed_index": index, "steps": steps}
    print(json.dumps(response, ensure_ascii=False))
    return code


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    parser = create_parser()
    if not sys.argv[1:]:
        parser.print_help()
        return 0
    parsed = parse_chain(parser, sys.argv[1:])
    steps = [] if len(parsed) > 1 else None
    for index, args in enumerate(parsed, start=1):
        try:
            with contextlib.redirect_stdout(sys.stderr):
                module, function = args._command_spec.handler.split(":", 1)
                result = getattr(importlib.import_module(module), function)(args)
        except CommandFailure as error:
            return emit_failure(args, error, steps)
        except OSError as error:
            return emit_failure(args, CommandFailure(f"File or process operation failed: {error}"), steps)
        except KeyboardInterrupt:
            return emit_failure(args, CommandFailure("Interrupted", 130), steps)
        response = {"ok": True, "command": args.command, "result": result}
        if steps is not None:
            steps.append({"index": index, **response})
    print(json.dumps(response if steps is None else {"ok": True, "steps": steps}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
