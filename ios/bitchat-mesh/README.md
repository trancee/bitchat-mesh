# BitchatMesh

Mesh-only Swift package for nearby device discovery and relay messaging over BLE.
This target excludes Tor and Nostr integrations and focuses on the local mesh stack.

## Usage

Add the package to your app (Xcode -> Package Dependencies) and target the `BitchatMesh` product.

```swift
import BitchatMesh

final class MeshClient: MeshListener {
    private let mesh = MeshManager()

    init() {
        mesh.listener = self
        mesh.start(nickname: "sample")
    }

    func onMessageReceived(_ message: BitchatMessage) {
        print("message from \(message.sender): \(message.content)")
    }
}
```

## Permissions

Add these to your app `Info.plist`:

- `NSBluetoothAlwaysUsageDescription`
- `NSBluetoothPeripheralUsageDescription`

If you send files, also include `NSPhotoLibraryAddUsageDescription` as needed.

## Notes

- `MeshManager` wraps `BLEService` and exposes a focused API for start/stop and messaging.
- QR verification is supported via `VerificationService` without Nostr fields.
- Identity keys are stored in the keychain using `MeshConfiguration` defaults.

## Upstream Sync

Sync date: 2026-02-11
Version: 124.4

- Re-synced protocol, identity, noise, sync, and BLE service sources from the app.
- Removed Nostr/favorites and UI-only helpers to keep the package mesh-only.
- Restored public API surface for core models and file transfer packets.
- Aligned keychain configuration with `MeshConfiguration`.
- Added sync log category and tightened TLV/binary decoding behavior.
