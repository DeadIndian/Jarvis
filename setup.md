# Setup Guide

## Prerequisites

1. OS

- Linux, macOS, or Windows.

2. Java

- JDK 21 installed and available in PATH.
- Verify:
  - java -version

3. Android toolchain

- Android Studio (recommended) or Android SDK command-line tools.
- Android SDK platform for API 35.
- Build-tools and platform-tools installed.
- adb available in PATH.
- Verify:
  - adb version

4. Device or emulator

- Connect a physical Android device with USB debugging enabled, or start an emulator.
- Verify:
  - adb devices -l

## Project bootstrap

From the repository root:

1. Make wrapper executable (Linux/macOS)

- chmod +x ./gradlew

2. Verify Gradle wrapper

- ./gradlew --version

## Build

1. Build all modules

- ./gradlew build

2. Build debug APK (MVP app)

- ./gradlew :app:assembleDebug

3. APK output

- app/build/outputs/apk/debug/app-debug.apk

## Install and run

1. Install APK

- adb install -r app/build/outputs/apk/debug/app-debug.apk

2. Launch app

- adb shell am start -n com.jarvis.app/.MainActivity

## Optional: clean rebuild

- ./gradlew clean :app:assembleDebug

## Troubleshooting

1. No device detected

- Run adb devices -l
- Reconnect cable or restart emulator.
- Accept USB debugging prompt on device.

2. Java/toolchain mismatch

- Ensure JDK 21 is active.
- Check java -version output.

3. Build cache issues

- Run ./gradlew clean
- Retry with --stacktrace for details.

4. Permission-related runtime issues

- Grant microphone permission when prompted.
- Reopen app after granting permissions.
