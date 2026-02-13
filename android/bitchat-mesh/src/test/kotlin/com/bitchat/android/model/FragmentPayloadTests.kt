package com.bitchat.android.model

import com.bitchat.android.protocol.MessageType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FragmentPayloadTests {
    @Test
    fun encodeDecodeRoundTrip() {
        val fragmentId = ByteArray(FragmentPayload.FRAGMENT_ID_SIZE) { 0x01 }
        val payload = FragmentPayload(
            fragmentID = fragmentId,
            index = 1,
            total = 3,
            originalType = MessageType.MESSAGE.value,
            data = "chunk".toByteArray(Charsets.UTF_8)
        )

        val encoded = payload.encode()
        val decoded = FragmentPayload.decode(encoded)
        assertNotNull(decoded)
        assertEquals(payload, decoded)
    }

    @Test
    fun decodeReturnsNullForShortPayload() {
        val tooShort = ByteArray(FragmentPayload.HEADER_SIZE - 1)
        assertNull(FragmentPayload.decode(tooShort))
    }

    @Test
    fun isValidChecksConstraints() {
        val fragmentId = ByteArray(FragmentPayload.FRAGMENT_ID_SIZE) { 0x02 }
        val valid = FragmentPayload(fragmentId, 0, 1, MessageType.MESSAGE.value, byteArrayOf(0x01))
        assertTrue(valid.isValid())

        val emptyData = FragmentPayload(fragmentId, 0, 1, MessageType.MESSAGE.value, ByteArray(0))
        assertFalse(emptyData.isValid())

        val badIndex = FragmentPayload(fragmentId, 2, 2, MessageType.MESSAGE.value, byteArrayOf(0x01))
        assertFalse(badIndex.isValid())
    }

    @Test
    fun generateFragmentIdHasExpectedLength() {
        val fragmentId = FragmentPayload.generateFragmentID()
        assertEquals(FragmentPayload.FRAGMENT_ID_SIZE, fragmentId.size)
        val payload = FragmentPayload(fragmentId, 0, 1, MessageType.MESSAGE.value, byteArrayOf(0x01))
        val encoded = payload.encode()
        assertArrayEquals(fragmentId, encoded.copyOfRange(0, FragmentPayload.FRAGMENT_ID_SIZE))
    }
}
