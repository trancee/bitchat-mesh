package com.bitchat.android.services

import android.content.Context

/**
 * Provides current user's nickname for announcements and leave messages.
 * If no nickname saved, falls back to the provided peerID.
 */
object NicknameProvider {
    @Volatile
    private var cachedNickname: String? = null

    fun setNickname(nickname: String?) {
        cachedNickname = nickname?.trim()?.ifEmpty { null }
    }

    fun getNickname(context: Context, myPeerID: String): String {
        val nick = cachedNickname
        return if (nick.isNullOrBlank()) myPeerID else nick
    }
}

