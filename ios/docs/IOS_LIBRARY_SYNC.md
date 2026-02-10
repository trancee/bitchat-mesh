# iOS Mesh Library Sync Guide

The `BitchatMesh` library lives inside this repo under [bitchat-mesh](../bitchat-mesh/README.md).
It is derived from the main app sources, with Nostr and Tor integration removed.

## What is copied

Library sources originate from these app folders:

- `bitchat/Models`
- `bitchat/Protocols`
- `bitchat/Noise`
- `bitchat/Sync`
- `bitchat/Identity`
- `bitchat/Utils`
- `bitchat/Services/BLE`
- `bitchat/Services/NoiseEncryptionService.swift`
- `bitchat/Services/Transport.swift`
- `bitchat/Services/TransportConfig.swift`
- `bitchat/Services/RelayController.swift`
- `bitchat/Services/NotificationStreamAssembler.swift`
- `bitchat/Services/MeshTopologyTracker.swift`
- `bitchat/Services/KeychainManager.swift`
- `bitchat/Services/VerificationService.swift`

## Mesh-only modifications

After copying, these changes are applied in the library:

- Remove Nostr types and Tor integration.
- Remove favorites/Nostr peer helpers from `PeerID` and identity cache.
- Remove favorite notifications from `BLEService` and `Transport`.
- Add `MeshManager`, `MeshListener`, and `MeshConfiguration` wrappers.
- Make required models (`PeerID`, `BitchatMessage`, `ReadReceipt`, `NoisePayloadType`) public.

## Updating from upstream

1) Pull the latest upstream changes into your local repo.
2) Re-copy the source folders/files listed above into `bitchat-mesh/Sources/BitchatMesh`.
3) Re-apply the mesh-only modifications from this guide.
4) Run your iOS build or `swift build` to validate.

This workflow keeps the library in sync while avoiding a separate fork.
