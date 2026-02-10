package com.bitchat.android.model

import com.bitchat.android.util.decodeUtf8OrNull

/**
 * Identity announcement structure with TLV encoding.
 * Compatible with iOS AnnouncementPacket TLV format.
 */
data class AnnouncementPacket(
    val nickname: String,
    val noisePublicKey: ByteArray,
    val signingPublicKey: ByteArray,
    val directNeighbors: List<ByteArray>?
) {
    private enum class TLVType(val value: UByte) {
        NICKNAME(0x01u),
        NOISE_PUBLIC_KEY(0x02u),
        SIGNING_PUBLIC_KEY(0x03u),
        DIRECT_NEIGHBORS(0x04u);

        companion object {
            fun fromValue(value: UByte): TLVType? = values().find { it.value == value }
        }
    }

    fun encode(): ByteArray? {
        val nicknameData = nickname.toByteArray(Charsets.UTF_8)
        if (nicknameData.isEmpty() || nicknameData.size > 255) return null
        if (noisePublicKey.isEmpty() || noisePublicKey.size > 255) return null
        if (signingPublicKey.isEmpty() || signingPublicKey.size > 255) return null

        val result = mutableListOf<Byte>()

        result.add(TLVType.NICKNAME.value.toByte())
        result.add(nicknameData.size.toByte())
        result.addAll(nicknameData.toList())

        result.add(TLVType.NOISE_PUBLIC_KEY.value.toByte())
        result.add(noisePublicKey.size.toByte())
        result.addAll(noisePublicKey.toList())

        result.add(TLVType.SIGNING_PUBLIC_KEY.value.toByte())
        result.add(signingPublicKey.size.toByte())
        result.addAll(signingPublicKey.toList())

        if (!directNeighbors.isNullOrEmpty()) {
            val neighbors = directNeighbors.take(10)
            val combined = neighbors.fold(ByteArray(0)) { acc, hop -> acc + hop }
            if (combined.isNotEmpty() && combined.size % 8 == 0) {
                result.add(TLVType.DIRECT_NEIGHBORS.value.toByte())
                result.add(combined.size.toByte())
                result.addAll(combined.toList())
            }
        }

        return result.toByteArray()
    }

    companion object {
        fun decode(data: ByteArray): AnnouncementPacket? {
            var offset = 0
            var nickname: String? = null
            var noisePublicKey: ByteArray? = null
            var signingPublicKey: ByteArray? = null
            var directNeighbors: List<ByteArray>? = null

            while (offset + 2 <= data.size) {
                val typeValue = data[offset].toUByte()
                val type = TLVType.fromValue(typeValue)
                offset += 1

                val length = data[offset].toUByte().toInt()
                offset += 1

                if (offset + length > data.size) return null

                val value = data.copyOfRange(offset, offset + length)
                offset += length

                when (type) {
                    TLVType.NICKNAME -> {
                        val decoded = decodeUtf8OrNull(value)
                        if (!decoded.isNullOrEmpty()) {
                            nickname = decoded
                        }
                    }
                    TLVType.NOISE_PUBLIC_KEY -> {
                        if (value.isNotEmpty()) {
                            noisePublicKey = value
                        }
                    }
                    TLVType.SIGNING_PUBLIC_KEY -> {
                        if (value.isNotEmpty()) {
                            signingPublicKey = value
                        }
                    }
                    TLVType.DIRECT_NEIGHBORS -> {
                        if (length > 0 && length % 8 == 0) {
                            val hops = mutableListOf<ByteArray>()
                            var idx = 0
                            while (idx < value.size) {
                                hops.add(value.copyOfRange(idx, idx + 8))
                                idx += 8
                            }
                            directNeighbors = hops
                        }
                    }
                    null -> {
                        continue
                    }
                }
            }

            val n = nickname ?: return null
            val noise = noisePublicKey ?: return null
            val sign = signingPublicKey ?: return null

            return AnnouncementPacket(
                nickname = n,
                noisePublicKey = noise,
                signingPublicKey = sign,
                directNeighbors = directNeighbors
            )
        }
    }
}
