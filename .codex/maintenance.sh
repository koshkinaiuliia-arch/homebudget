#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
if [[ ! -f /etc/profile.d/homebudget-android.sh ]]; then
  echo 'Run bash .codex/setup.sh first.' >&2
  exit 1
fi
source /etc/profile.d/homebudget-android.sh
chmod +x gradlew
./gradlew assembleDebug --no-daemon --console=plain
