---
description: Sync the relevant changes from the upstream repository and remove unnecessary files
allowed-tools: [Bash, Read, Write, Edit, MultiEdit, Glob, Grep, Git, Gradle, Swift, Find, Rsync, Python3, TodoWrite]
---

There are platforms: android and ios in their respective folder

- `bitchat` is the iOS implementation and is synced into the `ios` folder with only the relevant parts for the BitChat mesh network to function (no UI).
- `bitchat-android` is the Android implementation and is synced into the `android` folder with only the relevant parts for the BitChat mesh network to function (no UI).

1. **ALWAYS checkout `main` branch and pull latest changes** (this is mandatory for `bitchat` and `bitchat-android` upstream repositories)
2. **NEVER make any changes in the upstream repositories** (`bitchat` and `bitchat-android`); only sync from them

## Process

**Use TodoWrite tool throughout for progress tracking**

1. Find the changes from the upstream repository
2. Apply those changes
3. Remove all UI-related parts
4. Prepend all Log.d lines with a BuildConfig.DEBUG condition
5. Wire real events from the mesh service:
- Add `didEstablishSession` and `didUpdateRSSI` to `BluetoothMeshDelegate`, emitted on key-exchange completion and RSSI updates in `BluetoothMeshService.kt`
- Forward those to `MeshListener` in `MeshManager.kt`
6. Ensure Android uses the debug BLE service UUID for testing builds and the release UUID for release builds

### Test

1. Make sure tests are not failing and fix them if necessary (do not change the sources, only the test implementation)
2. Add new tests if features were added to cover them

### Summary

1. Add summary of the changes with versioning in the README.md of the platform (android / ios) folder

## Diff Pointers (Sync Aide)

### iOS vs bitchat

**Only in upstream (`bitchat`)**
- Repo meta/docs: `.gitattributes`, `.github`, `.gitignore`, `BRING_THE_NOISE.md`, `Justfile`, `LICENSE`, `PRIVACY_POLICY.md`, `WHITEPAPER.md`, `docs/` (SOURCE_ROUTING, TOR-INTEGRATION, etc.)
- App targets: `bitchat/` (full app), `bitchatShareExtension/`, `bitchatTests/`, `relays/`, `localPackages/Arti/`

**Only in this repo (`ios`)**
- Library and sample: `bitchat-mesh/`, `bitchatMeshSample/`, `Tests/`
- Docs and tooling: `docs/IOS_LIBRARY_SYNC.md`, `localPackages/BitLogger/.swiftpm`, `.swiftpm`

**Files that diverge**
- `ios/Package.swift` vs `bitchat/Package.swift`
- `ios/Configs/Release.xcconfig` vs `bitchat/Configs/Release.xcconfig`
- `ios/README.md` vs `bitchat/README.md`

### Android vs bitchat-android

**Only in upstream (`bitchat-android`)**
- Repo meta/docs: `.github`, `.gitignore`, `AGENTS.md`, `CHANGELOG.md`, `GOOGLE_PLAY.md`, `PRIVACY_POLICY.md`, `LICENSE.md`, `fastlane/`, `tools/`, `docs/` (ANNOUNCEMENT_GOSSIP, SOURCE_ROUTING, file_transfer, etc.)
- App-specific assets: `app/src/main/assets/`, `app/src/main/jniLibs/`, `app/src/main/res/mipmap-*`, `app/src/main/res/drawable/ic_*`, `app/src/main/res/values-*` locales, `app/src/main/res/xml/`
- App code: `app/src/main/java/com/bitchat/**`, `app/src/main/java/info/**`, `app/src/main/java/org/**`, `app/src/test/`
- App config: `app/proguard-rules.pro`, `app/lint-baseline.xml`

**Only in this repo (`android`)**
- Library module: `android/bitchat-mesh/**`
- Sample app package: `app/src/main/java/com/permissionless/**`
- Sample app UI resources: `app/src/main/res/layout/`, `app/src/main/res/drawable/bg_*`, `app/src/main/res/values/colors.xml`, `styles.xml`
- Project tooling: `.kotlin`, `.vscode`, `UPSTREAM_SYNC.md`, `verify.sh`, `docs/implementation_plan.md`, `gradle/gradle-daemon-jvm.properties`, `LICENSE`

**Files that diverge**
- `android/README.md` vs `bitchat-android/README.md`
- `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/settings.gradle.kts`
- `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/values/strings.xml`

## Notes

- Sending a file requires an established session; the new button will log and refuse sending if no session exists. Consider disabling the button in the UI unless a session is established and decide whether to trigger session establishment automatically when sending is attempted.

- Planning to add an `onTransferProgress` method with a default implementation to the Android `MeshListener` interface to avoid breaking changes, and to forward it via `MeshManager`. Also implement a progress manager in iOS and integrate it through the iOS `MeshManager` after reviewing its code.
