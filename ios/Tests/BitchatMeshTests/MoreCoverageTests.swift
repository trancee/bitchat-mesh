import XCTest
import UniformTypeIdentifiers
@testable import BitchatMesh

final class MessagePaddingTests: XCTestCase {
    func testPadAndUnpadRoundTrip() {
        let data = Data([0x01, 0x02, 0x03])
        let padded = MessagePadding.pad(data, toSize: 10)
        XCTAssertEqual(padded.count, 10)
        XCTAssertEqual(padded.last, 7)
        XCTAssertEqual(MessagePadding.unpad(padded), data)
    }

    func testPadDoesNothingWhenTargetIsTooSmallOrTooLarge() {
        let data = Data([0x01, 0x02, 0x03])
        XCTAssertEqual(MessagePadding.pad(data, toSize: 3), data)
        XCTAssertEqual(MessagePadding.pad(data, toSize: 300), data)
    }

    func testUnpadRejectsInvalidPadding() {
        let data = Data([0x01, 0x02, 0x03, 0x10])
        XCTAssertEqual(MessagePadding.unpad(data), data)
    }

    func testOptimalBlockSizeSelection() {
        XCTAssertEqual(MessagePadding.optimalBlockSize(for: 1), 256)
        XCTAssertEqual(MessagePadding.optimalBlockSize(for: 300), 512)
        XCTAssertEqual(MessagePadding.optimalBlockSize(for: 3000), 3000)
    }
}

final class CommandInfoTests: XCTestCase {
    func testAliasesAndPlaceholders() {
        XCTAssertEqual(CommandInfo.block.alias, "/block")
        XCTAssertNotNil(CommandInfo.block.placeholder)
        XCTAssertNil(CommandInfo.clear.placeholder)
        XCTAssertNil(CommandInfo.who.placeholder)
    }

    func testAllCommandsIncludesExpectedEntries() {
        let all = CommandInfo.all(isGeoPublic: false, isGeoDM: false)
        XCTAssertEqual(all.count, 7)
        XCTAssertTrue(all.contains(.message))
        XCTAssertFalse(CommandInfo.hug.description.isEmpty)
    }
}

final class ReadReceiptTests: XCTestCase {
    func testJsonEncodeDecodeRoundTrip() {
        let receipt = ReadReceipt(originalMessageID: UUID().uuidString,
                                  readerID: PeerID(str: "0123456789abcdef"),
                                  readerNickname: "alice")
        let data = receipt.encode()
        XCTAssertNotNil(data)
        let decoded = ReadReceipt.decode(from: data ?? Data())
        XCTAssertEqual(decoded?.originalMessageID, receipt.originalMessageID)
        XCTAssertEqual(decoded?.readerID, receipt.readerID)
        XCTAssertEqual(decoded?.readerNickname, receipt.readerNickname)
    }

    func testBinaryEncodeDecodeRoundTrip() {
        let receipt = ReadReceipt(originalMessageID: UUID().uuidString,
                                  readerID: PeerID(str: "0123456789abcdef"),
                                  readerNickname: "alice")
        let data = receipt.toBinaryData()
        let decoded = ReadReceipt.fromBinaryData(data)
        XCTAssertNotNil(decoded)
        XCTAssertEqual(decoded?.originalMessageID, receipt.originalMessageID)
        XCTAssertEqual(decoded?.readerID, receipt.readerID)
        XCTAssertEqual(decoded?.readerNickname, receipt.readerNickname)
    }

    func testBinaryDecodeRejectsInvalidTimestamp() {
        var data = Data()
        data.appendUUID(UUID().uuidString)
        data.appendUUID(UUID().uuidString)
        data.append(Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]))
        data.appendDate(Date(timeIntervalSinceNow: 1000))
        data.appendString("alice")

        XCTAssertNil(ReadReceipt.fromBinaryData(data))
    }
}

