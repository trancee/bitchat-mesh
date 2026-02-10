# Bitchat Mesh - Android Bluetooth Mesh Networking Library

A lightweight Android library providing Bluetooth mesh networking capabilities extracted from [bitchat-android](https://github.com/permissionlesstech/bitchat-android).

## Overview

**Bitchat Mesh** is a standalone Android library that provides core Bluetooth Low Energy (BLE) mesh networking, encryption, and peer-to-peer messaging functionality. It maintains 100% protocol compatibility with the original bitchat application for cross-platform communication.

### What's Included

✅ **Bluetooth Mesh Networking**
- Automatic peer discovery and connection management
- Multi-hop message relay
- Store-and-forward for offline peers
- Adaptive duty cycling for battery optimization

✅ **End-to-End Encryption**
- X25519 key exchange
- AES-256-GCM encryption
- Ed25519 digital signatures
- Noise Protocol Framework implementation

✅ **Binary Protocol**
- Compact packet format optimized for BLE
- TTL-based message routing (max 7 hops)
- Automatic fragmentation for large messages
- Message deduplication via unique IDs

✅ **Identity Management**
- Ephemeral identities (no registration required)
- Public/private key pairs
- Secure key storage

### What's Excluded

❌ **Tor Integration** - No Arti/Tor SOCKS proxy support  
❌ **Nostr Integration** - No relay-based messaging or geohash channels  
❌ **Application UI** - No pre-built screens or activities (service layer only)

## Requirements

- **Android 8.0+ (API 26+)**
- **Bluetooth LE** capable device
- **Kotlin 1.8.0+**

## Installation

Add this library to your Android project:

### Gradle (build.gradle.kts)

```kotlin
dependencies {
    implementation(files("libs/bitchat-mesh-release.aar"))
    
    // Required dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("no.nordicsemi.android:ble:2.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
}
```

### Permissions

The library requires the following permissions in your app's AndroidManifest.xml:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<uses-feature
    android:name="android.hardware.bluetooth_le"
    android:required="true" />
```

## Usage

### Basic Setup

```kotlin
import com.bitchat.android.service.MeshForegroundService
import com.bitchat.android.identity.IdentityManager
import com.bitchat.android.mesh.MeshManager

// 1. Request necessary permissions (Bluetooth, Location)

// 2. Start the mesh service
val serviceIntent = Intent(context, MeshForegroundService::class.java)
context.startForegroundService(serviceIntent)

// 3. Send a message
val meshManager = MeshManager.getInstance()
meshManager.sendMessage("Hello, mesh network!")
```

### Observing Mesh State

```kotlin
import com.bitchat.android.mesh.MeshViewModel
import androidx.lifecycle.ViewModelProvider

class YourActivity : AppCompatActivity() {
    private lateinit var meshViewModel: MeshViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        meshViewModel = ViewModelProvider(this)[MeshViewModel::class.java]
        
        // Observe connected peers
        meshViewModel.peers.observe(this) { peers ->
            // Update UI with peer list
        }
        
        // Observe received messages
        meshViewModel.messages.observe(this) { messages ->
            // Handle incoming messages
        }
    }
}
```

## Architecture

The library follows clean architecture principles:

```
bitchat-mesh/
├── core/          # Core utilities and base classes
├── crypto/        # Cryptographic operations (BouncyCastle)
├── identity/      # User identity and key management
├── mesh/          # BLE mesh networking logic
├── model/         # Data models
├── noise/         # Noise Protocol implementation
├── protocol/      # Binary protocol definitions
├── service/       # Background service management
└── util/          # Utility classes
```

## Protocol Compatibility

This library maintains 100% binary protocol compatibility with:
- [bitchat-android](https://github.com/permissionlesstech/bitchat-android) (original Android app)
- [bitchat-ios](https://github.com/jackjackbits/bitchat) (original iOS app)

Compatible features:
- Bluetooth LE service/characteristic UUIDs
- 13-byte packet header format
- Message types and routing logic
- Encryption algorithms (X25519, Ed25519, AES-GCM)
- Fragmentation for large content

## Keeping Up-to-Date

This library is designed to be easily synchronized with upstream changes from bitchat-android. See [UPSTREAM_SYNC.md](UPSTREAM_SYNC.md) for detailed instructions on applying upstream patches.

## License

This project is released into the public domain (Unlicense). See the [LICENSE](LICENSE) file for details.

This library is derived from [bitchat-android](https://github.com/permissionlesstech/bitchat-android), which is also in the public domain.

## Support

- **Original Project:** https://github.com/permissionlesstech/bitchat-android
- **iOS Version:** https://github.com/jackjackbits/bitchat

## Contributing

Contributions are welcome! When submitting pull requests:
1. Ensure changes don't reintroduce Tor or Nostr dependencies
2. Maintain protocol compatibility with bitchat
3. Add tests for new functionality
4. Update documentation as needed
