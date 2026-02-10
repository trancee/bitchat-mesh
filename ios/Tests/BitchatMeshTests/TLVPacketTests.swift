import XCTest
@testable import BitchatMesh

final class TLVPacketTests: XCTestCase {
    func testAnnouncementPacketEncodeDecodeRoundTrip() {
        let packet = AnnouncementPacket(
            nickname: "Alice",
            noisePublicKey: Data(repeating: 0x01, count: 32),
            signingPublicKey: Data(repeating: 0x02, count: 32),
            directNeighbors: [
                Data([0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA]),
                Data([0xBB, 0xBB, 0xBB, 0xBB, 0xBB, 0xBB, 0xBB, 0xBB])
            ]
        )

        guard let encoded = packet.encode() else {
            XCTFail("Expected encoded data")
            return
        }
        guard let decoded = AnnouncementPacket.decode(from: encoded) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.nickname, packet.nickname)
        XCTAssertEqual(decoded.noisePublicKey, packet.noisePublicKey)
        XCTAssertEqual(decoded.signingPublicKey, packet.signingPublicKey)
        XCTAssertEqual(decoded.directNeighbors, packet.directNeighbors)
    }

    func testAnnouncementPacketEncodeRejectsLongNickname() {
        let nickname = String(repeating: "a", count: 256)
        let packet = AnnouncementPacket(
            nickname: nickname,
            noisePublicKey: Data(repeating: 0x01, count: 32),
            signingPublicKey: Data(repeating: 0x02, count: 32),
            directNeighbors: nil
        )

        XCTAssertNil(packet.encode())
    }

    func testAnnouncementPacketEncodeRejectsEmptyNickname() {
        let packet = AnnouncementPacket(
            nickname: "",
            noisePublicKey: Data(repeating: 0x01, count: 32),
            signingPublicKey: Data(repeating: 0x02, count: 32),
            directNeighbors: nil
        )

        XCTAssertNil(packet.encode())
    }

    func testAnnouncementPacketTruncatesNeighborsToLimit() {
        let neighbors = (0..<12).map { i in
            Data(repeating: UInt8(i + 1), count: 8)
        }
        let packet = AnnouncementPacket(
            nickname: "Bob",
            noisePublicKey: Data(repeating: 0x01, count: 32),
            signingPublicKey: Data(repeating: 0x02, count: 32),
            directNeighbors: neighbors
        )

        guard let encoded = packet.encode() else {
            XCTFail("Expected encoded data")
            return
        }
        guard let decoded = AnnouncementPacket.decode(from: encoded) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.directNeighbors?.count, 10)
        XCTAssertEqual(decoded.directNeighbors, Array(neighbors.prefix(10)))
    }

    func testAnnouncementPacketOmitsEmptyNeighbors() {
        let packet = AnnouncementPacket(
            nickname: "Eve",
            noisePublicKey: Data(repeating: 0x01, count: 32),
            signingPublicKey: Data(repeating: 0x02, count: 32),
            directNeighbors: []
        )

        guard let encoded = packet.encode() else {
            XCTFail("Expected encoded data")
            return
        }
        guard let decoded = AnnouncementPacket.decode(from: encoded) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertNil(decoded.directNeighbors)
    }

    func testPrivateMessagePacketEncodeDecodeRoundTrip() {
        let packet = PrivateMessagePacket(messageID: "msg-1", content: "hello")
        guard let encoded = packet.encode() else {
            XCTFail("Expected encoded data")
            return
        }
        guard let decoded = PrivateMessagePacket.decode(from: encoded) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.messageID, packet.messageID)
        XCTAssertEqual(decoded.content, packet.content)
    }

    func testPrivateMessagePacketDecodeRejectsUnknownTLV() {
        var data = Data()
        data.append(0x99) // unknown TLV type
        data.append(0x01)
        data.append(0x00)

        XCTAssertNil(PrivateMessagePacket.decode(from: data))
    }

    func testAnnouncementPacketDecodeRejectsMalformedLength() {
        var data = Data()
        data.append(0x01) // nickname
        data.append(0x05) // length 5
        data.append(contentsOf: [0x41, 0x42]) // only 2 bytes

        XCTAssertNil(AnnouncementPacket.decode(from: data))
    }

    func testAnnouncementPacketDecodeSkipsUnknownTLVs() {
        var data = Data()

        data.append(0x01) // nickname
        data.append(0x03)
        data.append(contentsOf: Array("Bob".utf8))

        data.append(0x7F) // unknown TLV
        data.append(0x02)
        data.append(contentsOf: [0xAA, 0xBB])

        data.append(0x02) // noisePublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x11, count: 32))

        data.append(0x03) // signingPublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x22, count: 32))

        guard let decoded = AnnouncementPacket.decode(from: data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.nickname, "Bob")
        XCTAssertEqual(decoded.noisePublicKey, Data(repeating: 0x11, count: 32))
        XCTAssertEqual(decoded.signingPublicKey, Data(repeating: 0x22, count: 32))
    }

    func testAnnouncementPacketDecodeSkipsUnknownZeroLengthTLV() {
        var data = Data()

        data.append(0x01) // nickname
        data.append(0x03)
        data.append(contentsOf: Array("Bob".utf8))

        data.append(0x7F) // unknown TLV
        data.append(0x00)

        data.append(0x02) // noisePublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x11, count: 32))

        data.append(0x03) // signingPublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x22, count: 32))

        guard let decoded = AnnouncementPacket.decode(from: data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.nickname, "Bob")
        XCTAssertEqual(decoded.noisePublicKey, Data(repeating: 0x11, count: 32))
        XCTAssertEqual(decoded.signingPublicKey, Data(repeating: 0x22, count: 32))
    }

    func testAnnouncementPacketDecodeSkipsUnknownTrailingTLV() {
        var data = Data()

        data.append(0x01) // nickname
        data.append(0x03)
        data.append(contentsOf: Array("Bob".utf8))

        data.append(0x02) // noisePublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x11, count: 32))

        data.append(0x03) // signingPublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x22, count: 32))

        data.append(0x7F) // unknown TLV trailing
        data.append(0x02)
        data.append(contentsOf: [0xAA, 0xBB])

        guard let decoded = AnnouncementPacket.decode(from: data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.nickname, "Bob")
        XCTAssertEqual(decoded.noisePublicKey, Data(repeating: 0x11, count: 32))
        XCTAssertEqual(decoded.signingPublicKey, Data(repeating: 0x22, count: 32))
    }

    func testAnnouncementPacketDecodeIgnoresInvalidNeighborLength() {
        var data = Data()

        data.append(0x01) // nickname
        data.append(0x03)
        data.append(contentsOf: Array("Ann".utf8))

        data.append(0x02) // noisePublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x11, count: 32))

        data.append(0x03) // signingPublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x22, count: 32))

        data.append(0x04) // directNeighbors
        data.append(0x07) // invalid length (not multiple of 8)
        data.append(contentsOf: Array(repeating: 0xFF, count: 7))

        guard let decoded = AnnouncementPacket.decode(from: data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertNil(decoded.directNeighbors)
    }

    func testAnnouncementPacketDecodeUsesLastDuplicateTLV() {
        var data = Data()

        data.append(0x01) // nickname
        data.append(0x03)
        data.append(contentsOf: Array("Old".utf8))

        data.append(0x01) // nickname duplicate
        data.append(0x03)
        data.append(contentsOf: Array("New".utf8))

        data.append(0x02) // noisePublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x11, count: 32))

        data.append(0x03) // signingPublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x22, count: 32))

        guard let decoded = AnnouncementPacket.decode(from: data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.nickname, "New")
    }

    func testAnnouncementPacketDecodeUsesLastDuplicateKeys() {
        var data = Data()

        data.append(0x01) // nickname
        data.append(0x03)
        data.append(contentsOf: Array("Ann".utf8))

        data.append(0x02) // noisePublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x11, count: 32))

        data.append(0x02) // noisePublicKey duplicate
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x33, count: 32))

        data.append(0x03) // signingPublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x22, count: 32))

        data.append(0x03) // signingPublicKey duplicate
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x44, count: 32))

        guard let decoded = AnnouncementPacket.decode(from: data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.noisePublicKey, Data(repeating: 0x33, count: 32))
        XCTAssertEqual(decoded.signingPublicKey, Data(repeating: 0x44, count: 32))
    }

    func testAnnouncementPacketDecodeUsesLastDuplicateNeighbors() {
        var data = Data()

        data.append(0x01) // nickname
        data.append(0x03)
        data.append(contentsOf: Array("Ann".utf8))

        data.append(0x02) // noisePublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x11, count: 32))

        data.append(0x03) // signingPublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x22, count: 32))

        data.append(0x04) // directNeighbors
        data.append(0x08)
        data.append(contentsOf: Array(repeating: 0xAA, count: 8))

        data.append(0x04) // directNeighbors duplicate
        data.append(0x10)
        data.append(contentsOf: Array(repeating: 0xBB, count: 16))

        guard let decoded = AnnouncementPacket.decode(from: data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.directNeighbors, [Data(repeating: 0xBB, count: 8), Data(repeating: 0xBB, count: 8)])
    }

    func testAnnouncementPacketDecodeKeepsNeighborsWhenDuplicateEmpty() {
        var data = Data()

        data.append(0x01) // nickname
        data.append(0x03)
        data.append(contentsOf: Array("Ann".utf8))

        data.append(0x02) // noisePublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x11, count: 32))

        data.append(0x03) // signingPublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x22, count: 32))

        data.append(0x04) // directNeighbors
        data.append(0x08)
        data.append(contentsOf: Array(repeating: 0xAA, count: 8))

        data.append(0x04) // directNeighbors duplicate empty
        data.append(0x00)

        guard let decoded = AnnouncementPacket.decode(from: data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.directNeighbors, [Data(repeating: 0xAA, count: 8)])
    }

    func testAnnouncementPacketDecodeIgnoresEmptyDuplicateTLV() {
        var data = Data()

        data.append(0x01) // nickname
        data.append(0x03)
        data.append(contentsOf: Array("Ann".utf8))

        data.append(0x01) // nickname duplicate with empty payload
        data.append(0x00)

        data.append(0x02) // noisePublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x11, count: 32))

        data.append(0x03) // signingPublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x22, count: 32))

        guard let decoded = AnnouncementPacket.decode(from: data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.nickname, "Ann")
    }

    func testAnnouncementPacketDecodeRejectsMissingNickname() {
        var data = Data()

        data.append(0x02) // noisePublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x11, count: 32))

        data.append(0x03) // signingPublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x22, count: 32))

        XCTAssertNil(AnnouncementPacket.decode(from: data))
    }

    func testAnnouncementPacketDecodeRejectsMissingNoisePublicKey() {
        var data = Data()

        data.append(0x01) // nickname
        data.append(0x03)
        data.append(contentsOf: Array("Ann".utf8))

        data.append(0x03) // signingPublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x22, count: 32))

        XCTAssertNil(AnnouncementPacket.decode(from: data))
    }

    func testAnnouncementPacketDecodeRejectsMissingSigningPublicKey() {
        var data = Data()

        data.append(0x01) // nickname
        data.append(0x03)
        data.append(contentsOf: Array("Ann".utf8))

        data.append(0x02) // noisePublicKey
        data.append(0x20)
        data.append(contentsOf: Array(repeating: 0x11, count: 32))

        XCTAssertNil(AnnouncementPacket.decode(from: data))
    }

    func testAnnouncementPacketEncodeRejectsEmptyKeys() {
        let packet = AnnouncementPacket(
            nickname: "Ann",
            noisePublicKey: Data(),
            signingPublicKey: Data(),
            directNeighbors: nil
        )

        XCTAssertNil(packet.encode())
    }

    func testPrivateMessagePacketDecodeRejectsMalformedLength() {
        var data = Data()
        data.append(0x00) // messageID
        data.append(0x04) // length 4
        data.append(contentsOf: [0x6D, 0x73]) // only 2 bytes

        XCTAssertNil(PrivateMessagePacket.decode(from: data))
    }

    func testPrivateMessagePacketDecodeRejectsMalformedUTF8() {
        var data = Data()

        data.append(0x00) // messageID
        data.append(0x02)
        data.append(contentsOf: [0xC3, 0x28]) // invalid UTF-8

        data.append(0x01) // content
        data.append(0x05)
        data.append(contentsOf: Array("hello".utf8))

        XCTAssertNil(PrivateMessagePacket.decode(from: data))
    }

    func testPrivateMessagePacketDecodeUsesLastDuplicateTLV() {
        var data = Data()

        data.append(0x00) // messageID
        data.append(0x05)
        data.append(contentsOf: Array("first".utf8))

        data.append(0x00) // messageID duplicate
        data.append(0x06)
        data.append(contentsOf: Array("second".utf8))

        data.append(0x01) // content
        data.append(0x05)
        data.append(contentsOf: Array("hello".utf8))

        guard let decoded = PrivateMessagePacket.decode(from: data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.messageID, "second")
        XCTAssertEqual(decoded.content, "hello")
    }

    func testPrivateMessagePacketDecodeUsesLastDuplicateContentTLV() {
        var data = Data()

        data.append(0x00) // messageID
        data.append(0x05)
        data.append(contentsOf: Array("msg-1".utf8))

        data.append(0x01) // content
        data.append(0x05)
        data.append(contentsOf: Array("first".utf8))

        data.append(0x01) // content duplicate
        data.append(0x06)
        data.append(contentsOf: Array("second".utf8))

        guard let decoded = PrivateMessagePacket.decode(from: data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.content, "second")
    }

    func testPrivateMessagePacketDecodeHandlesOutOfOrderTLVs() {
        var data = Data()

        data.append(0x01) // content first
        data.append(0x05)
        data.append(contentsOf: Array("hello".utf8))

        data.append(0x00) // messageID second
        data.append(0x05)
        data.append(contentsOf: Array("msg-1".utf8))

        guard let decoded = PrivateMessagePacket.decode(from: data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.messageID, "msg-1")
        XCTAssertEqual(decoded.content, "hello")
    }

    func testPrivateMessagePacketDecodeRejectsMissingMessageID() {
        var data = Data()

        data.append(0x01) // content
        data.append(0x05)
        data.append(contentsOf: Array("hello".utf8))

        XCTAssertNil(PrivateMessagePacket.decode(from: data))
    }

    func testPrivateMessagePacketDecodeRejectsMissingContent() {
        var data = Data()

        data.append(0x00) // messageID
        data.append(0x05)
        data.append(contentsOf: Array("msg-1".utf8))

        XCTAssertNil(PrivateMessagePacket.decode(from: data))
    }

    func testPrivateMessagePacketEncodeRejectsEmptyMessageID() {
        let packet = PrivateMessagePacket(messageID: "", content: "hello")
        XCTAssertNil(packet.encode())
    }

    func testPrivateMessagePacketEncodeRejectsEmptyContent() {
        let packet = PrivateMessagePacket(messageID: "msg-1", content: "")
        XCTAssertNil(packet.encode())
    }
}
