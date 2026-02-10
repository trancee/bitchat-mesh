# Upstream Synchronization Guide

This document provides instructions for keeping the bitchat-mesh library synchronized with upstream changes from [bitchat-android](https://github.com/permissionlesstech/bitchat-android).

## Overview

The bitchat-mesh library is a clean extraction of core mesh networking components from bitchat-android, with Tor and Nostr integrations removed. This guide helps you apply upstream updates while maintaining this separation.

## File Mapping

### Included Directories (synced with upstream)

| Upstream Path | Library Path | Notes |
|---------------|--------------|-------|
| `app/src/main/java/com/bitchat/android/core/` | `bitchat-mesh/src/main/java/com/bitchat/android/core/` | Core utilities - sync fully |
| `app/src/main/java/com/bitchat/android/crypto/` | `bitchat-mesh/src/main/java/com/bitchat/android/crypto/` | Cryptography - sync fully |
| `app/src/main/java/com/bitchat/android/identity/` | `bitchat-mesh/src/main/java/com/bitchat/android/identity/` | Identity management - sync fully |
| `app/src/main/java/com/bitchat/android/mesh/` | `bitchat-mesh/src/main/java/com/bitchat/android/mesh/` | Mesh networking - sync fully |
| `app/src/main/java/com/bitchat/android/model/` | `bitchat-mesh/src/main/java/com/bitchat/android/model/` | Data models - sync fully |
| `app/src/main/java/com/bitchat/android/noise/` | `bitchat-mesh/src/main/java/com/bitchat/android/noise/` | Noise protocol - sync fully |
| `app/src/main/java/com/bitchat/android/protocol/` | `bitchat-mesh/src/main/java/com/bitchat/android/protocol/` | Binary protocol - sync fully |
| `app/src/main/java/com/bitchat/android/service/` | `bitchat-mesh/src/main/java/com/bitchat/android/service/` | Background services - review for Tor/Nostr refs |
| `app/src/main/java/com/bitchat/android/services/` | `bitchat-mesh/src/main/java/com/bitchat/android/services/` | Additional services - review for Tor/Nostr refs |
| `app/src/main/java/com/bitchat/android/util/` | `bitchat-mesh/src/main/java/com/bitchat/android/util/` | Utilities - sync fully |
| `app/src/main/java/com/bitchat/android/utils/` | `bitchat-mesh/src/main/java/com/bitchat/android/utils/` | Utilities - sync fully |
| `app/src/main/java/com/bitchat/android/ui/` | `bitchat-mesh/src/main/java/com/bitchat/android/ui/` | UI components - sync selectively |
| `app/src/main/java/com/bitchat/android/features/` | `bitchat-mesh/src/main/java/com/bitchat/android/features/` | Feature modules - sync selectively |
| `app/src/main/java/com/bitchat/android/sync/` | `bitchat-mesh/src/main/java/com/bitchat/android/sync/` | Sync utilities - sync fully |
| `app/src/main/java/com/bitchat/android/favorites/` | `bitchat-mesh/src/main/java/com/bitchat/android/favorites/` | Favorites - sync fully |
| `app/src/main/java/com/bitchat/android/onboarding/` | `bitchat-mesh/src/main/java/com/bitchat/android/onboarding/` | Onboarding - sync selectively |

### Excluded Directories (DO NOT sync)

| Upstream Path | Reason |
|---------------|--------|
| `app/src/main/java/com/bitchat/android/nostr/` | **Nostr integration** - intentionally excluded |
| `app/src/main/java/com/bitchat/android/geohash/` | **Geohash channels** - depends on Nostr, excluded |
| `app/src/main/java/com/bitchat/android/net/ArtiTorManager.kt` | **Tor integration** - intentionally excluded |
| `app/src/main/java/com/bitchat/android/net/TorMode.kt` | **Tor integration** - intentionally excluded |
| `app/src/main/java/com/bitchat/android/net/TorPreferenceManager.kt` | **Tor integration** - intentionally excluded |
| `app/src/main/java/info/guardianproject/` | **Tor JNI bindings** - intentionally excluded |
| `app/src/main/java/org/torproject/` | **Tor JNI bindings** - intentionally excluded |
| `app/src/main/jniLibs/*/libarti_android.so` | **Tor native libraries** - intentionally excluded |
| `tools/arti-build/` | **Tor build scripts** - intentionally excluded |

### Modified Files (partial sync)

| File | Library Version | Notes |
|------|-----------------|-------|
| `app/src/main/java/com/bitchat/android/net/OkHttpProvider.kt` | Simplified version | Remove Tor proxy logic when syncing |

## Synchronization Workflow

### 1. Check for Upstream Updates

```bash
cd /path/to/bitchat-android-original
git fetch origin
git log --oneline HEAD..origin/main
```

### 2. Pull Latest Changes

```bash
git pull origin main
```

### 3. Identify Relevant Changes

Review the commit history and identify changes to:
- Core mesh networking components
- Cryptography improvements
- Protocol updates
- Bug fixes

Ignore changes to:
- Tor integration (`ArtiTorManager`, `TorMode`, etc.)
- Nostr integration (`nostr/` directory)
- Geohash features

### 4. Apply Changes to Library

#### For Full Directory Sync

```bash
# Example: Syncing crypto directory
cp -r /path/to/bitchat-android-original/app/src/main/java/com/bitchat/android/crypto/* \
      /path/to/bitchat-mesh-lib/bitchat-mesh/src/main/java/com/bitchat/android/crypto/
```

#### For Selective File Updates

```bash
# Example: Updating a specific file
cp /path/to/bitchat-android-original/app/src/main/java/com/bitchat/android/mesh/MeshManager.kt \
   /path/to/bitchat-mesh-lib/bitchat-mesh/src/main/java/com/bitchat/android/mesh/
```

### 5. Review and Clean Up

After copying files, check for newly introduced Tor/Nostr references:

```bash
cd /path/to/bitchat-mesh-lib/bitchat-mesh/src/main/java

# Check for Tor references
grep -r "ArtiTorManager\|TorPreferenceManager\|org.torproject\|info.guardianproject.arti" .

# Check for Nostr references
grep -r "NostrRelayManager\|NostrClient\|NostrProtocol\|GeohashRepository" .
```

If any references are found:
1. Review the file to understand the dependency
2. Either remove the reference or replace with library-appropriate alternative
3. Test that the build still works

### 6. Handle OkHttpProvider Changes

If `OkHttpProvider.kt` was updated upstream:

```bash
# View differences
diff /path/to/bitchat-android-original/app/src/main/java/com/bitchat/android/net/OkHttpProvider.kt \
     /path/to/bitchat-mesh-lib/bitchat-mesh/src/main/java/com/bitchat/android/net/OkHttpProvider.kt
```

Manually apply only non-Tor changes to the library version. The library version should NOT include:
- `ArtiTorManager.getInstance()` calls
- SOCKS proxy configuration
- any Tor-related logic

### 7. Update Dependencies

Check if `app/build.gradle.kts` dependencies were updated:

```bash
# Compare dependency versions
diff /path/to/bitchat-android-original/app/build.gradle.kts \
     /path/to/bitchat-mesh-lib/bitchat-mesh/build.gradle.kts
```

Update library dependencies to match, EXCEPT:
- Do NOT add Guardian Project Maven repository
- Do NOT add Arti dependencies

### 8. Test the Build

```bash
cd /path/to/bitchat-mesh-lib
./gradlew clean build
```

Verify:
- Build completes successfully
- Tests pass (if any)
- AAR file is generated

### 9. Verify No Forbidden Dependencies

```bash
cd /path/to/bitchat-mesh-lib
./gradlew :bitchat-mesh:dependencies --configuration releaseRuntimeClasspath | grep -i "arti\|tor\|nostr"
```

Output should be empty (no Tor/Nostr/Arti dependencies).

## Automated Sync Script (Optional)

Create a script to automate the sync process:

```bash
#!/bin/bash
# sync-upstream.sh

UPSTREAM_DIR="/path/to/bitchat-android-original"
LIB_DIR="/path/to/bitchat-mesh-lib"

# Directories to fully sync
SYNC_DIRS="core crypto identity mesh model noise protocol util utils sync favorites"

for dir in $SYNC_DIRS; do
    echo "Syncing $dir..."
    cp -r "$UPSTREAM_DIR/app/src/main/java/com/bitchat/android/$dir" \
          "$LIB_DIR/bitchat-mesh/src/main/java/com/bitchat/android/"
done

# Verify no unwanted references
cd "$LIB_DIR/bitchat-mesh/src/main/java"
if grep -r "ArtiTorManager\|NostrRelayManager" .; then
    echo "WARNING: Found Tor or Nostr references!"
    exit 1
fi

echo "Sync complete. Please review changes and test build."
```

## Version Tracking

After each sync, document the update:

1. Update `CHANGELOG.md` in the library
2. Note the upstream commit hash that was synced
3. List any manual modifications made during sync

Example entry:

```markdown
## [1.1.0] - 2026-02-07
### Synced from Upstream
- Upstream commit: abc123def456
- Updated mesh networking components
- Applied cryptography improvements
### Library-Specific Changes
- Maintained simplified OkHttpProvider (no Tor)
- Excluded new Nostr features from upstream
```

## Common Issues

### Issue: Newly Added Tor/Nostr Dependencies

**Solution:** Remove the dependencies from the synced files. If the feature is critical to mesh networking, create a Tor/Nostr-free alternative implementation.

### Issue: Service Layer Changes Reference Tor/Nostr

**Solution:** Review the service changes carefully. Either:
1. Remove Tor/Nostr calls if they're optional
2. Create stub methods that no-op for excluded features
3. Don't sync that particular change if it's purely Tor/Nostr related

### Issue: Build Fails After Sync

**Solution:**
1. Check for missing import statements (may reference excluded packages)
2. Verify all dependencies are declared in `build.gradle.kts`
3. Look for compilation errors related to Tor/Nostr and remove those code paths

## Contact

For questions about syncing or maintaining the library, refer to the main [README.md](README.md).
