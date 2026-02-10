package com.bitchat.android.model

import com.bitchat.android.util.FileTransferLimits
import com.bitchat.android.util.decodeUtf8OrNull

/**
 * TLV payload for Bluetooth mesh file transfers.
 * Mirrors the iOS specification for cross-platform compatibility.
 */
data class BitchatFilePacket(
    val fileName: String?,
    val fileSize: Long?,
    val mimeType: String?,
    val content: ByteArray
) {
    private enum class TLVType(val v: UByte) {
        FILE_NAME(0x01u), FILE_SIZE(0x02u), MIME_TYPE(0x03u), CONTENT(0x04u);

        companion object { fun from(value: UByte) = values().find { it.v == value } }
    }

    fun encode(): ByteArray? {
        val resolvedSize = fileSize ?: content.size.toLong()
        if (resolvedSize > UInt.MAX_VALUE.toLong()) return null
        if (resolvedSize > FileTransferLimits.maxPayloadBytes) return null
        if (content.size > UInt.MAX_VALUE.toLong()) return null
        if (!FileTransferLimits.isValidPayload(content.size)) return null

        fun appendBE(value: Int, data: MutableList<Byte>) {
            data.add(((value shr 8) and 0xFF).toByte())
            data.add((value and 0xFF).toByte())
        }

        fun appendBE32(value: Long, data: MutableList<Byte>) {
            data.add(((value shr 24) and 0xFF).toByte())
            data.add(((value shr 16) and 0xFF).toByte())
            data.add(((value shr 8) and 0xFF).toByte())
            data.add((value and 0xFF).toByte())
        }

        val encoded = mutableListOf<Byte>()

        fileName?.let { name ->
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            if (nameBytes.size <= UShort.MAX_VALUE.toInt()) {
                encoded.add(TLVType.FILE_NAME.v.toByte())
                appendBE(nameBytes.size, encoded)
                encoded.addAll(nameBytes.toList())
            }
        }

        encoded.add(TLVType.FILE_SIZE.v.toByte())
        appendBE(4, encoded)
        appendBE32(resolvedSize, encoded)

        mimeType?.let { mime ->
            val mimeBytes = mime.toByteArray(Charsets.UTF_8)
            if (mimeBytes.size <= UShort.MAX_VALUE.toInt()) {
                encoded.add(TLVType.MIME_TYPE.v.toByte())
                appendBE(mimeBytes.size, encoded)
                encoded.addAll(mimeBytes.toList())
            }
        }

        encoded.add(TLVType.CONTENT.v.toByte())
        appendBE32(content.size.toLong(), encoded)
        encoded.addAll(content.toList())

        return encoded.toByteArray()
    }

    companion object {
        fun decode(data: ByteArray): BitchatFilePacket? {
            var offset = 0
            var name: String? = null
            var size: Long? = null
            var mime: String? = null
            var content = ByteArray(0)

            fun readLength(bytes: Int): Int? {
                if (offset + bytes > data.size) return null
                var result = 0L
                repeat(bytes) {
                    result = (result shl 8) or (data[offset].toLong() and 0xFF)
                    offset += 1
                }
                if (result > Int.MAX_VALUE) return null
                return result.toInt()
            }

            while (offset < data.size) {
                val typeRaw = data[offset].toUByte()
                offset += 1

                val tlvType = TLVType.from(typeRaw)
                val length = if (tlvType == TLVType.CONTENT) {
                    val snapshot = offset
                    val canonical = readLength(4)
                    if (canonical != null && offset + canonical <= data.size) {
                        canonical
                    } else {
                        offset = snapshot
                        readLength(2)
                    }
                } else {
                    readLength(2)
                } ?: return null

                if (length < 0 || offset + length > data.size) return null
                val value = data.copyOfRange(offset, offset + length)
                offset += length

                when (tlvType) {
                    TLVType.FILE_NAME -> {
                        name = decodeUtf8OrNull(value)
                    }
                    TLVType.FILE_SIZE -> {
                        if (length == 4 || length == 8) {
                            var parsed = 0L
                            value.forEach { b ->
                                parsed = (parsed shl 8) or (b.toLong() and 0xFF)
                            }
                            if (parsed > FileTransferLimits.maxPayloadBytes) return null
                            size = parsed
                        }
                    }
                    TLVType.MIME_TYPE -> {
                        mime = decodeUtf8OrNull(value)
                    }
                    TLVType.CONTENT -> {
                        val proposed = content.size + value.size
                        if (proposed > FileTransferLimits.maxPayloadBytes) return null
                        content += value
                    }
                    null -> continue
                }
            }

            if (content.isEmpty()) return null
            if (!FileTransferLimits.isValidPayload(content.size)) return null

            return BitchatFilePacket(
                fileName = name,
                fileSize = size ?: content.size.toLong(),
                mimeType = mime,
                content = content
            )
        }
    }
}

