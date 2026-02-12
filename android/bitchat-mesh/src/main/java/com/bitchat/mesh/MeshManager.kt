package com.bitchat.mesh

import android.content.Context
import com.bitchat.android.mesh.BluetoothMeshDelegate
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.mesh.TransferProgressManager
import com.bitchat.android.model.BitchatFilePacket
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.services.NicknameProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MeshManager(private val context: Context) {
    private var service: BluetoothMeshService? = null
    private var listener: MeshListener? = null
    private var nickname: String? = null
    private var lastPeers: Set<String> = emptySet()
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        progressScope.launch {
            TransferProgressManager.events.collect { event ->
                listener?.onTransferProgress(event.transferId, event.sent, event.total, event.completed)
            }
        }
    }

    fun setListener(listener: MeshListener?) {
        this.listener = listener
        service?.delegate = buildDelegate()
    }

    fun start(nickname: String? = null) {
        this.nickname = nickname?.trim()?.ifEmpty { null }
        NicknameProvider.setNickname(this.nickname)
        val mesh = BluetoothMeshService(context.applicationContext)
        mesh.delegate = buildDelegate()
        mesh.startServices()
        service = mesh
        listener?.onStarted()
    }

    fun stop() {
        service?.stopServices()
        service = null
        lastPeers = emptySet()
        listener?.onStopped()
    }

    fun myPeerId(): String? = service?.myPeerID

    fun isRunning(): Boolean = service?.isReusable() == true

    fun isStarted(): Boolean = service != null && service?.isReusable() == true

    fun sendBroadcastMessage(content: String, mentions: List<String> = emptyList(), channel: String? = null) {
        service?.sendMessage(content, mentions, channel)
        listener?.onSent(null, null)
    }

    fun sendPrivateMessage(
        content: String,
        recipientPeerID: String,
        recipientNickname: String,
        messageID: String? = null
    ) {
        service?.sendPrivateMessage(content, recipientPeerID, recipientNickname, messageID)
        listener?.onSent(messageID, recipientPeerID)
    }

    fun establish(peerID: String) {
        service?.initiateNoiseHandshake(peerID)
    }

    fun isEstablished(peerID: String): Boolean = service?.hasEstablishedSession(peerID) == true

    fun sendFileBroadcast(file: BitchatFilePacket) {
        service?.sendFileBroadcast(file)
    }

    fun sendFilePrivate(recipientPeerID: String, file: BitchatFilePacket) {
        service?.sendFilePrivate(recipientPeerID, file)
    }

    fun cancelFileTransfer(transferId: String): Boolean {
        return service?.cancelFileTransfer(transferId) == true
    }

    fun peerNicknames(): Map<String, String> = service?.getPeerNicknames().orEmpty()

    fun peerRssi(): Map<String, Int> = service?.getPeerRSSI().orEmpty()

    private fun buildDelegate(): BluetoothMeshDelegate {
        return object : BluetoothMeshDelegate {
            override fun didReceiveMessage(message: BitchatMessage) {
                listener?.onMessageReceived(message)
                listener?.onReceived(message)
            }

            override fun didUpdatePeerList(peers: List<String>) {
                val newPeers = peers.toSet()
                val added = newPeers - lastPeers
                val removed = lastPeers - newPeers
                if (added.isNotEmpty()) {
                    added.forEach { peerID ->
                        listener?.onFound(peerID)
                        listener?.onConnected(peerID)
                    }
                }
                if (removed.isNotEmpty()) {
                    removed.forEach { peerID ->
                        listener?.onLost(peerID)
                        listener?.onDisconnected(peerID)
                    }
                }
                lastPeers = newPeers
                listener?.onPeerListUpdated(peers)
            }

            override fun didEstablishSession(peerID: String) {
                listener?.onEstablished(peerID)
            }

            override fun didUpdatePeerRSSI(peerID: String, rssi: Int) {
                listener?.onRSSIUpdated(peerID, rssi)
            }

            override fun didReceiveChannelLeave(channel: String, fromPeer: String) {
                // Not exposed in the minimal listener API.
            }

            override fun didReceiveDeliveryAck(messageID: String, recipientPeerID: String) {
                listener?.onDeliveryAck(messageID, recipientPeerID)
            }

            override fun didReceiveReadReceipt(messageID: String, recipientPeerID: String) {
                listener?.onReadReceipt(messageID, recipientPeerID)
            }

            override fun didReceiveVerifyChallenge(peerID: String, payload: ByteArray, timestampMs: Long) {
                listener?.onVerifyChallenge(peerID, payload, timestampMs)
            }

            override fun didReceiveVerifyResponse(peerID: String, payload: ByteArray, timestampMs: Long) {
                listener?.onVerifyResponse(peerID, payload, timestampMs)
            }

            override fun didReceiveFileTransfer(peerID: String, fileName: String, fileSize: Long, mimeType: String, localPath: String) {
                listener?.onFileReceived(peerID, fileName, fileSize, mimeType, localPath)
            }

            override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? {
                return null
            }

            override fun getNickname(): String? {
                return nickname
            }

            override fun isFavorite(peerID: String): Boolean {
                return false
            }
        }
    }
}
