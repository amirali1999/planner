#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="${0:A:h}"
PROJECT_ROOT="${SCRIPT_DIR:h:h}"
RUNTIME_DIR="$PROJECT_ROOT/.runtime"
AGENTS_DIR="$HOME/Library/LaunchAgents"
LOG_DIR="$HOME/Library/Logs/Planner"
DOMAIN="gui/$(id -u)"

JAVA_BIN="$(command -v java || true)"
NODE_BIN="$(command -v node || true)"
NPM_BIN="$(command -v npm || true)"
if [[ -z "$JAVA_BIN" || -z "$NODE_BIN" || -z "$NPM_BIN" ]]; then
  osascript -e 'display alert "پیش‌نیاز Planner پیدا نشد" message "Java 21، Node.js و npm باید نصب باشند." as critical'
  exit 1
fi
if ! "$JAVA_BIN" -version 2>&1 | head -n 1 | grep -q 'version "21'; then
  osascript -e 'display alert "نسخه Java پشتیبانی نمی‌شود" message "Planner به Java 21 نیاز دارد." as critical'
  exit 1
fi

mkdir -p "$RUNTIME_DIR" "$PROJECT_ROOT/data" "$AGENTS_DIR" "$LOG_DIR"

echo "Building backend…"
(cd "$PROJECT_ROOT/backend" && ./mvnw -o -DskipTests package)
cp "$PROJECT_ROOT/backend/target/planner-backend-0.0.1-SNAPSHOT.jar" "$RUNTIME_DIR/planner.jar.new"

echo "Building frontend…"
(cd "$PROJECT_ROOT/frontend" && "$NPM_BIN" run build)

mv -f "$RUNTIME_DIR/planner.jar.new" "$RUNTIME_DIR/planner.jar"

for label in com.lifeplanner.frontend com.lifeplanner.backend; do
  if launchctl print "$DOMAIN/$label" >/dev/null 2>&1; then
    launchctl bootout "$DOMAIN" "$AGENTS_DIR/$label.plist" || true
  fi
done

"$NODE_BIN" "$SCRIPT_DIR/generate-plists.mjs" "$PROJECT_ROOT" "$JAVA_BIN" "$NODE_BIN" "$HOME"
/usr/bin/plutil -convert xml1 "$AGENTS_DIR/com.lifeplanner.backend.plist"
/usr/bin/plutil -convert xml1 "$AGENTS_DIR/com.lifeplanner.frontend.plist"
/usr/bin/plutil -lint "$AGENTS_DIR/com.lifeplanner.backend.plist" "$AGENTS_DIR/com.lifeplanner.frontend.plist"

"$SCRIPT_DIR/start.command"
osascript -e 'display notification "از این پس Planner هنگام ورود به macOS خودکار اجرا می‌شود." with title "Planner نصب شد"'
