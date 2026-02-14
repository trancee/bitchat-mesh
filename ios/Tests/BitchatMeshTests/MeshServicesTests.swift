import XCTest
@testable import BitchatMesh

final class MeshTopologyTrackerTests: XCTestCase {
    func testComputeRouteFindsIntermediateHop() {
        let tracker = MeshTopologyTracker()
        let a = Data([0x01])
        let b = Data([0x02])
        let c = Data([0x03])

        tracker.updateNeighbors(for: a, neighbors: [b])
        tracker.updateNeighbors(for: b, neighbors: [a, c])
        tracker.updateNeighbors(for: c, neighbors: [b])

        let route = tracker.computeRoute(from: a, to: c)
        XCTAssertEqual(route, [pad(b)])
        XCTAssertEqual(tracker.computeRoute(from: a, to: a), [])
    }

    func testRemoveAndPruneClearRoutes() {
        let tracker = MeshTopologyTracker()
        let a = Data([0x01])
        let b = Data([0x02])

        tracker.updateNeighbors(for: a, neighbors: [b])
        tracker.updateNeighbors(for: b, neighbors: [a])

        tracker.removePeer(b)
        XCTAssertNil(tracker.computeRoute(from: a, to: b))

        tracker.updateNeighbors(for: b, neighbors: [a])
        tracker.prune(olderThan: 0)
        XCTAssertNil(tracker.computeRoute(from: a, to: b))
    }

    func testRouteRequiresMutualClaims() {
        let tracker = MeshTopologyTracker()
        let a = Data([0x01])
        let b = Data([0x02])
        let c = Data([0x03])

        tracker.updateNeighbors(for: a, neighbors: [b])
        tracker.updateNeighbors(for: b, neighbors: [c])
        tracker.updateNeighbors(for: c, neighbors: [])

        XCTAssertNil(tracker.computeRoute(from: a, to: c))
    }

    func testSanitizePadsAndTruncatesRoutingIds() {
        let tracker = MeshTopologyTracker()
        let longA = Data(repeating: 0x01, count: 10)
        let shortB = Data([0x02])
        let exactC = Data(repeating: 0x03, count: 8)

        tracker.updateNeighbors(for: longA, neighbors: [shortB])
        tracker.updateNeighbors(for: shortB, neighbors: [longA, exactC])
        tracker.updateNeighbors(for: exactC, neighbors: [shortB])

        let route = tracker.computeRoute(from: Data(repeating: 0x01, count: 8), to: exactC)
        XCTAssertEqual(route, [pad(shortB)])
    }

    func testComputeRouteReturnsNilForInvalidInputs() {
        let tracker = MeshTopologyTracker()
        XCTAssertNil(tracker.computeRoute(from: nil, to: Data([0x01])))
        XCTAssertNil(tracker.computeRoute(from: Data(), to: Data([0x01])))
        XCTAssertNil(tracker.computeRoute(from: Data([0x01]), to: Data()))
    }

    private func pad(_ data: Data) -> Data {
        var value = data
        if value.count < 8 {
            value.append(Data(repeating: 0, count: 8 - value.count))
        } else if value.count > 8 {
            value = Data(value.prefix(8))
        }
        return value
    }
}

