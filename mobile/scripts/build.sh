#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
./gradlew :app:assembleDebug

echo "APK: app/build/outputs/apk/debug/app-debug.apk"
