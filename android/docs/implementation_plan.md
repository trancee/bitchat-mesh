# Create Bitchat Mesh Library Without Tor/Nostr

Extract core Bluetooth mesh networking functionality from bitchat-android into a standalone Android library, excluding Tor and Nostr integrations while maintaining compatibility with the original protocol.

## Summary

The bitchat-android project contains both essential mesh networking components and optional network extensions (Tor and Nostr). Our goal is to create a clean Android library that:

1. **Retains** all core Bluetooth mesh networking, encryption, and protocol components
2. **Removes** Tor integration (Arti bridge, native libraries, and management code)
3. **Removes** Nostr integration (relay management, geohash features, and protocol handling)
4. **Maintains** compatibility with the bitchat binary protocol for cross-platform communication
5. **Ensures** easy synchronization with upstream changes via clear separation

### Key Components to Extract

**Core Components (KEEP):**
- Bluetooth mesh networking (`mesh/`)
- Binary protocol (`protocol/`)
- Cryptography and encryption (`crypto/`, `noise/`)
- Identity management (`identity/`)
- Core services (`service/`)
- Utility classes (`util/`, `utils/`)
- Core model classes (`model/`)

**Components to REMOVE:**
- Tor integration:
  - `net/ArtiTorManager.kt`
  - `net/TorMode.kt` 
  - `net/TorPreferenceManager.kt`
  - `info/guardianproject/arti/` package
  - `org/torproject/arti/` package
  - Native libraries in `src/main/jniLibs/**/libarti_android.so`
  - Build tools in `tools/arti-build/`
- Nostr integration:
  - Entire `nostr/` directory (24 Kotlin files)
  - Nostr-related code in `net/OkHttpProvider.kt` (if any)
  - Geohash integration that depends on Nostr (in `geohash/`)

## User Review Required

> [!IMPORTANT]
> **Library Module Structure**: The library will be created as a new Gradle module named `bitchat-mesh` in a new project directory. This approach:
> - Clearly separates the library from the full application
> - Makes it easy to track upstream changes by comparing against the original repo
> - Allows independent versioning
> - Simplifies integration into other Android projects
>
> The library will be organized to mirror the original structure where possible, making it straightforward to apply upstream patches.

> [!WARNING]
> **Breaking Changes**: The following features from the original app will NOT be available in the library:
> - Tor anonymization (no ArtiTorManager, no SOCKS proxy support)
> - Nostr relay connectivity (no geohash channels, no relay-based messaging)
> - UI components (library provides only core mesh services, not UI)
>
> Projects using this library will need to implement their own UI layer using the provided services and ViewModels.

## Proposed Changes

### Library Project Structure

#### [NEW] Project Setup

Create new Android library project at `/home/phil/.gemini/antigravity/scratch/bitchat-mesh-lib/`:

```
bitchat-mesh-lib/
├── bitchat-mesh/              # Main library module
│   ├── build.gradle.kts       # Library build configuration
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml
│   │       └── java/com/bitchat/android/
│   │           ├── core/
│   │           ├── crypto/
│   │           ├── identity/
│   │           ├── mesh/
│   │           ├── model/
│   │           ├── noise/
│   │           ├── protocol/
│   │           ├── service/
│   │           ├── util/
│   │           └── utils/
├── build.gradle.kts           # Root build file
├── settings.gradle.kts        # Project settings
├── gradle.properties
├── README.md                  # Library documentation
└── UPSTREAM_SYNC.md          # Guide for syncing with upstream
```

---

### Core Library Module

#### [NEW] [bitchat-mesh/build.gradle.kts](file:///home/phil/.gemini/antigravity/scratch/bitchat-mesh-lib/bitchat-mesh/build.gradle.kts)

Android library build configuration with:
- Dependencies from original (excluding Tor/Nostr)
- BouncyCastle for cryptography
- Nordic BLE library
- Kotlin Coroutines
- Jetpack Compose (for any reusable UI components)
- Minimum SDK 26 (Android 8.0)

#### [NEW] [bitchat-mesh/src/main/AndroidManifest.xml](file:///home/phil/.gemini/antigravity/scratch/bitchat-mesh-lib/bitchat-mesh/src/main/AndroidManifest.xml)

Minimal manifest declaring required permissions:
- Bluetooth permissions
- Location permissions (required for BLE scanning)

---

### Source Code Migration