final class NotificationStreamAssemblerTests: XCTestCase {
    func testReassemblesSplitFrame() {
        let packet = BitchatPacket(
            type: 0x02,
            senderID: Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]),
            recipientID: nil,
            timestamp: 1_234_567_890,
            payload: Data("hello".utf8),
            signature: nil,
            ttl: 3
        )
        guard let data = BinaryProtocol.encode(packet) else {
            XCTFail("Failed to encode packet")
            return
        }

        var assembler = NotificationStreamAssembler()
        let prefix = data.prefix(5)
        let suffix = data.dropFirst(5)

        let first = assembler.append(Data(prefix))
        XCTAssertTrue(first.frames.isEmpty)
        XCTAssertFalse(first.reset)

        let second = assembler.append(Data(suffix))
        XCTAssertEqual(second.frames.count, 1)
        XCTAssertEqual(second.frames.first, data)
    }

    func testDropsInvalidVersionPrefixes() {
        var assembler = NotificationStreamAssembler()
        let result = assembler.append(Data([0xFF, 0x01, 0x02]))
        XCTAssertEqual(result.droppedPrefixes.first, 0xFF)
    }

    func testOverflowResetsAssembler() {
        var assembler = NotificationStreamAssembler()
        let oversized = Data(repeating: 0x01, count: TransportConfig.bleNotificationAssemblerHardCapBytes + 1)
        let result = assembler.append(oversized)
        XCTAssertTrue(result.reset)
        XCTAssertTrue(result.frames.isEmpty)
    }

    func testStalledFrameResetsAfterTimeout() {
        let packet = BitchatPacket(
            type: 0x02,
            senderID: Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]),
            recipientID: nil,
            timestamp: 1_234_567_890,
            payload: Data("hello".utf8),
            signature: nil,
            ttl: 3
        )
        guard let data = BinaryProtocol.encode(packet) else {
            XCTFail("Failed to encode packet")
            return
        }

        var assembler = NotificationStreamAssembler()
        let first = assembler.append(Data(data.prefix(5)))
        XCTAssertFalse(first.reset)

        usleep(UInt32(TransportConfig.bleAssemblerStallResetMs + 20) * 1000)
        let second = assembler.append(Data([0x00]))
        XCTAssertTrue(second.reset)
    }

    func testZeroFilledBufferResetsSilently() {
        var assembler = NotificationStreamAssembler()
        let result = assembler.append(Data(repeating: 0x00, count: 6))
        XCTAssertTrue(result.frames.isEmpty)
        XCTAssertTrue(result.droppedPrefixes.isEmpty)
    }

    func testCompressedPayloadLengthTooShortResets() {
        var buffer = Data()
        buffer.append(1)
        buffer.append(0x02)
        buffer.append(0x01)
        buffer.append(contentsOf: Array(repeating: 0x00, count: 8))
        buffer.append(BinaryProtocol.Flags.isCompressed)
        buffer.append(0x00)
        buffer.append(0x01)
        buffer.append(contentsOf: Array(repeating: 0x00, count: BinaryProtocol.senderIDSize))

        var assembler = NotificationStreamAssembler()
        let result = assembler.append(buffer)
        XCTAssertTrue(result.reset)
        XCTAssertTrue(result.frames.isEmpty)
    }

    func testRouteFramesAssembledCorrectly() {
        let packet = BitchatPacket(
            type: 0x02,
            senderID: Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]),
            recipientID: Data([0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10]),
            timestamp: 1_234_567_890,
            payload: Data("route".utf8),
            signature: nil,
            ttl: 3,
            version: 1,
            route: [Data([0xAA]), Data([0xBB])]
        )
        guard let data = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Failed to encode packet")
            return
        }

        var assembler = NotificationStreamAssembler()
        let first = assembler.append(Data(data.prefix(10)))
        XCTAssertTrue(first.frames.isEmpty)

        let second = assembler.append(Data(data.dropFirst(10)))
        XCTAssertEqual(second.frames.first, data)
    }

    func testRouteCountOverflowDropsFrame() {
        let packet = BitchatPacket(
            type: 0x02,
            senderID: Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]),
            recipientID: nil,
            timestamp: 1_234_567_890,
            payload: Data("bad".utf8),
            signature: nil,
            ttl: 3,
            version: 2,
            route: [Data([0xAA])]
        )
        guard var data = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Failed to encode packet")
            return
        }

        let flagsIndex = BinaryProtocol.Offsets.flags
        data[flagsIndex] |= BinaryProtocol.Flags.hasRoute

        let senderOffset = BinaryProtocol.v2HeaderSize
        let routeCountOffset = senderOffset + BinaryProtocol.senderIDSize
        guard routeCountOffset < data.count else {
            XCTFail("Invalid data layout")
            return
        }
        data[routeCountOffset] = 2

        let hopSize = BinaryProtocol.senderIDSize
        let bytesToRemove = hopSize
        let removeStart = routeCountOffset + 1 + hopSize
        if removeStart + bytesToRemove <= data.count {
            data.removeSubrange(removeStart..<(removeStart + bytesToRemove))
        }

        var assembler = NotificationStreamAssembler()
        let result = assembler.append(data)
        XCTAssertTrue(result.frames.isEmpty)
    }

    func testRecipientAndSignatureFramesAssembled() {
        let signature = Data(repeating: 0xAB, count: BinaryProtocol.signatureSize)
        let packet = BitchatPacket(
            type: 0x02,
            senderID: Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]),
            recipientID: Data([0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10]),
            timestamp: 1_234_567_890,
            payload: Data("sig".utf8),
            signature: signature,
            ttl: 3,
            version: 1
        )
        guard let data = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Failed to encode packet")
            return
        }

        var assembler = NotificationStreamAssembler()
        let first = assembler.append(Data(data.prefix(15)))
        XCTAssertTrue(first.frames.isEmpty)
        let second = assembler.append(Data(data.dropFirst(15)))
        XCTAssertEqual(second.frames.first, data)
    }
}

