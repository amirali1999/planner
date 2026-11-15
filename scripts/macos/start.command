#!/bin/zsh
set -euo pipefail

DOMAIN="gui/$(id -u)"
AGENTS_DIR="$HOME/Library/LaunchAgents"
BACKEND_LABEL="com.lifeplanner.backend"
FRONTEND_LABEL="com.lifeplanner.frontend"
BACKEND_PLIST="$AGENTS_DIR/$BACKEND_LABEL.plist"
FRONTEND_PLIST="$AGENTS_DIR/$FRONTEND_LABEL.plist"

if [[ ! -f "$BACKEND_PLIST" || ! -f "$FRONTEND_PLIST" ]]; then
  osascript -e 'display alert "Planner نصب نشده است" message "ابتدا روی Install Planner.command دوبار کلیک کنید." as critical'
  exit 1
fi

start_one() {
  local label="$1"
  local plist="$2"
  if ! launchctl print "$DOMAIN/$label" >/dev/null 2>&1; then
    launchctl bootstrap "$DOMAIN" "$plist"
  fi
}

start_one "$BACKEND_LABEL" "$BACKEND_PLIST"
start_one "$FRONTEND_LABEL" "$FRONTEND_PLIST"

for _ in {1..120}; do
  if /usr/bin/curl -fsS http://127.0.0.1:4173/ >/dev/null 2>&1 \
      && /usr/bin/curl -fsS http://127.0.0.1:18080/api/v1/health >/dev/null 2>&1; then
    open http://127.0.0.1:4173/
    exit 0
  fi
  sleep 1
done

osascript -e 'display alert "Planner اجرا نشد" message "لاگ‌ها را در ~/Library/Logs/Planner بررسی کنید." as critical'
exit 1
