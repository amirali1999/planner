#!/bin/zsh
set -euo pipefail

DOMAIN="gui/$(id -u)"
AGENTS_DIR="$HOME/Library/LaunchAgents"

stop_one() {
  local label="$1"
  local plist="$2"
  if launchctl print "$DOMAIN/$label" >/dev/null 2>&1; then
    launchctl bootout "$DOMAIN" "$plist"
  fi
}

stop_one "com.lifeplanner.frontend" "$AGENTS_DIR/com.lifeplanner.frontend.plist"
stop_one "com.lifeplanner.backend" "$AGENTS_DIR/com.lifeplanner.backend.plist"

osascript -e 'display notification "سرویس‌های Planner متوقف شدند." with title "Planner"'