#### [NEW] Core Package Structure

Copy the following directories from original repo, removing Tor/Nostr references:

1. **`core/`** - Core utilities and base classes
2. **`crypto/`** - Cryptographic operations
3. **`identity/`** - User identity management  
4. **`mesh/`** - Bluetooth mesh networking
5. **`model/`** - Data models
6. **`noise/`** - Noise Protocol implementation
7. **`protocol/`** - Binary protocol
8. **`service/`** - Background services
9. **`util/` and `utils/`** - Utility classes

#### [MODIFY] Network Provider

Create simplified `OkHttpProvider.kt` (if needed) without Tor SOCKS proxy configuration:
- Remove all Tor-related proxy setup
- Standard OkHttp client configuration only
- Keep WebSocket support for potential future extensions

#### [MODIFY] Service Layer

Update services to remove Tor/Nostr dependencies:
- Remove calls to `ArtiTorManager`
- Remove calls to `NostrRelayManager`
- Remove Nostr event handling
- Keep core mesh service functionality

---

### Documentation

#### [NEW] [README.md](file:///home/phil/.gemini/antigravity/scratch/bitchat-mesh-lib/README.md)

Library usage documentation including:
- Feature overview (what's included/excluded)
- Integration guide
- API documentation
- Sample usage code
- Dependencies list
- Compatibility notes with original bitchat protocol

#### [NEW] [UPSTREAM_SYNC.md](file:///home/phil/.gemini/antigravity/scratch/bitchat-mesh-lib/UPSTREAM_SYNC.md)

Guide for syncing with upstream bitchat-android changes:
- File mapping between original repo and library
- Excluded files/directories list
- Instructions for applying upstream patches
- Testing checklist after sync
- Known divergence points
- Script for automated file comparison (optional)

---

### Build Configuration

#### [NEW] [build.gradle.kts](file:///home/phil/.gemini/antigravity/scratch/bitchat-mesh-lib/build.gradle.kts)

Root project build file with plugin configurations

#### [NEW] [settings.gradle.kts](file:///home/phil/.gemini/antigravity/scratch/bitchat-mesh-lib/settings.gradle.kts)

Project settings including library module

#### [NEW] [gradle.properties](file:///home/phil/.gemini/antigravity/scratch/bitchat-mesh-lib/gradle.properties)

Gradle configuration (Kotlin compiler, Android settings)

## Verification Plan

### Automated Tests

1. **Build Verification**
   ```bash
   cd /home/phil/.gemini/antigravity/scratch/bitchat-mesh-lib
   ./gradlew clean build
   ```
   Verify the library builds successfully without errors.

2. **Gradle Version Catalog Check**
   ```bash
   cd /home/phil/.gemini/antigravity/scratch/bitchat-mesh-lib
   ./gradlew dependencies --configuration releaseRuntimeClasspath
   ```
   Verify no Tor or Nostr dependencies are included.

3. **Source Code Verification**
   ```bash
   # Verify no Tor references remain
   grep -r "ArtiTorManager\|org.torproject\|info.guardianproject.arti" /home/phil/.gemini/antigravity/scratch/bitchat-mesh-lib/bitchat-mesh/src/ || echo "No Tor references found ✓"
   
   # Verify no Nostr references remain  
   grep -r "NostrRelayManager\|NostrClient\|NostrProtocol" /home/phil/.gemini/antigravity/scratch/bitchat-mesh-lib/bitchat-mesh/src/ || echo "No Nostr references found ✓"
   ```

4. **AAR Generation**
   ```bash
   cd /home/phil/.gemini/antigravity/scratch/bitchat-mesh-lib
   ./gradlew :bitchat-mesh:assembleRelease
   ls -lh bitchat-mesh/build/outputs/aar/
   ```
   Verify the Android library (AAR file) is generated successfully.

### Manual Verification

1. **File Structure Review**: Examine the library structure to ensure:
   - All core mesh networking files are present
   - No Tor-related files exist
   - No Nostr-related files exist
   - Package structure matches original for compatibility

2. **Documentation Review**: Verify that README.md and UPSTREAM_SYNC.md clearly explain:
   - What's included vs. excluded
   - How to integrate the library
   - How to sync with upstream changes

3. **Size Comparison**: Compare library size to original app to verify Tor/Nostr removal:
   - Original APK includes ~4MB of native Tor libraries
   - Library AAR should be significantly smaller
