package com.bitchat.android.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NicknameProviderTests {
    @Test
    fun returnsPeerIdWhenNicknameMissing() {
        NicknameProvider.setNickname(null)

        val nickname = NicknameProvider.getNickname(context = DummyContext(), myPeerID = "peer-1")

        assertEquals("peer-1", nickname)
    }

    @Test
    fun returnsTrimmedNickname() {
        NicknameProvider.setNickname("  Alice  ")

        val nickname = NicknameProvider.getNickname(context = DummyContext(), myPeerID = "peer-2")

        assertEquals("Alice", nickname)
    }

    private class DummyContext : android.content.ContextWrapper(null)
}
