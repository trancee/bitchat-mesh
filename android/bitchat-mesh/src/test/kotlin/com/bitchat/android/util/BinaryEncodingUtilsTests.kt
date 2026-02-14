package com.bitchat.android.util

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.Date

class BinaryEncodingUtilsTests {
    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testBinaryEncodingRoundTrip() {
        val builder = BinaryDataBuilder()
        builder.appendUInt8(0xABu)
        builder.appendUInt16(0xCDEFu)
        builder.appendUInt32(0x01234567u)
        builder.appendUInt64(0x0123456789ABCDEFu)
        builder.appendString("hello")
        builder.appendData(byteArrayOf(0x00, 0xFF.toByte(), 0x10))
        builder.appendDate(Date(1_700_000_000_000L))
        builder.appendUUID("00112233-4455-6677-8899-AABBCCDDEEFF")

        val data = builder.toByteArray()
        val offset = intArrayOf(0)

        assertEquals(0xABu.toUByte(), data.readUInt8(offset))
        assertEquals(0xCDEFu.toUShort(), data.readUInt16(offset))
        assertEquals(0x01234567u, data.readUInt32(offset))
        assertEquals(0x0123456789ABCDEFu, data.readUInt64(offset))
        assertEquals("hello", data.readString(offset))
        assertArrayEquals(byteArrayOf(0x00, 0xFF.toByte(), 0x10), data.readData(offset))

        val decodedDate = data.readDate(offset)
        assertNotNull(decodedDate)
        assertEquals(1_700_000_000_000L, decodedDate!!.time)

        val decodedUUID = data.readUUID(offset)
        assertEquals("00112233-4455-6677-8899-AABBCCDDEEFF", decodedUUID)
        assertEquals(data.size, offset[0])
    }

    @Test
    fun testHexEncodingDecodingRoundTrip() {
        val bytes = byteArrayOf(0x0A, 0xFF.toByte(), 0x10)
        val hex = bytes.hexEncodedString()
        assertEquals("0aff10", hex)
        assertArrayEquals(bytes, "0aff10".dataFromHexString())
        assertNull("zz".dataFromHexString())
    }

    @Test
    fun testReadDataReturnsNullWhenLengthExceedsBuffer() {
        val data = byteArrayOf(0x05, 0x01, 0x02, 0x03)
        val offset = intArrayOf(0)

        assertNull(data.readData(offset, maxLength = 255))
        assertEquals(1, offset[0])
    }

    @Test
    fun testReadFixedBytesReturnsNullWhenOutOfBounds() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val offset = intArrayOf(2)

