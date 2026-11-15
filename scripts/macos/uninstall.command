#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="${0:A:h}"
PROJECT_ROOT="${SCRIPT_DIR:h:h}"
AGENTS_DIR="$HOME/Library/LaunchAgents"

"$SCRIPT_DIR/stop.command" || true
rm -f "$AGENTS_DIR/com.lifeplanner.backend.plist" "$AGENTS_DIR/com.lifeplanner.frontend.plist"

osascript -e 'display alert "Planner از اجرای خودکار خارج شد" message "دیتابیس و اطلاعات شما حذف نشده‌اند. فایل‌های build در .runtime نیز برای نصب دوباره باقی مانده‌اند."'
