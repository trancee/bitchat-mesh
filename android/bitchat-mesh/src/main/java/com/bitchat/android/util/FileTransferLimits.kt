package com.bitchat.android.util

import com.bitchat.android.protocol.BinaryProtocol

/**
 * Centralized thresholds for Bluetooth file transfers.
 */
object FileTransferLimits {
    const val maxPayloadBytes: Int = 1 * 1024 * 1024
    const val maxVoiceNoteBytes: Int = 512 * 1024
    const val maxImageBytes: Int = 512 * 1024

    val maxFramedFileBytes: Int = run {
        val maxMetadataBytes = UShort.MAX_VALUE.toInt() * 2
        val tlvEnvelopeOverhead = 18 + maxMetadataBytes
        val binaryEnvelopeOverhead = BinaryProtocol.V2_HEADER_SIZE +
            BinaryProtocol.SENDER_ID_SIZE +
            BinaryProtocol.RECIPIENT_ID_SIZE +
            BinaryProtocol.SIGNATURE_SIZE
        maxPayloadBytes + tlvEnvelopeOverhead + binaryEnvelopeOverhead
    }

    fun isValidPayload(size: Int): Boolean = size <= maxPayloadBytes
}
