#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
./gradlew :app:installDebug
adb shell am start -n com.jarvis/.MainActivity
