# iOS sync diffs

This document records local changes under ios/ and the upstream bitchat/ tree so upstream sync can be performed by diff only while preserving local additions.

## Local changes under ios/

The following additions were made in the iOS library tests to increase coverage:

- Added test file [ios/Tests/BitchatMeshTests/UtilsAndSyncTests.swift](../Tests/BitchatMeshTests/UtilsAndSyncTests.swift)
- Added test file [ios/Tests/BitchatMeshTests/MoreCoverageTests.swift](../Tests/BitchatMeshTests/MoreCoverageTests.swift)
- Added test file [ios/Tests/BitchatMeshTests/MeshServicesTests.swift](../Tests/BitchatMeshTests/MeshServicesTests.swift)
- Added test file [ios/Tests/BitchatMeshTests/ProtocolAndNoiseTests.swift](../Tests/BitchatMeshTests/ProtocolAndNoiseTests.swift)
- Added test file [ios/Tests/BitchatMeshTests/NoiseAndSyncManagerTests.swift](../Tests/BitchatMeshTests/NoiseAndSyncManagerTests.swift)

These are local-only test additions and should be preserved during upstream sync.

## Upstream bitchat/ changes

No changes were made under the upstream [bitchat/](../../bitchat) folder in this workspace.

## Upstream sync guidance (diff-only)

When syncing from upstream, apply changes by diff only and do not overwrite local-only additions:

- Preserve local test files listed above under [ios/Tests/BitchatMeshTests](../Tests/BitchatMeshTests)
- Preserve coverage documentation updates in [TESTING.md](../../TESTING.md)
- If upstream changes touch any of the same files, merge carefully so local test cases remain intact

## Test and coverage status

- Latest tests run: `swift test --enable-code-coverage`
- Coverage report command: `xcrun llvm-cov report` with merged profdata
- Current iOS totals are recorded in [TESTING.md](../../TESTING.md)
