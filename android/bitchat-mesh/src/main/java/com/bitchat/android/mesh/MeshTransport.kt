package com.bitchat.android.mesh

import com.bitchat.android.model.BitchatFilePacket

interface MeshTransport {
    var delegate: BluetoothMeshDelegate?
    val myPeerID: String

    fun startServices()
    fun stopServices()
    fun isReusable(): Boolean

    fun sendMessage(content: String, mentions: List<String> = emptyList(), channel: String? = null)
    fun sendPrivateMessage(
        content: String,
        recipientPeerID: String,
        recipientNickname: String,
        messageID: String? = null
    )

    fun initiateNoiseHandshake(peerID: String)
    fun hasEstablishedSession(peerID: String): Boolean

    fun sendFileBroadcast(file: BitchatFilePacket)
    fun sendFilePrivate(recipientPeerID: String, file: BitchatFilePacket)
    fun cancelFileTransfer(transferId: String): Boolean

    fun getPeerNicknames(): Map<String, String>
    fun getPeerRSSI(): Map<String, Int>
}
