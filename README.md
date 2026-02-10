# bitchat-mesh

Cross-platform Bluetooth mesh library for BitChat, with Android and iOS implementations plus test suites and sample apps.

## Library build and test

### Android (Gradle)

Platform notes:
- Requires Java 21 (Gradle is configured to use Java 21 via android/gradle.properties).

Build the Android library AAR:

```bash
cd android
./gradlew :bitchat-mesh:assemble
```

Run unit tests:

```bash
cd android
./gradlew :bitchat-mesh:test
```

Artifacts land in:

```
android/bitchat-mesh/build/outputs/aar
```

### iOS (Swift Package)

Platform notes:
- Requires Xcode with Swift Package Manager support (Xcode 15+ recommended).

Build the iOS library:

```bash
cd ios
swift build
```

Run unit tests:

```bash
cd ios
swift test
```

## Sample apps (for testing)

### Android sample app

Build the debug APK:

```bash
cd bitchat-android
./gradlew :app:assembleDebug
```

Install on a connected device or emulator:

```bash
cd bitchat-android
./gradlew :app:installDebug
```

### iOS sample app

Open the sample app in Xcode and run it on a simulator or device:

```bash
cd ios
open bitchatMeshSample/bitchatMeshSample.xcodeproj
```

CLI build (optional):

```bash
cd ios
xcodebuild -project bitchatMeshSample/bitchatMeshSample.xcodeproj \
	-scheme bitchatMeshSample \
	-destination 'platform=iOS Simulator,name=iPhone 15' \
	build
```

## Migration notes

- iOS `MeshManager` now mirrors Android with `setListener`, `start(nickname:)`, `isRunning`/`isStarted`, `establish`/`isEstablished`, and file send helpers.
- `sendBroadcastMessage` accepts `mentions` and `channel` for parity; `channel` is currently ignored on iOS.
- New listener callbacks are available for start/stop, send, and verify events; existing listeners can adopt them as needed.

## Library maintenance

Keep the libraries aligned with the upstream app sources:

- iOS source of truth: the BitChat app code in [bitchat](bitchat) (Swift Package sources live under bitchat/).
- Android source of truth: the BitChat Android app in [bitchat-android](bitchat-android).

For Android, follow the upstream sync workflow and directory mapping in
[android/UPSTREAM_SYNC.md](android/UPSTREAM_SYNC.md). It documents which packages are synced
verbatim, which are excluded (Tor/Nostr), and the recommended review steps after copying changes.
