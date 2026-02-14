import XCTest
@testable import BitchatMesh

final class BinaryProtocolTests: XCTestCase {
    func testBinaryProtocolEncodeDecodeV1WithRecipientSignatureAndRoute() {
        let senderID = Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08])
        let recipientID = Data([0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17])
        let payload = Data("hello".utf8)
        let signature = Data(repeating: 0xAA, count: 64)
        let route = [
            Data([0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09]),
            Data([0x0B, 0x0B, 0x0B, 0x0B])
        ]

        let packet = BitchatPacket(
            type: 0x02,
            senderID: senderID,
            recipientID: recipientID,
            timestamp: 1_700_000_000_000,
            payload: payload,
            signature: signature,
            ttl: 7,
            version: 1,
            route: route
        )

        guard let encoded = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Expected encoded data")
            return
        }
        guard let decoded = BinaryProtocol.decode(encoded) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.version, packet.version)
        XCTAssertEqual(decoded.type, packet.type)
        XCTAssertEqual(decoded.senderID, senderID)
        XCTAssertEqual(decoded.recipientID, recipientID)
        XCTAssertEqual(decoded.timestamp, packet.timestamp)
        XCTAssertEqual(decoded.payload, payload)
        XCTAssertEqual(decoded.signature, signature)
        XCTAssertEqual(decoded.ttl, packet.ttl)

        XCTAssertEqual(decoded.route?.count, 2)
        XCTAssertEqual(decoded.route?.first, route[0])
        let expectedSecondHop = Data([0x0B, 0x0B, 0x0B, 0x0B, 0x00, 0x00, 0x00, 0x00])
        XCTAssertEqual(decoded.route?.last, expectedSecondHop)
    }

    func testBinaryProtocolEncodeDecodeV2WithoutOptionalFields() {
        let senderID = Data([0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28])
        let payload = Data([0x00, 0x01, 0x02])

        let packet = BitchatPacket(
            type: 0x11,
            senderID: senderID,
            recipientID: nil,
            timestamp: 1_700_000_000_123,
            payload: payload,
            signature: nil,
            ttl: 1,
            version: 2,
            route: nil
        )

        guard let encoded = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Expected encoded data")
            return
        }
        guard let decoded = BinaryProtocol.decode(encoded) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.version, packet.version)
        XCTAssertEqual(decoded.type, packet.type)
        XCTAssertEqual(decoded.senderID, senderID)
        XCTAssertNil(decoded.recipientID)
        XCTAssertEqual(decoded.timestamp, packet.timestamp)
        XCTAssertEqual(decoded.payload, payload)
        XCTAssertNil(decoded.signature)
        XCTAssertEqual(decoded.ttl, packet.ttl)
        XCTAssertNil(decoded.route)
    }

    func testBinaryProtocolCompressionRoundTripSetsFlag() {
        let senderID = Data([0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38])
        let payload = Data(repeating: 0x41, count: 200)

        let packet = BitchatPacket(
            type: 0x02,
            senderID: senderID,
            recipientID: nil,
            timestamp: 1_700_000_001_000,
            payload: payload,
            signature: nil,
            ttl: 3,
            version: 1,
            route: nil
        )

        guard let encoded = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Expected encoded data")
            return
        }
        XCTAssertTrue((encoded[11] & 0x04) != 0)

        guard let decoded = BinaryProtocol.decode(encoded) else {
            XCTFail("Expected decoded packet")
            return
        }
        XCTAssertEqual(decoded.payload, payload)
    }

    func testBinaryProtocolDecodeHandlesPadding() {
        let senderID = Data([0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48])
        let payload = Data("padded".utf8)
        let packet = BitchatPacket(
            type: 0x01,
            senderID: senderID,
            recipientID: nil,
            timestamp: 1_700_000_002_000,
            payload: payload,
            signature: nil,
            ttl: 2,
            version: 1,
            route: nil
        )

        guard let padded = BinaryProtocol.encode(packet, padding: true) else {
            XCTFail("Expected padded data")
            return
        }
        guard let unpadded = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Expected unpadded data")
            return
        }

        XCTAssertGreaterThanOrEqual(padded.count, unpadded.count)
        let decoded = BinaryProtocol.decode(padded)
        XCTAssertEqual(decoded?.payload, payload)
    }

    func testBinaryProtocolDecodeRejectsSuspiciousCompressionRatio() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.isCompressed)
        data.appendUInt16(3) // payload length (original size + compressed data)
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID
        data.appendUInt16(0xC351) // original size = 50001
        data.append(0x00) // compressed byte

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsOversizedOriginalLength() {
        var data = Data()
        data.append(2) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.isCompressed)
        data.appendUInt32(5) // payload length (original size + compressed data)
        data.append(contentsOf: Array(repeating: 0xBB, count: 8)) // senderID
        data.appendUInt32(UInt32(FileTransferLimits.maxFramedFileBytes + 1))
        data.append(0x00) // compressed byte

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsInvalidVersion() {
        var data = Data(repeating: 0, count: BinaryProtocol.v1HeaderSize + BinaryProtocol.senderIDSize)
        data[0] = 3
        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsShortHeader() {
        let data = Data(repeating: 0, count: BinaryProtocol.v1HeaderSize + BinaryProtocol.senderIDSize - 1)
        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsTruncatedPayload() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(0x00) // flags
        data.append(0x00) // length high
        data.append(0x01) // length low (1 byte payload)
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID
        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsMissingRecipient() {
        let senderID = Data([0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58])
        let recipientID = Data([0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68])
        let payload = Data([0x01, 0x02, 0x03])
        let packet = BitchatPacket(
            type: 0x02,
            senderID: senderID,
            recipientID: recipientID,
            timestamp: 1_700_000_003_000,
            payload: payload,
            signature: nil,
            ttl: 2,
            version: 1,
            route: nil
        )

        guard let encoded = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Expected encoded data")
            return
        }
        let truncated = encoded.prefix(BinaryProtocol.v1HeaderSize + BinaryProtocol.senderIDSize)
        XCTAssertNil(BinaryProtocol.decode(Data(truncated)))
    }

    func testBinaryProtocolDecodeRejectsMissingSignatureBytes() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.hasSignature)
        data.appendUInt16(0) // payload length
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsRouteCountOutOfBounds() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.hasRoute)
        data.appendUInt16(1) // payload length includes only route count
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID
        data.append(0x02) // route count without hops

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsCompressedMissingOriginalSize() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.isCompressed)
        data.appendUInt16(0) // payload length
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsCompressedDecompressionFailure() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.isCompressed)
        data.appendUInt16(3) // original size (2) + 1 byte compressed
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID
        data.appendUInt16(2) // original size
        data.append(0xFF) // invalid compressed data

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsPayloadLengthOverMax() {
        var data = Data()
        data.append(2) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(0x00) // flags
        let oversized = UInt32(FileTransferLimits.maxFramedFileBytes + 1)
        data.append(UInt8((oversized >> 24) & 0xFF))
        data.append(UInt8((oversized >> 16) & 0xFF))
        data.append(UInt8((oversized >> 8) & 0xFF))
        data.append(UInt8(oversized & 0xFF))
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsPartialRouteHop() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.hasRoute)
        data.appendUInt16(0) // payload length
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID
        data.append(0x01) // route count
        data.append(contentsOf: [0x01, 0x02, 0x03, 0x04]) // partial hop

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsPayloadLengthIncludingRouteBytes() {
        let senderID = Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08])
        let payload = Data("route".utf8)
        let route = [Data([0xAA]), Data([0xBB])]

        let packet = BitchatPacket(
            type: 0x02,
            senderID: senderID,
            recipientID: nil,
            timestamp: 1_700_000_000_000,
            payload: payload,
            signature: nil,
            ttl: 1,
            version: 1,
            route: route
        )

        guard var encoded = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Expected encoded data")
            return
        }

        let routeBytes = 1 + route.count * BinaryProtocol.senderIDSize
        let payloadLength = UInt16(payload.count + routeBytes)
        encoded[12] = UInt8((payloadLength >> 8) & 0xFF)
        encoded[13] = UInt8(payloadLength & 0xFF)

        XCTAssertNil(BinaryProtocol.decode(encoded))
    }

    func testBinaryProtocolDecodeRejectsV2PartialRecipientID() {
        let packet = BitchatPacket(
            type: 0x02,
            senderID: Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]),
            recipientID: Data([0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10]),
            timestamp: 1_700_000_000_000,
            payload: Data([0x01]),
            signature: nil,
            ttl: 1,
            version: 2,
            route: nil
        )

        guard var encoded = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Expected encoded data")
            return
        }

        encoded.removeLast()
        XCTAssertNil(BinaryProtocol.decode(encoded))
    }

    func testBinaryProtocolDecodeRejectsV2MissingSignatureBytes() {
        let signature = Data(repeating: 0xAA, count: 64)
        let packet = BitchatPacket(
            type: 0x02,
            senderID: Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]),
            recipientID: nil,
            timestamp: 1_700_000_000_000,
            payload: Data([0x01]),
            signature: signature,
            ttl: 1,
            version: 2,
            route: nil
        )

        guard var encoded = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Expected encoded data")
            return
        }

        encoded.removeLast(10)
        XCTAssertNil(BinaryProtocol.decode(encoded))
    }

    func testBinaryProtocolDecodeRejectsV2RouteMissingHopWithRecipient() {
        var data = Data()
        data.append(2) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.hasRecipient | BinaryProtocol.Flags.hasRoute)
        data.append(contentsOf: [0x00, 0x00, 0x00, 0x00]) // payload length
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID
        data.append(contentsOf: Array(repeating: 0xBB, count: 8)) // recipientID
        data.append(0x01) // route count but no hop bytes

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsV2RouteCountMismatch() {
        let route = [Data([0xAA]), Data([0xBB])]
        let packet = BitchatPacket(
            type: 0x02,
            senderID: Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]),
            recipientID: nil,
            timestamp: 1_700_000_000_000,
            payload: Data([0x01]),
            signature: nil,
            ttl: 1,
            version: 2,
            route: route
        )

        guard var encoded = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Expected encoded data")
            return
        }

        let senderOffset = BinaryProtocol.v2HeaderSize
        let routeCountOffset = senderOffset + BinaryProtocol.senderIDSize
        if routeCountOffset < encoded.count {
            encoded[routeCountOffset] = 3
        }

        XCTAssertNil(BinaryProtocol.decode(encoded))
    }

    func testBinaryProtocolDecodeV2CompressedWithRoute() {
        let senderID = Data([0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18])
        let payload = Data(repeating: 0x41, count: 200)
        let route = [Data([0xAA]), Data([0xBB, 0xCC])]

        let packet = BitchatPacket(
            type: 0x02,
            senderID: senderID,
            recipientID: nil,
            timestamp: 1_700_000_000_999,
            payload: payload,
            signature: nil,
            ttl: 3,
            version: 2,
            route: route
        )

        guard let encoded = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Expected encoded data")
            return
        }

        XCTAssertTrue((encoded[BinaryProtocol.Offsets.flags] & BinaryProtocol.Flags.isCompressed) != 0)
        XCTAssertTrue((encoded[BinaryProtocol.Offsets.flags] & BinaryProtocol.Flags.hasRoute) != 0)

        guard let decoded = BinaryProtocol.decode(encoded) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.version, 2)
        XCTAssertEqual(decoded.payload, payload)
        XCTAssertEqual(decoded.route?.count, 2)
    }

    func testBinaryProtocolDecodeV2CompressedWithRecipientAndSignature() {
        let senderID = Data([0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28])
        let recipientID = Data([0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38])
        let payload = Data(repeating: 0x42, count: 200)
        let signature = Data(repeating: 0xCC, count: 64)

        let packet = BitchatPacket(
            type: 0x02,
            senderID: senderID,
            recipientID: recipientID,
            timestamp: 1_700_000_001_111,
            payload: payload,
            signature: signature,
            ttl: 4,
            version: 2,
            route: nil
        )

        guard let encoded = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Expected encoded data")
            return
        }

        let flags = encoded[BinaryProtocol.Offsets.flags]
        XCTAssertTrue((flags & BinaryProtocol.Flags.isCompressed) != 0)
        XCTAssertTrue((flags & BinaryProtocol.Flags.hasRecipient) != 0)
        XCTAssertTrue((flags & BinaryProtocol.Flags.hasSignature) != 0)

        guard let decoded = BinaryProtocol.decode(encoded) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.version, 2)
        XCTAssertEqual(decoded.payload, payload)
        XCTAssertEqual(decoded.recipientID, recipientID)
        XCTAssertEqual(decoded.signature, signature)
    }

    func testBinaryProtocolDecodeRejectsCorruptedPadding() {
        let senderID = Data([0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48])
        let payload = Data("padded".utf8)
        let packet = BitchatPacket(
            type: 0x01,
            senderID: senderID,
            recipientID: nil,
            timestamp: 1_700_000_002_000,
            payload: payload,
            signature: nil,
            ttl: 2,
            version: 1,
            route: nil
        )

        guard var padded = BinaryProtocol.encode(packet, padding: true) else {
            XCTFail("Expected padded data")
            return
        }

        if let last = padded.last {
            padded[padded.count - 1] = last &+ 1
        }

        XCTAssertNil(BinaryProtocol.decode(padded))
    }

    func testBinaryProtocolDecodeRejectsRouteMissingCount() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.hasRoute)
        data.appendUInt16(0) // payload length
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsRouteWithInsufficientHopBytes() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.hasRoute)
        data.appendUInt16(1 + 7) // route count + 7 bytes (short)
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID
        data.append(0x01) // route count
        data.append(contentsOf: Array(repeating: 0xBB, count: 7))

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsCompressedLengthTooShort() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.isCompressed)
        data.appendUInt16(1) // payload length shorter than size field
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsCompressedWithoutPayloadBytes() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.isCompressed)
        data.appendUInt16(2) // payload length == size field only
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID
        data.appendUInt16(1) // original size

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsInvalidPaddingBytes() {
        var data = Data(repeating: 0, count: BinaryProtocol.v1HeaderSize + BinaryProtocol.senderIDSize - 1)
        data.append(0x00) // invalid PKCS#7 padding byte

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsTruncatedTimestamp() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 7)) // timestamp short by 1

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsMissingLengthFieldForV1() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(0x00) // flags
        data.append(0x00) // only 1 byte of length

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsMissingLengthFieldForV2() {
        var data = Data()
        data.append(2) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(0x00) // flags
        data.append(0x00) // only 1 byte of length

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeIgnoresTrailingBytesAfterSignature() {
        let senderID = Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08])
        let signature = Data(repeating: 0xAA, count: 64)
        let packet = BitchatPacket(
            type: 0x02,
            senderID: senderID,
            recipientID: nil,
            timestamp: 1_700_000_000_000,
            payload: Data(),
            signature: signature,
            ttl: 1,
            version: 1,
            route: nil
        )

        guard var encoded = BinaryProtocol.encode(packet, padding: false) else {
            XCTFail("Expected encoded data")
            return
        }
        encoded.append(0xFF)

        let decoded = BinaryProtocol.decode(encoded)
        XCTAssertEqual(decoded?.signature, signature)
    }

    func testBinaryProtocolDecodeRejectsRecipientWithoutLengthField() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.hasRecipient)

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsPayloadLengthMismatch() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(0x00) // flags
        data.appendUInt16(4) // payload length
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID
        data.append(contentsOf: [0x01, 0x02]) // only 2 bytes

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsPartialRecipientID() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.hasRecipient)
        data.appendUInt16(0) // payload length
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID
        data.append(contentsOf: Array(repeating: 0xBB, count: 4)) // partial recipient

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsRouteCountOverflow() {
        var data = Data()
        data.append(1) // version
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(BinaryProtocol.Flags.hasRoute)
        data.appendUInt16(1) // payload length includes only route count
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID
        data.append(0xFF) // route count

        XCTAssertNil(BinaryProtocol.decode(data))
    }

    func testBinaryProtocolDecodeRejectsVersionLengthMismatch() {
        var data = Data()
        data.append(2) // version expects 4-byte length
        data.append(0x01) // type
        data.append(0x01) // ttl
        data.append(contentsOf: Array(repeating: 0, count: 8)) // timestamp
        data.append(0x00) // flags
        data.appendUInt16(0) // v1-style length only
        data.append(contentsOf: Array(repeating: 0xAA, count: 8)) // senderID

        XCTAssertNil(BinaryProtocol.decode(data))
    }
}