        assertNull(data.readFixedBytes(offset, count = 4))
        assertEquals(2, offset[0])
    }

    @Test
    fun testReadUInt16ReturnsNullWhenInsufficientBytes() {
        val data = byteArrayOf(0x01)
        val offset = intArrayOf(0)

        assertNull(data.readUInt16(offset))
        assertEquals(0, offset[0])
    }

    @Test
    fun testReadUUIDReturnsNullWhenInsufficientBytes() {
        val data = ByteArray(15)
        val offset = intArrayOf(0)

        assertNull(data.readUUID(offset))
        assertEquals(0, offset[0])
    }

    @Test
    fun testReadStringWithUInt16Length() {
        val builder = BinaryDataBuilder()
        builder.appendUInt16(5u)
        builder.buffer.addAll("hello".toByteArray(Charsets.UTF_8).toList())

        val data = builder.toByteArray()
        val offset = intArrayOf(0)

        assertEquals("hello", data.readString(offset, maxLength = 300))
        assertEquals(7, offset[0])
    }

    @Test
    fun testReadDataWithUInt16Length() {
        val builder = BinaryDataBuilder()
        builder.appendUInt16(3u)
        builder.buffer.addAll(listOf(0x01, 0x02, 0x03).map { it.toByte() })

        val data = builder.toByteArray()
        val offset = intArrayOf(0)

        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), data.readData(offset, maxLength = 300))
        assertEquals(5, offset[0])
    }

    @Test
    fun testHexInitTruncatesOddLength() {
        assertArrayEquals(byteArrayOf(0xAB.toByte()), "abc".dataFromHexString())
    }

    @Test
    fun testReadUInt8ReturnsNullWhenOutOfBounds() {
        val data = byteArrayOf()
        val offset = intArrayOf(0)

        assertNull(data.readUInt8(offset))
        assertEquals(0, offset[0])
    }

    @Test
    fun testReadDateReturnsNullWhenInsufficientBytes() {
        val data = ByteArray(7)
        val offset = intArrayOf(0)

        assertNull(data.readDate(offset))
        assertEquals(0, offset[0])
    }

    @Test
    fun testAppendStringTruncatesToMaxLengthUInt8() {
        val builder = BinaryDataBuilder()
        builder.appendString("abcdefghijk", maxLength = 5)

        val data = builder.toByteArray()
        val offset = intArrayOf(0)

        assertEquals("abcde", data.readString(offset, maxLength = 5))
        assertEquals(6, offset[0])
    }

    @Test
    fun testAppendStringTruncatesToMaxLengthUInt16() {
        val builder = BinaryDataBuilder()
        val value = "a".repeat(400)
        builder.appendString(value, maxLength = 300)

        val data = builder.toByteArray()
        val offset = intArrayOf(0)

        val decoded = data.readString(offset, maxLength = 300)
        assertEquals(300, decoded?.length)
        assertEquals(302, offset[0])
    }

    @Test
    fun testAppendDataTruncatesToMaxLength() {
        val builder = BinaryDataBuilder()
        builder.appendData(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06), maxLength = 5)

        val data = builder.toByteArray()
        val offset = intArrayOf(0)

        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05), data.readData(offset, maxLength = 5))
        assertEquals(6, offset[0])
    }

    @Test
    fun testReadStringReturnsNullForInvalidUtf8() {
        val data = byteArrayOf(0x02, 0xFF.toByte(), 0xFF.toByte())
        val offset = intArrayOf(0)

        val decoded = data.readString(offset)
        assertNotNull(decoded)
        assertEquals(2, decoded!!.length)
        assertEquals(3, offset[0])
    }

    @Test
    fun testReadFixedBytesWithZeroCountReturnsEmptyData() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val offset = intArrayOf(1)

        assertArrayEquals(ByteArray(0), data.readFixedBytes(offset, count = 0))
        assertEquals(1, offset[0])
    }

    @Test
    fun testSha256HexMatchesKnownVector() {
        val data = "abc".toByteArray(Charsets.UTF_8)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex(data)
        )
    }

    @Test
    fun testAppendUUIDWithPartialHexPadsZeroes() {
        val builder = BinaryDataBuilder()
        builder.appendUUID("0011")

        val data = builder.toByteArray()
        val offset = intArrayOf(0)
        val bytes = data.readFixedBytes(offset, count = 16)

        val expected = byteArrayOf(0x00, 0x11) + ByteArray(14)
        assertArrayEquals(expected, bytes)
        assertEquals(16, offset[0])
    }

    @Test
    fun testBinaryDataReaderReadsValuesAndAdvancesOffset() {
        val builder = BinaryDataBuilder()
        builder.appendUInt8(0x01u)
        builder.appendUInt16(0x0203u)
        builder.appendUInt32(0x04050607u)
        builder.appendUInt64(0x08090A0B0C0D0E0Fu)
        builder.appendString("hi")
        builder.appendData(byteArrayOf(0x0A, 0x0B))
        builder.appendUUID("00112233-4455-6677-8899-AABBCCDDEEFF")

        val reader = BinaryDataReader(builder.toByteArray())

        assertEquals(0x01u.toUByte(), reader.readUInt8())
        assertEquals(0x0203u.toUShort(), reader.readUInt16())
        assertEquals(0x04050607u, reader.readUInt32())
        assertEquals(0x08090A0B0C0D0E0Fu, reader.readUInt64())
        assertEquals("hi", reader.readString())
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), reader.readData())
        assertEquals("00112233-4455-6677-8899-AABBCCDDEEFF", reader.readUUID())
        assertEquals(builder.toByteArray().size, reader.currentOffset)
    }

    @Test
    fun testBinaryDataReaderReturnsNullWhenOutOfBounds() {
        val reader = BinaryDataReader(byteArrayOf(0x01))

        assertNull(reader.readUInt16())
        assertNull(reader.readUInt32())
        assertNull(reader.readUInt64())
        assertNull(reader.readString())
        assertNull(reader.readData())
        assertNull(reader.readUUID())
        assertNull(reader.readFixedBytes(2))
    }

    @Test
    fun testBinaryDataReaderReadsUInt16LengthFields() {
        val builder = BinaryDataBuilder()
        builder.appendUInt16(3u)
        builder.buffer.addAll("hey".toByteArray(Charsets.UTF_8).toList())
        builder.appendUInt16(2u)
        builder.buffer.addAll(byteArrayOf(0x01, 0x02).toList())

        val reader = BinaryDataReader(builder.toByteArray())

        assertEquals("hey", reader.readString(maxLength = 300))
        assertArrayEquals(byteArrayOf(0x01, 0x02), reader.readData(maxLength = 300))
    }

    @Test
    fun testBinaryMessageTypeFromValue() {
        assertEquals(BinaryMessageType.READ_RECEIPT, BinaryMessageType.fromValue(0x02u))
        assertNull(BinaryMessageType.fromValue(0xFFu))
    }
}
