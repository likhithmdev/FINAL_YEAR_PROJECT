#!/usr/bin/env bash
#
# flash-firmware.sh - compile and flash the SAPTCS firmware with arduino-cli.
#
#   bash scripts/flash-firmware.sh list
#   bash scripts/flash-firmware.sh compile all
#   bash scripts/flash-firmware.sh compile receiver
#   bash scripts/flash-firmware.sh flash receiver COM9
#   bash scripts/flash-firmware.sh monitor receiver COM9
#
# Board aliases:
#   receiver    lora_receiver_esp32         ESP32   talks to Firebase, opens the corridor
#   signal      traffic_signal_unit         ESP32   drives the physical traffic lights
#   ambulance   ambulance_unit              ESP32   the LoRa transmitter
#   controller  arduino_traffic_controller  UNO     legacy controller, NOT in the 3-board demo
#
# Set ARDUINO_CLI to override the arduino-cli binary location.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILDROOT="${LOCALAPPDATA:-$HOME/AppData/Local}/Temp/saptcs-build/arduino-build"
ALL_BOARDS="receiver signal ambulance controller"

usage() {
  cat <<'USAGE'
Compile and flash the SAPTCS firmware.

  list                          show USB serial ports (and which ones are Bluetooth)
  compile all                   compile every sketch, no board needed
  compile <board>               compile one sketch
  flash <board> <COMx>          compile + upload one board
  flash <board> auto            wait for a new port to appear, then flash it
  monitor <board> <COMx>        open the serial monitor at the board's baud rate

Boards: receiver | signal | ambulance | controller
  receiver    lora_receiver_esp32        ESP32  talks to Firebase
  signal      traffic_signal_unit        ESP32  drives the physical lights
  ambulance   ambulance_unit             ESP32  LoRa transmitter
  controller  arduino_traffic_controller UNO    legacy, not in the 3-board demo

Examples:
  bash scripts/flash-firmware.sh list
  bash scripts/flash-firmware.sh compile all
  bash scripts/flash-firmware.sh flash receiver auto
  bash scripts/flash-firmware.sh monitor receiver COM9

'auto' is the one-board-at-a-time helper: it snapshots the serial ports, waits
for one that was not there before (i.e. the board you just plugged in), and
flashes to it. Machines with Bluetooth virtual ports make the explicit COM
number ambiguous, so prefer 'auto' unless a board was already connected.
USAGE
}

# ---------------------------------------------------------------- arduino-cli
find_cli() {
  if [ -n "${ARDUINO_CLI:-}" ] && [ -x "${ARDUINO_CLI}" ]; then
    printf '%s\n' "${ARDUINO_CLI}"; return 0
  fi
  if command -v arduino-cli >/dev/null 2>&1; then
    command -v arduino-cli; return 0
  fi
  local temp_cli="${LOCALAPPDATA:-$HOME/AppData/Local}/Temp/saptcs-build/arduino/arduino-cli.exe"
  if [ -x "$temp_cli" ]; then printf '%s\n' "$temp_cli"; return 0; fi
  return 1
}

if ! CLI="$(find_cli)"; then
  echo "arduino-cli not found. Install it, or set ARDUINO_CLI=/path/to/arduino-cli." >&2
  exit 1
fi

# --------------------------------------------------------------- board table
# Echoes: <sketch folder>|<fqbn>|<monitor baud>
board_info() {
  case "$1" in
    receiver|lora_receiver_esp32)          echo "lora_receiver_esp32|esp32:esp32:esp32|115200" ;;
    signal|traffic_signal_unit)            echo "traffic_signal_unit|esp32:esp32:esp32|115200" ;;
    ambulance|ambulance_unit)              echo "ambulance_unit|esp32:esp32:esp32|115200" ;;
    controller|arduino_traffic_controller) echo "arduino_traffic_controller|arduino:avr:uno|9600" ;;
    *) return 1 ;;
  esac
}

describe_board() {
  local info
  info="$(board_info "$1")" || {
    echo "Unknown board '$1'. Expected one of: $ALL_BOARDS" >&2
    return 2
  }
  printf '%s\n' "$info"
}

# ------------------------------------------------------------------- actions
# Prints the COM ports currently present, one per line, sorted. Quiet on failure.
list_ports() {
  if command -v powershell.exe >/dev/null 2>&1; then
    powershell.exe -NoProfile -NonInteractive -Command \
      "Get-CimInstance Win32_PnPEntity | Where-Object { \$_.Name -match '\((COM\d+)\)' } | ForEach-Object { \$_.Name -replace '.*\((COM\d+)\).*','\$1' }" \
      2>/dev/null | tr -d '\r' | grep -E '^COM[0-9]+$' | sort -u
  else
    "$CLI" board list 2>/dev/null | awk '$1 ~ /^COM[0-9]+$/ {print $1}' | sort -u
  fi
  return 0
}

