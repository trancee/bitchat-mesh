---
description: Sync the relevant changes from the upstream repository and remove unnecessary files
allowed-tools: [Bash, Read, Write, Edit, MultiEdit, Glob, Grep, Git, Gradle, Swift, Find, Rsync, TodoWrite]
---

There are platforms: android and ios in their respective folder

- `bitchat` is the iOS implementation and is synced into the `ios` folder with only the relevant parts for the BitChat mesh network to function (no UI).
- `bitchat-android` is the Android implementation and is synced into the `android` folder with only the relevant parts for the BitChat mesh network to function (no UI).

1. **ALWAYS checkout `main` branch and pull latest changes** (this is mandatory for `bitchat` and `bitchat-android` upstream repositories)

## Process

**Use TodoWrite tool throughout for progress tracking**

1. Find the changes from the upstream repository
2. Apply those changes
3. Remove all UI-related parts
4. Prepend all Log.d lines with a BuildConfig.DEBUG condition
5. Wire real events from the mesh service:
- Add `didEstablishSession` and `didUpdatePeerRSSI` to `BluetoothMeshDelegate`, emitted on key-exchange completion and RSSI updates in `BluetoothMeshService.kt`
- Forward those to `MeshListener` in `MeshManager.kt`

## Test

1. Make sure tests are not failing and fix them if necessary (do not change the sources, only the test implementation)
2. Add new tests if features were added to cover them

## Summary

1. Add summary of the changes with versioning in the README.md of the platform (android / ios) folder