final class RelayControllerTests: XCTestCase {
    func testRelayDecisionBranches() {
        let suppressed = RelayController.decide(
            ttl: 1,
            senderIsSelf: false,
            isEncrypted: false,
            isDirectedEncrypted: false,
            isFragment: false,
            isDirectedFragment: false,
            isHandshake: false,
            isAnnounce: false,
            degree: 0,
            highDegreeThreshold: TransportConfig.bleHighDegreeThreshold
        )
        XCTAssertFalse(suppressed.shouldRelay)

        let handshake = RelayController.decide(
            ttl: 5,
            senderIsSelf: false,
            isEncrypted: false,
            isDirectedEncrypted: false,
            isFragment: false,
            isDirectedFragment: false,
            isHandshake: true,
            isAnnounce: false,
            degree: 1,
            highDegreeThreshold: TransportConfig.bleHighDegreeThreshold
        )
        XCTAssertTrue(handshake.shouldRelay)
        XCTAssertEqual(handshake.newTTL, 4)
        XCTAssertTrue((10...35).contains(handshake.delayMs))

        let fragment = RelayController.decide(
            ttl: 7,
            senderIsSelf: false,
            isEncrypted: false,
            isDirectedEncrypted: false,
            isFragment: true,
            isDirectedFragment: false,
            isHandshake: false,
            isAnnounce: false,
            degree: 1,
            highDegreeThreshold: TransportConfig.bleHighDegreeThreshold
        )
        XCTAssertTrue(fragment.shouldRelay)
        XCTAssertEqual(fragment.newTTL, 4)
        XCTAssertTrue((TransportConfig.bleFragmentRelayMinDelayMs...TransportConfig.bleFragmentRelayMaxDelayMs).contains(fragment.delayMs))

        let broadcast = RelayController.decide(
            ttl: 7,
            senderIsSelf: false,
            isEncrypted: false,
            isDirectedEncrypted: false,
            isFragment: false,
            isDirectedFragment: false,
            isHandshake: false,
            isAnnounce: true,
            degree: 1,
            highDegreeThreshold: TransportConfig.bleHighDegreeThreshold
        )
        XCTAssertTrue(broadcast.shouldRelay)
        XCTAssertEqual(broadcast.newTTL, 6)
        XCTAssertTrue((10...40).contains(broadcast.delayMs))
    }
}

final class TransferProgressManagerTests: XCTestCase {
    func testProgressLifecycleEmitsEvents() {
        let manager = TransferProgressManager.shared
        let exp = expectation(description: "progress events")
        exp.expectedFulfillmentCount = 3

        var events: [TransferProgressEvent] = []
        let id = manager.addObserver { event in
            events.append(event)
            exp.fulfill()
        }

        manager.start(id: "transfer-1", totalFragments: 2)
        manager.recordFragmentSent(id: "transfer-1")
        manager.recordFragmentSent(id: "transfer-1")

        wait(for: [exp], timeout: 1.0)
        manager.removeObserver(id)

        XCTAssertEqual(events.first?.sent, 0)
        XCTAssertEqual(events.last?.sent, 2)
        XCTAssertEqual(events.last?.total, 2)
        XCTAssertTrue(events.last?.completed ?? false)
    }
}

final class GCSFilterTests: XCTestCase {
    func testBuildAndDecodeFilter() {
        let ids = [
            Data([0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F]),
            Data([0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F]),
            Data([0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F])
        ]

        let params = GCSFilter.buildFilter(ids: ids, maxBytes: 128, targetFpr: 0.01)
        let decoded = GCSFilter.decodeToSortedSet(p: params.p, m: params.m, data: params.data)

        for id in ids {
            let bucket = GCSFilter.bucket(for: id, modulus: params.m)
            XCTAssertTrue(GCSFilter.contains(sortedValues: decoded, candidate: bucket))
        }
    }

    func testBuildFilterHandlesEmptyInput() {
        let params = GCSFilter.buildFilter(ids: [], maxBytes: 64, targetFpr: 0.02)
        XCTAssertEqual(params.m, 1)
        XCTAssertTrue(params.data.isEmpty)
    }

    func testEstimateAndDeriveHelpers() {
        let p = GCSFilter.deriveP(targetFpr: 0.02)
        XCTAssertGreaterThanOrEqual(p, 1)
        let maxElements = GCSFilter.estimateMaxElements(sizeBytes: 32, p: p)
        XCTAssertGreaterThan(maxElements, 0)
    }
}
