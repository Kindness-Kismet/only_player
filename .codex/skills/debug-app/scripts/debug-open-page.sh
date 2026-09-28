#!/usr/bin/env bash
set -euo pipefail

DEVICE_ID=""
PAGE_ID=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--serial)
            DEVICE_ID="$2"
            shift 2
            ;;
        -p|--page)
            PAGE_ID="$2"
            shift 2
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 2
            ;;
    esac
done

if [[ -z "$PAGE_ID" ]]; then
    echo "Missing --page <page_id>" >&2
    exit 2
fi

ADB=(adb)
if [[ -n "$DEVICE_ID" ]]; then
    ADB+=("-s" "$DEVICE_ID")
fi

"${ADB[@]}" shell input keyevent KEYCODE_WAKEUP
"${ADB[@]}" shell wm dismiss-keyguard
"${ADB[@]}" shell am start -n one.only.player.debug/one.only.player.MainActivity >/dev/null
RESULT="$(${ADB[@]} shell content call \
    --uri content://one.only.player.debug.commands \
    --method page.open \
    --arg "$PAGE_ID")"

printf '%s\n' "$RESULT"
if [[ "$RESULT" != *"ok=true"* ]]; then
    exit 1
fi
