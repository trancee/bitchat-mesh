import XCTest
@testable import BitchatMesh

final class BinaryEncodingUtilsTests: XCTestCase {
    func testBinaryEncodingRoundTrip() {
        var data = Data()

        data.appendUInt8(0xAB)
        data.appendUInt16(0xCDEF)
        data.appendUInt32(0x01234567)
        data.appendUInt64(0x0123456789ABCDEF)
        data.appendString("hello")
        data.appendData(Data([0x00, 0xFF, 0x10]))
        data.appendDate(Date(timeIntervalSince1970: 1_700_000_000.0))
        data.appendUUID("00112233-4455-6677-8899-AABBCCDDEEFF")

        var offset = 0
        XCTAssertEqual(data.readUInt8(at: &offset), 0xAB)
        XCTAssertEqual(data.readUInt16(at: &offset), 0xCDEF)
        XCTAssertEqual(data.readUInt32(at: &offset), 0x01234567)
        XCTAssertEqual(data.readUInt64(at: &offset), 0x0123456789ABCDEF)
        XCTAssertEqual(data.readString(at: &offset), "hello")
        XCTAssertEqual(data.readData(at: &offset), Data([0x00, 0xFF, 0x10]))

        let decodedDate = data.readDate(at: &offset)
        XCTAssertNotNil(decodedDate)
        XCTAssertEqual(decodedDate?.timeIntervalSince1970 ?? 0.0, 1_700_000_000.0, accuracy: 0.001)

        let decodedUUID = data.readUUID(at: &offset)
        XCTAssertEqual(decodedUUID, "00112233-4455-6677-8899-AABBCCDDEEFF")
        XCTAssertEqual(offset, data.count)
    }

    func testHexEncodingDecodingRoundTrip() {
        let bytes = Data([0x0A, 0xFF, 0x10])
        let hex = bytes.hexEncodedString()
        XCTAssertEqual(hex, "0aff10")
        XCTAssertEqual(Data(hexString: hex), bytes)
        XCTAssertNil(Data(hexString: "zz"))
    }

    func testReadDataReturnsNilWhenLengthExceedsBuffer() {
        var data = Data()
        data.appendUInt8(5)
        data.append(Data([0x01, 0x02, 0x03]))

        var offset = 0
        XCTAssertNil(data.readData(at: &offset, maxLength: 255))
        XCTAssertEqual(offset, 1)
    }

    func testReadFixedBytesReturnsNilWhenOutOfBounds() {
        let data = Data([0x01, 0x02, 0x03])
        var offset = 2

        XCTAssertNil(data.readFixedBytes(at: &offset, count: 4))
        XCTAssertEqual(offset, 2)
    }

    func testReadUInt16ReturnsNilWhenInsufficientBytes() {
        let data = Data([0x01])
        var offset = 0

        XCTAssertNil(data.readUInt16(at: &offset))
        XCTAssertEqual(offset, 0)
    }

    func testReadUUIDReturnsNilWhenInsufficientBytes() {
        let data = Data(repeating: 0x00, count: 15)
        var offset = 0

        XCTAssertNil(data.readUUID(at: &offset))
        XCTAssertEqual(offset, 0)
    }

    func testReadStringWithUInt16Length() {
        var data = Data()
        data.appendUInt16(5)
        data.append(contentsOf: Array("hello".utf8))

        var offset = 0
        XCTAssertEqual(data.readString(at: &offset, maxLength: 300), "hello")
        XCTAssertEqual(offset, 7)
    }

    func testReadDataWithUInt16Length() {
        var data = Data()
        data.appendUInt16(3)
        data.append(contentsOf: [0x01, 0x02, 0x03])

        var offset = 0
        XCTAssertEqual(data.readData(at: &offset, maxLength: 300), Data([0x01, 0x02, 0x03]))
        XCTAssertEqual(offset, 5)
    }

    func testHexInitTruncatesOddLength() {
        XCTAssertEqual(Data(hexString: "abc"), Data([0xAB]))
    }

    func testReadUInt8ReturnsNilWhenOutOfBounds() {
        let data = Data()
        var offset = 0

        XCTAssertNil(data.readUInt8(at: &offset))
        XCTAssertEqual(offset, 0)
    }

    func testReadDateReturnsNilWhenInsufficientBytes() {
        let data = Data(repeating: 0x00, count: 7)
        var offset = 0

        XCTAssertNil(data.readDate(at: &offset))
        XCTAssertEqual(offset, 0)
    }

    func testAppendStringTruncatesToMaxLengthUInt8() {
        var data = Data()
        data.appendString("abcdefghijk", maxLength: 5)

        var offset = 0
        XCTAssertEqual(data.readString(at: &offset, maxLength: 5), "abcde")
        XCTAssertEqual(offset, 6)
    }

    func testAppendStringTruncatesToMaxLengthUInt16() {
        var data = Data()
        let string = String(repeating: "a", count: 400)
        data.appendString(string, maxLength: 300)

        var offset = 0
        let decoded = data.readString(at: &offset, maxLength: 300)
        XCTAssertEqual(decoded?.count, 300)
        XCTAssertEqual(offset, 302)
    }

    func testAppendDataTruncatesToMaxLength() {
        var data = Data()
        data.appendData(Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06]), maxLength: 5)

        var offset = 0
        XCTAssertEqual(data.readData(at: &offset, maxLength: 5), Data([0x01, 0x02, 0x03, 0x04, 0x05]))
        XCTAssertEqual(offset, 6)
    }

    func testReadStringReturnsNilForInvalidUtf8() {
        var data = Data()
        data.appendUInt8(2)
        data.append(contentsOf: [0xFF, 0xFF])

        var offset = 0
        XCTAssertNil(data.readString(at: &offset))
        XCTAssertEqual(offset, 3)
    }

    func testReadFixedBytesWithZeroCountReturnsEmptyData() {
        let data = Data([0x01, 0x02, 0x03])
        var offset = 1

        XCTAssertEqual(data.readFixedBytes(at: &offset, count: 0), Data())
        XCTAssertEqual(offset, 1)
    }

    func testSha256HexMatchesKnownVector() {
        let data = Data("abc".utf8)
        XCTAssertEqual(data.sha256Hex(), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    func testAppendUUIDWithPartialHexPadsZeroes() {
        var data = Data()
        data.appendUUID("0011")

        var offset = 0
        let bytes = data.readFixedBytes(at: &offset, count: 16)
        XCTAssertEqual(bytes, Data([0x00, 0x11] + Array(repeating: 0x00, count: 14)))
        XCTAssertEqual(offset, 16)
    }
}
