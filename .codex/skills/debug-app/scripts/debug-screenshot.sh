#!/usr/bin/env bash
set -euo pipefail

DEVICE_ID=""
NAME="screen"

while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--serial)
            DEVICE_ID="$2"
            shift 2
            ;;
        -n|--name)
            NAME="$2"
            shift 2
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 2
            ;;
    esac
done

ADB=(adb)
if [[ -n "$DEVICE_ID" ]]; then
    ADB+=("-s" "$DEVICE_ID")
fi

OUTPUT_DIR="build/screenshots"
mkdir -p "$OUTPUT_DIR"

DISPLAY_ID="$("${ADB[@]}" shell dumpsys SurfaceFlinger --display-id | tr -d '\r' | awk '/^Display [0-9-]+/ {print $2; exit}')"
if [[ -z "$DISPLAY_ID" ]]; then
    DISPLAY_ID="$("${ADB[@]}" shell dumpsys SurfaceFlinger --display-id | tr -d '\r' | awk -F'id=' '/Display/ {print $2; exit}' | awk '{print $1}')"
fi
if [[ -z "$DISPLAY_ID" ]]; then
    DISPLAY_ID="$("${ADB[@]}" shell dumpsys SurfaceFlinger --display-id | tr -d '\r' | awk '/^[[:space:]]*[0-9-]+$/ {print $1; exit}')"
fi
if [[ -z "$DISPLAY_ID" ]]; then
    echo "Cannot resolve display id" >&2
    exit 1
fi

OUTPUT_FILE="$OUTPUT_DIR/${NAME}.png"
MSYS_NO_PATHCONV=1 "${ADB[@]}" exec-out screencap -d "$DISPLAY_ID" -p > "$OUTPUT_FILE"
printf '%s\n' "$OUTPUT_FILE"