# Waits for a serial port that was not present at call time. The port name is the
# only thing written to stdout, so it can be captured; progress goes to stderr.
wait_for_new_port() {
  local timeout="${1:-${SAPTCS_PORT_WAIT:-120}}" elapsed=0 before after port new
  before="$(list_ports | tr '\n' ' ')"
  echo "Serial ports present now: ${before:-<none>}" >&2
  echo "Waiting up to ${timeout}s - plug the board in now." >&2
  while [ "$elapsed" -lt "$timeout" ]; do
    sleep 2
    elapsed=$((elapsed + 2))
    after="$(list_ports | tr '\n' ' ')"
    new=""
    for port in $after; do
      case " $before " in
        *" $port "*) ;;
        *) new="$port"; break ;;
      esac
    done
    if [ -n "$new" ]; then
      echo "New port appeared: $new" >&2
      printf '%s\n' "$new"
      return 0
    fi
    if [ $((elapsed % 10)) -eq 0 ]; then
      echo "  ... still waiting (${elapsed}s)" >&2
    fi
  done
  return 1
}

cmd_list() {
  echo "USB serial ports on this machine:"
  if command -v powershell.exe >/dev/null 2>&1; then
    powershell.exe -NoProfile -NonInteractive -Command \
      "Get-CimInstance Win32_PnPEntity | Where-Object { \$_.Name -match '\(COM\d+\)' } | ForEach-Object { \$_.Name }" \
      2>/dev/null | tr -d '\r' | sed 's/^/  /' || true
  else
    echo "  (powershell not available - see arduino-cli's view below)"
  fi
  echo
  echo "arduino-cli's view:"
  "$CLI" board list
  echo
  echo "Note: 'Standard Serial over Bluetooth link' entries are virtual ports that"
  echo "are always present. Your board is the port whose device name mentions"
  echo "CP210x, CH340, USB-SERIAL, or FTDI."
}

# build <board> [port] - compiles, and uploads when a port is given
build() {
  local alias="$1" port="${2:-}"
  local info sketch fqbn baud
  info="$(describe_board "$alias")" || return $?
  IFS='|' read -r sketch fqbn baud <<<"$info"

  local sketch_dir="$ROOT/firmware/$sketch"
  if [ ! -f "$sketch_dir/$sketch.ino" ]; then
    echo "Sketch not found: $sketch_dir/$sketch.ino" >&2
    return 2
  fi

  local build_path="$BUILDROOT/$sketch"
  mkdir -p "$build_path"

  local args=(compile -b "$fqbn" --build-path "$build_path")
  if [ -n "$port" ]; then
    args+=(-u -p "$port")
  fi
  args+=("$sketch_dir")

  if [ -n "$port" ]; then
    echo "== $sketch -> $port ($fqbn) =="
  else
    echo "== $sketch (compile only, $fqbn) =="
  fi
  "$CLI" "${args[@]}"
}

cmd_compile() {
  local target="${1:-all}"
  if [ "$target" = "all" ]; then
    local b
    for b in $ALL_BOARDS; do
      build "$b"
      echo
    done
    echo "All sketches compiled."
  else
    build "$target"
    echo
    echo "Compiled $target. Flash it with:  bash scripts/flash-firmware.sh flash <board> <COMx>"
  fi
}

cmd_flash() {
  local alias="${1:-}" port="${2:-}"
  if [ -z "$alias" ] || [ -z "$port" ]; then
    echo "usage: bash scripts/flash-firmware.sh flash <board> <COMx|auto>" >&2
    echo "Run 'list' first to find the port." >&2
    return 2
  fi
  if [ "$port" = "auto" ]; then
    if ! port="$(wait_for_new_port)"; then
      echo "No new serial port appeared within ${SAPTCS_PORT_WAIT:-120}s." >&2
      echo "Run 'list' to see what is connected, then pass the port explicitly." >&2
      return 1
    fi
  fi
  build "$alias" "$port"
  echo
  echo "Flashed $alias to $port."
  echo "Watch it boot:  bash scripts/flash-firmware.sh monitor $alias $port"
}

cmd_monitor() {
  local alias="${1:-}" port="${2:-}"
  if [ -z "$alias" ] || [ -z "$port" ]; then
    echo "usage: bash scripts/flash-firmware.sh monitor <board> <COMx>" >&2
    return 2
  fi
  local info sketch fqbn baud
  info="$(describe_board "$alias")" || return $?
  IFS='|' read -r sketch fqbn baud <<<"$info"
  echo "Opening $port at $baud baud. Press Ctrl+C to exit."
  echo "(Close this before flashing again - an open port blocks the upload.)"
  echo
  "$CLI" monitor -p "$port" -b "$fqbn" -c baudrate="$baud"
}

# ---------------------------------------------------------------------- main
case "${1:-}" in
  list|ports)  cmd_list ;;
  compile)     shift; cmd_compile "${1:-all}" ;;
  flash)       shift; cmd_flash "${1:-}" "${2:-}" ;;
  monitor)     shift; cmd_monitor "${1:-}" "${2:-}" ;;
  help|-h|--help|"") usage ;;
  *)
    echo "Unknown command '$1'." >&2
    echo >&2
    usage >&2
    exit 2
    ;;
esac
