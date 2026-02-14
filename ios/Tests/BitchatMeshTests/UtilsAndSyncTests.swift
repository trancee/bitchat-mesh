import XCTest
@testable import BitchatMesh

final class InputValidatorTests: XCTestCase {
    func testValidateUserStringTrimsAndRejectsInvalid() {
        XCTAssertEqual(InputValidator.validateUserString("  hi  ", maxLength: 10), "hi")
        XCTAssertNil(InputValidator.validateUserString("   ", maxLength: 10))
        XCTAssertNil(InputValidator.validateUserString(String(repeating: "a", count: 11), maxLength: 10))
        XCTAssertNil(InputValidator.validateUserString("bad\u{0007}", maxLength: 10))
    }

    func testValidateNicknameEnforcesMaxLength() {
        let max = String(repeating: "a", count: InputValidator.Limits.maxNicknameLength)
        XCTAssertEqual(InputValidator.validateNickname(max), max)
        XCTAssertNil(InputValidator.validateNickname(max + "a"))
    }

    func testValidateTimestampWindow() {
        let now = Date()
        XCTAssertTrue(InputValidator.validateTimestamp(now))
        XCTAssertFalse(InputValidator.validateTimestamp(now.addingTimeInterval(-301)))
        XCTAssertFalse(InputValidator.validateTimestamp(now.addingTimeInterval(301)))
    }
}

final class MessageDeduplicatorTests: XCTestCase {
    func testIsDuplicateTracksAndReturnsStatus() {
        let dedup = MessageDeduplicator(maxAge: 60, maxCount: 10)

        XCTAssertFalse(dedup.isDuplicate("msg-1"))
        XCTAssertTrue(dedup.isDuplicate("msg-1"))
        XCTAssertTrue(dedup.contains("msg-1"))
    }

    func testTrimEvictsOldestEntries() {
        let dedup = MessageDeduplicator(maxAge: 60, maxCount: 2)

        _ = dedup.isDuplicate("a")
        _ = dedup.isDuplicate("b")
        _ = dedup.isDuplicate("c")

        XCTAssertFalse(dedup.contains("a"))
        XCTAssertFalse(dedup.contains("b"))
        XCTAssertTrue(dedup.contains("c"))
    }

    func testCleanupRemovesExpiredEntries() {
        let dedup = MessageDeduplicator(maxAge: 1, maxCount: 10)
        let old = Date(timeIntervalSinceNow: -10)
        let recent = Date()

        dedup.record("old", timestamp: old)
        dedup.record("recent", timestamp: recent)
        dedup.cleanup()

        XCTAssertFalse(dedup.contains("old"))
        XCTAssertTrue(dedup.contains("recent"))
    }
}

final class NicknameUtilsTests: XCTestCase {
    func testSplitSuffixHandlesHexSuffixes() {
        XCTAssertEqual("alice#1a2b".splitSuffix().0, "alice")
        XCTAssertEqual("alice#1a2b".splitSuffix().1, "#1a2b")
        XCTAssertEqual("@bob#ABCD".splitSuffix().0, "bob")
        XCTAssertEqual("@bob#ABCD".splitSuffix().1, "#ABCD")
    }

    func testSplitSuffixReturnsEmptyWhenInvalid() {
        XCTAssertEqual("tiny".splitSuffix().1, "")
        XCTAssertEqual("nope#xyz!".splitSuffix().1, "")
    }
}

final class PeerDisplayNameResolverTests: XCTestCase {
    func testResolveAddsSuffixForConnectedCollisions() {
        let peer1 = PeerID(str: "abcd1111")
        let peer2 = PeerID(str: "efgh2222")
        let peer3 = PeerID(str: "ijkl3333")

        let peers = [
            (peerID: peer1, nickname: "sam", isConnected: true),
            (peerID: peer2, nickname: "sam", isConnected: true),
            (peerID: peer3, nickname: "sam", isConnected: false)
        ]

        let resolved = PeerDisplayNameResolver.resolve(peers, selfNickname: "sam")

        XCTAssertEqual(resolved[peer1], "sam#" + peer1.id.prefix(4))
        XCTAssertEqual(resolved[peer2], "sam#" + peer2.id.prefix(4))
        XCTAssertEqual(resolved[peer3], "sam")
    }
}

