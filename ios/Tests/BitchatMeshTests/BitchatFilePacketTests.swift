import XCTest
@testable import BitchatMesh

final class BitchatFilePacketTests: XCTestCase {
    func testBitchatFilePacketEncodeDecodeRoundTrip() {
        let packet = BitchatFilePacket(
            fileName: "note.txt",
            fileSize: nil,
            mimeType: "text/plain",
            content: Data("hello".utf8)
        )

        guard let encoded = packet.encode() else {
            XCTFail("Expected encoded data")
            return
        }
        guard let decoded = BitchatFilePacket.decode(encoded) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.fileName, packet.fileName)
        XCTAssertEqual(decoded.fileSize, UInt64(packet.content.count))
        XCTAssertEqual(decoded.mimeType, packet.mimeType)
        XCTAssertEqual(decoded.content, packet.content)
    }

    func testBitchatFilePacketDecodeRejectsMissingContent() {
        var data = Data()
        data.append(0x01) // fileName
        data.append(contentsOf: [0x00, 0x03])
        data.append(contentsOf: [0x66, 0x6F, 0x6F])

        XCTAssertNil(BitchatFilePacket.decode(data))
    }

    func testBitchatFilePacketDecodeRejectsOversizedFileSize() {
        var data = Data()
        data.append(0x02) // fileSize
        data.append(contentsOf: [0x00, 0x04])
        let oversized = UInt32(FileTransferLimits.maxPayloadBytes + 1)
        data.append(UInt8((oversized >> 24) & 0xFF))
        data.append(UInt8((oversized >> 16) & 0xFF))
        data.append(UInt8((oversized >> 8) & 0xFF))
        data.append(UInt8(oversized & 0xFF))

        data.append(0x04) // content
        data.append(contentsOf: [0x00, 0x00, 0x00, 0x01])
        data.append(0x00)

        XCTAssertNil(BitchatFilePacket.decode(data))
    }

    func testBitchatFilePacketDecodeRejectsMalformedContentLength() {
        var data = Data()
        data.append(0x04) // content
        data.append(contentsOf: [0x00, 0x00, 0x00, 0x02])
        data.append(0xAB) // only 1 byte

        XCTAssertNil(BitchatFilePacket.decode(data))
    }

    func testBitchatFilePacketDecodeSupportsLegacyLengths() {
        var data = Data()
        data.append(0x02) // fileSize
        data.append(contentsOf: [0x00, 0x08])
        data.append(contentsOf: [0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02])

        data.append(0x04) // content
        data.append(contentsOf: [0x00, 0x02])
        data.append(contentsOf: [0x68, 0x69]) // "hi"

        guard let decoded = BitchatFilePacket.decode(data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.fileSize, 2)
        XCTAssertEqual(decoded.content, Data([0x68, 0x69]))
    }

    func testBitchatFilePacketEncodeRejectsOversizedFileSize() {
        let packet = BitchatFilePacket(
            fileName: "too-big.bin",
            fileSize: UInt64(FileTransferLimits.maxPayloadBytes + 1),
            mimeType: "application/octet-stream",
            content: Data(repeating: 0x00, count: 1)
        )

        XCTAssertNil(packet.encode())
    }

    func testBitchatFilePacketEncodeRejectsOversizedContent() {
        let packet = BitchatFilePacket(
            fileName: "too-big.bin",
            fileSize: nil,
            mimeType: "application/octet-stream",
            content: Data(repeating: 0x00, count: FileTransferLimits.maxPayloadBytes + 1)
        )

        XCTAssertNil(packet.encode())
    }

    func testBitchatFilePacketEncodeAcceptsMaxLengthFileNameAndMimeType() {
        let maxName = String(repeating: "a", count: Int(UInt16.max))
        let maxMime = String(repeating: "b", count: Int(UInt16.max))
        let packet = BitchatFilePacket(
            fileName: maxName,
            fileSize: nil,
            mimeType: maxMime,
            content: Data([0x01])
        )

        guard let encoded = packet.encode() else {
            XCTFail("Expected encoded data")
            return
        }
        guard let decoded = BitchatFilePacket.decode(encoded) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.fileName, maxName)
        XCTAssertEqual(decoded.mimeType, maxMime)
    }

    func testBitchatFilePacketEncodeOmitsTooLongOptionalFields() {
        let tooLong = String(repeating: "c", count: Int(UInt16.max) + 1)
        let packet = BitchatFilePacket(
            fileName: tooLong,
            fileSize: nil,
            mimeType: tooLong,
            content: Data([0x02])
        )

        guard let encoded = packet.encode() else {
            XCTFail("Expected encoded data")
            return
        }
        guard let decoded = BitchatFilePacket.decode(encoded) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertNil(decoded.fileName)
        XCTAssertNil(decoded.mimeType)
        XCTAssertEqual(decoded.content, Data([0x02]))
    }

    func testBitchatFilePacketDecodeHandlesMixedTLVsAndSplitContent() {
        var data = Data()

        data.append(0x04) // content
        data.append(contentsOf: [0x00, 0x00, 0x00, 0x02])
        data.append(contentsOf: [0x68, 0x65]) // "he"

        data.append(0x01) // fileName
        data.append(contentsOf: [0x00, 0x08])
        data.append(contentsOf: Array("note.txt".utf8))

        data.append(0x03) // mimeType
        data.append(contentsOf: [0x00, 0x0A])
        data.append(contentsOf: Array("text/plain".utf8))

        data.append(0x02) // fileSize
        data.append(contentsOf: [0x00, 0x04])
        data.append(contentsOf: [0x00, 0x00, 0x00, 0x05])

        data.append(0x04) // content
        data.append(contentsOf: [0x00, 0x00, 0x00, 0x03])
        data.append(contentsOf: [0x6C, 0x6C, 0x6F]) // "llo"

        guard let decoded = BitchatFilePacket.decode(data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.fileName, "note.txt")
        XCTAssertEqual(decoded.mimeType, "text/plain")
        XCTAssertEqual(decoded.fileSize, 5)
        XCTAssertEqual(decoded.content, Data("hello".utf8))
    }

    func testBitchatFilePacketDecodeHandlesOutOfOrderOptionalTLVs() {
        var data = Data()

        data.append(0x03) // mimeType
        data.append(contentsOf: [0x00, 0x0A])
        data.append(contentsOf: Array("text/plain".utf8))

        data.append(0x01) // fileName
        data.append(contentsOf: [0x00, 0x08])
        data.append(contentsOf: Array("note.txt".utf8))

        data.append(0x04) // content
        data.append(contentsOf: [0x00, 0x00, 0x00, 0x03])
        data.append(contentsOf: [0x66, 0x6F, 0x6F]) // "foo"

        guard let decoded = BitchatFilePacket.decode(data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.fileName, "note.txt")
        XCTAssertEqual(decoded.mimeType, "text/plain")
        XCTAssertEqual(decoded.content, Data("foo".utf8))
    }

    func testBitchatFilePacketDecodeSkipsUnknownTLVs() {
        var data = Data()

        data.append(0x7F) // unknown TLV
        data.append(contentsOf: [0x00, 0x02])
        data.append(contentsOf: [0xAA, 0xBB])

        data.append(0x04) // content
        data.append(contentsOf: [0x00, 0x00, 0x00, 0x03])
        data.append(contentsOf: [0x62, 0x61, 0x72]) // "bar"

        guard let decoded = BitchatFilePacket.decode(data) else {
            XCTFail("Expected decoded packet")
            return
        }

        XCTAssertEqual(decoded.content, Data("bar".utf8))
        XCTAssertNil(decoded.fileName)
        XCTAssertNil(decoded.mimeType)
    }
}
