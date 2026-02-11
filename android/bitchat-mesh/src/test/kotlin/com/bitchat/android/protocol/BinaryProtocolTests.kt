package com.bitchat.android.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BinaryProtocolTests {
    private companion object {
        const val HEADER_SIZE_V1 = 13
        const val SENDER_ID_SIZE = 8
    }

    private class ByteArrayBuilder {
        private val data = ArrayList<Byte>()

        fun append(value: Int) = data.add(value.toByte())
        fun appendByte(value: Byte) = data.add(value)
        fun appendBytes(bytes: ByteArray) = data.addAll(bytes.toList())

        fun appendUInt16(value: Int) {
            data.add(((value shr 8) and 0xFF).toByte())
            data.add((value and 0xFF).toByte())
        }

        fun appendUInt32(value: Long) {
            data.add(((value shr 24) and 0xFF).toByte())
            data.add(((value shr 16) and 0xFF).toByte())
            data.add(((value shr 8) and 0xFF).toByte())
            data.add((value and 0xFF).toByte())
        }

        fun toByteArray(): ByteArray = data.toByteArray()
    }

    @Test
    fun testBinaryProtocolEncodeDecodeV2WithRecipientSignatureAndRoute() {
        val senderID = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val recipientID = byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17)
        val payload = "hello".toByteArray(Charsets.UTF_8)
        val signature = ByteArray(64) { 0xAA.toByte() }
        val route = listOf(
            byteArrayOf(0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09),
            byteArrayOf(0x0B, 0x0B, 0x0B, 0x0B)
        )

        val packet = BitchatPacket(
            type = 0x02u,
            senderID = senderID,
            recipientID = recipientID,
            timestamp = 1_700_000_000_000uL,
            payload = payload,
            signature = signature,
            ttl = 7u,
            version = 2u,
            route = route
        )

        val encoded = BinaryProtocol.encode(packet)
        assertNotNull(encoded)
        val decoded = BinaryProtocol.decode(encoded!!)
        assertNotNull(decoded)

        assertEquals(packet.version, decoded!!.version)
        assertEquals(packet.type, decoded.type)
        assertArrayEquals(senderID, decoded.senderID)
        assertArrayEquals(recipientID, decoded.recipientID)
        assertEquals(packet.timestamp, decoded.timestamp)
        assertArrayEquals(payload, decoded.payload)
        assertArrayEquals(signature, decoded.signature)
        assertEquals(packet.ttl, decoded.ttl)

        assertEquals(2, decoded.route?.size)
        assertArrayEquals(route[0], decoded.route?.first())
        val expectedSecondHop = byteArrayOf(0x0B, 0x0B, 0x0B, 0x0B, 0x00, 0x00, 0x00, 0x00)
        assertArrayEquals(expectedSecondHop, decoded.route?.last())
    }

    @Test
    fun testBinaryProtocolEncodeDecodeV2WithoutOptionalFields() {
        val senderID = byteArrayOf(0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28)
        val payload = byteArrayOf(0x00, 0x01, 0x02)

        val packet = BitchatPacket(
            type = 0x11u,
            senderID = senderID,
            recipientID = null,
            timestamp = 1_700_000_000_123uL,
            payload = payload,
            signature = null,
            ttl = 1u,
            version = 2u,
            route = null
        )

        val encoded = BinaryProtocol.encode(packet)
        assertNotNull(encoded)
        val decoded = BinaryProtocol.decode(encoded!!)
        assertNotNull(decoded)

        assertEquals(packet.version, decoded!!.version)
        assertEquals(packet.type, decoded.type)
        assertArrayEquals(senderID, decoded.senderID)
        assertNull(decoded.recipientID)
        assertEquals(packet.timestamp, decoded.timestamp)
        assertArrayEquals(payload, decoded.payload)
        assertNull(decoded.signature)
        assertEquals(packet.ttl, decoded.ttl)
        assertNull(decoded.route)
    }

    @Test
    fun testBinaryProtocolCompressionRoundTripSetsFlag() {
        val senderID = byteArrayOf(0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38)
        val payload = ByteArray(200) { 0x41 }

        val packet = BitchatPacket(
            type = 0x02u,
            senderID = senderID,
            recipientID = null,
            timestamp = 1_700_000_001_000uL,
            payload = payload,
            signature = null,
            ttl = 3u,
            version = 1u,
            route = null
        )

        val encoded = BinaryProtocol.encode(packet)
        assertNotNull(encoded)
        assertTrue((encoded!![11].toInt() and 0x04) != 0)

        val decoded = BinaryProtocol.decode(encoded)
        assertNotNull(decoded)
        assertArrayEquals(payload, decoded!!.payload)
    }

    @Test
    fun testBinaryProtocolDecodeHandlesPadding() {
        val senderID = byteArrayOf(0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48)
        val payload = "padded".toByteArray(Charsets.UTF_8)
        val packet = BitchatPacket(
            type = 0x01u,
            senderID = senderID,
            recipientID = null,
            timestamp = 1_700_000_002_000uL,
            payload = payload,
            signature = null,
            ttl = 2u,
            version = 1u,
            route = null
        )

        val encoded = BinaryProtocol.encode(packet)
        assertNotNull(encoded)

        val unpadded = MessagePadding.unpad(encoded!!)
        assertTrue(encoded.size >= unpadded.size)

        val decoded = BinaryProtocol.decode(encoded)
        assertArrayEquals(payload, decoded?.payload)
        val decodedUnpadded = BinaryProtocol.decode(unpadded)
        assertArrayEquals(payload, decodedUnpadded?.payload)
    }

    @Test
    fun testBinaryProtocolDecodeRejectsSuspiciousCompressionRatio() {
        val builder = ByteArrayBuilder()
        builder.append(1)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(BinaryProtocol.Flags.IS_COMPRESSED.toInt())
        builder.appendUInt16(3)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })
        builder.appendUInt16(0xC351)
        builder.append(0x00)

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsInvalidVersion() {
        val data = ByteArray(HEADER_SIZE_V1 + SENDER_ID_SIZE)
        data[0] = 3
        assertNull(BinaryProtocol.decode(data))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsShortHeader() {
        val data = ByteArray(HEADER_SIZE_V1 + SENDER_ID_SIZE - 1)
        assertNull(BinaryProtocol.decode(data))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsTruncatedPayload() {
        val builder = ByteArrayBuilder()
        builder.append(1)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(0x00)
        builder.appendUInt16(1)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsMissingRecipient() {
        val senderID = byteArrayOf(0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58)
        val recipientID = byteArrayOf(0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68)
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val packet = BitchatPacket(
            type = 0x02u,
            senderID = senderID,
            recipientID = recipientID,
            timestamp = 1_700_000_003_000uL,
            payload = payload,
            signature = null,
            ttl = 2u,
            version = 1u,
            route = null
        )

        val encoded = BinaryProtocol.encode(packet)
        assertNotNull(encoded)
        val truncated = encoded!!.copyOf(HEADER_SIZE_V1 + SENDER_ID_SIZE)
        assertNull(BinaryProtocol.decode(truncated))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsMissingSignatureBytes() {
        val builder = ByteArrayBuilder()
        builder.append(1)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(BinaryProtocol.Flags.HAS_SIGNATURE.toInt())
        builder.appendUInt16(0)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsRouteCountOutOfBounds() {
        val builder = ByteArrayBuilder()
        builder.append(2)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(BinaryProtocol.Flags.HAS_ROUTE.toInt())
        builder.appendUInt32(0)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })
        builder.append(0x02)

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsCompressedMissingOriginalSize() {
        val builder = ByteArrayBuilder()
        builder.append(1)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(BinaryProtocol.Flags.IS_COMPRESSED.toInt())
        builder.appendUInt16(0)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsRouteMissingCount() {
        val builder = ByteArrayBuilder()
        builder.append(2)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(BinaryProtocol.Flags.HAS_ROUTE.toInt())
        builder.appendUInt32(0)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsRouteWithInsufficientHopBytes() {
        val builder = ByteArrayBuilder()
        builder.append(2)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(BinaryProtocol.Flags.HAS_ROUTE.toInt())
        builder.appendUInt32(0)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })
        builder.append(0x01)
        builder.appendBytes(ByteArray(7) { 0xBB.toByte() })

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsCompressedLengthTooShort() {
        val builder = ByteArrayBuilder()
        builder.append(1)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(BinaryProtocol.Flags.IS_COMPRESSED.toInt())
        builder.appendUInt16(1)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsCompressedWithoutPayloadBytes() {
        val builder = ByteArrayBuilder()
        builder.append(1)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(BinaryProtocol.Flags.IS_COMPRESSED.toInt())
        builder.appendUInt16(2)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })
        builder.appendUInt16(1)

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsInvalidPaddingBytes() {
        val data = ByteArray(HEADER_SIZE_V1 + SENDER_ID_SIZE - 1)
        val invalid = data + byteArrayOf(0x00)
        assertNull(BinaryProtocol.decode(invalid))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsTruncatedTimestamp() {
        val builder = ByteArrayBuilder()
        builder.append(1)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(7))

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsMissingLengthFieldForV1() {
        val builder = ByteArrayBuilder()
        builder.append(1)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(0x00)
        builder.append(0x00)

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsMissingLengthFieldForV2() {
        val builder = ByteArrayBuilder()
        builder.append(2)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(0x00)
        builder.append(0x00)

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeIgnoresTrailingBytesAfterSignature() {
        val senderID = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val signature = ByteArray(64) { 0xAA.toByte() }
        val packet = BitchatPacket(
            type = 0x02u,
            senderID = senderID,
            recipientID = null,
            timestamp = 1_700_000_000_000uL,
            payload = ByteArray(0),
            signature = signature,
            ttl = 1u,
            version = 1u,
            route = null
        )

        val encoded = BinaryProtocol.encode(packet)
        assertNotNull(encoded)
        val withTrailing = encoded!! + byteArrayOf(0xFF.toByte())

        val decoded = BinaryProtocol.decode(withTrailing)
        assertArrayEquals(signature, decoded?.signature)
    }

    @Test
    fun testBinaryProtocolDecodeRejectsRecipientWithoutLengthField() {
        val builder = ByteArrayBuilder()
        builder.append(1)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(BinaryProtocol.Flags.HAS_RECIPIENT.toInt())

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsPayloadLengthMismatch() {
        val builder = ByteArrayBuilder()
        builder.append(1)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(0x00)
        builder.appendUInt16(4)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })
        builder.appendBytes(byteArrayOf(0x01, 0x02))

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsPartialRecipientID() {
        val builder = ByteArrayBuilder()
        builder.append(1)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(BinaryProtocol.Flags.HAS_RECIPIENT.toInt())
        builder.appendUInt16(0)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })
        builder.appendBytes(ByteArray(4) { 0xBB.toByte() })

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsRouteCountOverflow() {
        val builder = ByteArrayBuilder()
        builder.append(2)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(BinaryProtocol.Flags.HAS_ROUTE.toInt())
        builder.appendUInt32(0)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })
        builder.append(0xFF)

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }

    @Test
    fun testBinaryProtocolDecodeRejectsVersionLengthMismatch() {
        val builder = ByteArrayBuilder()
        builder.append(2)
        builder.append(0x01)
        builder.append(0x01)
        builder.appendBytes(ByteArray(8))
        builder.append(0x00)
        builder.appendUInt16(0)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })

        assertNull(BinaryProtocol.decode(builder.toByteArray()))
    }
}