final class RequestSyncPacketTests: XCTestCase {
    func testEncodeDecodeRoundTrip() {
        let packet = RequestSyncPacket(
            p: 7,
            m: 123_456,
            data: Data([0x01, 0x02, 0x03]),
            types: SyncTypeFlags(messageTypes: [.announce, .message]),
            sinceTimestamp: 1_700_000_000,
            fragmentIdFilter: "frag-1"
        )

        let encoded = packet.encode()
        let decoded = RequestSyncPacket.decode(from: encoded)
        XCTAssertNotNil(decoded)
        XCTAssertEqual(decoded?.p, packet.p)
        XCTAssertEqual(decoded?.m, packet.m)
        XCTAssertEqual(decoded?.data, packet.data)
        XCTAssertEqual(decoded?.types?.toMessageTypes().sorted { $0.rawValue < $1.rawValue }, packet.types?.toMessageTypes().sorted { $0.rawValue < $1.rawValue })
        XCTAssertEqual(decoded?.sinceTimestamp, packet.sinceTimestamp)
        XCTAssertEqual(decoded?.fragmentIdFilter, packet.fragmentIdFilter)
    }

    func testDecodeRejectsMissingOrOversizedPayload() {
        var encoded = Data()
        encoded.append(0x01)
        encoded.append(0x00)
        encoded.append(0x01)
        encoded.append(0x07)

        encoded.append(0x02)
        encoded.append(0x00)
        encoded.append(0x04)
        encoded.append(contentsOf: [0x00, 0x00, 0x00, 0x01])

        XCTAssertNil(RequestSyncPacket.decode(from: encoded))

        let payload = Data(repeating: 0x01, count: 6)
        var encodedPayload = Data()
        encodedPayload.append(0x01)
        encodedPayload.append(0x00)
        encodedPayload.append(0x01)
        encodedPayload.append(0x07)
        encodedPayload.append(0x02)
        encodedPayload.append(0x00)
        encodedPayload.append(0x04)
        encodedPayload.append(contentsOf: [0x00, 0x00, 0x00, 0x01])
        encodedPayload.append(0x03)
        encodedPayload.append(0x00)
        encodedPayload.append(UInt8(payload.count))
        encodedPayload.append(payload)

        XCTAssertNil(RequestSyncPacket.decode(from: encodedPayload, maxAcceptBytes: 4))
    }
}

final class MimeTypeTests: XCTestCase {
    func testMimeTypeInitializationAndAllowedSet() {
        XCTAssertEqual(MimeType("image/jpeg"), .jpeg)
        XCTAssertEqual(MimeType("image/jpg"), .jpeg)
        XCTAssertNil(MimeType("application/unknown"))
        XCTAssertTrue(MimeType.jpeg.isAllowed)
        XCTAssertTrue(MimeType.allowed.contains(.png))
    }

    func testMimeTypeProperties() {
        XCTAssertEqual(MimeType.png.mimeString, "image/png")
        XCTAssertEqual(MimeType.mp3.defaultExtension, "mp3")
        XCTAssertEqual(MimeType.pdf.category, .file)
        XCTAssertEqual(MimeType.gif.utType, UTType.gif)
    }

