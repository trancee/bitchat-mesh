# AGENTS

## Purpose

This repository contains cross-platform Bluetooth mesh libraries for BitChat.
Use this file to keep contributor notes consistent for agents and automation.

## General rules

- Never make changes in the upstream repositories (`bitchat` and `bitchat-android`); only sync from them.

## Sync process

### Android

The Android library in android/bitchat-mesh is a curated extract of the app in
bitchat-android. Follow the upstream sync guide:

- android/UPSTREAM_SYNC.md

That document defines the directories to sync, exclusions (Tor/Nostr), and the
review steps after copying changes.

### iOS

The iOS library in ios/bitchat-mesh should be kept aligned with the app code in
bitchat. Review changes under bitchat/ and port protocol or model updates into
ios/bitchat-mesh as needed.

## Testing commands

### Android library

```bash
cd android
./gradlew :bitchat-mesh:test
```

### iOS library

```bash
cd ios
swift test
```
