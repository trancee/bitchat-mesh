import XCTest
@testable import BitchatMesh

final class LocationChannelTests: XCTestCase {
    func testGeohashChannelLevelCodableCompatibility() throws {
        let decoder = JSONDecoder()
        XCTAssertEqual(try decoder.decode(GeohashChannelLevel.self, from: Data("\"region\"".utf8)), .province)
        XCTAssertEqual(try decoder.decode(GeohashChannelLevel.self, from: Data("\"country\"".utf8)), .region)
        XCTAssertEqual(try decoder.decode(GeohashChannelLevel.self, from: Data("5".utf8)), .city)
    }

    func testChannelIdDisplayNameAndFlags() {
        let geo = GeohashChannel(level: .city, geohash: "u4pruy")
        let mesh = ChannelID.mesh
        let location = ChannelID.location(geo)

        XCTAssertEqual(mesh.displayName, "Mesh")
        XCTAssertTrue(mesh.isMesh)
        XCTAssertFalse(mesh.isLocation)
        XCTAssertTrue(location.isLocation)
        XCTAssertFalse(location.isMesh)
        XCTAssertTrue(location.displayName.contains("u4pruy"))
    }
}

final class PacketTLVTests: XCTestCase {
    func testAnnouncementPacketEncodeDecode() {
        let packet = AnnouncementPacket(
            nickname: "alice",
            noisePublicKey: Data(repeating: 0x01, count: 32),
            signingPublicKey: Data(repeating: 0x02, count: 32),
            directNeighbors: [Data(repeating: 0x03, count: 8), Data(repeating: 0x04, count: 8)]
        )

        let data = packet.encode()
        XCTAssertNotNil(data)
        let decoded = AnnouncementPacket.decode(from: data ?? Data())
        XCTAssertEqual(decoded?.nickname, packet.nickname)
        XCTAssertEqual(decoded?.noisePublicKey, packet.noisePublicKey)
        XCTAssertEqual(decoded?.signingPublicKey, packet.signingPublicKey)
        XCTAssertEqual(decoded?.directNeighbors?.count, 2)
    }

    func testAnnouncementPacketRejectsInvalidFields() {
        let invalid = AnnouncementPacket(
            nickname: "",
            noisePublicKey: Data(repeating: 0x01, count: 32),
            signingPublicKey: Data(repeating: 0x02, count: 32),
            directNeighbors: nil
        )
        XCTAssertNil(invalid.encode())
    }

    func testPrivateMessagePacketEncodeDecode() {
        let packet = PrivateMessagePacket(messageID: "msg-1", content: "hello")
        let data = packet.encode()
        XCTAssertNotNil(data)
        let decoded = PrivateMessagePacket.decode(from: data ?? Data())
        XCTAssertEqual(decoded?.messageID, "msg-1")
        XCTAssertEqual(decoded?.content, "hello")
    }
}

final class NoiseLimiterTests: XCTestCase {
    func testHandshakeRateLimitPerPeer() {
        let limiter = NoiseRateLimiter()
        let peer = PeerID(str: "peer-1")

        for _ in 0..<NoiseSecurityConstants.maxHandshakesPerMinute {
            XCTAssertTrue(limiter.allowHandshake(from: peer))
        }
        XCTAssertFalse(limiter.allowHandshake(from: peer))
    }

    func testMessageRateLimitPerPeer() {
        let limiter = NoiseRateLimiter()
        let peer = PeerID(str: "peer-2")

        for _ in 0..<NoiseSecurityConstants.maxMessagesPerSecond {
            XCTAssertTrue(limiter.allowMessage(from: peer))
        }
        XCTAssertFalse(limiter.allowMessage(from: peer))
    }

    func testResetAllowsNewHandshake() {
        let limiter = NoiseRateLimiter()
        let peer = PeerID(str: "peer-3")

        for _ in 0..<NoiseSecurityConstants.maxHandshakesPerMinute {
            _ = limiter.allowHandshake(from: peer)
        }
        XCTAssertFalse(limiter.allowHandshake(from: peer))

        limiter.reset(for: peer)
        let waitForReset = expectation(description: "wait for reset")
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.05) {
            waitForReset.fulfill()
        }
        wait(for: [waitForReset], timeout: 1.0)

        XCTAssertTrue(limiter.allowHandshake(from: peer))
    }

    func testGlobalHandshakeLimit() {
        let limiter = NoiseRateLimiter()
        let peers = (0..<NoiseSecurityConstants.maxGlobalHandshakesPerMinute).map { PeerID(str: "peer-\($0)") }

        for peer in peers {
            XCTAssertTrue(limiter.allowHandshake(from: peer))
        }

        XCTAssertFalse(limiter.allowHandshake(from: PeerID(str: "peer-over")))
    }

    func testResetAllClearsLimits() {
        let limiter = NoiseRateLimiter()
        let peer = PeerID(str: "peer-reset")

        for _ in 0..<NoiseSecurityConstants.maxMessagesPerSecond {
            _ = limiter.allowMessage(from: peer)
        }
        XCTAssertFalse(limiter.allowMessage(from: peer))

        limiter.resetAll()
        let waitForReset = expectation(description: "wait for reset all")
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.05) {
            waitForReset.fulfill()
        }
        wait(for: [waitForReset], timeout: 1.0)

        XCTAssertTrue(limiter.allowMessage(from: peer))
    }
}

final class NoiseSecurityValidatorTests: XCTestCase {
    func testMessageAndHandshakeSizeValidation() {
        XCTAssertTrue(NoiseSecurityValidator.validateMessageSize(Data(repeating: 0x00, count: 10)))
        XCTAssertFalse(NoiseSecurityValidator.validateMessageSize(Data(repeating: 0x00, count: NoiseSecurityConstants.maxMessageSize + 1)))

        XCTAssertTrue(NoiseSecurityValidator.validateHandshakeMessageSize(Data(repeating: 0x00, count: 10)))
        XCTAssertFalse(NoiseSecurityValidator.validateHandshakeMessageSize(Data(repeating: 0x00, count: NoiseSecurityConstants.maxHandshakeMessageSize + 1)))
    }
}