    func testMimeTypeSignatureMatching() {
        XCTAssertFalse(MimeType.jpeg.matches(data: Data()))
        XCTAssertTrue(MimeType.octetStream.matches(data: Data([0x00])))

        XCTAssertTrue(MimeType.jpeg.matches(data: Data([0xFF, 0xD8, 0xFF, 0x00])))
        XCTAssertTrue(MimeType.png.matches(data: Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])))
        XCTAssertTrue(MimeType.gif.matches(data: Data([0x47, 0x49, 0x46, 0x38, 0x39, 0x61])))
        XCTAssertTrue(MimeType.webp.matches(data: Data([0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50])))

        XCTAssertTrue(MimeType.m4a.matches(data: Data(repeating: 0x00, count: 101)))
        XCTAssertTrue(MimeType.mp3.matches(data: Data([0x49, 0x44, 0x33, 0x00])))
        XCTAssertTrue(MimeType.mpeg.matches(data: Data([0xFF, 0xE2])))

        XCTAssertTrue(MimeType.wav.matches(data: Data([0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45])))
        XCTAssertTrue(MimeType.ogg.matches(data: Data([0x4F, 0x67, 0x67, 0x53])))
        XCTAssertTrue(MimeType.pdf.matches(data: Data([0x25, 0x50, 0x44, 0x46])))
    }

    func testAllCasesHaveMetadataAndMatchSamples() {
        for type in MimeType.allCases {
            XCTAssertFalse(type.mimeString.isEmpty)
            XCTAssertFalse(type.defaultExtension.isEmpty)
            _ = type.utType
            XCTAssertTrue(type.isAllowed)
            XCTAssertTrue(type.matches(data: sampleData(for: type)))
        }
    }

    private func sampleData(for type: MimeType) -> Data {
        switch type {
        case .jpeg, .jpg:
            return Data([0xFF, 0xD8, 0xFF, 0x00])
        case .png:
            return Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
        case .gif:
            return Data([0x47, 0x49, 0x46, 0x38, 0x39, 0x61])
        case .webp:
            return Data([0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50])
        case .mp4Audio, .m4a, .aac:
            return Data(repeating: 0x00, count: 101)
        case .mpeg, .mp3:
            return Data([0x49, 0x44, 0x33, 0x00])
        case .wav, .xWav:
            return Data([0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45])
        case .ogg:
            return Data([0x4F, 0x67, 0x67, 0x53])
        case .pdf:
            return Data([0x25, 0x50, 0x44, 0x46])
        case .octetStream:
            return Data([0x00])
        }
    }
}

final class PeerIDTests: XCTestCase {
    func testPeerIDParsingAndValidation() {
        let shortID = PeerID(str: "0123456789abcdef")
        XCTAssertTrue(shortID.isValid)
        XCTAssertTrue(shortID.isShort)

        let invalidHex = PeerID(str: "zzzzzzzzzzzzzzzz")
        XCTAssertFalse(invalidHex.isValid)

        let prefixed = PeerID(str: "noise:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        XCTAssertTrue(prefixed.isValid)
        XCTAssertTrue(prefixed.toShort().isShort)
    }

    func testRoutingDataConversion() {
        let shortID = PeerID(str: "0123456789abcdef")
        let routing = shortID.routingData
        XCTAssertEqual(routing?.count, 8)
        XCTAssertEqual(PeerID(routingData: routing ?? Data()), shortID)
    }
}

final class MeshConfigurationTests: XCTestCase {
    func testCustomConfigurationValues() {
        let config = MeshConfiguration(keychainService: "service", keychainAccessGroup: "group")
        XCTAssertEqual(config.keychainService, "service")
        XCTAssertEqual(config.keychainAccessGroup, "group")
        XCTAssertFalse(MeshConfiguration.default.keychainService.isEmpty)
    }
}

final class BitchatPacketTests: XCTestCase {
    func testBinarySigningDataResetsMutableFields() {
        let packet = BitchatPacket(
            type: 0x02,
            senderID: Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]),
            recipientID: Data([0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10]),
            timestamp: 1_234_567_890,
            payload: Data([0x10, 0x20, 0x30]),
            signature: Data([0xAA]),
            ttl: 9,
            isRSR: true
        )

        let signingData = packet.toBinaryDataForSigning()
        XCTAssertNotNil(signingData)

        let decoded = BitchatPacket.from(signingData ?? Data())
        XCTAssertEqual(decoded?.ttl, 0)
        XCTAssertNil(decoded?.signature)
        XCTAssertEqual(decoded?.type, packet.type)
        XCTAssertEqual(decoded?.senderID, packet.senderID)
        XCTAssertEqual(decoded?.payload, packet.payload)
    }

    func testConvenienceInitAndBinaryRoundTrip() {
        let packet = BitchatPacket(
            type: 0x02,
            ttl: 3,
            senderID: PeerID(str: "0011223344556677"),
            payload: Data([0x01, 0x02])
        )

        XCTAssertEqual(packet.senderID, Data([0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77]))
        let data = packet.toBinaryData()
        XCTAssertNotNil(data)
        let decoded = BitchatPacket.from(data ?? Data())
        XCTAssertEqual(decoded?.type, packet.type)
        XCTAssertEqual(decoded?.payload, packet.payload)
    }
}