final class SyncTypeFlagsTests: XCTestCase {
    func testEncodeDecodeRoundTrip() {
        let flags = SyncTypeFlags(messageTypes: [.announce, .message, .fragment])
        let data = flags.toData()
        XCTAssertNotNil(data)
        let decoded = SyncTypeFlags.decode(data ?? Data())
        XCTAssertEqual(decoded?.toMessageTypes().sorted { $0.rawValue < $1.rawValue }, flags.toMessageTypes().sorted { $0.rawValue < $1.rawValue })
    }

    func testContainsUnionIntersection() {
        let publicFlags = SyncTypeFlags.publicMessages
        let fragmentFlags = SyncTypeFlags.fragment

        XCTAssertTrue(publicFlags.contains(.announce))
        XCTAssertFalse(publicFlags.contains(.fragment))

        let union = publicFlags.union(fragmentFlags)
        XCTAssertTrue(union.contains(.fragment))

        let intersection = union.intersection(publicFlags)
        XCTAssertFalse(intersection.contains(.fragment))
        XCTAssertTrue(intersection.contains(.message))
    }

    func testDecodeRejectsInvalidLengthAndZero() {
        XCTAssertNil(SyncTypeFlags.decode(Data()))
        XCTAssertNil(SyncTypeFlags.decode(Data(repeating: 0x01, count: 9)))

        let zero = SyncTypeFlags(rawValue: 0)
        XCTAssertNil(zero.toData())

        let masked = SyncTypeFlags(rawValue: 0x0100_0000_0000_0000)
        XCTAssertNil(masked.toData())
    }
}

final class RequestSyncManagerTests: XCTestCase {
    func testValidResponseRequiresPendingRequestAndRSRFlag() {
        let manager = RequestSyncManager()
        let peer = PeerID(str: "a1b2c3d4")
        let other = PeerID(str: "deadbeef")

        XCTAssertFalse(manager.isValidResponse(from: peer, isRSR: true))

        manager.registerRequest(to: peer)

        let waitForRegister = expectation(description: "wait for register")
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.05) {
            waitForRegister.fulfill()
        }
        wait(for: [waitForRegister], timeout: 1.0)

        XCTAssertTrue(manager.isValidResponse(from: peer, isRSR: true))
        XCTAssertFalse(manager.isValidResponse(from: peer, isRSR: false))
        XCTAssertFalse(manager.isValidResponse(from: other, isRSR: true))
    }
}

final class PacketIdUtilTests: XCTestCase {
    func testComputeIdDeterministicAndSensitiveToPayload() {
        let packet = BitchatPacket(
            type: 0x02,
            senderID: Data([0x01, 0x02, 0x03, 0x04]),
            recipientID: nil,
            timestamp: 1_234_567_890,
            payload: Data([0x10, 0x20, 0x30]),
            signature: nil,
            ttl: 3
        )

        let first = PacketIdUtil.computeId(packet)
        let second = PacketIdUtil.computeId(packet)

        XCTAssertEqual(first, second)
        XCTAssertEqual(first.count, 16)

        let mutated = BitchatPacket(
            type: packet.type,
            senderID: packet.senderID,
            recipientID: packet.recipientID,
            timestamp: packet.timestamp,
            payload: Data([0x10, 0x20, 0x31]),
            signature: packet.signature,
            ttl: packet.ttl
        )

        let mutatedId = PacketIdUtil.computeId(mutated)
        XCTAssertNotEqual(first, mutatedId)
    }
}

final class FileTransferLimitsTests: XCTestCase {
    func testFileTransferPayloadLimits() {
        XCTAssertTrue(FileTransferLimits.isValidPayload(FileTransferLimits.maxPayloadBytes))
        XCTAssertFalse(FileTransferLimits.isValidPayload(FileTransferLimits.maxPayloadBytes + 1))
        XCTAssertGreaterThan(FileTransferLimits.maxFramedFileBytes, FileTransferLimits.maxPayloadBytes)
    }
}
