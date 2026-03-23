# Run Jarvis Mobile From VS Code CLI

All commands below are run from `mobile/`.

## 1. Build APK

```bash
./gradlew :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 2. Install On Connected Phone

```bash
adb devices -l
./gradlew :app:installDebug
```

## 3. Launch App

```bash
adb shell am start -n com.jarvis/.MainActivity
```

## 4. One-command Dev Flow

```bash
./scripts/dev.sh
```

## Helper Scripts

- `./scripts/build.sh` -> build debug APK
- `./scripts/install.sh` -> install debug APK
- `./scripts/run.sh` -> launch app
- `./scripts/dev.sh` -> install + launch

## Optional: Emulator From CLI

If needed, add SDK tools to PATH for this shell:

```bash
export ANDROID_SDK_ROOT=/home/dead/Android/Sdk
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
```

List AVDs and start one:

```bash
emulator -list-avds
emulator -avd <YOUR_AVD_NAME>
```

Then install/run as above.
