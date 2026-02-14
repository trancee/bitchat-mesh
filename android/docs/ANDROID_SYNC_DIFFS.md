# Android sync diffs

This document records local changes under android/ and the upstream bitchat-android/ tree so upstream sync can be performed by diff only while preserving local additions.

## Local changes under android/

Local coverage work touched the following areas and should be preserved during upstream sync:

- Build and coverage wiring: android/bitchat-mesh/build.gradle.kts (Jacoco full report task and exclusions)
- Unit tests under android/bitchat-mesh/src/test/kotlin/com/bitchat/android and android/bitchat-mesh/src/test/kotlin/com/bitchat/mesh
- Instrumentation tests under android/bitchat-mesh/src/androidTest/kotlin/com/bitchat/android
- Test stub for Android Log: android/bitchat-mesh/src/test/java/android/util/Log.kt
- Coverage documentation: TESTING.md (repo root)

Unit test files touched for coverage:

- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/features/file/FileUtilsTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/mesh/FragmentManagerTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/mesh/IntegrationMessagingTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/mesh/MessageHandlerTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/mesh/MeshEndToEndTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/mesh/PacketProcessorTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/mesh/PacketRelayManagerTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/mesh/PeerFingerprintManagerTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/mesh/PeerManagerMoreTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/mesh/PeerManagerTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/mesh/SecurityManagerMoreTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/mesh/SecurityManagerTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/mesh/StoreForwardManagerTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/mesh/TransferProgressManagerTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/model/BitchatFilePacketTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/model/BitchatMessageTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/model/FragmentPayloadTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/model/IdentityAnnouncementTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/model/NoiseEncryptedTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/model/RequestSyncPacketTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/model/TLVPacketTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/noise/NoiseChannelEncryptionTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/noise/NoiseSessionInternalTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/noise/NoiseSessionManagerTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/noise/NoiseSessionTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/noise/southernstorm/crypto/ChaChaCoreTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/protocol/BinaryProtocolTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/protocol/BitchatPacketTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/protocol/CompressionUtilTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/protocol/MessagePaddingTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/services/NicknameProviderTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/services/VerificationServiceTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/services/meshgraph/GossipTLVTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/services/meshgraph/MeshGraphServiceTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/services/meshgraph/RoutePlannerTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/sync/GCSFilterTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/sync/GossipSyncManagerTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/sync/PacketIdUtilTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/sync/SyncDefaultsTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/util/BinaryEncodingUtilsTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/util/ByteArrayExtensionsTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/util/ByteArrayWrapperTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/android/util/NotificationIntervalManagerTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/mesh/MeshListenerTests.kt
- android/bitchat-mesh/src/test/kotlin/com/bitchat/mesh/MeshManagerTests.kt

Instrumentation test files touched for coverage:

- android/bitchat-mesh/src/androidTest/kotlin/com/bitchat/android/crypto/EncryptionServiceInstrumentationTests.kt
- android/bitchat-mesh/src/androidTest/kotlin/com/bitchat/android/features/file/FileUtilsInstrumentationTests.kt
- android/bitchat-mesh/src/androidTest/kotlin/com/bitchat/android/identity/SecureIdentityStateManagerInstrumentationTests.kt
- android/bitchat-mesh/src/androidTest/kotlin/com/bitchat/android/mesh/BluetoothConnectionTrackerInstrumentationTests.kt
- android/bitchat-mesh/src/androidTest/kotlin/com/bitchat/android/mesh/BluetoothPermissionManagerInstrumentationTests.kt
- android/bitchat-mesh/src/androidTest/kotlin/com/bitchat/android/mesh/PeerFingerprintManagerInstrumentationTests.kt
- android/bitchat-mesh/src/androidTest/kotlin/com/bitchat/android/mesh/PowerManagerInstrumentationTests.kt
- android/bitchat-mesh/src/androidTest/kotlin/com/bitchat/android/noise/NoiseEncryptionServiceInstrumentationTests.kt
- android/bitchat-mesh/src/androidTest/kotlin/com/bitchat/android/noise/NoiseSessionManagerInstrumentationTests.kt
- android/bitchat-mesh/src/androidTest/kotlin/com/bitchat/android/services/SeenMessageStoreInstrumentationTests.kt
- android/bitchat-mesh/src/androidTest/kotlin/com/bitchat/android/services/VerificationServiceInstrumentationTests.kt
- android/bitchat-mesh/src/androidTest/kotlin/com/bitchat/android/utils/DeviceUtilsInstrumentationTests.kt

## Upstream bitchat-android/ changes

No changes were made under the upstream bitchat-android/ folder in this workspace.

## Upstream sync guidance (diff-only)

When syncing from upstream, apply changes by diff only and do not overwrite local-only additions:

- Preserve the local test files listed above under android/bitchat-mesh/src/test and android/bitchat-mesh/src/androidTest
- Preserve the Jacoco report task and coverage exclusions in android/bitchat-mesh/build.gradle.kts
- Preserve the Android Log test stub in android/bitchat-mesh/src/test/java/android/util/Log.kt
- Preserve coverage documentation updates in TESTING.md
- If upstream changes touch any of the same files, merge carefully so local test cases remain intact

## Test and coverage status

- Latest Android unit tests: ./gradlew :bitchat-mesh:test
- Latest Android instrumentation tests: ./gradlew :bitchat-mesh:connectedDebugAndroidTest
- Latest Jacoco report: ./gradlew :bitchat-mesh:jacocoFullReport
- Current Android totals are recorded in TESTING.md
