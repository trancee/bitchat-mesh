package com.bitchat.android.protocol

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Log

/**
 * Message types - exact same as iOS version with Noise Protocol support
 */
enum class MessageType(val value: UByte) {
    ANNOUNCE(0x01u),
    MESSAGE(0x02u),  // All user messages (private and broadcast)
    LEAVE(0x03u),
    NOISE_HANDSHAKE(0x10u),  // Noise handshake
    NOISE_ENCRYPTED(0x11u),  // Noise encrypted transport message
    FRAGMENT(0x20u), // Fragmentation for large packets
    REQUEST_SYNC(0x21u), // GCS-based sync request
    FILE_TRANSFER(0x22u); // New: File transfer packet (BLE voice notes, etc.)

    companion object {
        fun fromValue(value: UByte): MessageType? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Special recipient IDs - exact same as iOS version
 */
object SpecialRecipients {
    val BROADCAST = ByteArray(8) { 0xFF.toByte() }  // All 0xFF = broadcast
}

/**
 * Binary packet format - 100% backward compatible with iOS version
 *
 * Header (13 bytes for v1, 15 bytes for v2):
 * - Version: 1 byte
 * - Type: 1 byte
 * - TTL: 1 byte
 * - Timestamp: 8 bytes (UInt64, big-endian)
 * - Flags: 1 byte (bit 0: hasRecipient, bit 1: hasSignature, bit 2: isCompressed)
 * - PayloadLength: 2 bytes (v1) / 4 bytes (v2) (big-endian)
 *
 * Variable sections:
 * - SenderID: 8 bytes (fixed)
 * - RecipientID: 8 bytes (if hasRecipient flag set)
 * - Payload: Variable length (includes original size if compressed)
 * - Signature: 64 bytes (if hasSignature flag set)
 */
@Parcelize
data class BitchatPacket(
    val version: UByte = 1u,
    val type: UByte,
    val senderID: ByteArray,
    val recipientID: ByteArray? = null,
    val timestamp: ULong,
    val payload: ByteArray,
    var signature: ByteArray? = null,  // Changed from val to var for packet signing
    var ttl: UByte,
    var route: List<ByteArray>? = null // Optional source route: ordered list of peerIDs (8 bytes each), not including sender and final recipient
) : Parcelable {

    constructor(
        type: UByte,
        ttl: UByte,
        senderID: String,
        payload: ByteArray
    ) : this(
        version = 1u,
        type = type,
        senderID = hexStringToByteArray(senderID),
        recipientID = null,
        timestamp = (System.currentTimeMillis()).toULong(),
        payload = payload,
        signature = null,
        ttl = ttl
    )

    fun toBinaryData(): ByteArray? {
        return BinaryProtocol.encode(this)
    }

    /**
     * Create binary representation for signing (without signature and TTL fields)
     * TTL is excluded because it changes during packet relay operations
     */
    fun toBinaryDataForSigning(): ByteArray? {
        // Create a copy without signature and with fixed TTL for signing
        // TTL must be excluded because it changes during relay
        val unsignedPacket = BitchatPacket(
            version = version,
            type = type,
            senderID = senderID,
            recipientID = recipientID,
            timestamp = timestamp,
            payload = payload,
            signature = null, // Remove signature for signing
            route = route,
            ttl = com.bitchat.android.util.AppConstants.SYNC_TTL_HOPS // Use fixed TTL=0 for signing to ensure relay compatibility
        )
        return BinaryProtocol.encode(unsignedPacket)
    }

    companion object {
        fun fromBinaryData(data: ByteArray): BitchatPacket? {
            return BinaryProtocol.decode(data)
        }
        
        /**
         * Convert hex string peer ID to binary data (8 bytes) - exactly same as iOS
         */
        private fun hexStringToByteArray(hexString: String): ByteArray {
            val result = ByteArray(8) { 0 } // Initialize with zeros, exactly 8 bytes
            var tempID = hexString
            var index = 0
            
            while (tempID.length >= 2 && index < 8) {
                val hexByte = tempID.substring(0, 2)
                val byte = hexByte.toIntOrNull(16)?.toByte()
                if (byte != null) {
                    result[index] = byte
                }
                tempID = tempID.substring(2)
                index++
            }
            
            return result
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BitchatPacket

        if (version != other.version) return false
        if (type != other.type) return false
        if (!senderID.contentEquals(other.senderID)) return false
        if (recipientID != null) {
            if (other.recipientID == null) return false
            if (!recipientID.contentEquals(other.recipientID)) return false
        } else if (other.recipientID != null) return false
        if (timestamp != other.timestamp) return false
        if (!payload.contentEquals(other.payload)) return false
        if (signature != null) {
            if (other.signature == null) return false
            if (!signature.contentEquals(other.signature)) return false
        } else if (other.signature != null) return false
        if (ttl != other.ttl) return false
        if (route != null || other.route != null) {
            val a = route?.map { it.toList() } ?: emptyList()
            val b = other.route?.map { it.toList() } ?: emptyList()
            if (a != b) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + senderID.contentHashCode()
        result = 31 * result + (recipientID?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (signature?.contentHashCode() ?: 0)
        result = 31 * result + ttl.hashCode()
        result = 31 * result + (route?.fold(1) { acc, bytes -> 31 * acc + bytes.contentHashCode() } ?: 0)
        return result
    }
}

/**
 * Binary Protocol implementation - supports v1 and v2, backward compatible
 */
object BinaryProtocol {
    const val V1_HEADER_SIZE = 14
    const val V2_HEADER_SIZE = 16
    const val SENDER_ID_SIZE = 8
    const val RECIPIENT_ID_SIZE = 8
    const val SIGNATURE_SIZE = 64

    object Flags {
        const val HAS_RECIPIENT: UByte = 0x01u
        const val HAS_SIGNATURE: UByte = 0x02u
        const val IS_COMPRESSED: UByte = 0x04u
        const val HAS_ROUTE: UByte = 0x08u
    }

    private fun safeLogError(message: String, throwable: Exception? = null) {
        try {
            if (throwable != null) {
                Log.e("BinaryProtocol", message, throwable)
            } else {
                Log.e("BinaryProtocol", message)
            }
        } catch (_: RuntimeException) {
            // Ignore Log errors in local unit tests.
        }
    }

    private fun safeLogWarn(message: String) {
        try {
            Log.w("BinaryProtocol", message)
        } catch (_: RuntimeException) {
            // Ignore Log errors in local unit tests.
        }
    }

    private fun getHeaderSize(version: UByte): Int {
        return when (version) {
            1u.toUByte() -> V1_HEADER_SIZE
            2u.toUByte() -> V2_HEADER_SIZE
            else -> -1
        }
    }

    private fun lengthFieldSize(version: UByte): Int {
        return if (version == 2u.toUByte()) 4 else 2
    }
    
    fun encode(packet: BitchatPacket, padding: Boolean = true): ByteArray? {
        try {
            val version = packet.version
            if (version != 1u.toUByte() && version != 2u.toUByte()) return null

            // Try to compress payload if beneficial
            var payload = packet.payload
            var originalPayloadSize: Int? = null
            var isCompressed = false

            val maxRepresentable = if (version == 2u.toUByte()) UInt.MAX_VALUE.toLong() else UShort.MAX_VALUE.toLong()
            if (CompressionUtil.shouldCompress(payload) && payload.size.toLong() <= maxRepresentable) {
                CompressionUtil.compress(payload)?.let { compressedPayload ->
                    originalPayloadSize = payload.size
                    payload = compressedPayload
                    isCompressed = true
                }
            }

            val lengthFieldBytes = lengthFieldSize(version)
            val originalRoute = packet.route ?: emptyList()
            if (originalRoute.any { it.isEmpty() }) return null
            val sanitizedRoute = originalRoute.map { hop ->
                val trimmed = hop.take(SENDER_ID_SIZE).toByteArray()
                if (trimmed.size < SENDER_ID_SIZE) trimmed + ByteArray(SENDER_ID_SIZE - trimmed.size) else trimmed
            }
            if (sanitizedRoute.size > 255) return null
            val hasRoute = sanitizedRoute.isNotEmpty()
            val routeLength = if (hasRoute) 1 + sanitizedRoute.size * SENDER_ID_SIZE else 0

            val sizeFieldBytes = if (isCompressed) lengthFieldBytes else 0
            val payloadDataSize = routeLength + payload.size + sizeFieldBytes

            if (version == 1u.toUByte() && payloadDataSize > UShort.MAX_VALUE.toInt()) return null
            if (version == 2u.toUByte() && payloadDataSize.toLong() > UInt.MAX_VALUE.toLong()) return null

            val headerSize = getHeaderSize(version)
            if (headerSize <= 0) return null
            val recipientBytes = if (packet.recipientID != null) RECIPIENT_ID_SIZE else 0
            val signatureBytes = if (packet.signature != null) SIGNATURE_SIZE else 0
            val capacity = headerSize + SENDER_ID_SIZE + recipientBytes + payloadDataSize + signatureBytes + 16
            val buffer = ByteBuffer.allocate(capacity.coerceAtLeast(256)).apply { order(ByteOrder.BIG_ENDIAN) }
            
            // Header
            buffer.put(packet.version.toByte())
            buffer.put(packet.type.toByte())
            buffer.put(packet.ttl.toByte())
            
            // Timestamp (8 bytes, big-endian)
            buffer.putLong(packet.timestamp.toLong())
            
            // Flags
            var flags: UByte = 0u
            if (packet.recipientID != null) {
                flags = flags or Flags.HAS_RECIPIENT
            }
            if (packet.signature != null) {
                flags = flags or Flags.HAS_SIGNATURE
            }
            if (isCompressed) {
                flags = flags or Flags.IS_COMPRESSED
            }
            if (hasRoute) {
                flags = flags or Flags.HAS_ROUTE
            }
            buffer.put(flags.toByte())
            
            // Payload length (2 or 4 bytes, big-endian) - includes original size if compressed
            if (version == 2u.toUByte()) {
                buffer.putInt(payloadDataSize)
            } else {
                buffer.putShort(payloadDataSize.toShort())
            }
            
            // SenderID (exactly 8 bytes)
            val senderBytes = packet.senderID.take(SENDER_ID_SIZE).toByteArray()
            buffer.put(senderBytes)
            if (senderBytes.size < SENDER_ID_SIZE) {
                buffer.put(ByteArray(SENDER_ID_SIZE - senderBytes.size))
            }
            
            // RecipientID (if present)
            packet.recipientID?.let { recipientID ->
                val recipientBytes = recipientID.take(RECIPIENT_ID_SIZE).toByteArray()
                buffer.put(recipientBytes)
                if (recipientBytes.size < RECIPIENT_ID_SIZE) {
                    buffer.put(ByteArray(RECIPIENT_ID_SIZE - recipientBytes.size))
                }
            }

            // Route (optional, v2+ only): 1 byte count + N*8 bytes
            if (hasRoute) {
                val count = sanitizedRoute.size
                buffer.put(count.toByte())
                sanitizedRoute.forEach { hop -> buffer.put(hop) }
            }
            
            // Payload (with original size prepended if compressed)
            if (isCompressed) {
                val originalSize = originalPayloadSize ?: return null
                if (version == 2u.toUByte()) {
                    buffer.putInt(originalSize)
                } else {
                    buffer.putShort(originalSize.toShort())
                }
            }
            buffer.put(payload)
            
            // Signature (if present)
            packet.signature?.let { signature ->
                buffer.put(signature.take(SIGNATURE_SIZE).toByteArray())
            }
            
            val result = ByteArray(buffer.position())
            buffer.rewind()
            buffer.get(result)

            return if (padding) {
                val optimalSize = MessagePadding.optimalBlockSize(result.size)
                MessagePadding.pad(result, optimalSize)
            } else {
                result
            }
            
        } catch (e: Exception) {
            safeLogError("Error encoding packet type ${packet.type}: ${e.message}", e)
            return null
        }
    }
    
    fun decode(data: ByteArray): BitchatPacket? {
        // Try decode as-is first (robust when padding wasn't applied) - iOS fix
        decodeCore(data)?.let { return it }
        
        // If that fails, try after removing padding
        val unpadded = MessagePadding.unpad(data)
        if (unpadded.contentEquals(data)) return null // No padding was removed, already failed
        
        return decodeCore(unpadded)
    }
    
    /**
     * Core decoding implementation used by decode() with and without padding removal - iOS fix
     */
    private fun decodeCore(raw: ByteArray): BitchatPacket? {
        try {
            if (raw.size < V1_HEADER_SIZE + SENDER_ID_SIZE) return null

            var offset = 0
            fun require(n: Int): Boolean = offset + n <= raw.size
            fun read8(): UByte? {
                if (!require(1)) return null
                val value = raw[offset].toUByte()
                offset += 1
                return value
            }
            fun read16(): UShort? {
                if (!require(2)) return null
                val value = ((raw[offset].toInt() and 0xFF) shl 8) or (raw[offset + 1].toInt() and 0xFF)
                offset += 2
                return value.toUShort()
            }
            fun read32(): UInt? {
                if (!require(4)) return null
                val value = ((raw[offset].toLong() and 0xFF) shl 24) or
                    ((raw[offset + 1].toLong() and 0xFF) shl 16) or
                    ((raw[offset + 2].toLong() and 0xFF) shl 8) or
                    (raw[offset + 3].toLong() and 0xFF)
                offset += 4
                return value.toUInt()
            }
            fun readData(n: Int): ByteArray? {
                if (!require(n)) return null
                val value = raw.copyOfRange(offset, offset + n)
                offset += n
                return value
            }

            val version = read8() ?: return null
            if (version != 1u.toUByte() && version != 2u.toUByte()) return null
            val headerSize = getHeaderSize(version)
            if (headerSize <= 0) return null
            if (raw.size < headerSize + SENDER_ID_SIZE) return null

            val type = read8() ?: return null
            val ttl = read8() ?: return null

            var timestamp = 0uL
            repeat(8) {
                val b = read8() ?: return null
                timestamp = (timestamp shl 8) or b.toULong()
            }

            val flags = read8() ?: return null
            val hasRecipient = (flags and Flags.HAS_RECIPIENT) != 0u.toUByte()
            val hasSignature = (flags and Flags.HAS_SIGNATURE) != 0u.toUByte()
            val isCompressed = (flags and Flags.IS_COMPRESSED) != 0u.toUByte()
            val hasRoute = (flags and Flags.HAS_ROUTE) != 0u.toUByte()

            val payloadLength = if (version == 2u.toUByte()) {
                read32()?.toInt() ?: return null
            } else {
                read16()?.toInt() ?: return null
            }
            if (payloadLength < 0) return null

            val senderID = readData(SENDER_ID_SIZE) ?: return null
            val recipientID = if (hasRecipient) {
                readData(RECIPIENT_ID_SIZE) ?: return null
            } else null

            var remainingPayloadBytes = payloadLength
            var route: List<ByteArray>? = null
            if (hasRoute) {
                if (remainingPayloadBytes < 1) return null
                val routeCount = read8()?.toInt() ?: return null
                remainingPayloadBytes -= 1
                if (routeCount > 0) {
                    val hops = mutableListOf<ByteArray>()
                    repeat(routeCount) {
                        if (remainingPayloadBytes < SENDER_ID_SIZE) return null
                        val hop = readData(SENDER_ID_SIZE) ?: return null
                        remainingPayloadBytes -= SENDER_ID_SIZE
                        hops.add(hop)
                    }
                    route = hops
                }
            }

            val payload = if (isCompressed) {
                val lengthFieldBytes = lengthFieldSize(version)
                if (remainingPayloadBytes < lengthFieldBytes) return null
                val originalSize = if (version == 2u.toUByte()) {
                    read32()?.toInt() ?: return null
                } else {
                    read16()?.toInt() ?: return null
                }
                remainingPayloadBytes -= lengthFieldBytes
                if (originalSize < 0 || originalSize > com.bitchat.android.util.FileTransferLimits.maxFramedFileBytes) return null

                val compressedSize = remainingPayloadBytes
                if (compressedSize <= 0) return null
                val compressed = readData(compressedSize) ?: return null
                remainingPayloadBytes = 0

                val ratio = originalSize.toDouble() / compressedSize.toDouble()
                if (ratio > 50_000.0) {
                    safeLogWarn("🚫 Suspicious compression ratio: ${ratio}:1")
                    return null
                }

                val decompressed = CompressionUtil.decompress(compressed, originalSize) ?: return null
                if (decompressed.size != originalSize) return null
                decompressed
            } else {
                val rawPayload = readData(remainingPayloadBytes) ?: return null
                remainingPayloadBytes = 0
                rawPayload
            }

            val signature = if (hasSignature) {
                readData(SIGNATURE_SIZE) ?: return null
            } else null

            if (offset > raw.size) return null

            return BitchatPacket(
                version = version,
                type = type,
                senderID = senderID,
                recipientID = recipientID,
                timestamp = timestamp,
                payload = payload,
                signature = signature,
                ttl = ttl,
                route = route
            )
            
        } catch (e: Exception) {
            safeLogError("Error decoding packet: ${e.message}", e)
            return null
        }
    }
}
